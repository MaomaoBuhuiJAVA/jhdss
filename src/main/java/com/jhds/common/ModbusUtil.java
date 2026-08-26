package com.jhds.common;

public class ModbusUtil {

    public static byte[] hexToBytes(String hex) {
        String[] parts = hex.trim().split("\\s+");
        byte[] bytes = new byte[parts.length];
        for (int i = 0; i < parts.length; i++) {
            bytes[i] = (byte) Integer.parseInt(parts[i], 16);
        }
        return bytes;
    }

    public static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            if (sb.length() > 0) sb.append(' ');
            sb.append(String.format("%02X", b & 0xFF));
        }
        return sb.toString();
    }

    public static int calculateCRC16(byte[] data) {
        int crc = 0xFFFF;
        for (byte b : data) {
            crc ^= (b & 0xFF);
            for (int i = 0; i < 8; i++) {
                if ((crc & 0x0001) != 0) {
                    crc = (crc >> 1) ^ 0xA001;
                } else {
                    crc >>= 1;
                }
            }
        }
        return crc & 0xFFFF;
    }

    public static boolean verifyCRC(byte[] frame) {
        if (frame.length < 3) return false;
        int expectedCrc = calculateCRC16(frame, frame.length - 2);
        int actualCrc = ((frame[frame.length - 1] & 0xFF) << 8) | (frame[frame.length - 2] & 0xFF);
        return expectedCrc == actualCrc;
    }

    public static boolean verifyCRC(String hexResponse) {
        return verifyCRC(hexToBytes(hexResponse));
    }

    private static int calculateCRC16(byte[] data, int length) {
        int crc = 0xFFFF;
        for (int i = 0; i < length; i++) {
            crc ^= (data[i] & 0xFF);
            for (int j = 0; j < 8; j++) {
                if ((crc & 0x0001) != 0) {
                    crc = (crc >> 1) ^ 0xA001;
                } else {
                    crc >>= 1;
                }
            }
        }
        return crc & 0xFFFF;
    }

    public static int parseRegister1(byte[] response) {
        if (response.length < 5) return 0;
        return ((response[3] & 0xFF) << 8) | (response[4] & 0xFF);
    }

    public static int parseRegister2(byte[] response) {
        if (response.length < 7) return 0;
        return ((response[5] & 0xFF) << 8) | (response[6] & 0xFF);
    }

    public static int parseRegister1(String hexResponse) {
        return parseRegister1(hexToBytes(hexResponse));
    }

    public static int parseRegister2(String hexResponse) {
        return parseRegister2(hexToBytes(hexResponse));
    }

    public static int parseRegister(byte[] response, int registerIndex) {
        int offset = 3 + (registerIndex - 1) * 2;
        if (response.length < offset + 2) return 0;
        return ((response[offset] & 0xFF) << 8) | (response[offset + 1] & 0xFF);
    }

    public static int parseRegister(String hexResponse, int registerIndex) {
        return parseRegister(hexToBytes(hexResponse), registerIndex);
    }

    public static String hexToAscii(String hexSpaceSeparated) {
        String[] parts = hexSpaceSeparated.trim().split("\\s+");
        byte[] bytes = new byte[parts.length];
        for (int i = 0; i < parts.length; i++) {
            bytes[i] = (byte) Integer.parseInt(parts[i], 16);
        }
        return new String(bytes);
    }
}
