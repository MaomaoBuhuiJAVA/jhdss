package com.jhds.service.mqtt;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.jhds.common.Constants;
import com.jhds.common.ModbusUtil;
import com.jhds.config.MqttProperties;
import com.jhds.entity.Equipment;
import com.jhds.mapper.EquipmentMapper;
import com.jhds.service.ControlLogService;
import com.jhds.service.ControlPanelService;
import com.jhds.service.DeviceTwinState;
import com.jhds.service.ModbusTcpTransport;
import com.jhds.service.ModbusMotorService;
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
    @Autowired
    private DeviceTwinState deviceTwinState;
    @Autowired
    private ModbusTcpTransport modbusTcpTransport;
    @Autowired
    private ModbusMotorService modbusMotorService;
    @Autowired
    private ControlPanelService controlPanelService;

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
    @Value("${device.commands.motor-stop-confirmation-timeout-ms:3500}")
    private long motorStopConfirmationTimeoutMs;
    @Value("${modbus.fallback.motor.relative-target:1000}")
    private int modbusFallbackMotorTarget;
    @Value("${modbus.fallback.motor.speed:60}")
    private int modbusFallbackMotorSpeed;
    @Value("${modbus.motor.unit-id:1}")
    private int modbusMotorUnitId;

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
        deviceTwinState.invalidate(alias);
        Equipment equipment = equipmentMapper.selectByAlias(alias);
        if (equipment == null) {
            log.warn("Equipment not found: {}", alias);
            return null;
        }
        if (!isConnected()) {
            if (isDirectMotorSignal(alias)) {
                return sendDirectMotorFallback(alias, equipment, value, automatic);
            }
            if (isGatewayOutputSignal(alias)) {
                String gatewayResponse = sendGatewayOutputFallback(alias, equipment, value, automatic);
                if (gatewayResponse != null) return gatewayResponse;
            }
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
                observeTwinResponse(alias, value, response);
            } else {
                deviceTwinState.invalidate(alias);
            }
            return response;
        } catch (Exception e) {
            log.error("Command failed: alias={}, value={}", alias, value, e);
            controlLogService.log(alias, equipment.getName(), value,
                    automatic ? 1 : 0, null, e.getMessage(), 0);
            pendingCommands.remove(requestId);
            deviceTwinState.invalidate(alias);
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
            boolean motorStopCommand = "MOTOR_STATE".equalsIgnoreCase(alias)
                    && ("close".equalsIgnoreCase(value) || "stop".equalsIgnoreCase(value));
            long motorTimeoutMs = motorStopCommand
                    ? motorStopConfirmationTimeoutMs : motorConfirmationTimeoutMs;
            long timeoutMs = momentaryMotorCommand
                    ? Math.max(250L, Math.min(5000L, motorTimeoutMs))
                    : isModbusWriteCommand(commandCode)
                    ? Math.min(Constants.COMMAND_TIMEOUT * 1000L, 5000L)
                    : Constants.COMMAND_TIMEOUT * 1000L;
            // Rail motor commands are momentary control actions. QoS 1 may be
            // queued by the broker while the DTU is offline and replayed when
            // the device powers on, causing an unexpected limit hit. QoS 0
            // makes these commands valid only while the device is connected.
            String response = sendHexSync(commandCode, timeoutMs, momentaryMotorCommand ? 0 : safeQos(mqttProperties.getCommandQos()));
            if (response == null && momentaryMotorCommand) {
                response = retryMotorFrameThroughModbus(alias, commandCode);
            }
            boolean success = response != null;
            controlLogService.log(alias, equipment.getName(), value,
                    automatic ? 1 : 0, commandCode, response, success ? 1 : 0);
            if (success) {
                equipment.setStatus("close".equals(value) || "stop".equals(value) ? 0 : 1);
                equipmentMapper.updateById(equipment);
                observeTwinResponse(alias, value, response);
            } else {
                deviceTwinState.invalidate(alias);
            }
            return response;
        } catch (Exception e) {
            log.error("Hex command failed: alias={}, value={}, code={}", alias, value, commandCode, e);
            deviceTwinState.invalidate(alias);
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
        boolean mqttConnected = isConnected();
        Map<String, Object> gateway = controlPanelService == null ? null : controlPanelService.connectionStatus();
        boolean gatewayReachable = gateway != null && Boolean.TRUE.equals(gateway.get("reachable"));
        Map<String, Object> fallback = modbusTcpTransport == null ? new LinkedHashMap<>()
                : modbusTcpTransport.status(!mqttConnected && !gatewayReachable);
        boolean directReachable = Boolean.TRUE.equals(fallback.get("reachable"));
        fallback.put("directReachable", directReachable);
        fallback.put("gateway", gateway);
        fallback.put("reachable", directReachable || gatewayReachable);
        fallback.put("mode", gatewayReachable ? "software-modbus"
                : directReachable ? "direct-modbus" : "offline");
        status.put("fallback", fallback);
        status.put("transportMode", mqttConnected ? "mqtt"
                : fallback != null && Boolean.TRUE.equals(fallback.get("enabled"))
                && Boolean.TRUE.equals(fallback.get("reachable")) ? "modbus" : "offline");
        return status;
    }

    private boolean isDirectMotorSignal(String alias) {
        return "MOTOR_DIRECTION".equalsIgnoreCase(alias) || "MOTOR_STATE".equalsIgnoreCase(alias);
    }

    private boolean isGatewayOutputSignal(String alias) {
        return "PUMP_CO2".equalsIgnoreCase(alias) || "PUMP_CIRCULATION".equalsIgnoreCase(alias);
    }

    private String sendGatewayOutputFallback(String alias, Equipment equipment, String value, boolean automatic) {
        if (controlPanelService == null || modbusTcpTransport == null
                || !modbusTcpTransport.isFallbackEnabled()) return null;
        boolean enabled = !"close".equalsIgnoreCase(value) && !"stop".equalsIgnoreCase(value);
        try {
            Map<String, Object> result = controlPanelService.deviceOutput(alias, enabled);
            result.put("transport", "modbus");
            result.put("fallbackPath", "software-http-modbus");
            result.put("alias", alias);
            result.put("success", true);
            String response = JSON.toJSONString(result);
            controlLogService.log(alias, equipment.getName(), value, automatic ? 1 : 0,
                    "MODBUS_GATEWAY " + alias + " " + value, response, 1);
            equipment.setStatus(enabled ? 1 : 0);
            equipmentMapper.updateById(equipment);
            observeTwinResponse(alias, value, response);
            return response;
        } catch (RuntimeException gatewayError) {
            log.warn("Local Modbus gateway output failed; trying direct TCP: alias={}, error={}",
                    alias, gatewayError.getMessage());
            return null;
        }
    }

    /**
     * Retries the exact rail frame through Modbus TCP when MQTT is connected
     * but the DTU loses or truncates the acknowledgement. Reusing the RTU
     * frame preserves its slave id and register/coil address, so the fallback
     * cannot accidentally operate the other motor axis.
     */
    private String retryMotorFrameThroughModbus(String alias, String commandCode) {
        if (modbusTcpTransport == null || !modbusTcpTransport.isFallbackEnabled()) {
            log.warn("Motor MQTT acknowledgement missing and Modbus fallback is disabled: alias={}", alias);
            return null;
        }
        try {
            log.warn("Motor MQTT acknowledgement missing; retrying the same frame through Modbus: alias={}", alias);
            String response = modbusTcpTransport.exchangeRtuFrame(commandCode);
            if (!isMatchingModbusWriteResponse(commandCode, response)) {
                log.warn("Modbus motor fallback returned an unrelated frame: alias={}, response={}", alias, response);
                return null;
            }
            return response;
        } catch (RuntimeException e) {
            log.error("Modbus motor fallback failed: alias={}, command={}", alias, commandCode, e);
            return null;
        }
    }

    private String sendDirectMotorFallback(String alias, Equipment equipment, String value, boolean automatic) {
        if (modbusTcpTransport == null || !modbusTcpTransport.isFallbackEnabled() || modbusMotorService == null) {
            log.warn("MQTT is offline and direct Modbus motor fallback is unavailable: {}", alias);
            return null;
        }
        try {
            Map<String, Object> result = sendMotorThroughGateway(alias, value);
            if (result == null) result = sendMotorDirectly(alias, value);
            result.put("transport", "modbus");
            result.put("alias", alias);
            result.put("success", true);
            String response = JSON.toJSONString(result);
            controlLogService.log(alias, equipment.getName(), value, automatic ? 1 : 0,
                    "MODBUS_TCP " + alias + " " + value, response, 1);
            equipment.setStatus("close".equalsIgnoreCase(value) || "stop".equalsIgnoreCase(value) ? 0 : 1);
            equipmentMapper.updateById(equipment);
            observeTwinResponse(alias, value, response);
            return response;
        } catch (RuntimeException e) {
            log.error("Direct Modbus motor fallback failed: alias={}, value={}", alias, value, e);
            if ("MOTOR_STATE".equalsIgnoreCase(alias)
                    && !"close".equalsIgnoreCase(value) && !"stop".equalsIgnoreCase(value)) {
                try {
                    modbusMotorService.stop();
                } catch (RuntimeException stopError) {
                    log.error("Direct Modbus safety stop also failed", stopError);
                }
            }
            controlLogService.log(alias, equipment.getName(), value, automatic ? 1 : 0,
                    "MODBUS_TCP " + alias + " " + value, e.getMessage(), 0);
            deviceTwinState.invalidate(alias);
            return null;
        }
    }

    private Map<String, Object> sendMotorThroughGateway(String alias, String value) {
        if (controlPanelService == null) return null;
        boolean close = "close".equalsIgnoreCase(value) || "stop".equalsIgnoreCase(value);
        try {
            Map<String, Object> result;
            if ("MOTOR_DIRECTION".equalsIgnoreCase(alias)) {
                result = controlPanelService.move(close ? "backward" : "forward", true);
            } else if (close) {
                result = controlPanelService.stopMotion();
            } else if (controlPanelService.hasActiveMotion()) {
                result = controlPanelService.motionStatus();
            } else {
                return null;
            }
            result.put("fallbackPath", "software-http-modbus");
            return result;
        } catch (RuntimeException gatewayError) {
            log.warn("Local Modbus gateway failed; trying direct TCP: {}", gatewayError.getMessage());
            return null;
        }
    }

    private Map<String, Object> sendMotorDirectly(String alias, String value) {
        if ("MOTOR_DIRECTION".equalsIgnoreCase(alias)) {
            boolean close = "close".equalsIgnoreCase(value) || "stop".equalsIgnoreCase(value);
            return modbusMotorService.prepareRelative(modbusFallbackMotorTarget,
                    modbusFallbackMotorSpeed, close ? "backward" : "forward");
        }
        boolean enabled = !"close".equalsIgnoreCase(value) && !"stop".equalsIgnoreCase(value);
        return modbusMotorService.run(enabled);
    }

    /** Updates D20 through MQTT first, then the local gateway/direct Modbus fallback. */
    public Map<String, Object> setMotorSpeed(int speed, boolean automatic) {
        if (speed < 1 || speed > 500) {
            throw new IllegalArgumentException("电机速度范围为 1-500");
        }

        String command = buildMotorSpeedFrame(modbusMotorUnitId, speed);
        if (isConnected()) {
            if (!tryLockSequential()) {
                log.warn("Motor speed update skipped because the control channel is busy");
                return null;
            }
            String response;
            try {
                response = sendHexSync(command, Math.max(250L, Math.min(5000L, motorConfirmationTimeoutMs)), 0);
            } finally {
                unlockSequential();
            }
            if (response == null) return null;
            modbusFallbackMotorSpeed = speed;
            return motorSpeedResult(speed, "mqtt", response);
        }

        if (modbusTcpTransport == null || !modbusTcpTransport.isFallbackEnabled()) return null;
        if (controlPanelService != null) {
            try {
                Map<String, Object> result = controlPanelService.motorSpeed(speed);
                result.put("transport", "modbus");
                result.put("fallbackPath", "software-http-modbus");
                modbusFallbackMotorSpeed = speed;
                return result;
            } catch (RuntimeException gatewayError) {
                log.warn("Local Modbus gateway speed command failed; trying direct TCP: {}",
                        gatewayError.getMessage());
            }
        }
        if (modbusMotorService == null) return null;
        try {
            Map<String, Object> result = modbusMotorService.setSpeed(speed);
            result.put("transport", "modbus");
            result.put("fallbackPath", "direct-modbus");
            modbusFallbackMotorSpeed = speed;
            return result;
        } catch (RuntimeException directError) {
            log.error("Direct Modbus motor speed command failed: speed={}", speed, directError);
            return null;
        }
    }

    private Map<String, Object> motorSpeedResult(int speed, String transport, String response) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("action", "speed");
        result.put("speed", speed);
        result.put("transport", transport);
        result.put("response", response);
        result.put("success", true);
        result.put("sentAt", System.currentTimeMillis());
        return result;
    }

    private String buildMotorSpeedFrame(int unitId, int speed) {
        if (unitId < 0 || unitId > 255) throw new IllegalArgumentException("Modbus 单元地址范围为 0-255");
        byte[] payload = new byte[]{
                (byte) unitId, 6, 0, 20, (byte) (speed >>> 8), (byte) speed
        };
        int crc = ModbusUtil.calculateCRC16(payload);
        byte[] frame = Arrays.copyOf(payload, payload.length + 2);
        frame[frame.length - 2] = (byte) (crc & 0xFF);
        frame[frame.length - 1] = (byte) ((crc >>> 8) & 0xFF);
        return ModbusUtil.bytesToHex(frame);
    }

    public boolean isConnected() {
        return mqttClient != null && mqttClient.isConnected();
    }

    /** True when commands can use MQTT or the explicitly configured local fallback. */
    public boolean hasAvailableTransport() {
        if (isConnected()) return true;
        if (controlPanelService != null && controlPanelService.isReachable()) return true;
        return modbusTcpTransport != null
                && modbusTcpTransport.isFallbackEnabled() && modbusTcpTransport.isReachable();
    }

    public long commandTransportEpoch() {
        return isConnected() ? twinConnectionEpoch()
                : modbusTcpTransport == null ? twinConnectionEpoch() : modbusTcpTransport.connectionEpoch();
    }

    private void observeTwinResponse(String alias, String value, String response) {
        if (response.trim().startsWith("{")) {
            try {
                JSONObject result = JSON.parseObject(response);
                if (Boolean.FALSE.equals(result.getBoolean("success"))
                        || (result.containsKey("code") && result.getIntValue("code") >= 400)
                        || result.get("error") != null) return;
            } catch (RuntimeException invalid) { return; }
        }
        deviceTwinState.acknowledge(alias, !"close".equals(value) && !"stop".equals(value));
    }

    public long twinConnectionEpoch() {
        return Math.max(lastConnectedAt, lastDisconnectedAt);
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

    private boolean tryLockSequential() {
        if (!sequentialLock.tryLock()) return false;
        sequentialMode = true;
        sequentialResponseQueue.clear();
        return true;
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
                if (modbusTcpTransport == null || !modbusTcpTransport.isFallbackEnabled()) {
                    log.warn("MQTT is not connected and Modbus fallback is disabled");
                    return null;
                }
                log.info("MQTT unavailable; sending RTU frame through Modbus TCP fallback: {}", hexCommand);
                return modbusTcpTransport.exchangeRtuFrame(hexCommand);
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
            if (request.length < 6 || reply.length < 8) return false;
            if (request[2] != reply[2] || request[3] != reply[3]) return false;
            if (!ModbusUtil.verifyCRC(reply)) return false;

            // The deployed rail controller returns a status word (observed
            // 45 04) for slave 3 / FC05 / coil 1 instead of echoing FF 00.
            // Scope this compatibility rule to that exact device address;
            // every other write still requires a full value echo.
            boolean deployedRailStatusAck = (request[0] & 0xFF) == 3
                    && requestFunction == 5
                    && (request[2] & 0xFF) == 0
                    && (request[3] & 0xFF) == 1;
            if (deployedRailStatusAck) return true;

            return request[4] == reply[4] && request[5] == reply[5];
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
