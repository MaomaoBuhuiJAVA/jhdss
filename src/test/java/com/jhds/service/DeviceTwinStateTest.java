package com.jhds.service;

import org.junit.Test;
import java.util.Map;
import static org.junit.Assert.*;

public class DeviceTwinStateTest {
    @Test public void startupIsUnknownNotOff() {
        Map<String, Object> data = new DeviceTwinState().snapshot("pump", true, 0);
        assertNull(data.get("active"));
        assertEquals("unknown", data.get("source"));
    }
    @Test public void acknowledgementsTrackOnAndOffIndependently() {
        DeviceTwinState state = new DeviceTwinState();
        state.acknowledge("drip", true);
        state.acknowledge("gas", false);
        assertEquals(true, state.snapshot("drip", true, 0).get("active"));
        assertEquals(false, state.snapshot("gas", true, 0).get("active"));
        assertEquals("command-response", state.snapshot("drip", true, 0).get("source"));
    }
    @Test public void offlineAndReconnectCannotReviveOldActivity() {
        DeviceTwinState state = new DeviceTwinState();
        state.acknowledge("pump", true);
        long at = (Long) state.snapshot("pump", true, 0).get("acknowledgedAt");
        assertNull(state.snapshot("pump", false, 0).get("active"));
        assertNull(state.snapshot("pump", true, at + 1).get("active"));
    }
    @Test public void failedOrPendingCommandInvalidatesPreviousState() {
        DeviceTwinState state = new DeviceTwinState();
        state.acknowledge("pump", true);
        state.invalidate("pump");
        assertNull(state.snapshot("pump", true, 0).get("active"));
    }
}
