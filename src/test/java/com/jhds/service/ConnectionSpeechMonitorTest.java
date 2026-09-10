package com.jhds.service;

import com.jhds.service.mqtt.MqttService;
import org.junit.Test;

import java.util.Collections;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class ConnectionSpeechMonitorTest {

    @Test
    public void announcesInitialConnectionsAndReconnectOnlyOnce() {
        MqttService mqtt = mock(MqttService.class);
        ControlPanelService controlPanel = mock(ControlPanelService.class);
        CameraSpeechBroadcastService speech = mock(CameraSpeechBroadcastService.class);
        ConnectionSpeechMonitor monitor = new ConnectionSpeechMonitor(mqtt, controlPanel, speech);

        when(mqtt.isConnected()).thenReturn(true, true, false, true);
        when(controlPanel.connectionStatus()).thenReturn(
                Collections.<String, Object>singletonMap("reachable", true),
                Collections.<String, Object>singletonMap("reachable", true),
                Collections.<String, Object>singletonMap("reachable", false),
                Collections.<String, Object>singletonMap("reachable", true));

        monitor.checkConnections();
        monitor.checkConnections();
        monitor.checkConnections();
        monitor.checkConnections();

        verify(speech, times(2)).broadcast("MQTT服务连接成功");
        verify(speech, times(2)).broadcast("蓝牙服务连接成功");
        monitor.shutdown();
    }
}
