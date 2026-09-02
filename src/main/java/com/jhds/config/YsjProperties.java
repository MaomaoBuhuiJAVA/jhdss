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
    /** 4 is EZVIZ FLV, which is played by the bundled flv.js client. */
    private Integer protocol = 4;
    /** 1 is the main stream and is also used when requesting H.264 conversion. */
    private Integer streamType = 1;
    private int tokenTtl = 7;
}
