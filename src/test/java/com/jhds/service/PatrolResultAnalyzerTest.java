package com.jhds.service;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.springframework.test.util.ReflectionTestUtils;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class PatrolResultAnalyzerTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void emitsOnlyConfirmedMultiFrameResultWithStablePlantNumber() throws Exception {
        Path frame = temporaryFolder.newFile("frame.jpg").toPath();
        BufferedImage image = new BufferedImage(320, 180, BufferedImage.TYPE_INT_RGB);
        image.getGraphics().setColor(Color.DARK_GRAY);
        ImageIO.write(image, "jpg", frame.toFile());

        PatrolCaptureGroup group = new PatrolCaptureGroup(2, "middle", "中点");
        group.addFrame(frame);
        group.addFrame(frame);
        group.addFrame(frame);

        YoloRealtimeDetectionService yolo = mock(YoloRealtimeDetectionService.class);
        when(yolo.detectFile(any(Path.class), isNull(), anyString()))
                .thenReturn(clearInference(), clearInference(), confirmedInference());

        PatrolResultAnalyzer analyzer = new PatrolResultAnalyzer();
        ReflectionTestUtils.setField(analyzer, "yoloService", yolo);
        ReflectionTestUtils.setField(analyzer, "resultPath", temporaryFolder.newFolder("results").getAbsolutePath());

        List<Map<String, Object>> results = analyzer.analyze("patrol_test",
                Collections.singletonList(group), ignored -> { });

        assertEquals(1, results.size());
        assertEquals("黑天牛", results.get(0).get("name"));
        assertEquals("第 5 株盆栽", results.get(0).get("plant"));
        assertEquals(3, results.get(0).get("hits"));
        assertEquals(1, ((List<?>) results.get(0).get("detections")).size());
        assertTrue(String.valueOf(results.get(0).get("image")).startsWith("/jhds/patrol-results/"));
        assertTrue(String.valueOf(results.get(0).get("focusImage")).startsWith("/jhds/patrol-results/"));
    }

    @Test
    public void doesNotEmitSingleFrameCandidate() throws Exception {
        Path frame = temporaryFolder.newFile("clear.jpg").toPath();
        ImageIO.write(new BufferedImage(120, 80, BufferedImage.TYPE_INT_RGB), "jpg", frame.toFile());
        PatrolCaptureGroup group = new PatrolCaptureGroup(1, "bottom", "底部");
        group.addFrame(frame);

        YoloRealtimeDetectionService yolo = mock(YoloRealtimeDetectionService.class);
        when(yolo.detectFile(any(Path.class), isNull(), anyString())).thenReturn(clearInference());
        PatrolResultAnalyzer analyzer = new PatrolResultAnalyzer();
        ReflectionTestUtils.setField(analyzer, "yoloService", yolo);
        ReflectionTestUtils.setField(analyzer, "resultPath", temporaryFolder.newFolder("empty-results").getAbsolutePath());

        assertTrue(analyzer.analyze("patrol_clear", Collections.singletonList(group), ignored -> { }).isEmpty());
    }

    @Test
    public void capsConfirmedResultsAtTen() throws Exception {
        Path frame = temporaryFolder.newFile("confirmed.jpg").toPath();
        ImageIO.write(new BufferedImage(160, 100, BufferedImage.TYPE_INT_RGB), "jpg", frame.toFile());
        List<PatrolCaptureGroup> groups = new ArrayList<>();
        for (int row = 1; row <= 12; row++) {
            PatrolCaptureGroup group = new PatrolCaptureGroup(row, "bottom", "底部");
            group.addFrame(frame);
            groups.add(group);
        }
        YoloRealtimeDetectionService yolo = mock(YoloRealtimeDetectionService.class);
        when(yolo.detectFile(any(Path.class), isNull(), anyString())).thenReturn(confirmedInference());
        PatrolResultAnalyzer analyzer = new PatrolResultAnalyzer();
        ReflectionTestUtils.setField(analyzer, "yoloService", yolo);
        ReflectionTestUtils.setField(analyzer, "resultPath", temporaryFolder.newFolder("capped-results").getAbsolutePath());

        List<Map<String, Object>> results = analyzer.analyze("patrol_many", groups, ignored -> { });

        assertEquals(10, results.size());
        for (Map<String, Object> result : results) {
            assertTrue(String.valueOf(result.get("plant")).contains("盆栽"));
            assertTrue(String.valueOf(result.get("annotatedImageUrl")).endsWith("_confirmed.jpg"));
        }
    }

    @Test
    public void encodesWindowsStylePathWithoutJsonEscapeCharacters() {
        Path path = java.nio.file.Paths.get("C:\\巡检照片\\第1株\\frame.jpg");
        String encoded = YoloRealtimeDetectionService.encodeImagePath(path);

        assertTrue(encoded.indexOf('\\') < 0);
        assertEquals(path.toAbsolutePath().normalize().toString(),
                new String(Base64.getDecoder().decode(encoded), StandardCharsets.UTF_8));
    }

    private Map<String, Object> clearInference() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("detected", false);
        result.put("detections", Collections.emptyList());
        return result;
    }

    private Map<String, Object> confirmedInference() {
        Map<String, Object> detection = new LinkedHashMap<>();
        detection.put("class", "black_longhorn");
        detection.put("confidence", 0.62);
        detection.put("averageConfidence", 0.58);
        detection.put("hits", 3);
        detection.put("box", Arrays.asList(40.0, 25.0, 130.0, 115.0));
        List<Map<String, Object>> detections = new ArrayList<>();
        detections.add(detection);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("detected", true);
        result.put("detections", detections);
        return result;
    }
}
