package com.jhds.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import javax.annotation.PostConstruct;
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
    @Autowired
    private DeviceTwinState deviceTwinState;

    @Value("${control-panel.base-url:http://169.254.240.33}")
    private String baseUrl;
    @Value("${control-panel.connect-timeout-ms:1200}")
    private int connectTimeoutMs;
    @Value("${control-panel.read-timeout-ms:3000}")
    private int readTimeoutMs;
    @Value("${patrol.automatic.vertical-travel-ms:11587}")
    private long verticalTravelMs;

    private RestTemplate restTemplate;

    private volatile boolean reachable;
    private volatile String lastError;
    private volatile long lastCheckedAt;
    private volatile boolean forwardActive;
    private volatile boolean backwardActive;
    private volatile boolean pumpActive;
    private volatile int motorSpeed = 60;
    private volatile long twinConnectionEpoch;
    /** Dead-reckoned forward travel from the bottom startup position. */
    private long verticalPositionMs;
    private long verticalMoveStartedAt;

    public synchronized Map<String, Object> twinPumpStatus() {
        if (System.currentTimeMillis() - lastCheckedAt > 5000) checkReachability();
        return deviceTwinState.snapshot("CONTROL_PANEL_PUMP", reachable, twinConnectionEpoch);
    }

    @PostConstruct
    public void initRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Math.max(250, connectTimeoutMs));
        factory.setReadTimeout(Math.max(500, readTimeoutMs));
        restTemplate = new RestTemplate(factory);
    }

    public synchronized Map<String, Object> connectionStatus() {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("baseUrl", normalizedBaseUrl());
        status.put("reachable", checkReachability());
        status.put("forwardActive", forwardActive);
        status.put("backwardActive", backwardActive);
        status.put("pumpActive", pumpActive);
        status.put("motorSpeed", motorSpeed);
        status.put("verticalPosition", twinMotionStatus().get("y"));
        status.put("lastCheckedAt", lastCheckedAt == 0 ? null : lastCheckedAt);
        status.put("lastError", lastError);
        return status;
    }

    public boolean isReachable() {
        return checkReachability();
    }

    public synchronized boolean hasActiveMotion() {
        return forwardActive || backwardActive;
    }

    public synchronized Map<String, Object> motionStatus() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("action", forwardActive ? "forward" : backwardActive ? "backward" : "stop");
        result.put("enabled", forwardActive || backwardActive);
        result.put("reachable", reachable);
        return result;
    }

    /**
     * Returns the live model pose for the forward/backward carriage. The
     * controller exposes relay state but no encoder, so position is estimated
     * from the same calibrated travel time used by automatic patrol.
     */
    public synchronized Map<String, Object> twinMotionStatus() {
        long now = System.currentTimeMillis();
        long estimated = estimatedVerticalPositionLocked(now);
        long travel = fullVerticalTravelMs();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("y", (double) estimated / travel);
        result.put("velocityY", forwardActive ? 1000.0 / travel
                : backwardActive ? -1000.0 / travel : 0.0);
        result.put("source", "motor-time-estimate");
        result.put("timestampMs", now);
        result.put("connected", reachable);
        result.put("movingDirection", forwardActive ? "forward" : backwardActive ? "backward" : null);
        return result;
    }

    /** Sends both direction-off commands so an unknown/stale UI state is safe. */
    public synchronized Map<String, Object> stopMotion() {
        RuntimeException failure = null;
        String forwardResponse = null;
        String backwardResponse = null;
        try {
            forwardResponse = post("/api/moveforward", false);
        } catch (RuntimeException e) {
            failure = e;
        }
        try {
            backwardResponse = post("/api/movebackward", false);
        } catch (RuntimeException e) {
            if (failure == null) failure = e;
        }
        bankVerticalPositionLocked(System.currentTimeMillis());
        forwardActive = false;
        backwardActive = false;
        verticalMoveStartedAt = 0L;
        if (failure != null) throw failure;

        Map<String, Object> result = commandResult("stop", false,
                String.valueOf(forwardResponse) + " | " + String.valueOf(backwardResponse));
        result.put("forwardResponse", forwardResponse);
        result.put("backwardResponse", backwardResponse);
        return result;
    }

    public synchronized Map<String, Object> move(String direction, boolean enabled) {
        if (!"forward".equals(direction) && !"backward".equals(direction)) {
            throw new IllegalArgumentException("控制面板移动方向无效");
        }

        // The original control panel turns the opposite relay off when the
        // user changes direction. Mirror that behavior server-side.
        if (enabled) {
            if ("forward".equals(direction)) {
                post("/api/movebackward", false);
                bankVerticalPositionLocked(System.currentTimeMillis());
                backwardActive = false;
            } else {
                post("/api/moveforward", false);
                bankVerticalPositionLocked(System.currentTimeMillis());
                forwardActive = false;
            }
        }

        String path = "forward".equals(direction) ? "/api/moveforward" : "/api/movebackward";
        String remote = post(path, enabled);
        long now = System.currentTimeMillis();
        bankVerticalPositionLocked(now);
        if ("forward".equals(direction)) {
            forwardActive = enabled;
        } else {
            backwardActive = enabled;
        }
        verticalMoveStartedAt = forwardActive || backwardActive ? now : 0L;
        Map<String, Object> result = commandResult(direction, enabled, remote);
        return result;
    }

    private long fullVerticalTravelMs() {
        return Math.max(1000L, verticalTravelMs);
    }

    private long estimatedVerticalPositionLocked(long now) {
        long estimated = verticalPositionMs;
        long elapsed = verticalMoveStartedAt == 0L ? 0L : Math.max(0L, now - verticalMoveStartedAt);
        if (forwardActive) estimated += elapsed;
        if (backwardActive) estimated -= elapsed;
        return Math.max(0L, Math.min(fullVerticalTravelMs(), estimated));
    }

    private void bankVerticalPositionLocked(long now) {
        verticalPositionMs = estimatedVerticalPositionLocked(now);
        if (verticalMoveStartedAt != 0L) verticalMoveStartedAt = now;
    }

    public Map<String, Object> pump(boolean enabled) {
        deviceTwinState.invalidate("CONTROL_PANEL_PUMP");
        String remote = post("/api/pump", enabled);
        pumpActive = enabled;
        deviceTwinState.acknowledge("CONTROL_PANEL_PUMP", enabled);
        return commandResult("pump", enabled, remote);
    }

    public synchronized Map<String, Object> motorSpeed(int speed) {
        if (speed < 1 || speed > 500) {
            throw new IllegalArgumentException("电机速度范围为 1-500");
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("Speed", speed);
        String remote = post("/api/speed", body);
        motorSpeed = speed;
        Map<String, Object> result = commandResult("speed", true, remote);
        result.put("speed", speed);
        return result;
    }

    public Map<String, Object> deviceOutput(String alias, boolean enabled) {
        if (!"PUMP_CO2".equalsIgnoreCase(alias) && !"PUMP_CIRCULATION".equalsIgnoreCase(alias)) {
            throw new IllegalArgumentException("本地 Modbus 网关不允许控制该设备");
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("alias", alias.toUpperCase());
        body.put("enabled", enabled);
        String remote = post("/api/device-control", body);
        return commandResult(alias.toUpperCase(), enabled, remote);
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
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("LenData", enabled);
        return post(path, body);
    }

    private String post(String path, Map<String, Object> body) {
        try {
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
            twinConnectionEpoch = System.currentTimeMillis();
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
        } catch (HttpStatusCodeException e) {
            // The bundled controller has no GET / route and returns 404. Any
            // HTTP response still proves the local service is reachable;
            // command endpoints validate their own response codes separately.
            reachable = true;
            lastError = null;
        } catch (RestClientException e) {
            reachable = false;
            lastError = e.getMessage();
        }
        lastCheckedAt = System.currentTimeMillis();
        if (!reachable) twinConnectionEpoch = lastCheckedAt;
        return reachable;
    }

    private String normalizedBaseUrl() {
        String value = baseUrl == null ? "" : baseUrl.trim();
        while (value.endsWith("/")) value = value.substring(0, value.length() - 1);
        return value;
    }
}
