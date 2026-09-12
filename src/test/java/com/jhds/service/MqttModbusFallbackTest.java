package com.jhds.service;

import com.jhds.config.MqttProperties;
import com.jhds.common.ModbusUtil;
import com.jhds.entity.Equipment;
import com.jhds.mapper.EquipmentMapper;
import com.jhds.service.mqtt.MqttService;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.*;

public class MqttModbusFallbackTest {

    @Test
    public void deployedRailAcceptsItsCrcValidStatusWordButOtherWritesStayStrict() {
        MqttService mqtt = new MqttService();

        Boolean railAck = ReflectionTestUtils.invokeMethod(mqtt, "isMatchingModbusWriteResponse",
                "03 05 00 01 FF 00 DC 18", "03 05 00 01 45 04 AE BB");
        Boolean differentAddress = ReflectionTestUtils.invokeMethod(mqtt, "isMatchingModbusWriteResponse",
                "03 05 00 01 FF 00 DC 18", "03 05 00 02 45 04 AE BB");
        Boolean ordinaryChangedValue = ReflectionTestUtils.invokeMethod(mqtt, "isMatchingModbusWriteResponse",
                "01 06 00 02 00 01 E9 CA", "01 06 00 02 00 00 28 0A");

        assertTrue(railAck);
        assertFalse(differentAddress);
        assertFalse(ordinaryChangedValue);
    }

    @Test
    public void disconnectedMqttUsesDirectModbusFrame() {
        MqttService mqtt = new MqttService();
        MqttProperties properties = new MqttProperties();
        RecordingTransport transport = new RecordingTransport();
        ReflectionTestUtils.setField(mqtt, "mqttProperties", properties);
        ReflectionTestUtils.setField(mqtt, "modbusTcpTransport", transport);

        String command = "01 06 00 02 00 01 E9 CA";
        String response = mqtt.sendHexSync(command, 500L);

        assertEquals(command, response);
        assertEquals(command, transport.command);
    }

    @Test
    public void connectedMqttWithMalformedMotorAcknowledgementRetriesTheExactFrameThroughModbus() throws Exception {
        MqttService mqtt = new MqttService();
        MqttProperties properties = new MqttProperties();
        RecordingTransport transport = new RecordingTransport();
        MqttClient client = mock(MqttClient.class);
        EquipmentMapper equipmentMapper = mock(EquipmentMapper.class);
        ControlLogService controlLogService = mock(ControlLogService.class);
        Equipment equipment = new Equipment();
        String command = "03 05 00 01 00 FF DD A8";
        equipment.setName("轨道电机方向");
        equipment.setAlias("MOTOR_DIRECTION");
        equipment.setOpenCode(command);
        when(client.isConnected()).thenReturn(true);
        when(equipmentMapper.selectByAlias("MOTOR_DIRECTION")).thenReturn(equipment);

        ReflectionTestUtils.setField(mqtt, "mqttProperties", properties);
        ReflectionTestUtils.setField(mqtt, "mqttClient", client);
        ReflectionTestUtils.setField(mqtt, "modbusTcpTransport", transport);
        ReflectionTestUtils.setField(mqtt, "equipmentMapper", equipmentMapper);
        ReflectionTestUtils.setField(mqtt, "controlLogService", controlLogService);
        ReflectionTestUtils.setField(mqtt, "deviceTwinState", new DeviceTwinState());
        ReflectionTestUtils.setField(mqtt, "motorConfirmationTimeoutMs", 250L);
        doAnswer(call -> {
            mqtt.handleResponse(properties.getTopic().getPrefix() + "/"
                    + properties.getTopic().getResponseSuffix(), "D0 DD A8");
            return null;
        }).when(client).publish(anyString(), any(MqttMessage.class));

        String response = mqtt.sendCommand("MOTOR_DIRECTION", "open", true);

        assertEquals(command, response);
        assertEquals(command, transport.command);
        verify(client).publish(anyString(), any(MqttMessage.class));
        verify(equipmentMapper).updateById(equipment);
    }

