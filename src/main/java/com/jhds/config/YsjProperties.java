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
    private int tokenTtl = 7;
}
