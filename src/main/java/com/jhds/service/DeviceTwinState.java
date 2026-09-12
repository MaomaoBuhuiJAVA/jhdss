package com.jhds.service;

import org.springframework.stereotype.Service;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Observations of successful command responses, never measured flow. */
@Service
public class DeviceTwinState {
    private final Map<String, Observation> observations = new ConcurrentHashMap<>();

    public void acknowledge(String alias, boolean active) {
        observations.put(alias, new Observation(active, System.currentTimeMillis()));
    }

    public void invalidate(String alias) {
        observations.remove(alias);
    }

    public Map<String, Object> snapshot(String alias, boolean connected, long connectionEpoch) {
        Observation observation = observations.get(alias);
        boolean known = connected && observation != null && observation.at >= connectionEpoch;
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("alias", alias);
        result.put("active", known ? observation.active : null);
        result.put("source", known ? "command-response" : "unknown");
        result.put("connected", connected);
        result.put("acknowledgedAt", observation == null ? null : observation.at);
        return result;
    }

    private static class Observation {
        final boolean active;
        final long at;
        Observation(boolean active, long at) { this.active = active; this.at = at; }
    }
}
