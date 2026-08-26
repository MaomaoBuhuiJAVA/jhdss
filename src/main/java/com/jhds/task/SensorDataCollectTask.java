package com.jhds.task;

import com.jhds.service.WeatherService;
import com.jhds.service.mqtt.MqttService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class SensorDataCollectTask {

    @Autowired
    private WeatherService weatherService;
    @Autowired
    private MqttService mqttService;

    @Scheduled(cron = "0 */15 * * * ?")
    public void collectAllData() {
        if (!mqttService.isConnected()) {
            log.warn("MQTT not connected, skip sensor data collection");
            return;
        }
        log.debug("Collecting sensor data...");
        weatherService.collectSensorData();
        weatherService.collectSoilData();
        weatherService.collectSoilSensorDataByHex();
    }
}
