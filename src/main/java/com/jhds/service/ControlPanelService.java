package com.jhds.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Proxy for the irrigation controller reachable through the Bluetooth PAN.
 * Keeping this call on the server avoids browser CORS restrictions and keeps
 * the controller address configurable for the other deployment computer.
 */
@Slf4j
@Service
public class ControlPanelService {

    @Value("${control-panel.base-url:http://169.254.240.33}")
    private String baseUrl;

    @Autowired
    private RestTemplate restTemplate;

    private volatile boolean reachable;
    private volatile String lastError;
    private volatile long lastCheckedAt;
    private volatile boolean forwardActive;
    private volatile boolean backwardActive;
    private volatile boolean pumpActive;

    public Map<String, Object> connectionStatus() {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("baseUrl", normalizedBaseUrl());
        status.put("reachable", checkReachability());
        status.put("forwardActive", forwardActive);
        status.put("backwardActive", backwardActive);
        status.put("pumpActive", pumpActive);
        status.put("lastCheckedAt", lastCheckedAt == 0 ? null : lastCheckedAt);
        status.put("lastError", lastError);
        return status;
    }

    public Map<String, Object> move(String direction, boolean enabled) {
        if (!"forward".equals(direction) && !"backward".equals(direction)) {
            throw new IllegalArgumentException("控制面板移动方向无效");
        }

        // The original control panel turns the opposite relay off when the
        // user changes direction. Mirror that behavior server-side.
        if (enabled) {
            if ("forward".equals(direction)) {
                post("/api/movebackward", false);
                backwardActive = false;
            } else {
                post("/api/moveforward", false);
                forwardActive = false;
            }
        }

        String path = "forward".equals(direction) ? "/api/moveforward" : "/api/movebackward";
        String remote = post(path, enabled);
        if ("forward".equals(direction)) {
            forwardActive = enabled;
        } else {
            backwardActive = enabled;
        }
        Map<String, Object> result = commandResult(direction, enabled, remote);
        return result;
    }

    public Map<String, Object> pump(boolean enabled) {
        String remote = post("/api/pump", enabled);
        pumpActive = enabled;
        return commandResult("pump", enabled, remote);
    }

    private Map<String, Object> commandResult(String action, boolean enabled, String remote) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("action", action);
        result.put("enabled", enabled);
        result.put("remoteResponse", remote);
        result.put("reachable", reachable);
        return result;
    }

    private String post(String path, boolean enabled) {
        try {
            Map<String, Boolean> body = new LinkedHashMap<>();
            body.put("LenData", enabled);
            ResponseEntity<String> response = restTemplate.postForEntity(
                    normalizedBaseUrl() + path, body, String.class);
            if (!response.getStatusCode().is2xxSuccessful()) {
                throw new IllegalStateException("控制面板返回 HTTP " + response.getStatusCodeValue());
            }
            reachable = true;
            lastError = null;
            lastCheckedAt = System.currentTimeMillis();
            return response.getBody();
        } catch (RestClientException | IllegalStateException e) {
            reachable = false;
            lastError = e.getMessage();
            lastCheckedAt = System.currentTimeMillis();
            log.warn("Control panel request failed: {} {}", path, e.getMessage());
            throw new IllegalStateException("控制面板不可访问，请检查蓝牙网络和地址 " + normalizedBaseUrl(), e);
        }
    }

    private boolean checkReachability() {
        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    normalizedBaseUrl() + "/", HttpMethod.GET, null, String.class);
            reachable = response.getStatusCode().is2xxSuccessful();
            lastError = reachable ? null : "HTTP " + response.getStatusCodeValue();
        } catch (RestClientException e) {
            reachable = false;
            lastError = e.getMessage();
        }
        lastCheckedAt = System.currentTimeMillis();
        return reachable;
    }

    private String normalizedBaseUrl() {
        String value = baseUrl == null ? "" : baseUrl.trim();
        while (value.endsWith("/")) value = value.substring(0, value.length() - 1);
        return value;
    }
}
