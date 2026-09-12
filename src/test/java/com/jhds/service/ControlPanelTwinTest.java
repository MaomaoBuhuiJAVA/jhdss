package com.jhds.service;

import org.junit.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.HttpClientErrorException;
import java.util.Map;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

public class ControlPanelTwinTest {
    @Test public void http404StillProvesBundledControlServiceIsReachable() {
        ControlPanelService panel = new ControlPanelService();
        RestTemplate http = mock(RestTemplate.class);
        ReflectionTestUtils.setField(panel, "restTemplate", http);
        ReflectionTestUtils.setField(panel, "baseUrl", "http://127.0.0.1:8999");
        when(http.exchange(anyString(), any(), isNull(), eq(String.class)))
                .thenThrow(new HttpClientErrorException(HttpStatus.NOT_FOUND));

        assertEquals(true, panel.connectionStatus().get("reachable"));
        assertNull(panel.connectionStatus().get("lastError"));
    }

    @Test public void onlySuccessfulHttpResponseActivatesModel() {
        ControlPanelService panel = new ControlPanelService();
        DeviceTwinState state = new DeviceTwinState();
        RestTemplate http = mock(RestTemplate.class);
        ReflectionTestUtils.setField(panel, "deviceTwinState", state);
        ReflectionTestUtils.setField(panel, "restTemplate", http);
        ReflectionTestUtils.setField(panel, "baseUrl", "http://test-controller");
        when(http.postForEntity(anyString(), any(), eq(String.class))).thenReturn(ResponseEntity.ok("OK"));
        panel.pump(true);
        assertEquals(true, panel.twinPumpStatus().get("active"));
        panel.pump(false);
        assertEquals(false, panel.twinPumpStatus().get("active"));
        when(http.postForEntity(anyString(), any(), eq(String.class))).thenThrow(new ResourceAccessException("offline"));
        try { panel.pump(true); fail("Must reject failed controller request"); }
        catch (IllegalStateException expected) { assertNull(panel.twinPumpStatus().get("active")); }
        verify(http, times(3)).postForEntity(anyString(), any(), eq(String.class));
        verifyNoMoreInteractions(http);
    }

    @Test public void forwardAndBackwardMotionAdvanceTheTwinCarriage() {
        ControlPanelService panel = new ControlPanelService();
        RestTemplate http = mock(RestTemplate.class);
        ReflectionTestUtils.setField(panel, "restTemplate", http);
        ReflectionTestUtils.setField(panel, "baseUrl", "http://test-controller");
        ReflectionTestUtils.setField(panel, "verticalTravelMs", 10000L);
        ReflectionTestUtils.setField(panel, "reachable", true);
        when(http.postForEntity(anyString(), any(), eq(String.class))).thenReturn(ResponseEntity.ok("OK"));

        panel.move("forward", true);
        ReflectionTestUtils.setField(panel, "verticalMoveStartedAt", System.currentTimeMillis() - 2500L);
        Map<String, Object> movingUp = panel.twinMotionStatus();
        assertEquals(.25, (Double) movingUp.get("y"), .02);
        assertTrue((Double) movingUp.get("velocityY") > 0);

        panel.move("forward", false);
        double stopped = (Double) panel.twinMotionStatus().get("y");
        assertEquals(0.0, (Double) panel.twinMotionStatus().get("velocityY"), .001);
        panel.move("backward", true);
        ReflectionTestUtils.setField(panel, "verticalMoveStartedAt", System.currentTimeMillis() - 1000L);
        assertTrue((Double) panel.twinMotionStatus().get("y") < stopped);
    }

    @Test public void stopMotionAlwaysTurnsOffBothDirectionOutputs() {
        ControlPanelService panel = new ControlPanelService();
        RestTemplate http = mock(RestTemplate.class);
        ReflectionTestUtils.setField(panel, "restTemplate", http);
        ReflectionTestUtils.setField(panel, "baseUrl", "http://test-controller");
        when(http.postForEntity(anyString(), any(), eq(String.class))).thenReturn(ResponseEntity.ok("OK"));

        Map<String, Object> result = panel.stopMotion();

        assertEquals("stop", result.get("action"));
        verify(http).postForEntity(eq("http://test-controller/api/moveforward"), any(), eq(String.class));
        verify(http).postForEntity(eq("http://test-controller/api/movebackward"), any(), eq(String.class));
    }
}
