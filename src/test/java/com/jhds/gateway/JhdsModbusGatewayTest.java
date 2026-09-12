package com.jhds.gateway;

import org.junit.Test;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class JhdsModbusGatewayTest {
    @Test
    public void whitelistedOutputsAndLegacyDirectionUseExpectedAddresses() throws Exception {
        try (FakePlc plc = new FakePlc()) {
            JhdsModbusGateway.Config config = new JhdsModbusGateway.Config(
                    "127.0.0.1", 0, "127.0.0.1", plc.port(), 1, 1000);
            try (JhdsModbusGateway.GatewayServer gateway = new JhdsModbusGateway.GatewayServer(config)) {
                gateway.start();

                assertEquals(200, post(gateway.port(), "/api/device-control",
                        "{\"alias\":\"PUMP_CIRCULATION\",\"enabled\":true}").status);
                assertEquals(200, post(gateway.port(), "/api/device-control",
                        "{\"alias\":\"PUMP_CO2\",\"enabled\":false}").status);
                assertEquals(200, post(gateway.port(), "/api/speed", "{\"Speed\":85}").status);
                assertEquals(200, post(gateway.port(), "/api/moveforward", "{\"LenData\":true}").status);

                assertWrite(plc.writes, 0, 6, 1, 1);
                assertWrite(plc.writes, 1, 6, 2, 0);
                assertWrite(plc.writes, 2, 6, 20, 85);
                assertWrite(plc.writes, 3, 5, 11, 0);
                assertWrite(plc.writes, 4, 5, 10, 0xFF00);
            }
        }
    }

    @Test
    public void arbitraryAliasesAndMalformedBodiesAreRejectedWithoutWriting() throws Exception {
        try (FakePlc plc = new FakePlc()) {
            JhdsModbusGateway.Config config = new JhdsModbusGateway.Config(
                    "127.0.0.1", 0, "127.0.0.1", plc.port(), 1, 1000);
            try (JhdsModbusGateway.GatewayServer gateway = new JhdsModbusGateway.GatewayServer(config)) {
                gateway.start();

                Response forbidden = post(gateway.port(), "/api/device-control",
                        "{\"alias\":\"MOTOR_STATE\",\"enabled\":true}");
                Response malformed = post(gateway.port(), "/api/co2", "{\"LenData\":1}");
                Response unsafeSpeed = post(gateway.port(), "/api/speed", "{\"Speed\":501}");

                assertEquals(400, forbidden.status);
                assertEquals(400, malformed.status);
                assertEquals(400, unsafeSpeed.status);
                assertTrue(plc.writes.isEmpty());
            }
        }
    }

    private static Response post(int port, String path, String body) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL("http://127.0.0.1:" + port + path).openConnection();
        connection.setConnectTimeout(1000);
        connection.setReadTimeout(1000);
        connection.setRequestMethod("POST");
        connection.setDoOutput(true);
        connection.setRequestProperty("Content-Type", "application/json");
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        try (OutputStream output = connection.getOutputStream()) {
            output.write(bytes);
        }
        int status = connection.getResponseCode();
        BufferedReader reader = new BufferedReader(new InputStreamReader(
                status >= 400 ? connection.getErrorStream() : connection.getInputStream(), StandardCharsets.UTF_8));
        StringBuilder response = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) response.append(line);
        reader.close();
        connection.disconnect();
        return new Response(status, response.toString());
    }

    private static void assertWrite(List<Write> writes, int index, int function, int address, int value) {
        Write write = writes.get(index);
        assertEquals(function, write.function);
        assertEquals(address, write.address);
        assertEquals(value, write.value);
    }

    private static final class Response {
        final int status;
        final String body;

        Response(int status, String body) {
            this.status = status;
            this.body = body;
        }
    }

    private static final class Write {
        final int function;
        final int address;
        final int value;

        Write(int function, int address, int value) {
            this.function = function;
            this.address = address;
            this.value = value;
        }
    }

    private static final class FakePlc implements AutoCloseable {
        final List<Write> writes = new CopyOnWriteArrayList<>();
        private final ServerSocket server;
        private final ExecutorService executor = Executors.newSingleThreadExecutor();
        private volatile boolean running = true;

        FakePlc() throws Exception {
            server = new ServerSocket(0, 1, java.net.InetAddress.getByName("127.0.0.1"));
            executor.submit(this::serve);
        }

        int port() {
            return server.getLocalPort();
        }

        private void serve() {
            while (running) {
                try (Socket socket = server.accept()) {
                    DataInputStream input = new DataInputStream(socket.getInputStream());
                    DataOutputStream output = new DataOutputStream(socket.getOutputStream());
                    while (running) {
                        int tx = input.readUnsignedShort();
                        int protocol = input.readUnsignedShort();
                        int length = input.readUnsignedShort();
                        int unit = input.readUnsignedByte();
                        byte[] pdu = new byte[length - 1];
                        input.readFully(pdu);
                        int function = pdu[0] & 0xFF;
                        int address = ((pdu[1] & 0xFF) << 8) | (pdu[2] & 0xFF);
                        int value = ((pdu[3] & 0xFF) << 8) | (pdu[4] & 0xFF);
                        writes.add(new Write(function, address, value));
                        output.writeShort(tx);
                        output.writeShort(protocol);
                        output.writeShort(length);
                        output.writeByte(unit);
                        output.write(pdu);
                        output.flush();
                    }
                } catch (Exception ignored) {
                    // Closing the server or client connection returns to accept.
                }
            }
        }

        @Override
        public void close() throws Exception {
            running = false;
            server.close();
            executor.shutdownNow();
        }
    }
}
