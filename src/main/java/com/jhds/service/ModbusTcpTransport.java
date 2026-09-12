package com.jhds.service;

import com.jhds.common.ModbusUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/** Shared Modbus TCP transport used by direct controls and the MQTT fallback. */
@Slf4j
@Service
public class ModbusTcpTransport {
    @Value("${modbus.motor.host:192.168.1.12}") private String host;
    @Value("${modbus.motor.port:502}") private int port;
    @Value("${modbus.motor.timeout-ms:1500}") private int timeoutMs;
    @Value("${modbus.fallback.enabled:true}") private boolean fallbackEnabled;
    @Value("${modbus.fallback.probe-cache-ms:3000}") private long probeCacheMs;

    private final AtomicInteger transaction = new AtomicInteger(1);
    private volatile Boolean reachable;
    private volatile String lastError;
    private volatile long lastSuccessAt;
    private volatile long lastFailureAt;
    private volatile long lastProbeAt;
    private volatile long connectionEpoch;

    public boolean isFallbackEnabled() {
        return fallbackEnabled;
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    public long connectionEpoch() {
        return connectionEpoch;
    }

    public boolean isReachable() {
        long now = System.currentTimeMillis();
        if (reachable != null && now - lastProbeAt < Math.max(0L, probeCacheMs)) {
            return reachable;
        }
        return probeConnection();
    }

    public Map<String, Object> status(boolean probe) {
        if (probe) isReachable();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("enabled", fallbackEnabled);
        out.put("host", host);
        out.put("port", port);
        out.put("reachable", Boolean.TRUE.equals(reachable));
        out.put("lastSuccessAt", lastSuccessAt == 0L ? null : lastSuccessAt);
        out.put("lastFailureAt", lastFailureAt == 0L ? null : lastFailureAt);
        out.put("lastError", lastError);
        return out;
    }

    public boolean probeConnection() {
        lastProbeAt = System.currentTimeMillis();
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), timeoutMs);
            markSuccess();
            return true;
        } catch (IOException e) {
            markFailure(e);
            return false;
        }
    }

    /** Converts a CRC-protected Modbus RTU frame into Modbus TCP and back. */
    public String exchangeRtuFrame(String hexFrame) {
        if (!fallbackEnabled) {
            throw new IllegalStateException("Modbus fallback is disabled");
        }
        byte[] rtu = ModbusUtil.hexToBytes(hexFrame);
        if (rtu.length < 4 || !ModbusUtil.verifyCRC(rtu)) {
            throw new IllegalArgumentException("Modbus RTU frame has an invalid CRC");
        }
        int unitId = rtu[0] & 0xFF;
        byte[] pdu = Arrays.copyOfRange(rtu, 1, rtu.length - 2);
        byte[] responsePdu = exchangePdu(unitId, pdu);
        byte[] responseRtu = new byte[responsePdu.length + 3];
        responseRtu[0] = (byte) unitId;
        System.arraycopy(responsePdu, 0, responseRtu, 1, responsePdu.length);
        int crc = ModbusUtil.calculateCRC16(Arrays.copyOf(responseRtu, responseRtu.length - 2));
        responseRtu[responseRtu.length - 2] = (byte) (crc & 0xFF);
        responseRtu[responseRtu.length - 1] = (byte) ((crc >>> 8) & 0xFF);
        return ModbusUtil.bytesToHex(responseRtu);
    }

    public byte[] exchangePdu(int unitId, byte[] pdu) {
        if (unitId < 0 || unitId > 255) throw new IllegalArgumentException("Modbus unit id must be 0-255");
        if (pdu == null || pdu.length < 1 || pdu.length > 253) {
            throw new IllegalArgumentException("Modbus PDU length must be 1-253 bytes");
        }
        int tx = transaction.updateAndGet(v -> v >= 65535 ? 1 : v + 1);
        lastProbeAt = System.currentTimeMillis();
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), timeoutMs);
            socket.setSoTimeout(timeoutMs);
            DataOutputStream out = new DataOutputStream(socket.getOutputStream());
            DataInputStream in = new DataInputStream(socket.getInputStream());
            out.writeShort(tx);
            out.writeShort(0);
            out.writeShort(pdu.length + 1);
            out.writeByte(unitId);
            out.write(pdu);
            out.flush();

            int responseTx = in.readUnsignedShort();
            int protocol = in.readUnsignedShort();
            int length = in.readUnsignedShort();
            if (length < 2 || length > 254) throw new IOException("invalid MBAP length " + length);
            int responseUnit = in.readUnsignedByte();
            byte[] responsePdu = new byte[length - 1];
            in.readFully(responsePdu);
            if (responseTx != tx || protocol != 0 || responseUnit != unitId) {
                throw new IOException("Modbus TCP response header mismatch");
            }
            int responseFunction = responsePdu[0] & 0xFF;
            if (responseFunction == ((pdu[0] & 0xFF) | 0x80)) {
                int exception = responsePdu.length > 1 ? responsePdu[1] & 0xFF : -1;
                throw new IOException("device exception code " + exception);
            }
            if (responseFunction != (pdu[0] & 0xFF)) {
                throw new IOException("Modbus TCP function mismatch");
            }
            validateWriteAcknowledgement(pdu, responsePdu);
            markSuccess();
            return responsePdu;
        } catch (IOException e) {
            markFailure(e);
            throw new IllegalStateException("Modbus TCP " + host + ":" + port + " failed: " + e.getMessage(), e);
        }
    }

    private void validateWriteAcknowledgement(byte[] request, byte[] response) throws IOException {
        int function = request[0] & 0xFF;
        int comparedLength;
        if (function == 5 || function == 6) {
            comparedLength = request.length;
        } else if (function == 15 || function == 16) {
            comparedLength = 5;
        } else {
            return;
        }
        if (response.length != comparedLength || request.length < comparedLength) {
            throw new IOException("invalid Modbus write acknowledgement length");
        }
        for (int i = 0; i < comparedLength; i++) {
            if (request[i] != response[i]) {
                throw new IOException("Modbus write acknowledgement mismatch");
            }
        }
    }

    private void markSuccess() {
        boolean changed = !Boolean.TRUE.equals(reachable);
        reachable = true;
        lastSuccessAt = System.currentTimeMillis();
        lastError = null;
        if (changed) connectionEpoch = lastSuccessAt;
    }

    private void markFailure(Exception error) {
        boolean changed = !Boolean.FALSE.equals(reachable);
        reachable = false;
        lastFailureAt = System.currentTimeMillis();
        lastError = error.getMessage();
        if (changed) connectionEpoch = lastFailureAt;
    }
}
