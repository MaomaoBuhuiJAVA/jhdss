package com.jhds.service.mqtt;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.jhds.common.Constants;
import com.jhds.common.ModbusUtil;
import com.jhds.config.MqttProperties;
import com.jhds.entity.Equipment;
import com.jhds.mapper.EquipmentMapper;
import com.jhds.service.ControlLogService;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.client.mqttv3.*;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantLock;

@Slf4j
@Service
public class MqttService implements DisposableBean {

    @Autowired
    private MqttProperties mqttProperties;
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;
    @Autowired
    private EquipmentMapper equipmentMapper;
    @Autowired
    private ControlLogService controlLogService;

    private MqttClient mqttClient;
    private final ConcurrentHashMap<String, CompletableFuture<String>> pendingCommands = new ConcurrentHashMap<>();

    private final ReentrantLock sequentialLock = new ReentrantLock();
    private final BlockingQueue<String> sequentialResponseQueue = new LinkedBlockingQueue<>();
    private volatile boolean sequentialMode = false;

    private static final Set<String> HEARTBEAT_HEX_PATTERNS = new HashSet<>(Arrays.asList(
            "77 77 77 2E 75 73 72 2E 63 6E"   // www.usr.cn
    ));
    private static final Set<String> HEARTBEAT_ASCII_PATTERNS = new HashSet<>(Arrays.asList(
            "www.usr.cn"
    ));
    private static final String REDIS_HEARTBEAT_KEY = Constants.REDIS_HEARTBEAT_KEY;

    @PostConstruct
    public void init() {
        if (!mqttProperties.isEnabled()) {
            log.warn("MQTT is disabled by configuration");
            return;
        }
        try {
            String clientId = mqttProperties.getClientId() + "-" + System.currentTimeMillis();
            mqttClient = new MqttClient(mqttProperties.getBrokerUrl(), clientId, new MemoryPersistence());
            MqttConnectOptions options = new MqttConnectOptions();
            options.setUserName(mqttProperties.getUsername());
            options.setPassword(mqttProperties.getPassword().toCharArray());
            options.setConnectionTimeout(mqttProperties.getConnectionTimeout());
            options.setKeepAliveInterval(mqttProperties.getKeepaliveInterval());
            options.setAutomaticReconnect(true);
            options.setCleanSession(true);
            mqttClient.setCallback(new MqttCallbackHandler(this));
            mqttClient.connect(options);
            String responseTopic = mqttProperties.getTopic().getPrefix() + "/" + mqttProperties.getTopic().getResponseSuffix();
            mqttClient.subscribe(responseTopic, 1);
            log.info("MQTT connected, subscribed to: {}", responseTopic);
        } catch (Exception e) {
            log.error("MQTT init error", e);
        }
    }

