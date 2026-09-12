package com.jhds.gateway;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Local HTTP-to-Modbus TCP gateway for the deployed JHDS controller.
 *
 * It intentionally exposes semantic, whitelisted actions rather than an
 * arbitrary register-write endpoint. This process owns and reuses the PLC
 * connection so Spring and the legacy control UI do not compete for port 502.
 */
public final class JhdsModbusGateway {
    private JhdsModbusGateway() {
    }

    public static void main(String[] args) throws Exception {
        Config config = Config.fromEnvironment();
        final GatewayServer gateway = new GatewayServer(config);
        Runtime.getRuntime().addShutdownHook(new Thread(gateway::close, "jhds-gateway-shutdown"));
        gateway.start();
        System.out.println("JHDS Modbus gateway listening on http://" + config.bindHost + ":" + gateway.port()
                + ", PLC=" + config.modbusHost + ":" + config.modbusPort + ", unit=" + config.unitId);
        new CountDownLatch(1).await();
    }

    static final class Config {
        final String bindHost;
        final int httpPort;
        final String modbusHost;
        final int modbusPort;
        final int unitId;
        final int timeoutMs;

        Config(String bindHost, int httpPort, String modbusHost, int modbusPort, int unitId, int timeoutMs) {
            this.bindHost = bindHost;
            this.httpPort = httpPort;
            this.modbusHost = modbusHost;
            this.modbusPort = modbusPort;
            this.unitId = unitId;
            this.timeoutMs = timeoutMs;
        }

        static Config fromEnvironment() {
            return new Config(
                    env("MODBUS_GATEWAY_BIND", "127.0.0.1"),
                    envInt("MODBUS_GATEWAY_PORT", 8999, 1, 65535),
                    env("MODBUS_MOTOR_HOST", "192.168.1.12"),
                    envInt("MODBUS_MOTOR_PORT", 502, 1, 65535),
                    envInt("MODBUS_MOTOR_UNIT_ID", 1, 0, 255),
                    envInt("MODBUS_MOTOR_TIMEOUT_MS", 1500, 250, 30000));
        }

        private static String env(String key, String fallback) {
            String value = System.getenv(key);
            return value == null || value.trim().isEmpty() ? fallback : value.trim();
        }

        private static int envInt(String key, int fallback, int min, int max) {
            String value = System.getenv(key);
            if (value == null || value.trim().isEmpty()) return fallback;
            try {
                int parsed = Integer.parseInt(value.trim());
                if (parsed < min || parsed > max) throw new IllegalArgumentException();
                return parsed;
            } catch (RuntimeException invalid) {
                throw new IllegalArgumentException(key + " must be between " + min + " and " + max);
            }
        }
    }

    static final class GatewayServer implements AutoCloseable {
        private static final int COIL_MODE_CONFIRM = 0;
        private static final int COIL_FORWARD = 10;
        private static final int COIL_BACKWARD = 11;
        private static final int COIL_RUN = 12;
        private static final int COIL_PUMP = 13;
        private static final int COIL_POSITION_1 = 21;
        private static final int COIL_POSITION_2 = 23;
        private static final int REGISTER_CIRCULATION = 1;
        private static final int REGISTER_CO2 = 2;
        private static final int REGISTER_SPEED = 20;
        private static final int REGISTER_RELATIVE_TARGET = 100;
        private static final int REGISTER_POSITION_1 = 200;
        private static final int REGISTER_POSITION_2 = 210;

        private final Config config;
        private final ModbusConnection plc;
        private final HttpServer server;
        private final ExecutorService executor;

        GatewayServer(Config config) throws IOException {
            this.config = config;
            this.plc = new ModbusConnection(config.modbusHost, config.modbusPort, config.unitId, config.timeoutMs);
            this.server = HttpServer.create(new InetSocketAddress(config.bindHost, config.httpPort), 0);
            this.executor = Executors.newFixedThreadPool(4, namedThreads("jhds-gateway-http-"));
            this.server.setExecutor(executor);
            this.server.createContext("/", new GatewayHandler());
        }

        void start() {
            server.start();
            plc.probe();
        }

        int port() {
            return server.getAddress().getPort();
        }

        @Override
        public void close() {
            server.stop(0);
            executor.shutdownNow();
            plc.close();
        }

