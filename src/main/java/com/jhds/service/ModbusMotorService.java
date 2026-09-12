package com.jhds.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/** Modbus TCP client for the motor PLC shown in the wiring document. */
@Slf4j
@Service
public class ModbusMotorService {
    @Value("${modbus.motor.unit-id:1}") private int unitId;
    private final ModbusTcpTransport transport;

    public ModbusMotorService(ModbusTcpTransport transport) {
        this.transport = transport;
    }

    public Map<String, Object> status() {
        Map<String, Object> out = new LinkedHashMap<>(transport.status(true));
        out.put("unitId", unitId);
        return out;
    }

    public synchronized Map<String, Object> relative(int target, int speed, String direction) {
        requireRange(target, 0, 65535, "目标位置");
        requireRange(speed, 0, 65535, "速度");
        boolean forward = "forward".equalsIgnoreCase(direction);
        if (!forward && !"backward".equalsIgnoreCase(direction)) throw new IllegalArgumentException("方向必须是 forward 或 backward");
        prepareRelative(target, speed, forward ? "forward" : "backward");
        setRunning(true);
        return result("relative", direction, target, speed);
    }

    /** Writes and confirms relative-motion parameters but leaves M12 stopped. */
    public synchronized Map<String, Object> prepareRelative(int target, int speed, String direction) {
        requireRange(target, 0, 65535, "目标位置");
        requireRange(speed, 0, 65535, "速度");
        boolean forward = "forward".equalsIgnoreCase(direction);
        if (!forward && !"backward".equalsIgnoreCase(direction)) {
            throw new IllegalArgumentException("方向必须是 forward 或 backward");
        }
        setRunning(false);
        writeRegister(100, target);
        writeRegister(20, speed);
        writeCoil(0, true);
        selectDirection(forward ? "forward" : "backward");
        return result("prepare-relative", direction, target, speed);
    }

    /** Selects the direct-controller direction without changing motion parameters. */
    public synchronized Map<String, Object> direction(String direction) {
        boolean forward = "forward".equalsIgnoreCase(direction);
        if (!forward && !"backward".equalsIgnoreCase(direction)) {
            throw new IllegalArgumentException("方向必须是 forward 或 backward");
        }
        selectDirection(forward ? "forward" : "backward");
        return result("direction", direction, 0, 0);
    }

    /** Mirrors the MQTT MOTOR_STATE signal on direct-controller coil M12. */
    public synchronized Map<String, Object> run(boolean enabled) {
        if (enabled) {
            setRunning(true);
            return result("run", "start", 0, 0);
        }
        return stop();
    }

    public synchronized Map<String, Object> setSpeed(int speed) {
        requireRange(speed, 1, 500, "速度");
        writeRegister(20, speed);
        return result("speed", "configured", 0, speed);
    }

    public synchronized Map<String, Object> absolute(int position, int speed, int channel) {
        requireRange(position, 0, 65535, "绝对位置");
        requireRange(speed, 0, 65535, "速度");
        if (channel != 1 && channel != 2) throw new IllegalArgumentException("定位通道必须是 1 或 2");
        writeRegister(channel == 1 ? 200 : 210, position);
        writeRegister(20, speed);
        writeCoil(0, true);
        writeCoil(channel == 1 ? 23 : 21, false);
        writeCoil(channel == 1 ? 21 : 23, true);
        return result("absolute", "position" + channel, position, speed);
    }

    public synchronized Map<String, Object> stop() {
        setRunning(false); writeCoil(10, false); writeCoil(11, false);
        return result("stop", "stop", 0, 0);
    }

    public synchronized Map<String, Object> pump(boolean enabled) {
        writeCoil(13, enabled);
        return result("pump", enabled ? "on" : "off", 0, 0);
    }

    private Map<String, Object> result(String action, String direction, int value, int speed) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("action", action); out.put("direction", direction); out.put("value", value); out.put("speed", speed);
        out.put("sentAt", System.currentTimeMillis()); return out;
    }

    private void requireRange(int value, int min, int max, String name) {
        if (value < min || value > max) throw new IllegalArgumentException(name + "范围为 " + min + "-" + max);
    }

    private void writeRegister(int address, int value) {
        exchangeSingle(6, address, value);
    }

    private void writeCoil(int address, boolean enabled) {
        exchangeSingle(5, address, enabled ? 0xFF00 : 0);
    }

    private void selectDirection(String direction) {
        boolean forward = "forward".equals(direction);
        writeCoil(forward ? 11 : 10, false);
        writeCoil(forward ? 10 : 11, true);
    }

    private void setRunning(boolean enabled) {
        writeCoil(12, enabled);
    }

    private void exchangeSingle(int function, int address, int value) {
        byte[] pdu = new byte[]{(byte) function, (byte) (address >>> 8), (byte) address,
                (byte) (value >>> 8), (byte) value};
        transport.exchangePdu(unitId, pdu);
    }
}
