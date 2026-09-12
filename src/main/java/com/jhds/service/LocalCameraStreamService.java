package com.jhds.service;

import com.jhds.config.LocalCameraProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.annotation.PreDestroy;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.charset.StandardCharsets;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.StandardOpenOption;
import java.util.UUID;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/** Bridges the camera's local RTSP stream to browser-compatible low-latency HLS. */
@Slf4j
@Service
public class LocalCameraStreamService {

    @Autowired
    private LocalCameraProperties properties;

    private final Object processLock = new Object();
    private volatile Process ffmpegProcess;
    private volatile String lastError;
    private volatile int actualWidth;
    private volatile int actualHeight;
    private volatile boolean qualityVerified;

    private volatile long startedAt;
    private volatile String activePath;
    private volatile long lastRestartAt;
    private volatile int restartCount;
    private volatile long nextRetryAt;
    private volatile long playlistBaselineModifiedAt;
    private volatile StreamQuality streamQuality = StreamQuality.HD;
    private FileChannel ownershipChannel;
    private FileLock ownershipLock;
    private volatile String segmentPrefix = "segment-unstarted-";
    private volatile boolean nativeVideo;

    private void acquireOwnership(Path output) throws IOException {
        if (ownershipLock != null && ownershipLock.isValid()) return;
        FileChannel channel = FileChannel.open(output.resolve("bridge.lock"),
                StandardOpenOption.CREATE, StandardOpenOption.WRITE);
        try {
            FileLock lock = channel.tryLock();
            if (lock == null) throw new IOException("视频目录已被另一个服务占用");
            ownershipChannel = channel;
            ownershipLock = lock;
        } catch (Exception e) {
            channel.close();
            throw new IOException("无法取得视频目录独占锁", e);
        }
    }

    private boolean isNativeH264(String inputPath) {
        Process probe = null;
        try {
            probe = new ProcessBuilder(properties.getFfprobePath(), "-v", "quiet",
                    "-rtsp_transport", "tcp", "-timeout", "8000000", "-select_streams", "v:0",
                    "-show_entries", "stream=codec_name", "-of", "default=nw=1:nk=1",
                    buildRtspUrl(inputPath)).redirectError(ProcessBuilder.Redirect.INHERIT).start();
            if (!probe.waitFor(12, TimeUnit.SECONDS)) return false;
            return probe.exitValue() == 0
                    && "h264".equals(new String(readAll(probe), StandardCharsets.UTF_8).trim());
        } catch (Exception e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            return false;
        } finally {
            if (probe != null && probe.isAlive()) probe.destroyForcibly();
        }
    }

    private enum StreamQuality {
        SMOOTH("smooth", 480, "1200k", "1800k"),
        HD("hd", 720, "2400k", "3600k"),
        UHD_4K("4k", 2160, "9000k", "13500k");

        private final String value;
        private final int height;
        private final String bitrate;
        private final String bufferSize;

        StreamQuality(String value, int height, String bitrate, String bufferSize) {
            this.value = value;
            this.height = height;
            this.bitrate = bitrate;
            this.bufferSize = bufferSize;
        }

        private static StreamQuality from(String value) {
            if (value != null) {
                for (StreamQuality quality : values()) {
                    if (quality.value.equalsIgnoreCase(value.trim())) return quality;
                }
            }
            throw new IllegalArgumentException("不支持的清晰度档位，请选择 smooth、hd 或 4k");
        }
    }

    private Path pidFile() {
        return outputDirectory().resolve("ffmpeg.pid");
    }

    private int targetHeight() {
        return streamQuality == StreamQuality.HD ? properties.getWidth() : streamQuality.height;
    }

    private String targetBitrate() {
        return streamQuality == StreamQuality.HD ? properties.getVideoBitrate() : streamQuality.bitrate;
    }

    private String targetBufferSize() {
        return streamQuality == StreamQuality.HD ? properties.getVideoBufferSize() : streamQuality.bufferSize;
    }

    private int targetFps() {
        return Math.max(1, Math.min(60, properties.getFps()));
    }

    public boolean isEnabled() {
        return properties.isEnabled();
    }

    public String getHlsPath() {
        return properties.getHlsPath();
    }

