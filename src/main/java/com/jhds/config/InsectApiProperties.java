package com.jhds.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "insect.api")
public class InsectApiProperties {

    private String baseUrl = "http://app.wlwapp.cn";
    private String username;
    private String password;
    private int tokenTtl = 7;
}
