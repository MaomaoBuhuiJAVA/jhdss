package com.jhds.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "device.mqtt")
public class MqttProperties {
    private String brokerUrl;
    private String clientId;
    private String username;
    private String password;
    private int connectionTimeout = 10;
    private int keepaliveInterval = 60;
    private boolean enabled = true;

    private Topic topic = new Topic();

    @Data
    public static class Topic {
        private String prefix = "/iot/jhds/prod";
        private String commandSuffix = "command";
        private String responseSuffix = "response";
    }
}
