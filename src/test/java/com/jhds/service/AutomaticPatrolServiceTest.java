package com.jhds.service;

import org.junit.Test;
import org.mockito.InOrder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

public class AutomaticPatrolServiceTest {

    @Test
    public void startRefusesToResetARecordedMidRailPosition() {
        AutomaticPatrolService service = new AutomaticPatrolService();
        PatrolService patrol = mock(PatrolService.class);
        RailPositionService rail = mock(RailPositionService.class);
        when(rail.positionMs()).thenReturn(3200L);
        ReflectionTestUtils.setField(service, "patrolService", patrol);
        ReflectionTestUtils.setField(service, "railPositionService", rail);

        try {
            service.start("standard", true);
            fail("Expected a non-origin patrol start to be rejected");
        } catch (IllegalStateException expected) {
            assertEquals("轨道位置记录显示当前不在最右端；请手动右移到机械原点，再点击轨道电机卡片上的重置按钮",
                    expected.getMessage());
        }
        verifyZeroInteractions(patrol);
    }

    @Test
    public void middleSprayAlwaysClosesPumpAndCountsCompletedSpray() {
        AutomaticPatrolService service = new AutomaticPatrolService();
        ControlPanelService panel = mock(ControlPanelService.class);
        when(panel.pump(true)).thenReturn(new LinkedHashMap<String, Object>());
        when(panel.pump(false)).thenReturn(new LinkedHashMap<String, Object>());
        ReflectionTestUtils.setField(service, "controlPanelService", panel);
        ReflectionTestUtils.setField(service, "foliarSprayMs", 0L);

        ReflectionTestUtils.invokeMethod(service, "sprayFoliarAtMiddle", 2, 50);

        InOrder commands = inOrder(panel);
        commands.verify(panel).pump(true);
        commands.verify(panel).pump(false);
        assertEquals(1, ReflectionTestUtils.getField(service, "sprayCount"));
        assertFalse((Boolean) ReflectionTestUtils.getField(service, "foliarPumpActive"));
    }

    @Test
    public void returnToOriginMovesRightForTrackedDistanceThenRezerosPosition() {
        AutomaticPatrolService service = new AutomaticPatrolService();
        PatrolService patrol = mock(PatrolService.class);
        RailPositionService rail = mock(RailPositionService.class);
        when(rail.remainingRightMs()).thenReturn(1L);
        when(rail.clampHorizontalDuration("right", 1L)).thenReturn(1L);
        when(patrol.control("right", true)).thenReturn("ok");
        when(patrol.control("stop")).thenReturn("ok");
        ReflectionTestUtils.setField(service, "patrolService", patrol);
        ReflectionTestUtils.setField(service, "railPositionService", rail);
        ReflectionTestUtils.setField(service, "settleMs", 0L);

        ReflectionTestUtils.invokeMethod(service, "returnToRightBottomOrigin", false, 1L);

        InOrder commands = inOrder(patrol);
        commands.verify(patrol).control("right", true);
        commands.verify(patrol).control("stop");
        commands.verify(patrol).markRightOrigin();
        verify(rail).remainingRightMs();
    }

    @Test
    public void restorePreviewQualityReturnsToSettingUsedBeforePatrol() {
        AutomaticPatrolService service = new AutomaticPatrolService();
        LocalCameraStreamService camera = mock(LocalCameraStreamService.class);
        ReflectionTestUtils.setField(service, "localCameraStreamService", camera);
        ReflectionTestUtils.setField(service, "previewQualityBeforePatrol", "smooth");

        ReflectionTestUtils.invokeMethod(service, "restorePreviewQuality");

        verify(camera).changeQuality("smooth");
    }

    @Test
    public void captureStopRetriesOneMissingRailAcknowledgement() {
        AutomaticPatrolService service = new AutomaticPatrolService();
        PatrolService patrol = mock(PatrolService.class);
        ControlPanelService panel = mock(ControlPanelService.class);
        when(patrol.control("stop")).thenReturn(null, "ok");
        when(panel.stopMotion()).thenReturn(new LinkedHashMap<String, Object>());
        ReflectionTestUtils.setField(service, "patrolService", patrol);
        ReflectionTestUtils.setField(service, "controlPanelService", panel);

        ReflectionTestUtils.invokeMethod(service, "stopMotionForCapture");

        verify(patrol, times(2)).control("stop");
        verify(panel).stopMotion();
    }

    @Test
    public void patrolBurstIsLimitedToThreeStableFrames() {
        AutomaticPatrolService service = new AutomaticPatrolService();
        ReflectionTestUtils.setField(service, "burstFrames", 7);

        int frames = ReflectionTestUtils.invokeMethod(service, "safeBurstFrames");

        assertEquals(3, frames);
    }

    @Test
    public void patrolCameraStopsWhenFourKIsUnavailable() {
        AutomaticPatrolService service = new AutomaticPatrolService();
        LocalCameraStreamService camera = mock(LocalCameraStreamService.class);
        when(camera.awaitReady(60000L)).thenReturn(true);
        when(camera.verifyCurrentQuality())
                .thenThrow(new IllegalStateException("截图分辨率未就绪：当前 1280x720，期望高度 2160"));
        ReflectionTestUtils.setField(service, "localCameraStreamService", camera);

        try {
            ReflectionTestUtils.invokeMethod(service, "preparePatrolCamera");
            fail("Expected patrol preparation to reject a non-4K camera stream");
        } catch (IllegalStateException expected) {
            assertEquals("截图分辨率未就绪：当前 1280x720，期望高度 2160", expected.getMessage());
        }

        InOrder calls = inOrder(camera);
        calls.verify(camera).changeQuality("4k");
        calls.verify(camera).awaitReady(60000L);
        calls.verify(camera).verifyCurrentQuality();
        verify(camera, never()).changeQuality("hd");
    }
}
