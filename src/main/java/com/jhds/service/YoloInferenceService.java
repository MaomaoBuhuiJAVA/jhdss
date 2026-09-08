package com.jhds.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/** Runs the locally trained YOLO model and exposes evaluation data to the web UI. */
@Service
public class YoloInferenceService {

    private static final Logger log = LoggerFactory.getLogger(YoloInferenceService.class);

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${ai.yolo.python-path:D:/jhdss-tools/yolo-venv311/Scripts/python.exe}")
    private String pythonPath;
    @Value("${ai.yolo.model-path:./LabelImg资料图片/yolo_runs/black_longhorn_yolo11s/weights/best.pt}")
    private String modelPath;
    @Value("${ai.yolo.script-path:./scripts/yolo_infer.py}")
    private String scriptPath;
    @Value("${ai.yolo.upload-path:./uploads/ai-inference}")
    private String uploadPath;
    @Value("${ai.yolo.evaluation-path:./LabelImg资料图片/yolo_runs/camera_test_eval}")
    private String evaluationPath;
    @Value("${ai.yolo.evaluation-source-path:./LabelImg资料图片/yolo_dataset/camera_test}")
    private String evaluationSourcePath;
    @Value("${ai.yolo.confidence:0.25}")
    private double confidence;
    @Value("${ai.yolo.timeout-seconds:120}")
    private long timeoutSeconds;

    @PostConstruct
    public void init() {
        try {
            Files.createDirectories(Paths.get(uploadPath).toAbsolutePath().normalize());
        } catch (IOException e) {
            throw new IllegalStateException("无法创建 YOLO 上传目录: " + uploadPath, e);
        }
    }

    public Map<String, Object> detect(MultipartFile multipartFile, Double requestedConfidence) throws IOException {
        if (multipartFile == null || multipartFile.isEmpty()) {
            throw new IllegalArgumentException("请选择一张图片");
        }
        String contentType = multipartFile.getContentType();
        if (contentType != null && !contentType.toLowerCase().startsWith("image/")) {
            throw new IllegalArgumentException("仅支持 JPG、PNG、WEBP 等图片格式");
        }
        validateRuntime();
        String extension = extension(multipartFile.getOriginalFilename());
        String id = UUID.randomUUID().toString().replace("-", "");
        Path root = Paths.get(uploadPath).toAbsolutePath().normalize();
        Path input = root.resolve(id + extension).normalize();
        Path output = root.resolve(id + "-result.jpg").normalize();
        if (!input.getParent().equals(root) || !output.getParent().equals(root)) {
            throw new IOException("非法的上传文件路径");
        }
        multipartFile.transferTo(input.toFile());
        try {
            double threshold = requestedConfidence == null ? confidence : Math.max(0.05, Math.min(0.95, requestedConfidence));
            Map<String, Object> result = run(input, output, threshold);
            result.put("fileName", multipartFile.getOriginalFilename());
            result.put("image", "/ai-inference/" + output.getFileName());
            result.put("model", modelPath);
            return result;
        } finally {
            Files.deleteIfExists(input);
        }
    }

    public Map<String, Object> evaluation() throws IOException {
        Path root = Paths.get(evaluationPath).toAbsolutePath().normalize();
        Path json = root.resolve("predictions.json").normalize();
        if (!Files.isRegularFile(json)) {
            throw new IOException("找不到测试集结果，请先运行摄像头评估脚本");
        }
        List<Map<String, Object>> rows = objectMapper.readValue(Files.readAllBytes(json),
                new TypeReference<List<Map<String, Object>>>() { });
        int detected = 0;
        int totalBoxes = 0;
        double confidenceSum = 0;
        List<Map<String, Object>> images = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> detections = (List<Map<String, Object>>) row.get("detections");
            if (detections == null) detections = new ArrayList<>();
            totalBoxes += detections.size();
            if (!detections.isEmpty()) detected++;
            for (Map<String, Object> detection : detections) {
                Object value = detection.get("confidence");
                if (value instanceof Number) confidenceSum += ((Number) value).doubleValue();
            }
            String fileName = String.valueOf(row.get("image"));
            Map<String, Object> image = new LinkedHashMap<>();
            image.put("name", fileName);
            image.put("detected", !detections.isEmpty());
            image.put("count", detections.size());
            image.put("detections", detections);
            image.put("image", "/ai-eval/" + fileName);
            image.put("sourceImage", "/ai-eval-source/" + fileName);
            images.add(image);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("model", modelPath);
        result.put("imageCount", rows.size());
        result.put("detectedCount", detected);
        result.put("undetectedCount", rows.size() - detected);
        result.put("detectionRate", rows.isEmpty() ? 0 : round(100.0 * detected / rows.size()));
        result.put("totalBoxes", totalBoxes);
        result.put("averageConfidence", totalBoxes == 0 ? 0 : round(confidenceSum / totalBoxes * 100));
        result.put("validation", validationMetrics());
        result.put("images", images);
        return result;
    }

