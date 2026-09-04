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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
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

    // Optional overrides are useful when the motor command table is managed by
    // deployment configuration instead of edited directly in MySQL.
    @Value("${device.commands.motor-direction-open:}")
    private String motorDirectionOpen;
    @Value("${device.commands.motor-direction-close:}")
    private String motorDirectionClose;
    @Value("${device.commands.motor-state-open:}")
    private String motorStateOpen;
    @Value("${device.commands.motor-state-close:}")
    private String motorStateClose;

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
            String configuredClientId = mqttProperties.getClientId();
            String clientId = mqttProperties.isAppendInstanceId()
                    ? configuredClientId + "-" + System.currentTimeMillis() : configuredClientId;
            mqttClient = new MqttClient(mqttProperties.getBrokerUrl(), clientId, new MemoryPersistence());
            MqttConnectOptions options = new MqttConnectOptions();
            options.setUserName(mqttProperties.getUsername());
            options.setPassword(mqttProperties.getPassword().toCharArray());
            options.setConnectionTimeout(mqttProperties.getConnectionTimeout());
            options.setKeepAliveInterval(mqttProperties.getKeepaliveInterval());
            options.setAutomaticReconnect(true);
            options.setCleanSession(mqttProperties.isCleanSession());
            mqttClient.setCallback(new MqttCallbackHandler(this));
            mqttClient.connect(options);
            String responseTopic = mqttProperties.getTopic().getPrefix() + "/" + mqttProperties.getTopic().getResponseSuffix();
            mqttClient.subscribe(responseTopic, safeQos(mqttProperties.getResponseQos()));
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
        if (commandCode == null || commandCode.trim().isEmpty()) {
            commandCode = configuredCommand(alias, value);
        }
        if (commandCode == null || commandCode.isEmpty()) {
            log.warn("Command code empty for {} value={}", alias, value);
            return null;
        }

        if (mqttProperties.isTransparentMode() && !isHexCommand(commandCode)) {
            log.warn("Transparent MQTT mode requires a hexadecimal serial frame: alias={}, value={}", alias, value);
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

            MqttMessage message = new MqttMessage(payload.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            message.setQos(safeQos(mqttProperties.getCommandQos()));
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
            // A transparent MQTT write should be acknowledged immediately by
            // the DTU echo or the controller. Cap the wait so an unrelated
            // serial frame cannot make a button appear hung for 30 seconds.
            long timeoutMs = isModbusWriteCommand(commandCode)
                    ? Math.min(Constants.COMMAND_TIMEOUT * 1000L, 5000L)
                    : Constants.COMMAND_TIMEOUT * 1000L;
            String response = sendHexSync(commandCode, timeoutMs);
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

    private boolean isHexCommand(String commandCode) {
        return commandCode != null && commandCode.trim().matches("(?i)([0-9a-f]{2})(\\s+[0-9a-f]{2})*");
    }

    private String configuredCommand(String alias, String value) {
        boolean close = "close".equalsIgnoreCase(value) || "stop".equalsIgnoreCase(value);
        if ("MOTOR_DIRECTION".equalsIgnoreCase(alias)) {
            return close ? motorDirectionClose : motorDirectionOpen;
        }
        if ("MOTOR_STATE".equalsIgnoreCase(alias)) {
            return close ? motorStateClose : motorStateOpen;
        }
        return null;
    }

    private int safeQos(int qos) {
        return qos < 0 || qos > 2 ? 1 : qos;
    }

    /** A password-free status snapshot for the web UI and troubleshooting. */
    public Map<String, Object> connectionStatus() {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("enabled", mqttProperties.isEnabled());
        status.put("connected", isConnected());
        status.put("brokerUrl", mqttProperties.getBrokerUrl());
        status.put("clientId", mqttProperties.getClientId());
        status.put("commandTopic", mqttProperties.getTopic().getPrefix() + "/" + mqttProperties.getTopic().getCommandSuffix());
        status.put("responseTopic", mqttProperties.getTopic().getPrefix() + "/" + mqttProperties.getTopic().getResponseSuffix());
        status.put("transparentMode", mqttProperties.isTransparentMode());
        return status;
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
            if (!isHexCommand(hexCommand)) {
                log.warn("Invalid hexadecimal serial frame: {}", hexCommand);
                return null;
            }
            if (!isConnected()) {
                log.warn("MQTT is not connected; command was not sent");
                return null;
            }
            String commandTopic = mqttProperties.getTopic().getPrefix() + "/"
                    + mqttProperties.getTopic().getCommandSuffix();
            MqttMessage message = new MqttMessage(ModbusUtil.hexToBytes(hexCommand));
            message.setQos(safeQos(mqttProperties.getCommandQos()));
            mqttClient.publish(commandTopic, message);
            log.debug("Hex command sent: {}", hexCommand);
            long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs);
            String normalizedCommand = normalizeHex(hexCommand);
            // Modbus write responses (functions 05/06) are normally identical
            // to the transmitted frame. In transparent mode the DTU may label
            // that frame as an echo, but it is also the only acknowledgement
            // available when the downstream controller does not publish a
            // second response. Accept it for writes so controls do not wait
            // for the full timeout or report a false failure.
            boolean writeCommand = isModbusWriteCommand(hexCommand);
            while (true) {
                long remainingNanos = deadline - System.nanoTime();
                if (remainingNanos <= 0) return null;
                String response = sequentialResponseQueue.poll(remainingNanos, TimeUnit.NANOSECONDS);
                if (response == null) return null;
                if (mqttProperties.isIgnoreEcho() && normalizedCommand.equals(normalizeHex(response)) && !writeCommand) {
                    log.debug("Ignoring DTU command echo: {}", response);
                    continue;
                }
                if (writeCommand && !isMatchingModbusWriteResponse(hexCommand, response)) {
                    // Sensor polling and a motor command share the transparent
                    // response topic. Do not consume an unrelated frame and
                    // report a false motor success.
                    log.debug("Ignoring unrelated response for {}: {}", hexCommand, response);
                    continue;
                }
                return response;
            }
        } catch (Exception e) {
            log.error("sendHexSync failed: {}", hexCommand, e);
            return null;
        }
    }

    private String normalizeHex(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", " ").toUpperCase();
    }

    private boolean isModbusWriteCommand(String hexCommand) {
        try {
            byte[] frame = ModbusUtil.hexToBytes(hexCommand);
            if (frame.length < 2) return false;
            int function = frame[1] & 0xFF;
            return function == 5 || function == 6 || function == 15 || function == 16;
        } catch (RuntimeException e) {
            return false;
        }
    }

    /**
     * A Modbus write acknowledgement must come from the same slave and use
     * the same function code/address. Most controllers echo the full frame,
     * while some return a valid status value in the data bytes. An exception
     * response uses function|0x80 and is not treated as success.
     */
    private boolean isMatchingModbusWriteResponse(String hexCommand, String response) {
        try {
            byte[] request = ModbusUtil.hexToBytes(hexCommand);
            byte[] reply = ModbusUtil.hexToBytes(response);
            if (request.length < 2 || reply.length < 2) return false;
            if ((request[0] & 0xFF) != (reply[0] & 0xFF)) return false;
            int requestFunction = request[1] & 0xFF;
            int replyFunction = reply[1] & 0xFF;
            if (replyFunction == (requestFunction | 0x80)) {
                log.warn("Modbus write exception for {}: {}", hexCommand, response);
                return false;
            }
            if (replyFunction != requestFunction) return false;
            // Write responses retain the slave, function and target address.
            // Validate the complete returned frame CRC, but allow the device
            // specific status/value bytes to differ from the request.
            int prefixLength = requestFunction == 15 || requestFunction == 16 ? 6 : 4;
            if (request.length < prefixLength || reply.length < prefixLength + 2) return false;
            for (int i = 0; i < prefixLength; i++) {
                if (request[i] != reply[i]) return false;
            }
            return ModbusUtil.verifyCRC(reply);
        } catch (RuntimeException e) {
            return false;
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
