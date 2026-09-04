package com.jhds.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "ys7")
public class YsjProperties {

    private String appKey;
    private String appSecret;
    /** Device verification code, required by EZVIZ when video encryption is enabled. */
    private String verifyCode;
    /** Default camera used by the patrol page. Override with YS7_DEVICE_SERIAL. */
    private String deviceSerial = "BG9980884";
    /** EZVIZ camera channel number. Most standalone cameras use channel 1. */
    private Integer channelNo = 1;
    /** 2 is EZVIZ HLS, which is broadly supported by the bundled hls.js player. */
    private Integer protocol = 2;
    /** 2 is the sub-stream and is preferred for lower-latency browser viewing. */
    private Integer streamType = 2;
    /** Ask EZVIZ to use H.264 before requesting a live URL. */
    private boolean forceH264 = true;
    private int tokenTtl = 7;
}