    private Map<String, Object> run(Path input, Path output, double threshold) throws IOException {
        List<String> command = new ArrayList<>();
        command.add(pythonPath);
        command.add(scriptPath);
        command.add("--model"); command.add(modelPath);
        command.add("--input"); command.add(input.toString());
        command.add("--output"); command.add(output.toString());
        command.add("--conf"); command.add(String.valueOf(threshold));
        Path processLog = Files.createTempFile(Paths.get(uploadPath).toAbsolutePath().normalize(), ".yolo-", ".log");
        Process process = new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(processLog.toFile()).start();
        boolean finished;
        try {
            finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            process.destroyForcibly();
            Thread.currentThread().interrupt();
            throw new IOException("YOLO 识别被中断", e);
        }
        if (!finished) {
            process.destroyForcibly();
            Files.deleteIfExists(processLog);
            throw new IOException("YOLO 识别超时");
        }
        String logs = new String(Files.readAllBytes(processLog), StandardCharsets.UTF_8);
        Files.deleteIfExists(processLog);
        if (process.exitValue() != 0 || !Files.isRegularFile(output)) {
            log.error("YOLO process failed: {}", logs);
            throw new IOException("YOLO 识别失败，请检查 Python 环境和模型路径");
        }
        String[] lines = logs.trim().split("\\R");
        if (lines.length == 0) throw new IOException("YOLO 未返回识别结果");
        try {
            return objectMapper.readValue(lines[lines.length - 1],
                    new TypeReference<Map<String, Object>>() { });
        } catch (Exception e) {
            log.error("Invalid YOLO output: {}", logs, e);
            throw new IOException("YOLO 返回结果格式错误", e);
        }
    }

    private Map<String, Object> validationMetrics() {
        Map<String, Object> metrics = new LinkedHashMap<>();
        Path csv = Paths.get(evaluationPath).toAbsolutePath().normalize().getParent()
                .resolve("black_longhorn_yolo11s").resolve("results.csv");
        if (!Files.isRegularFile(csv)) return metrics;
        try {
            List<String> lines = Files.readAllLines(csv, StandardCharsets.UTF_8);
            if (lines.size() < 2) return metrics;
            String[] header = lines.get(0).split(",");
            int bestIndex = -1;
            double bestMap = -1;
            for (int i = 1; i < lines.size(); i++) {
                String[] values = lines.get(i).split(",");
                if (values.length != header.length) continue;
                double map = number(values, header, "metrics/mAP50-95(B)");
                if (map > bestMap) { bestMap = map; bestIndex = i; }
            }
            if (bestIndex < 0) return metrics;
            String[] values = lines.get(bestIndex).split(",");
            metrics.put("epoch", integer(values, header, "epoch"));
            metrics.put("precision", percent(number(values, header, "metrics/precision(B)")));
            metrics.put("recall", percent(number(values, header, "metrics/recall(B)")));
            metrics.put("map50", percent(number(values, header, "metrics/mAP50(B)")));
            metrics.put("map50_95", percent(number(values, header, "metrics/mAP50-95(B)")));
        } catch (Exception e) {
            log.warn("Unable to read YOLO validation metrics from {}", csv, e);
        }
        return metrics;
    }

    private double number(String[] values, String[] header, String key) {
        for (int i = 0; i < header.length; i++) if (key.equals(header[i])) return Double.parseDouble(values[i]);
        return 0;
    }

    private int integer(String[] values, String[] header, String key) { return (int) number(values, header, key); }
    private double percent(double value) { return round(value * 100); }

    private void validateRuntime() {
        if (!Files.isRegularFile(Paths.get(pythonPath))) throw new IllegalStateException("Python 路径不存在: " + pythonPath);
        if (!Files.isRegularFile(Paths.get(modelPath))) throw new IllegalStateException("模型文件不存在: " + modelPath);
        if (!Files.isRegularFile(Paths.get(scriptPath))) throw new IllegalStateException("YOLO 脚本不存在: " + scriptPath);
    }

    private String extension(String name) {
        if (name == null) return ".jpg";
        String value = name.toLowerCase();
        int dot = value.lastIndexOf('.');
        String ext = dot >= 0 ? value.substring(dot) : ".jpg";
        return ext.matches("\\.(jpg|jpeg|png|webp|bmp)") ? ext : ".jpg";
    }

    private double round(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    public String getUploadPath() { return uploadPath; }
    public String getEvaluationPath() { return evaluationPath; }
    public String getEvaluationSourcePath() { return evaluationSourcePath; }
}
