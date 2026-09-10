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
import org.springframework.scheduling.annotation.Scheduled;
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
import java.util.concurrent.atomic.AtomicBoolean;
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
    @Value("${device.commands.motor-confirmation-timeout-ms:1200}")
    private long motorConfirmationTimeoutMs;

    private volatile MqttClient mqttClient;
    private volatile String effectiveClientId;
    private volatile String lastDisconnectReason;
    private volatile String lastConnectionError;
    private volatile long lastConnectedAt;
    private volatile long lastDisconnectedAt;
    private volatile long lastConnectionAttemptAt;
    private volatile boolean responseSubscribed;
    private final ConcurrentHashMap<String, CompletableFuture<String>> pendingCommands = new ConcurrentHashMap<>();
    private final Object connectionLock = new Object();
    private final AtomicBoolean reconnectInProgress = new AtomicBoolean(false);
    private volatile boolean shuttingDown;

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
        shuttingDown = false;
        connectClient();
    }

    /**
     * Connects one client at a time. A failed initial connect leaves a Paho
     * client in a state where a later reconnect is not always reliable, so the
     * failed instance is closed and recreated on the next attempt.
     */
    private void connectClient() {
        synchronized (connectionLock) {
            if (shuttingDown || !mqttProperties.isEnabled() || isConnected()) {
                return;
            }
            lastConnectionAttemptAt = System.currentTimeMillis();
            MqttClient client = mqttClient;
            try {
                if (client == null) {
                    String configuredClientId = requireValue(mqttProperties.getClientId(), "client-id");
                    String clientId = mqttProperties.isAppendInstanceId()
                            ? configuredClientId + "-" + System.currentTimeMillis() : configuredClientId;
                    effectiveClientId = clientId;
                    client = new MqttClient(requireValue(mqttProperties.getBrokerUrl(), "broker-url"),
                            clientId, new MemoryPersistence());
                    client.setCallback(new MqttCallbackHandler(this));
                    mqttClient = client;
                }
                client.connect(connectOptions());
                log.info("MQTT connect request succeeded, clientId={}, broker={}",
                        effectiveClientId, client.getCurrentServerURI());
            } catch (Exception e) {
                lastConnectionError = describeException(e);
                log.error("MQTT connection attempt failed: {}", lastConnectionError);
                closeFailedClient(client);
            }
        }
    }

    private MqttConnectOptions connectOptions() {
        MqttConnectOptions options = new MqttConnectOptions();
        String username = mqttProperties.getUsername();
        String password = mqttProperties.getPassword();
        if (username != null && !username.trim().isEmpty()) {
            options.setUserName(username.trim());
        }
        if (password != null) {
            options.setPassword(password.toCharArray());
        }
        options.setConnectionTimeout(Math.max(3, mqttProperties.getConnectionTimeout()));
        options.setKeepAliveInterval(Math.max(15, mqttProperties.getKeepaliveInterval()));
        options.setAutomaticReconnect(true);
        options.setCleanSession(mqttProperties.isCleanSession());
        return options;
    }

    private String requireValue(String value, String name) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("MQTT " + name + " is empty");
        }
        return value.trim();
    }

    private String describeException(Throwable error) {
        Throwable root = error;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        StringBuilder message = new StringBuilder(root.getClass().getSimpleName());
        if (root instanceof MqttException) {
            message.append(" reasonCode=").append(((MqttException) root).getReasonCode());
        }
        if (root.getMessage() != null && !root.getMessage().trim().isEmpty()) {
            message.append(": ").append(root.getMessage().trim());
        }
        return message.toString();
    }

    private void closeFailedClient(MqttClient client) {
        if (client == null) {
            mqttClient = null;
            return;
        }
        try {
            if (client.isConnected()) {
                client.disconnectForcibly();
            }
        } catch (Exception ignored) {
        }
        try {
            client.close();
        } catch (Exception ignored) {
        }
        if (mqttClient == client) {
            mqttClient = null;
        }
    }

    /** Called after the initial connection and after Paho automatic reconnect. */
    public synchronized void handleConnected(boolean reconnect, String serverUri) {
        try {
            String responseTopic = mqttProperties.getTopic().getPrefix() + "/" + mqttProperties.getTopic().getResponseSuffix();
            if (mqttClient != null && mqttClient.isConnected()) {
                mqttClient.subscribe(responseTopic, safeQos(mqttProperties.getResponseQos()));
                responseSubscribed = true;
                lastConnectedAt = System.currentTimeMillis();
                lastConnectionError = null;
                log.info("MQTT {}connected, clientId={}, subscribed to: {}",
                        reconnect ? "re" : "", effectiveClientId, responseTopic);
            }
        } catch (Exception e) {
            lastConnectionError = e.getMessage();
            log.error("MQTT subscription failed after connection", e);
        }
    }

    public void handleConnectionLost(Throwable cause) {
        responseSubscribed = false;
        lastDisconnectedAt = System.currentTimeMillis();
        lastDisconnectReason = cause == null ? "unknown" : cause.toString();
        log.error("MQTT connection lost, clientId={}, reason={}", effectiveClientId, lastDisconnectReason);
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
            boolean momentaryMotorCommand = "MOTOR_DIRECTION".equalsIgnoreCase(alias)
                    || "MOTOR_STATE".equalsIgnoreCase(alias);
            long timeoutMs = momentaryMotorCommand
                    ? Math.max(250L, Math.min(5000L, motorConfirmationTimeoutMs))
                    : isModbusWriteCommand(commandCode)
                    ? Math.min(Constants.COMMAND_TIMEOUT * 1000L, 5000L)
                    : Constants.COMMAND_TIMEOUT * 1000L;
            // Rail motor commands are momentary control actions. QoS 1 may be
            // queued by the broker while the DTU is offline and replayed when
            // the device powers on, causing an unexpected limit hit. QoS 0
            // makes these commands valid only while the device is connected.
            String response = sendHexSync(commandCode, timeoutMs, momentaryMotorCommand ? 0 : safeQos(mqttProperties.getCommandQos()));
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
        status.put("effectiveClientId", effectiveClientId);
        status.put("commandTopic", mqttProperties.getTopic().getPrefix() + "/" + mqttProperties.getTopic().getCommandSuffix());
        status.put("responseTopic", mqttProperties.getTopic().getPrefix() + "/" + mqttProperties.getTopic().getResponseSuffix());
        status.put("transparentMode", mqttProperties.isTransparentMode());
        status.put("lastDisconnectReason", lastDisconnectReason);
        status.put("lastConnectionError", lastConnectionError);
        status.put("lastConnectedAt", lastConnectedAt == 0 ? null : new Date(lastConnectedAt));
        status.put("lastDisconnectedAt", lastDisconnectedAt == 0 ? null : new Date(lastDisconnectedAt));
        status.put("lastConnectionAttemptAt", lastConnectionAttemptAt == 0 ? null : new Date(lastConnectionAttemptAt));
        return status;
    }

    public boolean isConnected() {
        return mqttClient != null && mqttClient.isConnected();
    }

    /**
     * Paho automatic reconnect starts only after a connection has succeeded.
     * Retry here as well so a temporary outage during application startup does
     * not leave the service permanently offline.
     */
    @Scheduled(
            fixedDelayString = "${device.mqtt.reconnect-interval-ms:10000}",
            initialDelayString = "${device.mqtt.reconnect-initial-delay-ms:10000}")
    public void ensureConnected() {
        if (!mqttProperties.isEnabled()) {
            return;
        }
        if (isConnected()) {
            if (!responseSubscribed) {
                handleConnected(true, mqttClient.getCurrentServerURI());
            }
            return;
        }
        reconnect();
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
        return sendHexSync(hexCommand, timeoutMs, safeQos(mqttProperties.getCommandQos()));
    }

    private String sendHexSync(String hexCommand, long timeoutMs, int qos) {
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
            message.setQos(qos);
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
            // A valid Modbus write acknowledgement echoes the address and the
            // written value/quantity. Requiring all six bytes prevents a
            // different controller status frame from being reported as a
            // successful motor command.
            int prefixLength = 6;
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
        if (!reconnectInProgress.compareAndSet(false, true)) {
            log.debug("MQTT reconnect already in progress");
            return;
        }
        try {
            synchronized (connectionLock) {
                if (shuttingDown || !mqttProperties.isEnabled() || isConnected()) {
                    return;
                }
                lastConnectionAttemptAt = System.currentTimeMillis();
                MqttClient client = mqttClient;
                try {
                    if (client == null) {
                        // Recreate clients whose initial connection failed.
                        connectClient();
                    } else {
                        client.reconnect();
                        log.info("MQTT reconnect request sent, clientId={}", effectiveClientId);
                    }
                } catch (Exception e) {
                    lastConnectionError = describeException(e);
                    log.warn("MQTT reconnect failed: {}", lastConnectionError);
                    closeFailedClient(client);
                }
            }
        } finally {
            reconnectInProgress.set(false);
        }
    }

    @Override
    public void destroy() {
        shuttingDown = true;
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
