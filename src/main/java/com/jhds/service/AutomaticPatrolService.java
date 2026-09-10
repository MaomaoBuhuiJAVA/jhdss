package com.jhds.service;


import com.jhds.config.YsjProperties;
import com.jhds.service.mqtt.MqttService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PreDestroy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Service
public class AutomaticPatrolService {

    private static final DateTimeFormatter FILE_TIME = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    @Autowired
    private PatrolService patrolService;
    @Autowired
    private RailPositionService railPositionService;
    @Autowired
    private MqttService mqttService;
    @Autowired
    private ControlPanelService controlPanelService;
    @Autowired
    private LocalCameraStreamService localCameraStreamService;
    @Autowired
    private EzvizService ezvizService;
    @Autowired
    private YsjProperties ysjProperties;


    @Value("${patrol.automatic.output-path:./LabelImg资料图片/摄像头实际拍摄照片}")
    private String outputPath;
    @Value("${patrol.automatic.horizontal-travel-ms:18422}")
    private long horizontalTravelMs;
    @Value("${patrol.automatic.vertical-travel-ms:11587}")
    private long verticalTravelMs;
    @Value("${patrol.automatic.coverage-ratio:0.92}")
    private double coverageRatio;
    @Value("${patrol.automatic.horizontal-coverage-ratio:0.80}")
    private double horizontalCoverageRatio;
    @Value("${patrol.automatic.settle-ms:350}")
    private long settleMs;

    private final Object stateLock = new Object();
    private final ExecutorService patrolExecutor = Executors.newSingleThreadExecutor(daemonFactory("auto-patrol"));
    private final ExecutorService stopExecutor = Executors.newCachedThreadPool(daemonFactory("patrol-stop"));
    private final AtomicInteger fileSequence = new AtomicInteger();

    private volatile Future<?> activeFuture;
    private volatile boolean running;
    private volatile boolean cancelRequested;
    private volatile String state = "IDLE";
    private volatile String phase = "等待开始";
    private volatile String planId = "standard";
    private volatile String planName = "标准巡检";
    private volatile int progress;
    private volatile int currentRow;
    private volatile int totalRows;
    private volatile int captureCount;
    private volatile long startedAt;
    private volatile long endedAt;
    private volatile String lastError;
    private volatile String warning;
    private volatile String capturePrefix;
    private volatile boolean outputPathFallbackLogged;

    public List<Map<String, Object>> plans() {
        List<Map<String, Object>> result = new ArrayList<>();
        result.add(plan("quick", "快速巡检", "3条扫描线，适合日常复查", 3));
        result.add(plan("standard", "标准巡检", "5条扫描线，覆盖效率均衡", 5));
        result.add(plan("detailed", "精细巡检", "7条扫描线，适合病虫害排查", 7));
        return result;
    }

    public Map<String, Object> start(String requestedPlanId, boolean originConfirmed) {
        if (!originConfirmed) {
            throw new IllegalArgumentException("请先确认轨道位于右下安全起点");
        }
        // The operator just confirmed the rail sits on the rightmost origin,
        // so re-zero the dead-reckoned soft-limit position before this run.
        patrolService.markRightOrigin();
        Map<String, Object> selected = findPlan(requestedPlanId);
        synchronized (stateLock) {
            if (running) {
                throw new IllegalStateException("已有自动巡检正在执行");
            }
            running = true;
            cancelRequested = false;
            state = "QUEUED";
            phase = "准备巡检";
            planId = String.valueOf(selected.get("id"));
            planName = String.valueOf(selected.get("name"));
            totalRows = ((Number) selected.get("scanRows")).intValue();
            currentRow = 0;
            progress = 0;
            captureCount = 0;
            fileSequence.set(0);
            startedAt = System.currentTimeMillis();
            endedAt = 0L;
            lastError = null;
            warning = null;
            capturePrefix = "patrol_" + LocalDateTime.now().format(FILE_TIME);
            activeFuture = patrolExecutor.submit(new Runnable() {
                @Override
                public void run() {
                    executePatrol();
                }
            });
            return status();
        }
    }

    public Map<String, Object> stop() {
        cancelRequested = true;
        if (running && !isTerminalState()) {
            state = "STOPPING";
            phase = "正在停止全部设备";
        }
        // Always issue stop commands, even when the in-memory patrol state is
        // IDLE. The physical controller may still be moving after a device or
        // application restart, and the emergency stop must remain effective.
        emergencyStop();
        return status();
    }