    /** Wait briefly for FFmpeg to publish a usable playlist after startup. */
    public boolean awaitReady(long timeoutMs) {
        long deadline = System.currentTimeMillis() + Math.max(0L, timeoutMs);
        Path playlist = outputDirectory().resolve("index.m3u8");
        while (System.currentTimeMillis() <= deadline) {
            if (isCurrentPlaylistReady(playlist)) return true;
            try {
                Thread.sleep(150L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        return isCurrentPlaylistReady(playlist);
    }

    public void ensureRunning() {
        synchronized (processLock) {
            long now = System.currentTimeMillis();
            if (ffmpegProcess != null && ffmpegProcess.isAlive()) {
                Path playlist = outputDirectory().resolve("index.m3u8");
                boolean ready = isCurrentPlaylistReady(playlist);
                // A camera may wait for its next key frame before FFmpeg can
                // write the first HLS segment. Do not restart the bridge while
                // that initial handshake is still in progress.
                boolean startupGracePeriod = startedAt > 0
                        && now - startedAt < Math.max(10000, properties.getStartupTimeoutMs());
                if (ready || startupGracePeriod) return;
                if (now - lastRestartAt < Math.max(1000, properties.getRestartCooldownMs())) {
                    return;
                }
                log.warn("Local RTSP bridge is alive but has produced no HLS playlist; restarting it");
                rotatePath();
            } else if (ffmpegProcess != null) {
                // A failed FFmpeg process can exit before the watchdog sees a
                // playlist. Rotate once so a bad main stream cannot loop
                // forever without trying the camera sub-stream.
                if (now < nextRetryAt) return;
                rotatePath();
            }
            stopProcessLocked();
            try {
                Path output = outputDirectory();
                Files.createDirectories(output);
                acquireOwnership(output);
                stopStaleProcesses(output);
                cleanOutput(output);
                Path playlist = output.resolve("index.m3u8");
                playlistBaselineModifiedAt = Files.exists(playlist)
                        ? Files.getLastModifiedTime(playlist).toMillis() : 0L;

                String inputPath = selectInitialPath();
                segmentPrefix = "segment-" + UUID.randomUUID().toString() + "-";
                nativeVideo = streamQuality == StreamQuality.UHD_4K && isNativeH264(inputPath);
                List<String> command = buildCommand(output, inputPath);
                ProcessBuilder builder = new ProcessBuilder(command);
                builder.directory(new File("."));
                // Use a per-process log file. Reusing one log file can fail on
                // Windows while a previous FFmpeg process is still releasing
                // its handle during a recovery restart.
                Path logFile = output.resolve("ffmpeg-" + System.currentTimeMillis() + ".log");
                builder.redirectError(logFile.toFile());
                ffmpegProcess = builder.start();
                long processId = processId(ffmpegProcess);
                if (processId > 0) {
                    Files.write(pidFile(), String.valueOf(processId).getBytes(StandardCharsets.US_ASCII));
                }
                startedAt = System.currentTimeMillis();
                lastRestartAt = startedAt;
                restartCount++;
                nextRetryAt = 0L;
                lastError = null;
                actualWidth = 0;
                actualHeight = 0;
                qualityVerified = false;
                log.info("Local RTSP bridge started: {}:{}{}, HLS directory={}, quality={}, targetHeight={}",
                        properties.getHost(), properties.getPort(), inputPath, output,
                        streamQuality.value, targetHeight());
            } catch (Exception e) {
                lastError = e.getMessage();
                nextRetryAt = System.currentTimeMillis() + Math.max(1000, properties.getRestartCooldownMs());
                log.error("Unable to start local RTSP bridge", e);
                String detail = e.getMessage() == null ? "未知错误" : e.getMessage();
                throw new IllegalStateException("本地摄像头流启动失败：" + detail
                        + "；请确认 FFmpeg 已安装并可在 PATH 中执行", e);
            }
        }
    }

    private List<String> buildCommand(Path output, String inputPath) {
        List<String> command = new ArrayList<>();
        command.add(properties.getFfmpegPath());
        command.add("-hide_banner");
        command.add("-loglevel");
        command.add("warning");
        command.add("-rtsp_transport");
        command.add(properties.getTransport() == null || properties.getTransport().trim().isEmpty()
                ? "udp" : properties.getTransport().trim());
        // Fail a dead RTSP socket quickly so the watchdog can switch streams.
        command.add("-timeout");
        command.add(String.valueOf(Math.max(1000, properties.getConnectTimeoutMs()) * 1000L));
        command.add("-rtsp_flags");
        command.add("prefer_tcp");
        command.add("-fflags");
        command.add("+genpts+discardcorrupt");
        command.add("-flags");
        command.add("low_delay");
        command.add("-max_delay");
        command.add("500000");
        command.add("-reorder_queue_size");
        command.add("0");
        command.add("-analyzeduration");
        command.add("1000000");
        command.add("-probesize");
        command.add("1000000");
        // This camera's RTP clock jumps backwards; use arrival timestamps.
        command.add("-use_wallclock_as_timestamps");
        command.add("1");
        command.add("-i");
        command.add(buildRtspUrl(inputPath));
        command.add("-map");
        command.add("0:v:0");
        command.add("-map");
        command.add("0:a:0?");
        if (streamQuality != StreamQuality.UHD_4K) {
            command.add("-vf");
            command.add("fps=" + targetFps() + ",scale=-2:" + targetHeight() + ",format=yuv420p");
        }
        if (nativeVideo) {
            command.add("-c:v");
            command.add("copy");
        } else {
        command.add("-r");
        command.add(String.valueOf(targetFps()));
        command.add("-fps_mode");
        command.add("cfr");
        command.add("-c:v");
        command.add("libx264");
        command.add("-preset");
        command.add("ultrafast");
        command.add("-tune");
        command.add("zerolatency");
        command.add("-profile:v");
        command.add("main");
        command.add("-x264-params");
        // Repeat SPS/PPS on every IDR frame so recovered clients do not
        // display a partial H.264 frame after a transient packet loss.
        command.add("keyint=" + targetFps() + ":min-keyint=" + targetFps()
                + ":scenecut=0:repeat-headers=1");
        command.add("-pix_fmt");
        command.add("yuv420p");
        command.add("-b:v");
        command.add(targetBitrate());
        command.add("-maxrate");
        command.add(targetBitrate());
        command.add("-bufsize");
        command.add(targetBufferSize());
        // A one-second GOP keeps segment timing stable at the configured source rate.
        command.add("-g");
        command.add(String.valueOf(targetFps()));
        command.add("-keyint_min");
        command.add(String.valueOf(targetFps()));
        command.add("-sc_threshold");
        command.add("0");
        }
        command.add("-c:a");
        command.add("aac");
        command.add("-b:a");
        command.add("64k");
        command.add("-ac");
        command.add("1");
        command.add("-af");
        command.add("aresample=async=1000:first_pts=0");
        command.add("-max_muxing_queue_size");
        command.add("1024");
        command.add("-avoid_negative_ts");
        command.add("make_zero");
        command.add("-muxdelay");
        command.add("0");
        command.add("-muxpreload");
        command.add("0");
        command.add("-f");
        command.add("hls");
        command.add("-hls_time");
        command.add(String.valueOf(Math.max(1, properties.getSegmentSeconds())));
        command.add("-hls_list_size");
        command.add(String.valueOf(Math.max(2, properties.getListSize())));
        command.add("-hls_delete_threshold");
        command.add("6");
        command.add("-hls_start_number_source");
        command.add("epoch");
        command.add("-hls_flags");
        // temp_file prevents the browser from reading a partially-written
        // segment during a reconnect. omit_endlist keeps the playlist live.
        command.add("delete_segments+independent_segments+program_date_time+temp_file+discont_start");
        command.add("-hls_segment_filename");
        command.add(output.resolve(segmentPrefix + "%010d.ts").toString());
        command.add(output.resolve("index.m3u8").toString());
        return command;
    }

    private String buildRtspUrl(String streamPath) {
        String user = properties.getUsername() == null ? "" : properties.getUsername();
        String password = properties.getPassword() == null ? "" : properties.getPassword();
        String auth = user.isEmpty() ? "" : user + (password.isEmpty() ? "" : ":" + password) + "@";
        String path = streamPath == null ? "/ch1/main" : streamPath.trim();
        if (!path.startsWith("/")) path = "/" + path;
        return "rtsp://" + auth + properties.getHost() + ":" + properties.getPort() + path;
    }

    private String configuredPath() {
        String path = properties.getPath() == null ? "/ch1/main" : properties.getPath().trim();
        return path.isEmpty() ? "/ch1/main" : (path.startsWith("/") ? path : "/" + path);
    }

    private String fallbackPath() {
        String path = properties.getFallbackPath();
        if (path == null || path.trim().isEmpty()) return null;
        path = path.trim();
        return path.startsWith("/") ? path : "/" + path;
    }

    private String selectInitialPath() {
        if (activePath != null && !activePath.trim().isEmpty()) return activePath;
        String configured = configuredPath();
        String fallback = fallbackPath();
        if (streamQuality != StreamQuality.UHD_4K
                && properties.isPreferFallback() && fallback != null && !fallback.equalsIgnoreCase(configured)) {
            activePath = fallback;
        } else {
            activePath = configured;
        }
        return activePath;
    }

    private void rotatePath() {
        String configured = configuredPath();
        String fallback = fallbackPath();
        if (fallback == null || fallback.equalsIgnoreCase(configured)) {
            activePath = configured;
        } else if (streamQuality == StreamQuality.UHD_4K) {
            // A 4K request must never silently fall back to the low-resolution
            // sub-stream. Report the main-stream failure instead of presenting
            // an upscaled image as 4K.
            activePath = configured;
        } else if (properties.isPreferFallback()) {
            // The sub-stream is deliberately the recovery target. Switching
            // back to a damaged 4K main stream after one network hiccup makes
            // the browser report a failed camera request again.
            activePath = fallback;
        } else {
            activePath = configured;
        }
        log.warn("Retrying local camera RTSP path {}", activePath);
    }

    private Path outputDirectory() {
        return Paths.get(properties.getHlsPath()).toAbsolutePath().normalize();
    }

    private boolean isPlaylistReady(Path playlist) {
        try {
            if (!Files.exists(playlist) || Files.size(playlist) <= 32) return false;
            long age = System.currentTimeMillis() - Files.getLastModifiedTime(playlist).toMillis();
            // A segment can be delayed briefly while the camera sends its next
            // key frame. Treating six seconds as dead caused needless process
            // restarts and transient /play-url failures.
            if (age > Math.max(10000L, properties.getStaleTimeoutMs())) return false;
            String content = new String(Files.readAllBytes(playlist), StandardCharsets.UTF_8);
            return content.contains("#EXTINF:") && !content.contains("#EXT-X-ENDLIST");
        } catch (IOException ignored) {
            return false;
        }
    }

    private boolean isCurrentPlaylistReady(Path playlist) {
        try {
            return isPlaylistReady(playlist)
                    && new String(Files.readAllBytes(playlist), StandardCharsets.UTF_8).contains(segmentPrefix)
                    && Files.getLastModifiedTime(playlist).toMillis() > playlistBaselineModifiedAt;
        } catch (IOException ignored) {
            return false;
        }
    }

    @Scheduled(fixedDelayString = "${camera.local.watchdog-interval-ms:10000}")
    public void watchdog() {
        if (!properties.isEnabled() || startedAt == 0) return;
        Path playlist = outputDirectory().resolve("index.m3u8");
        if (ffmpegProcess == null || !ffmpegProcess.isAlive() || !isCurrentPlaylistReady(playlist)) {
            try {
                ensureRunning();
            } catch (RuntimeException e) {
                log.warn("Local camera watchdog restart failed: {}", e.getMessage());
            }
        }
    }

    private void cleanOutput(Path output) throws IOException {
        try (DirectoryStream<Path> files = Files.newDirectoryStream(output)) {
            for (Path file : files) {
                String name = file.getFileName().toString();
                long age = System.currentTimeMillis() - Files.getLastModifiedTime(file).toMillis();
                boolean temporary = name.equals("index.m3u8.tmp")
                        || name.startsWith("segment-") && name.endsWith(".tmp");
                boolean staleSegment = name.startsWith("segment-") && name.endsWith(".ts") && age > 120000L;
                boolean staleLog = (name.equals("ffmpeg.log") || name.startsWith("ffmpeg-")) && age > 86400000L;
                if (temporary || staleSegment || staleLog) {
                    deleteWithRetry(file);
                }
            }
        }
    }

    private void deleteWithRetry(Path file) throws IOException {
        IOException failure = null;
        for (int attempt = 0; attempt < 6; attempt++) {
            try {
                Files.deleteIfExists(file);
                return;
            } catch (IOException e) {
                failure = e;
                try {
                    Thread.sleep(250L);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        // Windows may keep a handle briefly after taskkill. Do not fail the
        // camera API just because a stale segment cannot be deleted yet.
        log.warn("Unable to delete stale camera output {}: {}", file,
                failure == null ? "unknown" : failure.getMessage());
    }

    private void stopStaleProcesses(Path output) {
        Path pid = output.resolve("ffmpeg.pid");
        if (Files.exists(pid)) {
            try {
                String value = new String(Files.readAllBytes(pid), StandardCharsets.US_ASCII).trim();
                // PID reuse can target unrelated processes; validate output ownership below instead.
            } catch (Exception e) {
                log.debug("Unable to inspect stale FFmpeg pid file", e);
            }
            try { Files.deleteIfExists(pid); } catch (IOException ignored) { }
        }

        // PID files are unavailable when Java 8 cannot reflect the child PID,
        // so also scan command lines for FFmpeg instances targeting this HLS
        // directory. This catches orphaned processes from previous launches.
        String target = output.toAbsolutePath().normalize().toString().replace("'", "''");
        String script = "$re=[regex]::Escape('" + target + "'); "
                + "Get-CimInstance Win32_Process -Filter \"Name = 'ffmpeg.exe'\" "
                + "| Where-Object { $_.CommandLine -and $_.CommandLine -match $re } "
                + "| ForEach-Object { $_.ProcessId }";
        try {
            String encoded = java.util.Base64.getEncoder().encodeToString(script.getBytes(StandardCharsets.UTF_16LE));
            Process scan = new ProcessBuilder("powershell", "-NoProfile", "-EncodedCommand", encoded)
                    .redirectErrorStream(true).start();
            if (!scan.waitFor(10, TimeUnit.SECONDS)) {
                scan.destroyForcibly();
                throw new IllegalStateException("旧视频进程检查超时，暂不启动新进程");
            }
            if (scan.exitValue() != 0) throw new IllegalStateException("旧视频进程检查失败");
            String text = new String(readAll(scan), StandardCharsets.UTF_8);
            for (String line : text.split("\\r?\\n")) {
                if (line.trim().matches("\\d+")) stopPid(Long.parseLong(line.trim()));
            }
        } catch (Exception e) {
            throw new IllegalStateException("无法安全清理旧视频进程", e);
        }
    }

    private byte[] readAll(Process process) throws IOException {
        java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
        byte[] bytes = new byte[256];
        int count;
        while ((count = process.getInputStream().read(bytes)) >= 0) buffer.write(bytes, 0, count);
        return buffer.toByteArray();
    }

    private void stopPid(long processId) {
        if (processId <= 0) return;
        log.warn("Stopping stale FFmpeg process {} before starting a new bridge", processId);
        try {
            Process killer = new ProcessBuilder("taskkill", "/PID", String.valueOf(processId), "/T", "/F")
                    .redirectErrorStream(true).start();
            killer.waitFor(3, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.debug("Unable to stop stale FFmpeg process " + processId, e);
        }
    }

    private long processId(Process process) {
        try {
            java.lang.reflect.Method method = Process.class.getMethod("pid");
            Object value = method.invoke(process);
            return value instanceof Number ? ((Number) value).longValue() : -1;
        } catch (Exception ignored) {
            return -1;
        }
    }

    public Map<String, Object> status() {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("enabled", properties.isEnabled());
        status.put("host", properties.getHost());
        status.put("port", properties.getPort());
        status.put("path", properties.getPath());
        status.put("activePath", activePath == null ? selectInitialPath() : activePath);
        status.put("quality", streamQuality.value);
        status.put("targetHeight", targetHeight());
        status.put("actualWidth", actualWidth == 0 ? null : actualWidth);
        status.put("actualHeight", actualHeight == 0 ? null : actualHeight);
        status.put("qualityVerified", qualityVerified);
        status.put("videoBitrate", targetBitrate());
        status.put("targetFps", targetFps());
        status.put("videoMode", nativeVideo ? "native-h264" : "transcode");
        boolean running = ffmpegProcess != null && ffmpegProcess.isAlive();
        status.put("running", running);
        Path playlist = outputDirectory().resolve("index.m3u8");
        status.put("hlsReady", running && isCurrentPlaylistReady(playlist));
        status.put("playlistUpdatedAt", lastModifiedAt(playlist));
        status.put("startedAt", startedAt == 0 ? null : startedAt);
        status.put("restartCount", restartCount);
        status.put("lastError", lastError);
        return status;
    }

    public Map<String, Object> changeQuality(String qualityValue) {
        StreamQuality requested = StreamQuality.from(qualityValue);
        synchronized (processLock) {
            if (requested != streamQuality) {
                streamQuality = requested;
                activePath = null;
                nextRetryAt = 0L;
                actualWidth = 0;
                actualHeight = 0;
                qualityVerified = false;
                stopProcessLocked();
                ensureRunning();
            }
            return status();
        }
    }

    /**
     * Extracts one full-resolution JPEG from the newest completed HLS segment.
     * Reusing the bridge output avoids opening a second RTSP connection while
     * an automatic patrol is already streaming at 4K.
     */
    public Path captureLatestFrame(Path destination) {
        return captureLatestFrameAfter(destination, startedAt);
    }

    /**
     * Captures from a completed segment published after the requested time.
     * Automatic patrol uses this after its focus delay so motion-blurred frames
     * still buffered in HLS cannot be selected for the next photo.
     */
    public Path captureLatestFrameAfter(Path destination, long notBeforeEpochMs) {
        if (destination == null) {
            throw new IllegalArgumentException("截图保存路径不能为空");
        }
        // Keep the stream generation stable while selecting and reading a
        // segment. The watchdog can otherwise restart FFmpeg between those
        // two operations after a transient RTSP disconnect.
        synchronized (processLock) {
            Path segment = awaitStableSegment(Math.max(startedAt, notBeforeEpochMs));
            if (segment == null) {
                throw new IllegalStateException("等待对焦后的清晰视频分片超时");
            }

            Path target = destination.toAbsolutePath().normalize();
            Path parent = target.getParent();
            if (parent == null) {
                throw new IllegalArgumentException("截图保存路径无效");
            }
            Path temporary = target.resolveSibling(target.getFileName().toString() + ".tmp.jpg");
            Process process = null;
            try {
                Files.createDirectories(parent);
                Files.deleteIfExists(temporary);
                List<String> command = new ArrayList<>();
                command.add(properties.getFfmpegPath());
                command.add("-hide_banner");
                command.add("-loglevel");
                command.add("error");
                command.add("-y");
                command.add("-i");
                command.add(segment.toString());
                command.add("-frames:v");
                command.add("1");
                // FFmpeg 8 rejects limited-range H.264 frames when the MJPEG
                // encoder expects full-range input unless the conversion is
                // explicit. One encoder thread also avoids a 4K MJPEG init bug
                // observed on the Windows deployment build.
                if (streamQuality != StreamQuality.UHD_4K) {
                    command.add("-vf");
                    command.add("fps=15,scale=-2:" + targetHeight() + ",format=yuv420p");
                }
                command.add("-q:v");
                command.add("2");
                command.add("-threads");
                command.add("1");
                command.add("-update");
                command.add("1");
                command.add(temporary.toString());

                process = new ProcessBuilder(command).redirectErrorStream(true).start();
                if (!process.waitFor(15, TimeUnit.SECONDS)) {
                    process.destroyForcibly();
                    throw new IllegalStateException("4K截图处理超时");
                }
                String output = new String(readAll(process), StandardCharsets.UTF_8).trim();
                if (process.exitValue() != 0 || !Files.exists(temporary) || Files.size(temporary) < 1024L) {
                    throw new IllegalStateException("4K截图失败" + (output.isEmpty() ? "" : "：" + output));
                }
                validateCapturedFrame(temporary);
                Files.move(temporary, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                return target;
            } catch (IOException e) {
                throw new IllegalStateException("4K截图保存失败：" + e.getMessage(), e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("4K截图被中断", e);
            } finally {
                if (process != null && process.isAlive()) process.destroyForcibly();
                try { Files.deleteIfExists(temporary); } catch (IOException ignored) { }
            }
        }
    }

    /**
     * Waits for a completed HLS segment from the current FFmpeg generation.
     * RTSP reconnects can leave the playlist alive while the first new segment
     * is still being decoded, so a single directory scan is too eager here.
     */
    private Path awaitStableSegment(long notBeforeEpochMs) {
        long timeout = Math.max(3000L, properties.getCaptureSegmentTimeoutMs());
        long deadline = System.currentTimeMillis() + timeout;
        while (System.currentTimeMillis() <= deadline) {
            ensureRunning();
            Path segment = newestStableSegment(notBeforeEpochMs);
            if (segment != null) return segment;
            long remaining = deadline - System.currentTimeMillis();
            if (remaining <= 0L) break;
            try {
                Thread.sleep(Math.min(250L, remaining));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("等待视频分片时被中断", e);
            }
        }
        return null;
    }

    /** Select a segment old enough that FFmpeg has closed it, but still inside the HLS retention window. */
    private void validateCapturedFrame(Path imageFile) throws IOException {
        BufferedImage image = ImageIO.read(imageFile.toFile());
        if (image == null) {
            throw new IllegalStateException("截图文件无法读取");
        }
        int expectedHeight = targetHeight();
        if (image.getHeight() != expectedHeight) {
            throw new IllegalStateException("截图分辨率未就绪：当前 " + image.getWidth() + "x" + image.getHeight()
                    + "，期望高度 " + expectedHeight);
        }
        if (streamQuality == StreamQuality.UHD_4K && (image.getWidth() != 3840 || image.getHeight() != 2160)) {
            throw new IllegalStateException("4K截图分辨率未就绪：当前 " + image.getWidth() + "x" + image.getHeight());
        }
        actualWidth = image.getWidth();
        actualHeight = image.getHeight();
        qualityVerified = streamQuality != StreamQuality.UHD_4K
                || (actualWidth == 3840 && actualHeight == 2160);
    }

    /** Capture and validate one disposable frame before a motion-sensitive workflow. */
    public Map<String, Object> verifyCurrentQuality() {
        Path probe = null;
        try {
            probe = Files.createTempFile("jhds-camera-quality-", ".jpg");
            captureLatestFrame(probe);
            return status();
        } catch (IOException e) {
            throw new IllegalStateException("摄像头画质校验文件创建失败：" + e.getMessage(), e);
        } finally {
            if (probe != null) {
                try { Files.deleteIfExists(probe); } catch (IOException ignored) { }
            }
        }
    }

    private Path newestStableSegment(long notBefore) {
        Path newest = null;
        long newestModifiedAt = Long.MIN_VALUE;
        long stableBefore = System.currentTimeMillis() - 1200L;
        try (DirectoryStream<Path> files = Files.newDirectoryStream(outputDirectory(), segmentPrefix + "*.ts")) {
            for (Path file : files) {
                if (!Files.isRegularFile(file) || Files.size(file) <= 1024L) continue;
                long modifiedAt = Files.getLastModifiedTime(file).toMillis();
                if (modifiedAt >= notBefore && modifiedAt <= stableBefore && modifiedAt > newestModifiedAt) {
                    newest = file;
                    newestModifiedAt = modifiedAt;
                }
            }
        } catch (IOException e) {
            log.warn("Unable to locate a stable camera segment: {}", e.getMessage());
        }
        return newest;
    }


    private Long lastModifiedAt(Path file) {
        try {
            return Files.exists(file) ? Files.getLastModifiedTime(file).toMillis() : null;
        } catch (IOException ignored) {
            return null;
        }
    }

    public void stop() {
        synchronized (processLock) {
            stopProcessLocked();
        }
    }

    private void stopProcessLocked() {
        if (ffmpegProcess != null) {
            try {
                ffmpegProcess.destroy();
                if (!ffmpegProcess.waitFor(1500, java.util.concurrent.TimeUnit.MILLISECONDS)) {
                    ffmpegProcess.destroyForcibly();
                    ffmpegProcess.waitFor(500, java.util.concurrent.TimeUnit.MILLISECONDS);
                }
            } catch (Exception ignored) {
            }
            ffmpegProcess = null;
            try { Files.deleteIfExists(pidFile()); } catch (IOException ignored) { }
        }
    }

    @PreDestroy
    public void destroy() {
        synchronized (processLock) {
            stopProcessLocked();
            try {
                if (ownershipLock != null) ownershipLock.release();
                if (ownershipChannel != null) ownershipChannel.close();
            } catch (IOException e) {
                log.warn("Unable to release camera directory lock", e);
            }
        }
    }
}