    public void handleResponse(String topic, String payload) {
        String trimmed = payload.trim();
        if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
            String requestId = extractRequestId(trimmed);
            if (requestId != null) {
                CompletableFuture<String> future = pendingCommands.remove(requestId);
                if (future != null) {
                    future.complete(trimmed);
                }
            }
        } else if (isHeartbeat(trimmed)) {
            log.debug("Heartbeat received: {}", ModbusUtil.hexToAscii(trimmed));
            redisTemplate.opsForValue().set(REDIS_HEARTBEAT_KEY, new Date().getTime());
        } else if (sequentialMode) {
            sequentialResponseQueue.offer(trimmed);
        } else {
            log.warn("Unexpected hex response (not in sequential mode): {}", trimmed);
        }
    }

    private boolean isHeartbeat(String hexPayload) {
        String normalized = hexPayload.replaceAll("\\s+", " ").trim();
        if (HEARTBEAT_HEX_PATTERNS.contains(normalized)) {
            return true;
        }
        if (HEARTBEAT_HEX_PATTERNS.contains(normalized.toUpperCase())) {
            return true;
        }
        try {
            String ascii = ModbusUtil.hexToAscii(hexPayload);
            if (HEARTBEAT_ASCII_PATTERNS.contains(ascii.trim())) {
                return true;
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    private String extractRequestId(String payload) {
        try {
            return JSON.parseObject(payload).getString("requestId");
        } catch (Exception e) {
            return null;
        }
    }

    public String sendCommand(String alias, String value, boolean automatic) {
        Equipment equipment = equipmentMapper.selectByAlias(alias);
        if (equipment == null) {
            log.warn("Equipment not found: {}", alias);
            return null;
        }
        String commandCode = equipment.getOpenCode();
        if ("close".equals(value) || "stop".equals(value)) {
            commandCode = equipment.getCloseCode();
        }
        if (commandCode == null || commandCode.isEmpty()) {
            log.warn("Command code empty for {} value={}", alias, value);
            return null;
        }

        if (commandCode.matches("^[0-9A-Fa-f ]+$")) {
            return sendHexCommand(alias, equipment, commandCode, value, automatic);
        }

        String requestId = UUID.randomUUID().toString().replace("-", "");
        String commandTopic = mqttProperties.getTopic().getPrefix() + "/"
                + mqttProperties.getTopic().getCommandSuffix();

        try {
            String payload = buildModbusFrame(requestId, alias, commandCode, value);
            String redisKey = Constants.REDIS_COMMAND_KEY + requestId;
            redisTemplate.opsForValue().set(redisKey, payload, Constants.COMMAND_TIMEOUT, TimeUnit.SECONDS);

            CompletableFuture<String> future = new CompletableFuture<>();
            pendingCommands.put(requestId, future);

            MqttMessage message = new MqttMessage(payload.getBytes());
            message.setQos(1);
            mqttClient.publish(commandTopic, message);
            log.info("Command sent: topic={}, payload={}", commandTopic, payload);

            String response = future.get(Constants.COMMAND_TIMEOUT, TimeUnit.SECONDS);
            boolean success = response != null;
            controlLogService.log(alias, equipment.getName(), value,
                    automatic ? 1 : 0, payload, response, success ? 1 : 0);
            if (success) {
                equipment.setStatus("close".equals(value) || "stop".equals(value) ? 0 : 1);
                equipmentMapper.updateById(equipment);
            }
            return response;
        } catch (Exception e) {
            log.error("Command failed: alias={}, value={}", alias, value, e);
            controlLogService.log(alias, equipment.getName(), value,
                    automatic ? 1 : 0, null, e.getMessage(), 0);
            pendingCommands.remove(requestId);
            return null;
        }
    }

    private String sendHexCommand(String alias, Equipment equipment, String commandCode, String value, boolean automatic) {
        try {
            lockSequential();
            String response = sendHexSync(commandCode, Constants.COMMAND_TIMEOUT * 1000L);
            boolean success = response != null;
            controlLogService.log(alias, equipment.getName(), value,
                    automatic ? 1 : 0, commandCode, response, success ? 1 : 0);
            if (success) {
                equipment.setStatus("close".equals(value) || "stop".equals(value) ? 0 : 1);
                equipmentMapper.updateById(equipment);
            }
            return response;
        } catch (Exception e) {
            log.error("Hex command failed: alias={}, value={}, code={}", alias, value, commandCode, e);
            controlLogService.log(alias, equipment.getName(), value,
                    automatic ? 1 : 0, commandCode, null, 0);
            return null;
        } finally {
            unlockSequential();
        }
    }

    private String buildModbusFrame(String requestId, String alias, String commandCode, String value) {
        JSONObject frame = new JSONObject();
        frame.put("requestId", requestId);
        frame.put("alias", alias);
        frame.put("command", commandCode);
        frame.put("value", value);
        frame.put("timestamp", System.currentTimeMillis());
        return frame.toJSONString();
    }

    public boolean isConnected() {
        return mqttClient != null && mqttClient.isConnected();
    }

    public void lockSequential() {
        sequentialLock.lock();
        sequentialMode = true;
        sequentialResponseQueue.clear();
    }

    public void unlockSequential() {
        sequentialMode = false;
        sequentialLock.unlock();
    }

    public String sendHexSync(String hexCommand, long timeoutMs) {
        try {
            String commandTopic = mqttProperties.getTopic().getPrefix() + "/"
                    + mqttProperties.getTopic().getCommandSuffix();
            MqttMessage message = new MqttMessage(ModbusUtil.hexToBytes(hexCommand));
            message.setQos(1);
            mqttClient.publish(commandTopic, message);
            log.debug("Hex command sent: {}", hexCommand);
            return sequentialResponseQueue.poll(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            log.error("sendHexSync failed: {}", hexCommand, e);
            return null;
        }
    }

    public void reconnect() {
        try {
            if (mqttClient != null && !mqttClient.isConnected()) {
                mqttClient.reconnect();
                log.info("MQTT reconnected");
            }
        } catch (Exception e) {
            log.error("MQTT reconnect error", e);
        }
    }

    @Override
    public void destroy() {
        try {
            if (mqttClient != null && mqttClient.isConnected()) {
                mqttClient.disconnect();
                mqttClient.close();
            }
        } catch (Exception e) {
            log.error("MQTT destroy error", e);
        }
    }
}
