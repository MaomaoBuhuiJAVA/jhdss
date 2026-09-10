package com.jhds.service;

import com.jhds.service.mqtt.MqttService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.annotation.PreDestroy;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;

/** Announces initial connections and offline-to-online transitions once. */
@Slf4j
@Service
public class ConnectionSpeechMonitor {

    private final MqttService mqttService;
    private final ControlPanelService controlPanelService;
    private final CameraSpeechBroadcastService cameraSpeechBroadcastService;
    private final AtomicBoolean checking = new AtomicBoolean();
    private final ExecutorService executor = Executors.newSingleThreadExecutor(new ThreadFactory() {
        @Override
        public Thread newThread(Runnable task) {
            Thread thread = new Thread(task, "connection-speech-monitor");
            thread.setDaemon(true);
            return thread;
        }
    });

    @Value("${camera.speech.connection-announcements-enabled:true}")
    private boolean enabled;

    private Boolean mqttWasConnected;
    private Boolean bluetoothWasConnected;

    public ConnectionSpeechMonitor(MqttService mqttService,
                                   ControlPanelService controlPanelService,
                                   CameraSpeechBroadcastService cameraSpeechBroadcastService) {
        this.mqttService = mqttService;
        this.controlPanelService = controlPanelService;
        this.cameraSpeechBroadcastService = cameraSpeechBroadcastService;
    }

    @Scheduled(initialDelayString = "${camera.speech.connection-initial-delay-ms:5000}",
            fixedDelayString = "${camera.speech.connection-check-interval-ms:10000}")
    public void scheduleCheck() {
        if (!enabled || !checking.compareAndSet(false, true)) return;
        executor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    checkConnections();
                } finally {
                    checking.set(false);
                }
            }
        });
    }

    void checkConnections() {
        boolean mqttConnected = mqttService.isConnected();
        if (mqttConnected && !Boolean.TRUE.equals(mqttWasConnected)) {
            mqttWasConnected = announce("MQTT服务连接成功") ? Boolean.TRUE : Boolean.FALSE;
        } else {
            mqttWasConnected = mqttConnected;
        }

        Map<String, Object> status = controlPanelService.connectionStatus();
        boolean bluetoothConnected = Boolean.TRUE.equals(status.get("reachable"));
        if (bluetoothConnected && !Boolean.TRUE.equals(bluetoothWasConnected)) {
            bluetoothWasConnected = announce("蓝牙服务连接成功") ? Boolean.TRUE : Boolean.FALSE;
        } else {
            bluetoothWasConnected = bluetoothConnected;
        }
    }

    private boolean announce(String text) {
        try {
            cameraSpeechBroadcastService.broadcast(text);
            return true;
        } catch (RuntimeException e) {
            log.warn("Camera connection announcement failed: {}", e.getMessage());
            return false;
        }
    }

    @PreDestroy
    public void shutdown() {
        executor.shutdownNow();
    }
}
