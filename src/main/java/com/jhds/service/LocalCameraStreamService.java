package com.jhds.service;

import com.jhds.config.LocalCameraProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.PreDestroy;
import java.io.File;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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

    public boolean isEnabled() {
        return properties.isEnabled();
    }

    public String getHlsPath() {
        return properties.getHlsPath();
    }

    public void ensureRunning() {
        synchronized (processLock) {
            if (ffmpegProcess != null && ffmpegProcess.isAlive()) {
                Path playlist = outputDirectory().resolve("index.m3u8");
                boolean ready = Files.exists(playlist);
                boolean startupGracePeriod = startedAt > 0
                        && System.currentTimeMillis() - startedAt < 15000;
                if (ready || startupGracePeriod) return;
                log.warn("Local RTSP bridge is alive but has produced no HLS playlist; restarting it");
            }
            stopProcessLocked();
            try {
                Path output = outputDirectory();
                Files.createDirectories(output);
                cleanOutput(output);

                List<String> command = buildCommand(output);
                ProcessBuilder builder = new ProcessBuilder(command);
                builder.directory(new File("."));
                // Use a per-process log file. Reusing one log file can fail on
                // Windows while a previous FFmpeg process is still releasing
                // its handle during a recovery restart.
                Path logFile = output.resolve("ffmpeg-" + System.currentTimeMillis() + ".log");
                builder.redirectError(logFile.toFile());
                ffmpegProcess = builder.start();
                startedAt = System.currentTimeMillis();
                lastError = null;
                log.info("Local RTSP bridge started: {}:{}{}, HLS path={}",
                        properties.getHost(), properties.getPort(), properties.getPath(), output);
            } catch (Exception e) {
                lastError = e.getMessage();
                log.error("Unable to start local RTSP bridge", e);
                String detail = e.getMessage() == null ? "未知错误" : e.getMessage();
                throw new IllegalStateException("本地摄像头流启动失败：" + detail
                        + "；请确认 FFmpeg 已安装并可在 PATH 中执行", e);
            }
        }
    }

    private List<String> buildCommand(Path output) {
        List<String> command = new ArrayList<>();
        command.add(properties.getFfmpegPath());
        command.add("-hide_banner");
        command.add("-loglevel");
        command.add("warning");
        command.add("-rtsp_transport");
        command.add(properties.getTransport() == null || properties.getTransport().trim().isEmpty()
                ? "udp" : properties.getTransport().trim());
        command.add("-fflags");
        command.add("nobuffer");
        command.add("-flags");
        command.add("low_delay");
        command.add("-analyzeduration");
        command.add("1000000");
        command.add("-probesize");
        command.add("1000000");
        command.add("-i");
        command.add(buildRtspUrl());
        command.add("-map");
        command.add("0:v:0");
        command.add("-map");
        command.add("0:a:0?");
        command.add("-vf");
        command.add("scale=-2:" + properties.getWidth());
        command.add("-c:v");
        command.add("libx264");
        command.add("-preset");
        command.add("ultrafast");
        command.add("-tune");
        command.add("zerolatency");
        command.add("-profile:v");
        command.add("main");
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
        command.add("-f");
        command.add("hls");
        command.add("-hls_time");
        command.add(String.valueOf(Math.max(1, properties.getSegmentSeconds())));
        command.add("-hls_list_size");
        command.add(String.valueOf(Math.max(2, properties.getListSize())));
        command.add("-hls_flags");
        command.add("delete_segments+append_list+independent_segments+program_date_time");
        command.add("-hls_segment_filename");
        command.add(output.resolve("segment-%03d.ts").toString());
        command.add(output.resolve("index.m3u8").toString());
        return command;
    }

    private String buildRtspUrl() {
        String user = properties.getUsername() == null ? "" : properties.getUsername();
        String password = properties.getPassword() == null ? "" : properties.getPassword();
        String auth = user.isEmpty() ? "" : user + (password.isEmpty() ? "" : ":" + password) + "@";
        String path = properties.getPath() == null ? "/ch1/main" : properties.getPath().trim();
        if (!path.startsWith("/")) path = "/" + path;
        return "rtsp://" + auth + properties.getHost() + ":" + properties.getPort() + path;
    }

    private Path outputDirectory() {
        return Paths.get(properties.getHlsPath()).toAbsolutePath().normalize();
    }

    private void cleanOutput(Path output) throws IOException {
        try (DirectoryStream<Path> files = Files.newDirectoryStream(output)) {
            for (Path file : files) {
                String name = file.getFileName().toString();
                if (name.equals("index.m3u8") || name.startsWith("segment-")
                        || name.equals("ffmpeg.log") || name.startsWith("ffmpeg-")) {
                    Files.deleteIfExists(file);
                }
            }
        }
    }

    public Map<String, Object> status() {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("enabled", properties.isEnabled());
        status.put("host", properties.getHost());
        status.put("port", properties.getPort());
        status.put("path", properties.getPath());
        status.put("running", ffmpegProcess != null && ffmpegProcess.isAlive());
        status.put("hlsReady", Files.exists(outputDirectory().resolve("index.m3u8")));
        status.put("startedAt", startedAt == 0 ? null : startedAt);
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
        }
    }

    @PreDestroy
    public void destroy() {
        stop();
    }
}
