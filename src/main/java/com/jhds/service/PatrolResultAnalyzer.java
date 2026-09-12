package com.jhds.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntConsumer;

/** Builds the final task report from patrol photos after all hardware has stopped. */
@Service
public class PatrolResultAnalyzer {

    private static final DateTimeFormatter DISPLAY_TIME = DateTimeFormatter.ofPattern("MM-dd HH:mm:ss");
    private static final int MAX_CONFIRMED_RESULTS = 10;

    @Autowired
    private YoloRealtimeDetectionService yoloService;

    @Value("${patrol.automatic.result-path:./uploads/patrol-results}")
    private String resultPath;

    public List<Map<String, Object>> analyze(String patrolId, List<PatrolCaptureGroup> groups,
                                             IntConsumer progressListener) throws IOException {
        List<Map<String, Object>> results = new ArrayList<>();
        int totalFrames = 0;
        for (PatrolCaptureGroup group : groups) totalFrames += group.getFrames().size();
        int analyzedFrames = 0;

        Path destination = Paths.get(resultPath).toAbsolutePath().normalize();
        Files.createDirectories(destination);
        for (PatrolCaptureGroup group : groups) {
            Map<String, Object> representative = null;
            Path representativeFrame = null;
            double representativeConfidence = -1.0;
            String streamId = patrolId + "-line-" + group.getRow() + "-" + group.getPoint();
            for (Path frame : group.getFrames()) {
                Map<String, Object> inference = yoloService.detectFile(frame, null, streamId);
                analyzedFrames++;
                progressListener.accept(totalFrames == 0 ? 100
                        : (int) Math.round(analyzedFrames * 100.0 / totalFrames));
                double inferenceConfidence = maximumConfidence(inference);
                if (Boolean.TRUE.equals(inference.get("detected"))
                        && inferenceConfidence > representativeConfidence) {
                    representative = inference;
                    representativeFrame = frame;
                    representativeConfidence = inferenceConfidence;
                }
            }
            if (representative != null && representativeFrame != null) {
                results.add(buildResult(patrolId, group, representativeFrame, representative, destination));
            }
        }
        results.sort(Comparator.comparingDouble(this::resultConfidence).reversed());
        return results.size() <= MAX_CONFIRMED_RESULTS
                ? results : new ArrayList<>(results.subList(0, MAX_CONFIRMED_RESULTS));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> buildResult(String patrolId, PatrolCaptureGroup group, Path source,
                                            Map<String, Object> inference, Path destination) throws IOException {
        List<Map<String, Object>> detections = (List<Map<String, Object>>) inference.get("detections");
        String filename = patrolId + "_line" + String.format("%02d", group.getRow())
                + "_" + group.getPoint() + "_confirmed.jpg";
        String focusFilename = patrolId + "_line" + String.format("%02d", group.getRow())
                + "_" + group.getPoint() + "_focus.jpg";
        Path annotated = destination.resolve(filename);
        Path focused = destination.resolve(focusFilename);
        drawDetections(source, annotated, focused, detections);

        double confidence = 0.0;
        int hits = 0;
        int frames = group.getFrames().size();
        for (Map<String, Object> detection : detections) {
            confidence = Math.max(confidence, number(detection.get("averageConfidence"),
                    number(detection.get("confidence"), 0.0)));
            hits = Math.max(hits, (int) number(detection.get("hits"), 0.0));
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("name", localizedName(detections));
        result.put("confidence", confidence);
        result.put("count", detections.size());
        result.put("detections", detections);
        result.put("image", "/jhds/patrol-results/" + filename);
        result.put("annotatedImageUrl", "/jhds/patrol-results/" + filename);
        result.put("focusImage", "/jhds/patrol-results/" + focusFilename);
        result.put("location", "第" + group.getRow() + "条扫描线 · " + group.getPointLabel());
        result.put("plant", inferredPlant(group));
        result.put("hits", hits);
        result.put("frames", frames);
        result.put("capturedAt", LocalDateTime.now().format(DISPLAY_TIME));
        return result;
    }

    private void drawDetections(Path source, Path destination, Path focusedDestination,
                                List<Map<String, Object>> detections) throws IOException {
        BufferedImage image = ImageIO.read(source.toFile());
        if (image == null) throw new IOException("无法读取巡检图片: " + source);
        int focusLeft = image.getWidth();
        int focusTop = image.getHeight();
        int focusRight = 0;
        int focusBottom = 0;
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            float strokeWidth = Math.max(4f, image.getWidth() / 500f);
            int fontSize = Math.max(20, image.getWidth() / 70);
            graphics.setStroke(new BasicStroke(strokeWidth));
            graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, fontSize));
            for (Map<String, Object> detection : detections) {
                List<?> box = detection.get("box") instanceof List ? (List<?>) detection.get("box") : null;
                if (box == null || box.size() != 4) continue;
                int x1 = coordinate(box.get(0), 0, image.getWidth() - 1);
                int y1 = coordinate(box.get(1), 0, image.getHeight() - 1);
                int x2 = coordinate(box.get(2), x1 + 1, image.getWidth());
                int y2 = coordinate(box.get(3), y1 + 1, image.getHeight());
                focusLeft = Math.min(focusLeft, x1);
                focusTop = Math.min(focusTop, y1);
                focusRight = Math.max(focusRight, x2);
                focusBottom = Math.max(focusBottom, y2);
                graphics.setColor(new Color(255, 48, 64));
                graphics.drawRect(x1, y1, x2 - x1, y2 - y1);
                String label = localizedDetectionName(detection) + " " + String.format("%.1f%%",
                        number(detection.get("averageConfidence"), number(detection.get("confidence"), 0.0)) * 100.0);
                FontMetrics metrics = graphics.getFontMetrics();
                int labelHeight = metrics.getHeight() + 10;
                int labelY = Math.max(0, y1 - labelHeight);
                graphics.fillRect(x1, labelY, metrics.stringWidth(label) + 18, labelHeight);
                graphics.setColor(Color.WHITE);
                graphics.drawString(label, x1 + 9, labelY + metrics.getAscent() + 5);
            }
        } finally {
            graphics.dispose();
        }
        if (!ImageIO.write(image, "jpg", destination.toFile())) {
            throw new IOException("无法保存标注后的巡检图片: " + destination);
        }
        writeFocusedImage(image, focusedDestination, focusLeft, focusTop, focusRight, focusBottom);
    }

    private void writeFocusedImage(BufferedImage image, Path destination,
                                   int left, int top, int right, int bottom) throws IOException {
        if (right <= left || bottom <= top) {
            if (!ImageIO.write(image, "jpg", destination.toFile())) {
                throw new IOException("无法保存虫害聚焦图片: " + destination);
            }
            return;
        }
        int boxWidth = right - left;
        int boxHeight = bottom - top;
        int horizontalPadding = Math.max(24, boxWidth / 2);
        int verticalPadding = Math.max(24, boxHeight / 2);
        int cropLeft = Math.max(0, left - horizontalPadding);
        int cropTop = Math.max(0, top - verticalPadding);
        int cropRight = Math.min(image.getWidth(), right + horizontalPadding);
        int cropBottom = Math.min(image.getHeight(), bottom + verticalPadding);
        BufferedImage crop = image.getSubimage(cropLeft, cropTop,
                Math.max(1, cropRight - cropLeft), Math.max(1, cropBottom - cropTop));
        if (!ImageIO.write(crop, "jpg", destination.toFile())) {
            throw new IOException("无法保存虫害聚焦图片: " + destination);
        }
    }

    static String inferredPlant(PatrolCaptureGroup group) {
        int level = "bottom".equals(group.getPoint()) ? 1 : "middle".equals(group.getPoint()) ? 2 : 3;
        return "第 " + ((group.getRow() - 1) * 3 + level) + " 株盆栽";
    }

    @SuppressWarnings("unchecked")
    private double maximumConfidence(Map<String, Object> inference) {
        Object value = inference.get("detections");
        if (!(value instanceof List)) return -1.0;
        double maximum = -1.0;
        for (Map<String, Object> detection : (List<Map<String, Object>>) value) {
            maximum = Math.max(maximum, number(detection.get("averageConfidence"),
                    number(detection.get("confidence"), -1.0)));
        }
        return maximum;
    }

    private double resultConfidence(Map<String, Object> result) {
        return number(result.get("confidence"), 0.0);
    }

    private String localizedName(List<Map<String, Object>> detections) {
        if (detections.isEmpty()) return "虫害";
        return localizedDetectionName(detections.get(0));
    }

    private String localizedDetectionName(Map<String, Object> detection) {
        String value = String.valueOf(detection.get("class"));
        String normalized = value.toLowerCase().replaceAll("[\\s_-]+", "");
        return normalized.contains("longhorn") || normalized.contains("blackbeetle") ? "黑天牛" : value;
    }

    private int coordinate(Object value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, (int) Math.round(number(value, minimum))));
    }

    private double number(Object value, double fallback) {
        return value instanceof Number ? ((Number) value).doubleValue() : fallback;
    }
}
