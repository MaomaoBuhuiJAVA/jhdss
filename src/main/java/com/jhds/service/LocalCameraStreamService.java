package com.jhds.service;

import com.jhds.config.LocalCameraProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.annotation.PreDestroy;
import java.io.File;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.charset.StandardCharsets;
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
    private volatile long startedAt;
    private volatile String activePath;
    private volatile long lastRestartAt;
    private volatile int restartCount;
    private volatile long nextRetryAt;

    private Path pidFile() {
        return outputDirectory().resolve("ffmpeg.pid");
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
            if (isPlaylistReady(playlist)) return true;
            try {
                Thread.sleep(150L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        return isPlaylistReady(playlist);
    }

    public void ensureRunning() {
        synchronized (processLock) {
            long now = System.currentTimeMillis();
            if (ffmpegProcess != null && ffmpegProcess.isAlive()) {
                Path playlist = outputDirectory().resolve("index.m3u8");
                boolean ready = isPlaylistReady(playlist);
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
                stopStaleProcesses(output);
                cleanOutput(output);

                String inputPath = selectInitialPath();
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
                log.info("Local RTSP bridge started: {}:{}{}, HLS directory={}, streamWidth={}",
                        properties.getHost(), properties.getPort(), inputPath, output, properties.getWidth());
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
        // Some cameras expose an RTP clock that jumps backwards after a
        // reconnect. Wall-clock timestamps keep the HLS muxer monotonic.
        command.add("-use_wallclock_as_timestamps");
        command.add("1");
        command.add("-i");
        command.add(buildRtspUrl(inputPath));
        command.add("-map");
        command.add("0:v:0");
        command.add("-map");
        command.add("0:a:0?");
        command.add("-vf");
        command.add("fps=15,scale=-2:" + properties.getWidth() + ",format=yuv420p");
        command.add("-r");
        command.add("15");
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
        command.add("keyint=15:min-keyint=15:scenecut=0:repeat-headers=1");
        command.add("-pix_fmt");
        command.add("yuv420p");
        command.add("-b:v");
        command.add(properties.getVideoBitrate());
        command.add("-maxrate");
        command.add(properties.getVideoBitrate());
        command.add("-bufsize");
        command.add("2000k");
        // The camera publishes 15 fps. A one-second GOP keeps HLS latency low.
        command.add("-g");
        command.add("15");
        command.add("-keyint_min");
        command.add("15");
        command.add("-sc_threshold");
        command.add("0");
        command.add("-c:a");
        command.add("aac");
        command.add("-b:a");
        command.add("64k");
        command.add("-ac");
        command.add("1");
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
        command.add("-hls_flags");
        // temp_file prevents the browser from reading a partially-written
        // segment during a reconnect. omit_endlist keeps the playlist live.
        command.add("delete_segments+independent_segments+program_date_time+temp_file");
        command.add("-hls_segment_filename");
        command.add(output.resolve("segment-%03d.ts").toString());
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
        if (properties.isPreferFallback() && fallback != null && !fallback.equalsIgnoreCase(configured)) {
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

    @Scheduled(fixedDelayString = "${camera.local.watchdog-interval-ms:10000}")
    public void watchdog() {
        if (!properties.isEnabled() || startedAt == 0) return;
        Path playlist = outputDirectory().resolve("index.m3u8");
        if (ffmpegProcess == null || !ffmpegProcess.isAlive() || !isPlaylistReady(playlist)) {
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
                if (name.equals("index.m3u8") || name.startsWith("segment-")
                        || name.equals("index.m3u8.tmp") || name.startsWith("segment-" ) && name.endsWith(".tmp")
                        || name.equals("ffmpeg.log") || name.startsWith("ffmpeg-")) {
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
                if (value.matches("\\d+")) stopPid(Long.parseLong(value));
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
            Process scan = new ProcessBuilder("powershell", "-NoProfile", "-Command", script)
                    .redirectErrorStream(true).start();
            String text = new String(readAll(scan), StandardCharsets.UTF_8);
            scan.waitFor(3, TimeUnit.SECONDS);
            for (String line : text.split("\\r?\\n")) {
                if (line.trim().matches("\\d+")) stopPid(Long.parseLong(line.trim()));
            }
        } catch (Exception e) {
            log.debug("Unable to scan stale FFmpeg processes", e);
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
            java.lang.reflect.Method method = process.getClass().getMethod("pid");
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
        status.put("running", ffmpegProcess != null && ffmpegProcess.isAlive());
        status.put("hlsReady", isPlaylistReady(outputDirectory().resolve("index.m3u8")));
        status.put("startedAt", startedAt == 0 ? null : startedAt);
        status.put("restartCount", restartCount);
        status.put("lastError", lastError);
        return status;
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
        stop();
    }
}
