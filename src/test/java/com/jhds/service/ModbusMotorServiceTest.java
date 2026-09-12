package com.jhds.service;

import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

public class ModbusMotorServiceTest {

    @Test
    public void relativeMotionStopsThenConfirmsM10BeforeStartingM12() {
        RecordingTransport transport = new RecordingTransport();
        ModbusMotorService motor = new ModbusMotorService(transport);
        ReflectionTestUtils.setField(motor, "unitId", 1);

        motor.relative(1000, 60, "forward");

        assertEquals(7, transport.pdus.size());
        assertPdu(transport, 0, 5, 12, 0);
        assertPdu(transport, 1, 6, 100, 1000);
        assertPdu(transport, 2, 6, 20, 60);
        assertPdu(transport, 3, 5, 0, 0xFF00);
        assertPdu(transport, 4, 5, 11, 0);
        assertPdu(transport, 5, 5, 10, 0xFF00);
        assertPdu(transport, 6, 5, 12, 0xFF00);
    }

    @Test
    public void speedWritesD20WithoutStartingTheMotor() {
        RecordingTransport transport = new RecordingTransport();
        ModbusMotorService motor = new ModbusMotorService(transport);
        ReflectionTestUtils.setField(motor, "unitId", 1);

        motor.setSpeed(85);

        assertEquals(1, transport.pdus.size());
        assertPdu(transport, 0, 6, 20, 85);
    }

    private void assertPdu(RecordingTransport transport, int index, int function, int address, int value) {
        assertArrayEquals(new byte[]{(byte) function, (byte) (address >>> 8), (byte) address,
                (byte) (value >>> 8), (byte) value}, transport.pdus.get(index));
    }

    private static class RecordingTransport extends ModbusTcpTransport {
        final List<byte[]> pdus = new ArrayList<>();

        @Override
        public byte[] exchangePdu(int unitId, byte[] pdu) {
            pdus.add(Arrays.copyOf(pdu, pdu.length));
            return Arrays.copyOf(pdu, pdu.length);
        }
    }
}
