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
    /** Match the DTU setting; can be overridden for brokers that require clean sessions. */
    private boolean cleanSession = false;
    /** Keep the configured client id stable unless a broker requires unique ids per process. */
    private boolean appendInstanceId = false;
    private boolean transparentMode = true;
    /** USR DTU may echo the transmitted frame before the downstream device responds. */
    private boolean ignoreEcho = true;
    private int commandQos = 1;
    private int responseQos = 1;

    private Topic topic = new Topic();

    @Data
    public static class Topic {
        private String prefix = "/iot/jhds/prod";
        private String commandSuffix = "command";
        private String responseSuffix = "response";
    }
}
