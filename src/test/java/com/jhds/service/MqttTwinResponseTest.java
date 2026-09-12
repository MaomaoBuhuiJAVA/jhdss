package com.jhds.service;

import com.jhds.service.mqtt.MqttService;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.Assert.*;

public class MqttTwinResponseTest {
    @Test public void explicitErrorsNeverActivateModel() {
        MqttService mqtt = new MqttService();
        DeviceTwinState state = new DeviceTwinState();
        ReflectionTestUtils.setField(mqtt, "deviceTwinState", state);
        for (String response : new String[]{"{\"success\":false}", "{\"code\":500}", "{\"error\":\"offline\"}", "{broken"}) {
            ReflectionTestUtils.invokeMethod(mqtt, "observeTwinResponse", "PUMP_CO2", "open", response);
            assertNull(state.snapshot("PUMP_CO2", true, 0).get("active"));
        }
    }
    @Test public void successfulResponsesKeepCommandSourceAndOffState() {
        MqttService mqtt = new MqttService();
        DeviceTwinState state = new DeviceTwinState();
        ReflectionTestUtils.setField(mqtt, "deviceTwinState", state);
        ReflectionTestUtils.invokeMethod(mqtt, "observeTwinResponse", "PUMP_CO2", "open", "{\"success\":true}");
        assertEquals(true, state.snapshot("PUMP_CO2", true, 0).get("active"));
        ReflectionTestUtils.invokeMethod(mqtt, "observeTwinResponse", "PUMP_CO2", "close", "01 06 00 02 00 00 28 0A");
        assertEquals(false, state.snapshot("PUMP_CO2", true, 0).get("active"));
        assertEquals("command-response", state.snapshot("PUMP_CO2", true, 0).get("source"));
    }
}
