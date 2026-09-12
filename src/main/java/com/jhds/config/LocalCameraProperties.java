package com.jhds.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "camera.local")
public class LocalCameraProperties {

    private boolean enabled = false;
    private String host = "127.0.0.1";
    private int port = 554;
    private String username = "admin";
    private String password = "";
    private String path = "/ch1/main";
    /** Lower-resolution stream used only when explicitly preferred or as recovery. */
    private String fallbackPath = "/ch1/sub";
    private boolean preferFallback = false;
    private String transport = "tcp";
    private String ffmpegPath = "ffmpeg";
    private String ffprobePath = "ffprobe";
    private String hlsPath = "./work/camera/hls";
    private int width = 720;
    private String videoBitrate = "2400k";
    private String videoBufferSize = "3600k";
    private int fps = 15;
    private int segmentSeconds = 1;
    private int listSize = 6;
    private int connectTimeoutMs = 8000;
    private int startupTimeoutMs = 30000;
    private int staleTimeoutMs = 30000;
    private int restartCooldownMs = 10000;
    private int captureSegmentTimeoutMs = 30000;
}
