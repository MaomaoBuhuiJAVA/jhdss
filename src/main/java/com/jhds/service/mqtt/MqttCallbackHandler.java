package com.jhds.service.mqtt;

import com.jhds.common.ModbusUtil;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
import org.eclipse.paho.client.mqttv3.MqttMessage;

import java.nio.charset.StandardCharsets;

@Slf4j
public class MqttCallbackHandler implements MqttCallbackExtended {

    private final MqttService mqttService;

    public MqttCallbackHandler(MqttService mqttService) {
        this.mqttService = mqttService;
    }

    @Override
    public void connectionLost(Throwable cause) {
        mqttService.handleConnectionLost(cause);
    }

    @Override
    public void connectComplete(boolean reconnect, String serverURI) {
        mqttService.handleConnected(reconnect, serverURI);
    }

    @Override
    public void messageArrived(String topic, MqttMessage message) {
        byte[] raw = message.getPayload();
        String payload = (raw.length > 0 && raw[0] == 0x7B)
                ? new String(raw, StandardCharsets.UTF_8)
                : ModbusUtil.bytesToHex(raw);
        log.debug("MQTT message arrived: topic={}, payload={}", topic, payload);
        mqttService.handleResponse(topic, payload);
    }

    @Override
    public void deliveryComplete(IMqttDeliveryToken token) {
    }
}
