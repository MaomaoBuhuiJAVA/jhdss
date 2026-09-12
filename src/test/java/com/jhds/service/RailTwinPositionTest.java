package com.jhds.service;

import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import static org.junit.Assert.*;

public class RailTwinPositionTest {
    private RailPositionService rail(String direction, long bank) {
        RailPositionService rail = new RailPositionService();
        ReflectionTestUtils.setField(rail, "horizontalTravelMs", 10000L);
        ReflectionTestUtils.setField(rail, "horizontalCoverageRatio", .6);
        ReflectionTestUtils.setField(rail, "positionMs", bank);
        ReflectionTestUtils.setField(rail, "activeDirection", direction);
        ReflectionTestUtils.setField(rail, "moveStartedAt", System.currentTimeMillis() - 2000L);
        return rail;
    }
    @Test public void joiningMidMoveIncludesElapsedTimeWithoutBanking() {
        RailPositionService rail = rail("left", 1000);
        Map<String, Object> data = rail.twinPosition();
        assertEquals(.7, (Double) data.get("x"), .01);
        assertEquals(1000, rail.positionMs());
        assertEquals("motor-time-estimate", data.get("source"));
    }
    @Test public void rightMoveAndSoftLimitsAreClamped() {
        assertEquals(1.0, (Double) rail("right", 1000).twinPosition().get("x"), .001);
        assertEquals(.4, (Double) rail("left", 7900).twinPosition().get("x"), .001);
    }
    @Test public void stationarySampleDoesNotAdvance() {
        assertEquals(.6, (Double) rail(null, 4000).twinPosition().get("x"), .001);
    }
    @Test public void rightOriginResetClearsSoftLimitWithoutMovingHardware() throws Exception {
        RailPositionService rail = rail(null, 6000);
        Path positionFile = Files.createTempFile("rail-position-test-", ".txt");
        ReflectionTestUtils.setField(rail, "positionFile", positionFile.toString());
        try {
            assertTrue(rail.atLeftLimit());
            rail.markRightOrigin();
            assertFalse(rail.atLeftLimit());
            assertEquals(0L, rail.positionMs());
            assertNull(rail.status().get("movingDirection"));
        } finally {
            Files.deleteIfExists(positionFile);
        }
    }
}