    public Map<String, Object> status() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("running", running);
        result.put("mqttConnected", mqttService.isConnected());
        result.put("state", state);
        result.put("phase", phase);
        result.put("planId", planId);
        result.put("planName", planName);
        result.put("progress", progress);
        result.put("currentRow", currentRow);
        result.put("totalRows", totalRows);
        result.put("captureCount", captureCount);
        Map<String, Object> camera = localCameraStreamService.status();
        result.put("quality", camera.get("quality"));
        result.put("actualWidth", camera.get("actualWidth"));
        result.put("actualHeight", camera.get("actualHeight"));
        result.put("qualityVerified", camera.get("qualityVerified"));
        result.put("capturesPerLine", 3);
        result.put("scanPattern", "serpentine");
        // Keep the API display portable; filesystem operations still use the
        // normalized absolute path returned by outputDirectory().
        result.put("outputPath", configuredOutputPath());
        result.put("horizontalCalibrationMs", horizontalTravelMs);
        result.put("verticalCalibrationMs", verticalTravelMs);
        result.put("workingHorizontalMs", workingHorizontalMs());
        result.put("workingVerticalMs", workingVerticalMs());
        result.put("coveragePercent", Math.round(safeCoverageRatio() * 100.0));
        // Rail soft-limit telemetry: the left end is capped at
        // patrol.automatic.horizontal-coverage-ratio of the calibrated travel.
        Map<String, Object> rail = railPositionService.status();
        result.put("rail", rail);
        result.put("railLeftLimitPercent", rail.get("safeRatioPercent"));
        result.put("railAtLeftLimit", rail.get("atLeftLimit"));
        result.put("startedAt", startedAt == 0L ? null : startedAt);
        result.put("endedAt", endedAt == 0L ? null : endedAt);
        result.put("lastError", lastError);
        result.put("warning", warning);
        return result;
    }

    private void executePatrol() {
        try {
            updateState("PREFLIGHT", "检查视频、MQTT和控制面板", 2);
            preflight();
            checkCancelled();
            long horizontalStepMs = totalRows <= 1 ? 0L
                    : Math.max(250L, workingHorizontalMs() / (totalRows - 1));
            long verticalMs = workingVerticalMs();
            long verticalMidMs = Math.max(250L, verticalMs / 2L);
            long verticalTopMs = Math.max(250L, verticalMs - verticalMidMs);

            boolean endedAtTop = false;
            for (int row = 0; row < totalRows; row++) {
                checkCancelled();
                currentRow = row + 1;
                boolean evenColumn = (row % 2 == 0);
                if (row > 0) {
                    updateState("SHIFTING", "向左步进到第" + currentRow + "条扫描线", scanProgress(row, 0));
                    moveHorizontal("left", horizontalStepMs);
                    waitInterruptibly(settleMs);
                }

                if (evenColumn) {
                    updateState("CAPTURING", "第" + currentRow + "条扫描线底部抓拍", scanProgress(row, 8));
                    captureFrame(row + 1, "bottom");
                    checkCancelled();
                    updateState("SCANNING", "第" + currentRow + "条扫描线上移至中点", scanProgress(row, 35));
                    moveVertical("forward", verticalMidMs);
                    waitInterruptibly(settleMs);
                    updateState("CAPTURING", "第" + currentRow + "条扫描线中点抓拍", scanProgress(row, 48));
                    captureFrame(row + 1, "middle");
                    checkCancelled();
                    updateState("SCANNING", "第" + currentRow + "条扫描线上移至顶部", scanProgress(row, 70));
                    moveVertical("forward", verticalTopMs);
                    waitInterruptibly(settleMs);
                    updateState("CAPTURING", "第" + currentRow + "条扫描线顶部抓拍", scanProgress(row, 82));
                    captureFrame(row + 1, "top");
                    endedAtTop = true;
                } else {
                    updateState("CAPTURING", "第" + currentRow + "条扫描线顶部抓拍", scanProgress(row, 8));
                    captureFrame(row + 1, "top");
                    checkCancelled();
                    updateState("SCANNING", "第" + currentRow + "条扫描线下移至中点", scanProgress(row, 35));
                    moveVertical("backward", verticalTopMs);
                    waitInterruptibly(settleMs);
                    updateState("CAPTURING", "第" + currentRow + "条扫描线中点抓拍", scanProgress(row, 48));
                    captureFrame(row + 1, "middle");
                    checkCancelled();
                    updateState("SCANNING", "第" + currentRow + "条扫描线下移至底部", scanProgress(row, 70));
                    moveVertical("backward", verticalMidMs);
                    waitInterruptibly(settleMs);
                    updateState("CAPTURING", "第" + currentRow + "条扫描线底部抓拍", scanProgress(row, 82));
                    captureFrame(row + 1, "bottom");
                    endedAtTop = false;
                }
                progress = scanProgress(row, 100);
            }
            if (endedAtTop) {
                updateState("SCANNING", "巡检结束，下移返回底部安全高度", 97);
                moveVertical("backward", verticalMs);
                waitInterruptibly(settleMs);
            }
            updateState("COMPLETED", "巡检完成，当前位置已回到底部安全高度", 100);
        } catch (PatrolCancelledException e) {
            state = "CANCELLED";
            phase = "巡检已停止，当前位置需要重新确认";
        } catch (Exception e) {
            lastError = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            state = "FAILED";
            phase = "巡检异常，已执行紧急停止";
            log.error("Automatic patrol failed", e);
        } finally {
            running = false;
            emergencyStop();
            endedAt = System.currentTimeMillis();
        }
    }

    private void preflight() throws Exception {
        if (!mqttService.isConnected()) {
            throw new IllegalStateException("MQTT网关未连接");
        }
        if (!awaitControlPanel()) {
            throw new IllegalStateException("蓝牙控制面板未连接");
        }
        Path captureDirectory = outputDirectory();
        try {
            Files.createDirectories(captureDirectory);
        } catch (Exception e) {
            throw new IllegalStateException("巡检照片目录无法创建：" + captureDirectory
                    + "；请将 PATROL_AUTO_OUTPUT_PATH 设置为项目内相对路径，例如"
                    + " .\\LabelImg资料图片\\摄像头实际拍摄照片", e);
        }
        localCameraStreamService.changeQuality("4k");
        if (!localCameraStreamService.awaitReady(60000L)) {
            throw new IllegalStateException("4K本地视频流未就绪");
        }
        Map<String, Object> camera = localCameraStreamService.verifyCurrentQuality();
        if (!Boolean.TRUE.equals(camera.get("qualityVerified"))
                || !Integer.valueOf(3840).equals(camera.get("actualWidth"))
                || !Integer.valueOf(2160).equals(camera.get("actualHeight"))) {
            throw new IllegalStateException("4K真实分辨率校验失败：当前 "
                    + camera.get("actualWidth") + "x" + camera.get("actualHeight"));
        }
        updateState("READY", "4K真实分辨率与控制设备已就绪", 5);
    }

    private boolean awaitControlPanel() {
        for (int attempt = 0; attempt < 2; attempt++) {
            checkCancelled();
            try {
                Map<String, Object> panel = controlPanelService.connectionStatus();
                if (Boolean.TRUE.equals(panel.get("reachable"))) return true;
            } catch (RuntimeException ignored) {
            }
            if (attempt < 1) waitInterruptibly(400L);
        }
        return false;
    }

    private boolean isTerminalState() {
        return "COMPLETED".equals(state) || "FAILED".equals(state) || "CANCELLED".equals(state);
    }

    private void captureFrame(int row, String point) {
        checkCancelled();
        int sequence = fileSequence.incrementAndGet();
        Path file = outputDirectory().resolve(capturePrefix + "_line" + String.format("%02d", row)
                + "_" + point + "_" + String.format("%06d", sequence) + ".jpg");
        localCameraStreamService.captureLatestFrame(file);
        captureCount++;
        warning = null;
    }

    private void moveHorizontal(String direction, long durationMs) {
        checkCancelled();
        // Never let a timed step cross the leftward soft limit. The right end
        // stays fully reachable, so only left travel is trimmed here.
        long allowedMs = railPositionService.clampHorizontalDuration(direction, durationMs);
        if ("left".equals(direction) && allowedMs <= 0L) {
            throw new IllegalStateException("已达到左侧 80% 软限位，无法继续向左巡检");
        }
        // start() accepted the operator's right-bottom origin confirmation;
        // authorize only this bounded movement step.
        String response = patrolService.control(direction, true);
        if (response == null) {
            throw new IllegalStateException("轨道" + directionLabel(direction) + "指令未收到响应");
        }
        long deadline = System.currentTimeMillis() + allowedMs;
        try {
            waitUntil(deadline);
        } finally {
            patrolService.control("stop");
        }
    }

    private void moveVertical(String direction, long durationMs) {
        checkCancelled();
        long deadline = System.currentTimeMillis() + durationMs;
        controlPanelService.move(direction, true);
        try {
            waitUntil(deadline);
        } finally {
            try {
                controlPanelService.move(direction, false);
            } catch (RuntimeException e) {
                warning = "纵向停止指令失败：" + e.getMessage();
            }
        }
    }


    private void emergencyStop() {
        List<Future<?>> stops = new ArrayList<>();
        stops.add(stopExecutor.submit(new Runnable() {
            @Override public void run() { patrolService.control("stop"); }
        }));
        stops.add(stopExecutor.submit(new Runnable() {
            @Override public void run() {
                try { controlPanelService.move("forward", false); } catch (RuntimeException ignored) { }
                try { controlPanelService.move("backward", false); } catch (RuntimeException ignored) { }
            }
        }));
        stops.add(stopExecutor.submit(new Runnable() {
            @Override public void run() {
                for (int direction = 0; direction <= 3; direction++) {
                    try { ezvizService.stopPtz(ysjProperties.getDeviceSerial(), ysjProperties.getChannelNo(), direction); }
                    catch (RuntimeException ignored) { }
                }
            }
        }));
        for (Future<?> stop : stops) {
            try { stop.get(6, TimeUnit.SECONDS); }
            catch (Exception e) { stop.cancel(true); }
        }
    }

    private void waitUntil(long deadline) {
        while (System.currentTimeMillis() < deadline) {
            checkCancelled();
            waitInterruptibly(Math.min(100L, deadline - System.currentTimeMillis()));
        }
    }

    private void waitInterruptibly(long durationMs) {
        long deadline = System.currentTimeMillis() + Math.max(0L, durationMs);
        while (System.currentTimeMillis() < deadline) {
            checkCancelled();
            try {
                Thread.sleep(Math.min(100L, Math.max(1L, deadline - System.currentTimeMillis())));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new PatrolCancelledException();
            }
        }
    }

    private void checkCancelled() {
        if (cancelRequested || Thread.currentThread().isInterrupted()) {
            throw new PatrolCancelledException();
        }
    }

    private void updateState(String nextState, String nextPhase, int nextProgress) {
        state = nextState;
        phase = nextPhase;
        progress = Math.max(progress, Math.min(100, nextProgress));
    }

    private int scanProgress(int row, int withinRowPercent) {
        double completed = row + Math.max(0, Math.min(100, withinRowPercent)) / 100.0;
        return 8 + (int) Math.round(completed / Math.max(1, totalRows) * 82.0);
    }

    private long workingHorizontalMs() {
        return Math.max(1000L, Math.round(horizontalTravelMs * safeHorizontalCoverageRatio()));
    }

    private double safeHorizontalCoverageRatio() {
        return Math.max(0.50, Math.min(0.97, horizontalCoverageRatio));
    }

    private long workingVerticalMs() {
        return Math.max(1000L, Math.round(verticalTravelMs * safeCoverageRatio()));
    }

    private double safeCoverageRatio() {
        return Math.max(0.50, Math.min(0.97, coverageRatio));
    }

    private Path outputDirectory() {
        String configured = outputPath == null || outputPath.trim().isEmpty()
                ? "./LabelImg资料图片/摄像头实际拍摄照片" : outputPath.trim();
        Path configuredDirectory = Paths.get(configured).toAbsolutePath().normalize();
        Path portableDirectory = Paths.get("./LabelImg资料图片/摄像头实际拍摄照片")
                .toAbsolutePath().normalize();
        if (Paths.get(configured).isAbsolute()
                && !Files.exists(configuredDirectory.getParent())
                && Files.exists(portableDirectory.getParent())) {
            if (!outputPathFallbackLogged) {
                log.warn("Configured patrol output path does not exist; using project-relative path {}",
                        portableDirectory);
                outputPathFallbackLogged = true;
            }
            return portableDirectory;
        }
        return configuredDirectory;
    }

    private String configuredOutputPath() {
        return outputPath == null || outputPath.trim().isEmpty()
                ? "./LabelImg资料图片/摄像头实际拍摄照片" : outputPath.trim();
    }

    private String directionLabel(String direction) {
        return "left".equals(direction) ? "左移" : "右移";
    }

    private Map<String, Object> findPlan(String requestedPlanId) {
        String id = requestedPlanId == null || requestedPlanId.trim().isEmpty() ? "standard" : requestedPlanId.trim();
        for (Map<String, Object> candidate : plans()) {
            if (id.equals(candidate.get("id"))) return candidate;
        }
        throw new IllegalArgumentException("自动巡检方案不存在");
    }

    private Map<String, Object> plan(String id, String name, String description, int scanRows) {
        Map<String, Object> plan = new LinkedHashMap<>();
        plan.put("id", id);
        plan.put("name", name);
        plan.put("description", description);
        plan.put("scanRows", scanRows);
        plan.put("quality", "4K");
        plan.put("capturesPerLine", 3);
        plan.put("scanPattern", "serpentine");
        return plan;
    }

    private static ThreadFactory daemonFactory(final String name) {
        return new ThreadFactory() {
            private final AtomicInteger number = new AtomicInteger();
            @Override
            public Thread newThread(Runnable runnable) {
                Thread thread = new Thread(runnable, name + "-" + number.incrementAndGet());
                thread.setDaemon(true);
                return thread;
            }
        };
    }

    @PreDestroy
    public void shutdown() {
        cancelRequested = true;
        emergencyStop();
        patrolExecutor.shutdownNow();
        stopExecutor.shutdownNow();
    }

    private static final class PatrolCancelledException extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }
}
