package com.jhds.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/** Keeps one YOLO process and CUDA model alive for low-latency video frame detection. */
@Service
public class YoloRealtimeDetectionService implements DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(YoloRealtimeDetectionService.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ExecutorService ioExecutor = Executors.newCachedThreadPool();
    private final Object workerLock = new Object();
    private final Map<String, VerificationSession> verificationSessions = new LinkedHashMap<>();

    @Value("${ai.yolo.enabled:true}") private boolean enabled;
    @Value("${ai.yolo.python-path:D:/jhdss-tools/yolo-venv311/Scripts/python.exe}") private String pythonPath;
    @Value("${ai.yolo.model-path:./weights/black_longhorn_best.pt}") private String modelPath;
    @Value("${ai.yolo.worker-script-path:./scripts/yolo_worker.py}") private String workerScriptPath;
    @Value("${ai.yolo.device:0}") private String device;
    @Value("${ai.yolo.confidence:0.25}") private double defaultConfidence;
    @Value("${ai.yolo.realtime-timeout-seconds:20}") private long timeoutSeconds;
    @Value("${ai.yolo.alarm-cooldown-seconds:60}") private long alarmCooldownSeconds;
    @Value("${ai.yolo.alarm-enabled:true}") private boolean alarmEnabled;
    @Value("${ai.yolo.realtime-interval-ms:1000}") private long realtimeIntervalMs;
    @Value("${ai.yolo.verification.window-frames:5}") private int verificationWindowFrames;
    @Value("${ai.yolo.verification.required-hits:3}") private int verificationRequiredHits;
    @Value("${ai.yolo.verification.max-frame-gap:1}") private int verificationMaxFrameGap;
    @Value("${ai.yolo.verification.minimum-iou:0.25}") private double verificationMinimumIou;
    @Value("${ai.yolo.verification.minimum-average-confidence:0.35}") private double verificationMinimumAverageConfidence;
    @Value("${ai.yolo.verification.minimum-strong-confidence:0.50}") private double verificationMinimumStrongConfidence;

    @Autowired private AlarmService alarmService;

    private Process worker;
    private BufferedWriter workerInput;
    private BufferedReader workerOutput;
    private long sequence;
    private long lastAlarmAt;

    public Map<String, Object> detect(String image, Double confidence, String location,
                                      String streamId) throws IOException {
        if (!enabled) throw new IllegalStateException("实时虫害识别未启用");
        if (image == null || image.trim().isEmpty()) throw new IllegalArgumentException("视频帧不能为空");
        if (image.length() > 4_000_000) throw new IllegalArgumentException("视频帧过大");
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("image", stripDataUrl(image));
        request.put("confidence", confidence == null ? defaultConfidence : confidence);
        return executeDetection(request, location, streamId, true);
    }

    /** Runs one saved patrol frame without creating a realtime alarm record. */
    public Map<String, Object> detectFile(Path image, Double confidence, String streamId) throws IOException {
        if (!enabled) throw new IllegalStateException("虫害识别未启用");
        if (image == null || !Files.isRegularFile(image)) {
            throw new IllegalArgumentException("巡检图片不存在: " + image);
        }
        Map<String, Object> request = new LinkedHashMap<>();
        // A base64 path keeps Windows backslashes and non-ASCII directory names
        // out of the worker's line-delimited JSON transport.
        request.put("image_path_b64", encodeImagePath(image));
        request.put("confidence", confidence == null ? defaultConfidence : confidence);
        return executeDetection(request, null, streamId, false);
    }

    static String encodeImagePath(Path image) {
        String normalized = image.toAbsolutePath().normalize().toString();
        return Base64.getEncoder().encodeToString(normalized.getBytes(StandardCharsets.UTF_8));
    }

    private Map<String, Object> executeDetection(Map<String, Object> request, String location,
                                                  String streamId, boolean createAlarm) throws IOException {
        synchronized (workerLock) {
            ensureWorker();
            request.put("id", ++sequence);
            try {
                workerInput.write(objectMapper.writeValueAsString(request));
                workerInput.newLine();
                workerInput.flush();
                Future<String> future = ioExecutor.submit(() -> workerOutput.readLine());
                String line = future.get(timeoutSeconds, TimeUnit.SECONDS);
                if (line == null) throw new IOException("YOLO 实时识别进程已退出");
                Map<String, Object> result = objectMapper.readValue(line,
                        new TypeReference<Map<String, Object>>() { });
                if (result.get("error") != null) throw new IOException(String.valueOf(result.get("error")));
                addDetectionSummary(result, streamId);
                if (createAlarm) maybeCreateAlarm(result, location);
                return result;
            } catch (InterruptedException e) {
                stopWorker();
                Thread.currentThread().interrupt();
                throw new IOException("实时识别被中断", e);
            } catch (Exception e) {
                stopWorker();
                if (e instanceof IOException) throw (IOException) e;
                throw new IOException("实时识别失败: " + e.getMessage(), e);
            }
        }
    }

    public Map<String, Object> status() {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("enabled", enabled);
        status.put("running", worker != null && worker.isAlive());
        status.put("model", modelPath);
        status.put("confidence", defaultConfidence);
        status.put("alarmCooldownSeconds", alarmCooldownSeconds);
        status.put("alarmEnabled", alarmEnabled);
        status.put("intervalMs", realtimeIntervalMs);
        Map<String, Object> verification = new LinkedHashMap<>();
        verification.put("windowFrames", verificationWindowFrames);
        verification.put("requiredHits", verificationRequiredHits);
        verification.put("maxFrameGap", verificationMaxFrameGap);
        verification.put("minimumIou", verificationMinimumIou);
        verification.put("minimumAverageConfidence", verificationMinimumAverageConfidence);
        verification.put("minimumStrongConfidence", verificationMinimumStrongConfidence);
        status.put("verification", verification);
        return status;
    }

    public Map<String, Object> setEnabled(boolean requestedEnabled) {
        synchronized (workerLock) {
            enabled = requestedEnabled;
            if (!enabled) stopWorker();
            return status();
        }
    }

    private void ensureWorker() throws IOException {
        if (worker != null && worker.isAlive()) return;
        validateRuntime();
        ProcessBuilder builder = new ProcessBuilder(pythonPath, workerScriptPath,
                "--model", modelPath, "--device", device, "--imgsz", "1024");
        builder.redirectError(ProcessBuilder.Redirect.INHERIT);
        worker = builder.start();
        workerInput = new BufferedWriter(new OutputStreamWriter(worker.getOutputStream(), StandardCharsets.UTF_8));
        workerOutput = new BufferedReader(new InputStreamReader(worker.getInputStream(), StandardCharsets.UTF_8));
        Future<String> readyFuture = ioExecutor.submit(() -> workerOutput.readLine());
        try {
            String ready = readyFuture.get(90, TimeUnit.SECONDS);
            if (ready == null || ready.indexOf("\"ready\": true") < 0) {
                throw new IOException("YOLO 工作进程启动失败: " + ready);
            }
            log.info("YOLO realtime worker ready: {}", modelPath);
        } catch (Exception e) {
            stopWorker();
            throw new IOException("YOLO 模型加载失败", e);
        }
    }

    @SuppressWarnings("unchecked")
    private void addDetectionSummary(Map<String, Object> result, String streamId) {
        List<Map<String, Object>> rawDetections = (List<Map<String, Object>>) result.get("detections");
        TemporalDetectionVerifier.VerificationResult verification = verifier(streamId).update(
                rawDetections, integer(result.get("width")), integer(result.get("height")));
        result.put("candidates", verification.candidates());
        result.put("candidateCount", verification.candidates().size());
        result.put("detections", verification.confirmed());
        result.put("detected", !verification.confirmed().isEmpty());
        result.put("count", verification.confirmed().size());
        result.put("verification", verification.summary());
        result.put("serverTime", System.currentTimeMillis());
    }

    private TemporalDetectionVerifier verifier(String streamId) {
        long now = System.currentTimeMillis();
        long idleResetMs = Math.max(5000L,
                realtimeIntervalMs * (verificationMaxFrameGap + 2L));
        long staleSessionMs = Math.max(60_000L,
                realtimeIntervalMs * Math.max(verificationWindowFrames, 1) * 4L);
        verificationSessions.entrySet().removeIf(
                entry -> now - entry.getValue().lastUpdatedAt > staleSessionMs);
        String key = normalizeStreamId(streamId);
        VerificationSession session = verificationSessions.get(key);
        if (session == null || now - session.lastUpdatedAt > idleResetMs) {
            session = new VerificationSession(new TemporalDetectionVerifier(verificationWindowFrames,
                    verificationRequiredHits, verificationMaxFrameGap, verificationMinimumIou,
                    verificationMinimumAverageConfidence, verificationMinimumStrongConfidence), now);
            verificationSessions.put(key, session);
        }
        session.lastUpdatedAt = now;
        return session.verifier;
    }

    private String normalizeStreamId(String streamId) {
        if (streamId == null || streamId.trim().isEmpty()) return "default";
        String normalized = streamId.trim();
        return normalized.length() <= 80 ? normalized : normalized.substring(0, 80);
    }

    private int integer(Object value) {
        return value instanceof Number ? ((Number) value).intValue() : 0;
    }

    private double percent(double value) {
        return Math.round(value * 1000.0) / 10.0;
    }

    @SuppressWarnings("unchecked")
    private void maybeCreateAlarm(Map<String, Object> result, String location) {
        List<Map<String, Object>> detections = (List<Map<String, Object>>) result.get("detections");
        if (!alarmEnabled || detections == null || detections.isEmpty()) return;
        long now = System.currentTimeMillis();
        if (now - lastAlarmAt < alarmCooldownSeconds * 1000L) return;
        double maximumAverage = 0;
        double maximumEvidence = 0;
        for (Map<String, Object> detection : detections) {
            Object average = detection.get("averageConfidence");
            Object evidence = detection.get("maximumConfidence");
            if (average instanceof Number) {
                maximumAverage = Math.max(maximumAverage, ((Number) average).doubleValue());
            }
            if (evidence instanceof Number) {
                maximumEvidence = Math.max(maximumEvidence, ((Number) evidence).doubleValue());
            }
        }
        String resolvedLocation = location == null || location.trim().isEmpty() ? "AI轨道巡检摄像头" : location.trim();
        try {
            alarmService.createAlarm("多帧复核发现黑天牛",
                    "视频流连续多帧确认" + detections.size() + "个黑天牛候选，平均置信度最高"
                            + percent(maximumAverage) + "%、单帧证据最高" + percent(maximumEvidence)
                            + "% ，请结合现场画面人工确认后处理。",
                    "urgent", "patrol", resolvedLocation);
            lastAlarmAt = now;
            result.put("alarmCreated", true);
        } catch (RuntimeException error) {
            log.error("Realtime detection succeeded but alarm persistence failed", error);
            result.put("alarmCreated", false);
            result.put("alarmError", "告警记录保存失败");
        }
    }

    private String stripDataUrl(String image) {
        int comma = image.indexOf(',');
        return image.startsWith("data:") && comma >= 0 ? image.substring(comma + 1) : image;
    }

    private void validateRuntime() {
        if (!Files.isRegularFile(Paths.get(pythonPath))) throw new IllegalStateException("Python 路径不存在: " + pythonPath);
        if (!Files.isRegularFile(Paths.get(modelPath))) throw new IllegalStateException("模型文件不存在: " + modelPath);
        if (!Files.isRegularFile(Paths.get(workerScriptPath))) throw new IllegalStateException("实时识别脚本不存在: " + workerScriptPath);
    }

    private void stopWorker() {
        try { if (workerInput != null) workerInput.close(); } catch (IOException ignored) { }
        try { if (workerOutput != null) workerOutput.close(); } catch (IOException ignored) { }
        if (worker != null && worker.isAlive()) worker.destroyForcibly();
        worker = null;
        workerInput = null;
        workerOutput = null;
        verificationSessions.clear();
    }

    @Override
    public void destroy() {
        synchronized (workerLock) { stopWorker(); }
        ioExecutor.shutdownNow();
    }

    private static final class VerificationSession {
        private final TemporalDetectionVerifier verifier;
        private long lastUpdatedAt;

        private VerificationSession(TemporalDetectionVerifier verifier, long lastUpdatedAt) {
            this.verifier = verifier;
            this.lastUpdatedAt = lastUpdatedAt;
        }
    }
}