        private final class GatewayHandler implements HttpHandler {
            @Override
            public void handle(HttpExchange exchange) throws IOException {
                addHeaders(exchange);
                if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                    exchange.sendResponseHeaders(204, -1);
                    exchange.close();
                    return;
                }
                String path = exchange.getRequestURI().getPath();
                try {
                    if (("/".equals(path) || "/health".equals(path))
                            && "GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                        health(exchange);
                        return;
                    }
                    if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                        json(exchange, 405, error("method not allowed"));
                        return;
                    }
                    JsonBody body = JsonBody.parse(readBody(exchange));
                    route(exchange, path, body);
                } catch (IllegalArgumentException badRequest) {
                    json(exchange, 400, error(badRequest.getMessage()));
                } catch (IllegalStateException unavailable) {
                    json(exchange, 503, error(unavailable.getMessage()));
                } catch (RuntimeException unexpected) {
                    json(exchange, 500, error("gateway failure"));
                }
            }
        }

        private void health(HttpExchange exchange) throws IOException {
            boolean connected = plc.probe();
            String payload = "{\"service\":\"jhds-modbus-gateway\",\"status\":\""
                    + (connected ? "UP" : "DEGRADED") + "\",\"connected\":" + connected
                    + ",\"plc\":\"" + escape(config.modbusHost + ":" + config.modbusPort) + "\""
                    + (plc.lastError() == null ? "" : ",\"lastError\":\"" + escape(plc.lastError()) + "\"")
                    + "}";
            json(exchange, connected ? 200 : 503, payload);
        }

        private void route(HttpExchange exchange, String path, JsonBody body) throws IOException {
            switch (path) {
                case "/api/moveforward":
                    setDirection(exchange, true, body.requiredBoolean("LenData"));
                    return;
                case "/api/movebackward":
                    setDirection(exchange, false, body.requiredBoolean("LenData"));
                    return;
                case "/api/pump":
                    setCoil(exchange, "pump", COIL_PUMP, body.requiredBoolean("LenData"));
                    return;
                case "/api/co2":
                    setRegisterOutput(exchange, "PUMP_CO2", REGISTER_CO2, body.requiredBoolean("LenData"));
                    return;
                case "/api/circulation":
                    setRegisterOutput(exchange, "PUMP_CIRCULATION", REGISTER_CIRCULATION,
                            body.requiredBoolean("LenData"));
                    return;
                case "/api/device-control":
                    setWhitelistedOutput(exchange, body.requiredString("alias"), body.requiredBoolean("enabled"));
                    return;
                case "/api/speed":
                    setSpeed(exchange, body.requiredInt("Speed"));
                    return;
                case "/api/confirm":
                    prepareRelative(exchange, body.requiredInt("LenData"), body.requiredInt("Speed"));
                    return;
                case "/api/situationconfirm":
                    setCoil(exchange, "confirm", COIL_MODE_CONFIRM, body.requiredBoolean("LenData"));
                    return;
                case "/api/startrun":
                    setCoil(exchange, "run", COIL_RUN, body.requiredBoolean("LenData"));
                    return;
                case "/api/position1":
                    setPosition(exchange, 1, body.requiredInt("LenData"));
                    return;
                case "/api/position2":
                    setPosition(exchange, 2, body.requiredInt("LenData"));
                    return;
                default:
                    json(exchange, 404, error("route not found"));
            }
        }

        private void setDirection(HttpExchange exchange, boolean forward, boolean enabled) throws IOException {
            int selected = forward ? COIL_FORWARD : COIL_BACKWARD;
            int opposite = forward ? COIL_BACKWARD : COIL_FORWARD;
            if (enabled) plc.writeCoil(opposite, false);
            plc.writeCoil(selected, enabled);
            json(exchange, 200, success((forward ? "forward" : "backward"), enabled));
        }

        private void setCoil(HttpExchange exchange, String action, int address, boolean enabled) throws IOException {
            plc.writeCoil(address, enabled);
            json(exchange, 200, success(action, enabled));
        }

        private void setWhitelistedOutput(HttpExchange exchange, String alias, boolean enabled) throws IOException {
            if ("PUMP_CO2".equalsIgnoreCase(alias)) {
                setRegisterOutput(exchange, "PUMP_CO2", REGISTER_CO2, enabled);
            } else if ("PUMP_CIRCULATION".equalsIgnoreCase(alias)) {
                setRegisterOutput(exchange, "PUMP_CIRCULATION", REGISTER_CIRCULATION, enabled);
            } else {
                throw new IllegalArgumentException("device alias is not allowed");
            }
        }

        private void setRegisterOutput(HttpExchange exchange, String alias, int address, boolean enabled)
                throws IOException {
            plc.writeRegister(address, enabled ? 1 : 0);
            json(exchange, 200, success(alias, enabled));
        }

        private void setSpeed(HttpExchange exchange, int speed) throws IOException {
            requireRange(speed, 1, 500, "Speed");
            plc.writeRegister(REGISTER_SPEED, speed);
            json(exchange, 200, "{\"code\":200,\"msg\":null,\"success\":true,\"action\":\"speed\",\"speed\":"
                    + speed + "}");
        }

        private void prepareRelative(HttpExchange exchange, int target, int speed) throws IOException {
            requireRange(target, -20000, 20000, "LenData");
            requireRange(speed, 0, 500, "Speed");
            plc.writeCoil(COIL_RUN, false);
            plc.writeRegister(REGISTER_RELATIVE_TARGET, target & 0xFFFF);
            plc.writeRegister(REGISTER_SPEED, speed);
            json(exchange, 200, "{\"code\":200,\"msg\":null,\"success\":true,\"action\":\"prepare\"}");
        }

        private void setPosition(HttpExchange exchange, int channel, int position) throws IOException {
            requireRange(position, -20000, 20000, "LenData");
            int register = channel == 1 ? REGISTER_POSITION_1 : REGISTER_POSITION_2;
            int selected = channel == 1 ? COIL_POSITION_1 : COIL_POSITION_2;
            int opposite = channel == 1 ? COIL_POSITION_2 : COIL_POSITION_1;
            plc.writeRegister(register, position & 0xFFFF);
            plc.writeCoil(opposite, false);
            plc.writeCoil(selected, true);
            json(exchange, 200, "{\"code\":200,\"msg\":null,\"success\":true,\"position\":"
                    + channel + "}");
        }

        private static void requireRange(int value, int min, int max, String field) {
            if (value < min || value > max) {
                throw new IllegalArgumentException(field + " must be between " + min + " and " + max);
            }
        }

        private static String success(String action, boolean enabled) {
            return "{\"code\":200,\"msg\":null,\"success\":true,\"action\":\"" + escape(action)
                    + "\",\"enabled\":" + enabled + "}";
        }

        private static String error(String message) {
            return "{\"code\":500,\"success\":false,\"msg\":\"" + escape(message) + "\"}";
        }

        private static String readBody(HttpExchange exchange) throws IOException {
            try (InputStream input = exchange.getRequestBody(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[512];
                int total = 0;
                int read;
                while ((read = input.read(buffer)) != -1) {
                    total += read;
                    if (total > 4096) throw new IllegalArgumentException("request body is too large");
                    output.write(buffer, 0, read);
                }
                return new String(output.toByteArray(), StandardCharsets.UTF_8);
            }
        }

        private static void addHeaders(HttpExchange exchange) {
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
            exchange.getResponseHeaders().set("Cache-Control", "no-store");
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
            exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");
        }

        private static void json(HttpExchange exchange, int status, String payload) throws IOException {
            byte[] bytes = payload.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, bytes.length);
            try (OutputStream output = exchange.getResponseBody()) {
                output.write(bytes);
            }
        }
    }

    static final class ModbusConnection implements AutoCloseable {
        private final String host;
        private final int port;
        private final int unitId;
        private final int timeoutMs;
        private int transactionId;
        private Socket socket;
        private DataInputStream input;
        private DataOutputStream output;
        private String lastError;

        ModbusConnection(String host, int port, int unitId, int timeoutMs) {
            this.host = host;
            this.port = port;
            this.unitId = unitId;
            this.timeoutMs = timeoutMs;
        }

        synchronized boolean probe() {
            try {
                ensureConnected();
                lastError = null;
                return true;
            } catch (IOException error) {
                lastError = error.getMessage();
                closeSocket();
                return false;
            }
        }

        synchronized String lastError() {
            return lastError;
        }

        synchronized void writeCoil(int address, boolean enabled) {
            exchangeWrite(5, address, enabled ? 0xFF00 : 0);
        }

        synchronized void writeRegister(int address, int value) {
            exchangeWrite(6, address, value);
        }

        private void exchangeWrite(int function, int address, int value) {
            IOException failure = null;
            for (int attempt = 0; attempt < 2; attempt++) {
                try {
                    ensureConnected();
                    int tx = nextTransactionId();
                    output.writeShort(tx);
                    output.writeShort(0);
                    output.writeShort(6);
                    output.writeByte(unitId);
                    output.writeByte(function);
                    output.writeShort(address);
                    output.writeShort(value);
                    output.flush();
                    validateResponse(tx, function, address, value);
                    lastError = null;
                    return;
                } catch (IOException error) {
                    failure = error;
                    lastError = error.getMessage();
                    closeSocket();
                }
            }
            throw new IllegalStateException("Modbus TCP " + host + ":" + port + " failed: "
                    + (failure == null ? "unknown error" : failure.getMessage()), failure);
        }

        private void validateResponse(int tx, int function, int address, int value) throws IOException {
            int responseTx = input.readUnsignedShort();
            int protocol = input.readUnsignedShort();
            int length = input.readUnsignedShort();
            int responseUnit = input.readUnsignedByte();
            if (length < 2 || length > 254) throw new IOException("invalid MBAP length " + length);
            byte[] pdu = new byte[length - 1];
            input.readFully(pdu);
            if (responseTx != tx || protocol != 0 || responseUnit != unitId) {
                throw new IOException("response header mismatch");
            }
            if (pdu.length >= 2 && (pdu[0] & 0xFF) == (function | 0x80)) {
                throw new IOException("device exception code " + (pdu[1] & 0xFF));
            }
            if (pdu.length != 5 || (pdu[0] & 0xFF) != function
                    || unsignedShort(pdu, 1) != address || unsignedShort(pdu, 3) != (value & 0xFFFF)) {
                throw new IOException("write acknowledgement mismatch");
            }
        }

        private void ensureConnected() throws IOException {
            if (socket != null && socket.isConnected() && !socket.isClosed()) return;
            Socket candidate = new Socket();
            candidate.connect(new InetSocketAddress(host, port), timeoutMs);
            candidate.setSoTimeout(timeoutMs);
            candidate.setKeepAlive(true);
            candidate.setTcpNoDelay(true);
            socket = candidate;
            input = new DataInputStream(candidate.getInputStream());
            output = new DataOutputStream(candidate.getOutputStream());
        }

        private int nextTransactionId() {
            transactionId = transactionId >= 65535 ? 1 : transactionId + 1;
            return transactionId;
        }

        @Override
        public synchronized void close() {
            closeSocket();
        }

        private void closeSocket() {
            if (socket != null) {
                try {
                    socket.close();
                } catch (IOException ignored) {
                }
            }
            socket = null;
            input = null;
            output = null;
        }
    }

    static final class JsonBody {
        private static final Pattern FIELD = Pattern.compile(
                "\\\"([A-Za-z0-9_]+)\\\"\\s*:\\s*(\\\"(?:[^\\\"\\\\]|\\\\.)*\\\"|true|false|-?[0-9]+)",
                Pattern.CASE_INSENSITIVE);
        private final Map<String, String> values;

        private JsonBody(Map<String, String> values) {
            this.values = values;
        }

        static JsonBody parse(String json) {
            if (json == null || !json.trim().startsWith("{") || !json.trim().endsWith("}")) {
                throw new IllegalArgumentException("request body must be a JSON object");
            }
            Map<String, String> values = new LinkedHashMap<>();
            Matcher matcher = FIELD.matcher(json);
            while (matcher.find()) values.put(matcher.group(1), matcher.group(2));
            return new JsonBody(values);
        }

        boolean requiredBoolean(String field) {
            String value = required(field);
            if (!"true".equalsIgnoreCase(value) && !"false".equalsIgnoreCase(value)) {
                throw new IllegalArgumentException(field + " must be boolean");
            }
            return Boolean.parseBoolean(value);
        }

        int requiredInt(String field) {
            String value = required(field);
            try {
                return Integer.parseInt(value);
            } catch (NumberFormatException invalid) {
                throw new IllegalArgumentException(field + " must be an integer");
            }
        }

        String requiredString(String field) {
            String value = required(field);
            if (value.length() < 2 || value.charAt(0) != '"' || value.charAt(value.length() - 1) != '"') {
                throw new IllegalArgumentException(field + " must be a string");
            }
            return value.substring(1, value.length() - 1).replace("\\\"", "\"").replace("\\\\", "\\");
        }

        private String required(String field) {
            String value = values.get(field);
            if (value == null) throw new IllegalArgumentException(field + " is required");
            return value;
        }
    }

    private static int unsignedShort(byte[] bytes, int offset) throws EOFException {
        if (bytes == null || offset < 0 || offset + 1 >= bytes.length) throw new EOFException();
        return ((bytes[offset] & 0xFF) << 8) | (bytes[offset + 1] & 0xFF);
    }

    private static String escape(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\r", "\\r").replace("\n", "\\n");
    }

    private static ThreadFactory namedThreads(final String prefix) {
        final AtomicInteger sequence = new AtomicInteger();
        return runnable -> {
            Thread thread = new Thread(runnable, prefix + sequence.incrementAndGet());
            thread.setDaemon(false);
            return thread;
        };
    }
}
