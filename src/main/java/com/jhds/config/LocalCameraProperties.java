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
    private String transport = "tcp";
    private String ffmpegPath = "ffmpeg";
    private String hlsPath = "./work/camera/hls";
    private int width = 1080;
    private String videoBitrate = "4000k";
    private int segmentSeconds = 1;
    private int listSize = 3;
}
