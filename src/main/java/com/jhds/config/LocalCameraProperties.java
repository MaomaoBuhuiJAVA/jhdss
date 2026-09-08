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
    /** Lower-resolution stream used first because it is less prone to RTSP decoder errors. */
    private String fallbackPath = "/ch1/sub";
    private boolean preferFallback = true;
    private String transport = "tcp";
    private String ffmpegPath = "ffmpeg";
    private String hlsPath = "./work/camera/hls";
    private int width = 720;
    private String videoBitrate = "2400k";
    private String videoBufferSize = "3600k";
    private int segmentSeconds = 1;
    private int listSize = 3;
    private int connectTimeoutMs = 8000;
    private int startupTimeoutMs = 30000;
    private int staleTimeoutMs = 30000;
    private int restartCooldownMs = 10000;
    private int captureSegmentTimeoutMs = 30000;
}
