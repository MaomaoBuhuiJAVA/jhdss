package com.jhds.service;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TemporalDetectionVerifierTest {

    @Test
    public void singleHighConfidenceFrameRemainsUnconfirmed() {
        TemporalDetectionVerifier verifier = verifier();
        TemporalDetectionVerifier.VerificationResult result = verifier.update(
                detections(detection(.92, 100, 100, 220, 240)), 640, 480);

        assertEquals(1, result.candidates().size());
        assertTrue(result.confirmed().isEmpty());
        assertFalse((Boolean) result.candidates().get(0).get("confirmed"));
    }

    @Test
    public void stableObjectIsConfirmedOnThirdHit() {
        TemporalDetectionVerifier verifier = verifier();
        verifier.update(detections(detection(.52, 100, 100, 220, 240)), 640, 480);
        verifier.update(detections(detection(.42, 104, 103, 224, 243)), 640, 480);
        TemporalDetectionVerifier.VerificationResult result = verifier.update(
                detections(detection(.38, 108, 105, 228, 245)), 640, 480);

        assertEquals(1, result.confirmed().size());
        assertEquals(3, result.maximumHits());
        assertTrue((Boolean) result.confirmed().get(0).get("confirmed"));
    }

    @Test
    public void distantBoxesAreNotCombinedIntoOneTrack() {
        TemporalDetectionVerifier verifier = verifier();
        verifier.update(detections(detection(.70, 20, 20, 100, 100)), 640, 480);
        verifier.update(detections(detection(.70, 400, 300, 500, 420)), 640, 480);
        TemporalDetectionVerifier.VerificationResult result = verifier.update(
                detections(detection(.70, 25, 20, 105, 100)), 640, 480);

        assertTrue(result.confirmed().isEmpty());
        assertEquals(2, result.maximumHits());
    }

    @Test
    public void confidenceGatesStillApplyAfterEnoughHits() {
        TemporalDetectionVerifier verifier = verifier();
        verifier.update(detections(detection(.31, 100, 100, 220, 240)), 640, 480);
        verifier.update(detections(detection(.33, 102, 100, 222, 240)), 640, 480);
        TemporalDetectionVerifier.VerificationResult result = verifier.update(
                detections(detection(.34, 104, 100, 224, 240)), 640, 480);

        assertTrue(result.confirmed().isEmpty());
        assertEquals("verifying", result.summary().get("state"));
    }

    @Test
    public void confirmationIsRevokedWhenStrongEvidenceLeavesWindow() {
        TemporalDetectionVerifier verifier = verifier();
        verifier.update(detections(detection(.60, 100, 100, 220, 240)), 640, 480);
        verifier.update(detections(detection(.40, 102, 100, 222, 240)), 640, 480);
        TemporalDetectionVerifier.VerificationResult confirmed = verifier.update(
                detections(detection(.40, 104, 100, 224, 240)), 640, 480);
        assertEquals(1, confirmed.confirmed().size());

        verifier.update(detections(detection(.36, 106, 100, 226, 240)), 640, 480);
        verifier.update(detections(detection(.36, 108, 100, 228, 240)), 640, 480);
        TemporalDetectionVerifier.VerificationResult result = verifier.update(
                detections(detection(.36, 110, 100, 230, 240)), 640, 480);

        assertTrue(result.confirmed().isEmpty());
        assertEquals("verifying", result.summary().get("state"));
    }

    @Test
    public void trackExpiresAfterMissingFrames() {
        TemporalDetectionVerifier verifier = verifier();
        verifier.update(detections(detection(.70, 100, 100, 220, 240)), 640, 480);
        verifier.update(Collections.emptyList(), 640, 480);
        verifier.update(Collections.emptyList(), 640, 480);
        TemporalDetectionVerifier.VerificationResult result = verifier.update(
                detections(detection(.70, 100, 100, 220, 240)), 640, 480);

        assertEquals(1, result.maximumHits());
        assertTrue(result.confirmed().isEmpty());
    }

    private TemporalDetectionVerifier verifier() {
        return new TemporalDetectionVerifier(5, 3, 1, .25, .35, .50);
    }

    private List<Map<String, Object>> detections(Map<String, Object> detection) {
        List<Map<String, Object>> detections = new ArrayList<>();
        detections.add(detection);
        return detections;
    }

    private Map<String, Object> detection(double confidence, double x1, double y1,
                                          double x2, double y2) {
        Map<String, Object> detection = new LinkedHashMap<>();
        detection.put("class", "longhorn_beetle");
        detection.put("confidence", confidence);
        detection.put("box", Arrays.asList(x1, y1, x2, y2));
        return detection;
    }
}
