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
import java.nio.file.Paths;
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

    @Value("${ai.yolo.enabled:true}") private boolean enabled;
    @Value("${ai.yolo.python-path:D:/jhdss-tools/yolo-venv311/Scripts/python.exe}") private String pythonPath;
    @Value("${ai.yolo.model-path:E:/LabelImg资料图片/yolo_runs/black_longhorn_yolo11s/weights/best.pt}") private String modelPath;
    @Value("${ai.yolo.worker-script-path:./scripts/yolo_worker.py}") private String workerScriptPath;
    @Value("${ai.yolo.device:0}") private String device;
    @Value("${ai.yolo.confidence:0.25}") private double defaultConfidence;
    @Value("${ai.yolo.realtime-timeout-seconds:20}") private long timeoutSeconds;
    @Value("${ai.yolo.alarm-cooldown-seconds:60}") private long alarmCooldownSeconds;
    @Value("${ai.yolo.alarm-enabled:true}") private boolean alarmEnabled;
    @Value("${ai.yolo.realtime-interval-ms:1000}") private long realtimeIntervalMs;

    @Autowired private AlarmService alarmService;

    private Process worker;
    private BufferedWriter workerInput;
    private BufferedReader workerOutput;
    private long sequence;
    private long lastAlarmAt;

    public Map<String, Object> detect(String image, Double confidence, String location) throws IOException {
        if (!enabled) throw new IllegalStateException("实时虫害识别未启用");
        if (image == null || image.trim().isEmpty()) throw new IllegalArgumentException("视频帧不能为空");
        if (image.length() > 4_000_000) throw new IllegalArgumentException("视频帧过大");
        synchronized (workerLock) {
            ensureWorker();
            Map<String, Object> request = new LinkedHashMap<>();
            request.put("id", ++sequence);
            request.put("image", stripDataUrl(image));
            request.put("confidence", confidence == null ? defaultConfidence : confidence);
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
                addDetectionSummary(result);
                maybeCreateAlarm(result, location);
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
    private void addDetectionSummary(Map<String, Object> result) {
        List<Map<String, Object>> detections = (List<Map<String, Object>>) result.get("detections");
        result.put("detected", detections != null && !detections.isEmpty());
        result.put("count", detections == null ? 0 : detections.size());
        result.put("serverTime", System.currentTimeMillis());
    }

    @SuppressWarnings("unchecked")
    private void maybeCreateAlarm(Map<String, Object> result, String location) {
        List<Map<String, Object>> detections = (List<Map<String, Object>>) result.get("detections");
        if (!alarmEnabled || detections == null || detections.isEmpty()) return;
        long now = System.currentTimeMillis();
        if (now - lastAlarmAt < alarmCooldownSeconds * 1000L) return;
        double maximum = 0;
        for (Map<String, Object> detection : detections) {
            Object value = detection.get("confidence");
            if (value instanceof Number) maximum = Math.max(maximum, ((Number) value).doubleValue());
        }
        String resolvedLocation = location == null || location.trim().isEmpty() ? "AI轨道巡检摄像头" : location.trim();
        try {
            alarmService.createAlarm("实时监测发现黑天牛",
                    "视频流检测到" + detections.size() + "个疑似黑天牛，最高置信度"
                            + Math.round(maximum * 1000.0) / 10.0 + "% ，请及时复核处理。",
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
    }

    @Override
    public void destroy() {
        synchronized (workerLock) { stopWorker(); }
        ioExecutor.shutdownNow();
    }
}
