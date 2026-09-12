package com.jhds.controller;

import com.jhds.common.Result;
import com.jhds.service.PatrolService;
import com.jhds.service.RailPositionService;
import com.jhds.service.mqtt.MqttService;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

public class PatrolMotorControlPriorityTest {

    @Test
    public void directionControlDoesNotDependOnTheOptionalSpeedValue() {
        PatrolController controller = new PatrolController();
        PatrolService patrolService = mock(PatrolService.class);
        ReflectionTestUtils.setField(controller, "patrolService", patrolService);
        when(patrolService.control("left", true)).thenReturn("motor-started");

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("dir", "left");
        body.put("motionConfirmed", true);
        body.put("speed", 500);

        Result<String> result = controller.control(body);

        assertEquals(200, result.getCode());
        assertEquals("motor-started", result.getData());
        verify(patrolService).control("left", true);
        verifyNoMoreInteractions(patrolService);
    }

    @Test
    public void railMovementUsesTheVerifiedDirectionFrameWithoutAnUnconfiguredStartSignal() {
        PatrolService patrolService = new PatrolService();
        MqttService mqttService = mock(MqttService.class);
        RailPositionService railPositionService = mock(RailPositionService.class);
        ReflectionTestUtils.setField(patrolService, "mqttService", mqttService);
        ReflectionTestUtils.setField(patrolService, "railPositionService", railPositionService);
        when(railPositionService.remainingLeftMs()).thenReturn(5000L);
        when(railPositionService.beginMove("left", Long.MAX_VALUE)).thenReturn(5000L);
        when(mqttService.sendCommand("MOTOR_DIRECTION", "open", false)).thenReturn("ack");

        assertEquals("ack", patrolService.control("left", true));

        org.mockito.InOrder order = inOrder(railPositionService, mqttService);
        order.verify(railPositionService).beginMove("left", Long.MAX_VALUE);
        order.verify(mqttService).sendCommand("MOTOR_DIRECTION", "open", false);
        verifyNoMoreInteractions(mqttService);
    }

    @Test
    public void failedLeftMoveStopsMotorAndBanksUnconfirmedTravelTime() {
        PatrolService patrolService = new PatrolService();
        MqttService mqttService = mock(MqttService.class);
        RailPositionService railPositionService = mock(RailPositionService.class);
        ReflectionTestUtils.setField(patrolService, "mqttService", mqttService);
        ReflectionTestUtils.setField(patrolService, "railPositionService", railPositionService);
        when(railPositionService.remainingLeftMs()).thenReturn(5000L);
        when(railPositionService.beginMove("left", Long.MAX_VALUE)).thenReturn(5000L);
        when(mqttService.sendCommand("MOTOR_DIRECTION", "open", false)).thenReturn(null);
        when(mqttService.sendCommand("MOTOR_STATE", "close", false)).thenReturn("stopped");

        assertNull(patrolService.control("left", true));

        org.mockito.InOrder order = inOrder(railPositionService, mqttService);
        order.verify(railPositionService).beginMove("left", Long.MAX_VALUE);
        order.verify(mqttService).sendCommand("MOTOR_DIRECTION", "open", false);
        order.verify(mqttService).sendCommand("MOTOR_STATE", "close", false);
        order.verify(railPositionService).endMove();
    }

    @Test
    public void failedRightMoveStopsMotorWithoutGrantingLeftTravelBudget() {
        PatrolService patrolService = new PatrolService();
        MqttService mqttService = mock(MqttService.class);
        RailPositionService railPositionService = mock(RailPositionService.class);
        ReflectionTestUtils.setField(patrolService, "mqttService", mqttService);
        ReflectionTestUtils.setField(patrolService, "railPositionService", railPositionService);
        when(mqttService.sendCommand("MOTOR_DIRECTION", "close", false)).thenReturn(null);
        when(mqttService.sendCommand("MOTOR_STATE", "close", false)).thenReturn("stopped");

        assertNull(patrolService.control("right", true));

        verify(mqttService).sendCommand("MOTOR_STATE", "close", false);
        verify(railPositionService, never()).beginMove("right", Long.MAX_VALUE);
        verify(railPositionService, never()).endMove();
    }
}
