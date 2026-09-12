package com.jhds.service;

import com.jhds.common.ModbusUtil;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.Assert.*;

public class ModbusTcpTransportTest {

    @Test
    public void convertsRtuWriteFrameToTcpAndReturnsRtuResponse() throws Exception {
        ServerSocket server = new ServerSocket(0);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<byte[]> received = executor.submit(new Callable<byte[]>() {
            @Override
            public byte[] call() throws Exception {
                try (Socket socket = server.accept()) {
                    DataInputStream in = new DataInputStream(socket.getInputStream());
                    DataOutputStream out = new DataOutputStream(socket.getOutputStream());
                    int transaction = in.readUnsignedShort();
                    assertEquals(0, in.readUnsignedShort());
                    int length = in.readUnsignedShort();
                    byte[] request = new byte[length];
                    in.readFully(request);

                    out.writeShort(transaction);
                    out.writeShort(0);
                    out.writeShort(length);
                    out.write(request);
                    out.flush();
                    return request;
                }
            }
        });

        try {
            ModbusTcpTransport transport = transport(server.getLocalPort());
            String response = transport.exchangeRtuFrame("03 05 00 01 FF 00 DC 18");
            assertArrayEquals(new byte[]{3, 5, 0, 1, (byte) 0xFF, 0}, received.get());
            assertEquals("03 05 00 01 FF 00 DC 18", response);
            assertTrue(ModbusUtil.verifyCRC(response));
        } finally {
            server.close();
            executor.shutdownNow();
        }
    }

    @Test
    public void rejectsRtuFrameWithInvalidCrcBeforeConnecting() {
        ModbusTcpTransport transport = transport(1);
        try {
            transport.exchangeRtuFrame("03 05 00 01 FF 00 00 00");
            fail("invalid CRC must be rejected");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("CRC"));
        }
    }

    @Test
    public void rejectsWriteAcknowledgementForDifferentAddress() throws Exception {
        ServerSocket server = new ServerSocket(0);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.submit(new Runnable() {
            @Override
            public void run() {
                try (Socket socket = server.accept()) {
                    DataInputStream in = new DataInputStream(socket.getInputStream());
                    DataOutputStream out = new DataOutputStream(socket.getOutputStream());
                    int transaction = in.readUnsignedShort();
                    in.readUnsignedShort();
                    int length = in.readUnsignedShort();
                    byte[] request = new byte[length];
                    in.readFully(request);
                    request[3] = 2;
                    out.writeShort(transaction);
                    out.writeShort(0);
                    out.writeShort(length);
                    out.write(request);
                    out.flush();
                } catch (Exception ignored) {
                }
            }
        });

        try {
            ModbusTcpTransport transport = transport(server.getLocalPort());
            try {
                transport.exchangeRtuFrame("03 05 00 01 FF 00 DC 18");
                fail("mismatched write acknowledgement must be rejected");
            } catch (IllegalStateException expected) {
                assertTrue(expected.getMessage().contains("acknowledgement mismatch"));
            }
        } finally {
            server.close();
            executor.shutdownNow();
        }
    }

    private ModbusTcpTransport transport(int port) {
        ModbusTcpTransport transport = new ModbusTcpTransport();
        ReflectionTestUtils.setField(transport, "host", "127.0.0.1");
        ReflectionTestUtils.setField(transport, "port", port);
        ReflectionTestUtils.setField(transport, "timeoutMs", 1000);
        ReflectionTestUtils.setField(transport, "fallbackEnabled", true);
        ReflectionTestUtils.setField(transport, "probeCacheMs", 0L);
        return transport;
    }
}