    @Test
    public void delayedStopAcknowledgementUsesTheDedicatedTimeoutWithoutModbusFallback() throws Exception {
        MqttService mqtt = new MqttService();
        MqttProperties properties = new MqttProperties();
        RecordingTransport transport = new RecordingTransport();
        MqttClient client = mock(MqttClient.class);
        EquipmentMapper equipmentMapper = mock(EquipmentMapper.class);
        ControlLogService controlLogService = mock(ControlLogService.class);
        Equipment equipment = new Equipment();
        String command = "03 05 00 01 00 00 9D E8";
        equipment.setName("轨道电机状态");
        equipment.setAlias("MOTOR_STATE");
        equipment.setCloseCode(command);
        when(client.isConnected()).thenReturn(true);
        when(equipmentMapper.selectByAlias("MOTOR_STATE")).thenReturn(equipment);

        ReflectionTestUtils.setField(mqtt, "mqttProperties", properties);
        ReflectionTestUtils.setField(mqtt, "mqttClient", client);
        ReflectionTestUtils.setField(mqtt, "modbusTcpTransport", transport);
        ReflectionTestUtils.setField(mqtt, "equipmentMapper", equipmentMapper);
        ReflectionTestUtils.setField(mqtt, "controlLogService", controlLogService);
        ReflectionTestUtils.setField(mqtt, "deviceTwinState", new DeviceTwinState());
        ReflectionTestUtils.setField(mqtt, "motorConfirmationTimeoutMs", 100L);
        ReflectionTestUtils.setField(mqtt, "motorStopConfirmationTimeoutMs", 650L);
        doAnswer(call -> {
            Thread acknowledgement = new Thread(() -> {
                try {
                    Thread.sleep(275L);
                    mqtt.handleResponse(properties.getTopic().getPrefix() + "/"
                            + properties.getTopic().getResponseSuffix(), command);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }, "delayed-motor-stop-ack");
            acknowledgement.setDaemon(true);
            acknowledgement.start();
            return null;
        }).when(client).publish(anyString(), any(MqttMessage.class));

        String response = mqtt.sendCommand("MOTOR_STATE", "close", true);

        assertEquals(command, response);
        assertEquals(null, transport.command);
        verify(client).publish(anyString(), any(MqttMessage.class));
        verify(equipmentMapper).updateById(equipment);
    }

    @Test
    public void disconnectedMqttMapsMotorSignalsToDocumentedDirectCoils() {
        MqttService mqtt = new MqttService();
        MqttProperties properties = new MqttProperties();
        RecordingTransport transport = new RecordingTransport();
        ModbusMotorService motor = mock(ModbusMotorService.class);
        EquipmentMapper equipmentMapper = mock(EquipmentMapper.class);
        ControlLogService controlLogService = mock(ControlLogService.class);
        Equipment equipment = new Equipment();
        equipment.setName("轨道电机");
        equipment.setAlias("MOTOR_DIRECTION");
        when(equipmentMapper.selectByAlias(anyString())).thenReturn(equipment);
        when(motor.prepareRelative(1000, 60, "forward")).thenReturn(new java.util.LinkedHashMap<String, Object>());
        when(motor.run(true)).thenReturn(new java.util.LinkedHashMap<String, Object>());

        ReflectionTestUtils.setField(mqtt, "mqttProperties", properties);
        ReflectionTestUtils.setField(mqtt, "modbusTcpTransport", transport);
        ReflectionTestUtils.setField(mqtt, "modbusMotorService", motor);
        ReflectionTestUtils.setField(mqtt, "equipmentMapper", equipmentMapper);
        ReflectionTestUtils.setField(mqtt, "controlLogService", controlLogService);
        ReflectionTestUtils.setField(mqtt, "deviceTwinState", new DeviceTwinState());
        ReflectionTestUtils.setField(mqtt, "modbusFallbackMotorTarget", 1000);
        ReflectionTestUtils.setField(mqtt, "modbusFallbackMotorSpeed", 60);

        mqtt.sendCommand("MOTOR_DIRECTION", "open", false);
        mqtt.sendCommand("MOTOR_STATE", "open", false);

        verify(motor).prepareRelative(1000, 60, "forward");
        verify(motor).run(true);
        verify(equipmentMapper, times(2)).updateById(equipment);
    }

    @Test
    public void disconnectedMqttReusesBundledModbusGatewayWhenItOwnsThePlcConnection() {
        MqttService mqtt = new MqttService();
        MqttProperties properties = new MqttProperties();
        RecordingTransport transport = new RecordingTransport();
        ModbusMotorService motor = mock(ModbusMotorService.class);
        ControlPanelService panel = mock(ControlPanelService.class);
        EquipmentMapper equipmentMapper = mock(EquipmentMapper.class);
        ControlLogService controlLogService = mock(ControlLogService.class);
        Equipment equipment = new Equipment();
        equipment.setName("轨道电机");
        equipment.setAlias("MOTOR_DIRECTION");
        when(equipmentMapper.selectByAlias(anyString())).thenReturn(equipment);
        when(panel.move(eq("forward"), eq(true))).thenAnswer(call -> new java.util.LinkedHashMap<String, Object>());
        when(panel.hasActiveMotion()).thenReturn(true);
        when(panel.motionStatus()).thenAnswer(call -> new java.util.LinkedHashMap<String, Object>());
        when(panel.stopMotion()).thenAnswer(call -> new java.util.LinkedHashMap<String, Object>());

        ReflectionTestUtils.setField(mqtt, "mqttProperties", properties);
        ReflectionTestUtils.setField(mqtt, "modbusTcpTransport", transport);
        ReflectionTestUtils.setField(mqtt, "modbusMotorService", motor);
        ReflectionTestUtils.setField(mqtt, "controlPanelService", panel);
        ReflectionTestUtils.setField(mqtt, "equipmentMapper", equipmentMapper);
        ReflectionTestUtils.setField(mqtt, "controlLogService", controlLogService);
        ReflectionTestUtils.setField(mqtt, "deviceTwinState", new DeviceTwinState());

        mqtt.sendCommand("MOTOR_DIRECTION", "open", false);
        mqtt.sendCommand("MOTOR_STATE", "open", false);
        mqtt.sendCommand("MOTOR_STATE", "close", false);

        verify(panel).move("forward", true);
        verify(panel).motionStatus();
        verify(panel).stopMotion();
        verifyZeroInteractions(motor);
    }

    @Test
    public void disconnectedMqttRoutesMappedOutputsThroughTheLocalGateway() {
        MqttService mqtt = new MqttService();
        RecordingTransport transport = new RecordingTransport();
        ControlPanelService panel = mock(ControlPanelService.class);
        EquipmentMapper equipmentMapper = mock(EquipmentMapper.class);
        ControlLogService controlLogService = mock(ControlLogService.class);
        Equipment equipment = new Equipment();
        equipment.setName("二氧化碳气肥");
        equipment.setAlias("PUMP_CO2");
        when(equipmentMapper.selectByAlias("PUMP_CO2")).thenReturn(equipment);
        when(panel.deviceOutput(eq("PUMP_CO2"), anyBoolean()))
                .thenAnswer(call -> new java.util.LinkedHashMap<String, Object>());

        ReflectionTestUtils.setField(mqtt, "modbusTcpTransport", transport);
        ReflectionTestUtils.setField(mqtt, "controlPanelService", panel);
        ReflectionTestUtils.setField(mqtt, "equipmentMapper", equipmentMapper);
        ReflectionTestUtils.setField(mqtt, "controlLogService", controlLogService);
        ReflectionTestUtils.setField(mqtt, "deviceTwinState", new DeviceTwinState());

        mqtt.sendCommand("PUMP_CO2", "open", false);
        mqtt.sendCommand("PUMP_CO2", "close", false);

        verify(panel).deviceOutput("PUMP_CO2", true);
        verify(panel).deviceOutput("PUMP_CO2", false);
        verify(equipmentMapper, times(2)).updateById(equipment);
        assertEquals(null, transport.command);
    }

    @Test
    public void motorSpeedFrameWritesD20WithAValidCrc() {
        MqttService mqtt = new MqttService();

        String frame = ReflectionTestUtils.invokeMethod(mqtt, "buildMotorSpeedFrame", 1, 85);
        byte[] bytes = ModbusUtil.hexToBytes(frame);

        assertEquals(1, bytes[0] & 0xFF);
        assertEquals(6, bytes[1] & 0xFF);
        assertEquals(20, ((bytes[2] & 0xFF) << 8) | (bytes[3] & 0xFF));
        assertEquals(85, ((bytes[4] & 0xFF) << 8) | (bytes[5] & 0xFF));
        assertTrue(ModbusUtil.verifyCRC(bytes));
    }

    @Test
    public void disconnectedMqttSetsMotorSpeedThroughTheLocalGateway() {
        MqttService mqtt = new MqttService();
        RecordingTransport transport = new RecordingTransport();
        ControlPanelService panel = mock(ControlPanelService.class);
        when(panel.motorSpeed(85)).thenAnswer(call -> new java.util.LinkedHashMap<String, Object>());

        ReflectionTestUtils.setField(mqtt, "modbusTcpTransport", transport);
        ReflectionTestUtils.setField(mqtt, "modbusMotorService", mock(ModbusMotorService.class));
        ReflectionTestUtils.setField(mqtt, "controlPanelService", panel);
        ReflectionTestUtils.setField(mqtt, "modbusMotorUnitId", 1);

        java.util.Map<String, Object> result = mqtt.setMotorSpeed(85, false);

        assertEquals("software-http-modbus", result.get("fallbackPath"));
        verify(panel).motorSpeed(85);
        assertEquals(null, transport.command);
    }

    private static class RecordingTransport extends ModbusTcpTransport {
        private String command;

        @Override
        public boolean isFallbackEnabled() {
            return true;
        }

        @Override
        public String exchangeRtuFrame(String hexFrame) {
            command = hexFrame;
            return hexFrame;
        }
    }
}
