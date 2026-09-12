package com.jhds.service;


import com.jhds.config.YsjProperties;
import com.jhds.service.mqtt.MqttService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PreDestroy;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
public class AutomaticPatrolService {

    private static final DateTimeFormatter FILE_TIME = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
    private static final Pattern CAPTURE_FILE = Pattern.compile(
            "^(.+)_line(\\d+)_(bottom|middle|top)_f\\d+_\\d+\\.jpg$", Pattern.CASE_INSENSITIVE);

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
    @Autowired
    private PatrolResultAnalyzer patrolResultAnalyzer;


    @Value("${patrol.automatic.output-path:./LabelImg资料图片/摄像头实际拍摄照片}")
    private String outputPath;
    @Value("${patrol.automatic.horizontal-travel-ms:18422}")
    private long horizontalTravelMs;
    @Value("${patrol.automatic.vertical-travel-ms:11587}")
    private long verticalTravelMs;
    @Value("${patrol.automatic.coverage-ratio:0.92}")
    private double coverageRatio;
    @Value("${patrol.automatic.horizontal-coverage-ratio:0.60}")
    private double horizontalCoverageRatio;
    @Value("${patrol.automatic.settle-ms:350}")
    private long settleMs;
    @Value("${patrol.automatic.focus-wait-ms:2000}")
    private long focusWaitMs;
    @Value("${patrol.automatic.foliar-spray-ms:1500}")
    private long foliarSprayMs;
    @Value("${patrol.automatic.burst-frames:5}")
    private int burstFrames;
    @Value("${patrol.automatic.burst-interval-ms:450}")
    private long burstIntervalMs;

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
    private volatile int sprayCount;
    private volatile boolean foliarPumpActive;
    private volatile long startedAt;
    private volatile long endedAt;
    private volatile String lastError;
    private volatile String warning;
    private volatile String capturePrefix;
    private volatile boolean outputPathFallbackLogged;
    private volatile String previewQualityBeforePatrol = "hd";
    private final List<PatrolCaptureGroup> captureGroups = new ArrayList<>();
    private volatile List<Map<String, Object>> analysisResults = Collections.emptyList();
    private volatile String analysisState = "IDLE";
    private volatile int analysisProgress;

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
        if (railPositionService.positionMs() > 0L) {
            throw new IllegalStateException("轨道位置记录显示当前不在最右端；请手动右移到机械原点，再点击轨道电机卡片上的重置按钮");
        }
        // The operator just confirmed the rail sits on the rightmost origin,
        // so re-zero the dead-reckoned soft-limit position before this run.
        patrolService.markRightOrigin();
        Map<String, Object> selected = findPlan(requestedPlanId);
        synchronized (stateLock) {
            if (running || "ANALYZING".equals(state)) {
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
            sprayCount = 0;
            foliarPumpActive = false;
            fileSequence.set(0);
            startedAt = System.currentTimeMillis();
            endedAt = 0L;
            lastError = null;
            warning = null;
            captureGroups.clear();
            analysisResults = Collections.emptyList();
            analysisState = "PENDING";
            analysisProgress = 0;
            capturePrefix = "patrol_" + LocalDateTime.now().format(FILE_TIME);
            previewQualityBeforePatrol = currentPreviewQuality();
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

    /** Re-runs image analysis only; it never sends a hardware command. */
    public Map<String, Object> retryLatestAnalysis() {
        final RecoveredCaptureBatch batch;
        synchronized (stateLock) {
            if (running || "ANALYZING".equals(state)) {
                throw new IllegalStateException("巡检或照片分析正在进行");
            }
            batch = captureGroups.isEmpty()
                    ? recoverLatestCaptureBatch() : new RecoveredCaptureBatch(
                            capturePrefix, new ArrayList<>(captureGroups));
            if (batch.groups.isEmpty()) {
                throw new IllegalStateException("没有可重新分析的巡检照片");
            }
            capturePrefix = batch.prefix;
            captureCount = countFrames(batch.groups);
            warning = null;
            state = "ANALYZING";
            phase = "正在重新分析最近一次巡检照片";
            analysisState = "ANALYZING";
            analysisProgress = 0;
            analysisResults = Collections.emptyList();
            activeFuture = patrolExecutor.submit(new Runnable() {
                @Override public void run() {
                    analyzeCaptureBatch(batch.prefix, batch.groups);
                }
            });
            return status();
        }
    }

    public Map<String, Object> status() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("running", running);
        result.put("mqttConnected", mqttService.isConnected());
        result.put("commandTransportAvailable", mqttService.hasAvailableTransport());
        result.put("state", state);
        result.put("phase", phase);
        result.put("planId", planId);
        result.put("planName", planName);
        result.put("progress", progress);
        result.put("currentRow", currentRow);
        result.put("totalRows", totalRows);
        result.put("captureCount", captureCount);
        result.put("burstFrames", safeBurstFrames());
        result.put("sprayCount", sprayCount);
        result.put("foliarPumpActive", foliarPumpActive);
        result.put("foliarSprayMs", Math.max(0L, foliarSprayMs));
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
        result.put("focusWaitMs", Math.max(0L, settleMs) + Math.max(0L, focusWaitMs));
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
        result.put("analysisState", analysisState);
        result.put("analysisProgress", analysisProgress);
        result.put("analysisResults", new ArrayList<>(analysisResults));
        return result;
    }

    private void executePatrol() {
        boolean completed = false;
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
                }

                if (evenColumn) {
                    focusAndCapture(row + 1, "bottom", "底部", scanProgress(row, 8));
                    checkCancelled();
                    updateState("SCANNING", "第" + currentRow + "条扫描线上移至中点", scanProgress(row, 35));
                    moveVertical("forward", verticalMidMs);
                    focusAndCapture(row + 1, "middle", "中点", scanProgress(row, 48));
                    sprayFoliarAtMiddle(row + 1, scanProgress(row, 58));
                    checkCancelled();
                    updateState("SCANNING", "第" + currentRow + "条扫描线上移至顶部", scanProgress(row, 70));
                    moveVertical("forward", verticalTopMs);
                    focusAndCapture(row + 1, "top", "顶部", scanProgress(row, 82));
                    endedAtTop = true;
                } else {
                    focusAndCapture(row + 1, "top", "顶部", scanProgress(row, 8));
                    checkCancelled();
                    updateState("SCANNING", "第" + currentRow + "条扫描线下移至中点", scanProgress(row, 35));
                    moveVertical("backward", verticalTopMs);
                    focusAndCapture(row + 1, "middle", "中点", scanProgress(row, 48));
                    sprayFoliarAtMiddle(row + 1, scanProgress(row, 58));
                    checkCancelled();
                    updateState("SCANNING", "第" + currentRow + "条扫描线下移至底部", scanProgress(row, 70));
                    moveVertical("backward", verticalMidMs);
                    focusAndCapture(row + 1, "bottom", "底部", scanProgress(row, 82));
                    endedAtTop = false;
                }
                progress = scanProgress(row, 100);
            }
            returnToRightBottomOrigin(endedAtTop, verticalMs);
            updateState("FINALIZING", "设备已回到最下最右初始位置，正在完成巡检", 99);
            completed = true;
        } catch (PatrolCancelledException e) {
            state = "CANCELLED";
            phase = "巡检已停止，当前位置需要重新确认";
        } catch (Exception e) {
            lastError = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            state = "FAILED";
            phase = "巡检异常，已执行紧急停止";
            log.error("Automatic patrol failed", e);
        } finally {
            emergencyStop();
            restorePreviewQuality();
            endedAt = System.currentTimeMillis();
            running = false;
        }
        if (completed) {
            analyzeCompletedPatrol();
        }
    }

    private void analyzeCompletedPatrol() {
        analyzeCaptureBatch(capturePrefix, new ArrayList<>(captureGroups));
    }

    private void analyzeCaptureBatch(String prefix, List<PatrolCaptureGroup> completedCaptures) {
        state = "ANALYZING";
        phase = "设备已安全停止，正在多帧复核巡检照片";
        progress = 100;
        analysisState = "ANALYZING";
        analysisProgress = 0;
        try {
            analysisResults = patrolResultAnalyzer.analyze(prefix, completedCaptures,
                    value -> analysisProgress = Math.max(0, Math.min(100, value)));
            analysisState = "COMPLETED";
            analysisProgress = 100;
            phase = analysisResults.isEmpty()
                    ? "巡检完成，多帧复核未发现明确虫害"
                    : "巡检完成，多帧复核发现 " + analysisResults.size() + " 处虫害";
        } catch (Exception e) {
            analysisState = "FAILED";
            warning = "巡检已安全完成，但照片分析失败：" + e.getMessage();
            phase = "巡检完成，照片分析暂不可用";
            log.error("Post-patrol image analysis failed", e);
        } finally {
            state = "COMPLETED";
        }
    }

    private RecoveredCaptureBatch recoverLatestCaptureBatch() {
        Path directory = outputDirectory();
        if (!Files.isDirectory(directory)) {
            throw new IllegalStateException("巡检照片目录不存在");
        }
        String latestPrefix = null;
        long latestModified = Long.MIN_VALUE;
        try (DirectoryStream<Path> files = Files.newDirectoryStream(directory, "*.jpg")) {
            for (Path file : files) {
                Matcher matcher = CAPTURE_FILE.matcher(file.getFileName().toString());
                if (!matcher.matches()) continue;
                long modified = Files.getLastModifiedTime(file).toMillis();
                if (modified > latestModified) {
                    latestModified = modified;
                    latestPrefix = matcher.group(1);
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("读取巡检照片失败: " + e.getMessage(), e);
        }
        if (latestPrefix == null) {
            throw new IllegalStateException("没有找到最近一次巡检照片");
        }

        Map<String, PatrolCaptureGroup> grouped = new LinkedHashMap<>();
        try (DirectoryStream<Path> files = Files.newDirectoryStream(directory, latestPrefix + "_*.jpg")) {
            for (Path file : files) {
                Matcher matcher = CAPTURE_FILE.matcher(file.getFileName().toString());
                if (!matcher.matches() || !latestPrefix.equals(matcher.group(1))) continue;
                int row = Integer.parseInt(matcher.group(2));
                String point = matcher.group(3).toLowerCase();
                String key = row + ":" + point;
                PatrolCaptureGroup group = grouped.get(key);
                if (group == null) {
                    group = new PatrolCaptureGroup(row, point, pointLabel(point));
                    grouped.put(key, group);
                }
                group.addFrame(file);
            }
        } catch (IOException e) {
            throw new IllegalStateException("读取巡检照片失败: " + e.getMessage(), e);
        }
        List<PatrolCaptureGroup> groups = new ArrayList<>(grouped.values());
        for (PatrolCaptureGroup group : groups) group.sortFrames();
        groups.sort((left, right) -> {
            int rowOrder = Integer.compare(left.getRow(), right.getRow());
            return rowOrder != 0 ? rowOrder
                    : Integer.compare(pointOrder(left.getPoint()), pointOrder(right.getPoint()));
        });
        return new RecoveredCaptureBatch(latestPrefix, groups);
    }

    private int countFrames(List<PatrolCaptureGroup> groups) {
        int count = 0;
        for (PatrolCaptureGroup group : groups) count += group.getFrames().size();
        return count;
    }

    private String pointLabel(String point) {
        return "bottom".equals(point) ? "底部" : "middle".equals(point) ? "中点" : "顶部";
    }

    private int pointOrder(String point) {
        return "bottom".equals(point) ? 0 : "middle".equals(point) ? 1 : 2;
    }

    private String currentPreviewQuality() {
        Object value = localCameraStreamService.status().get("quality");
        String quality = value == null ? "hd" : String.valueOf(value).toLowerCase();
        return "smooth".equals(quality) || "4k".equals(quality) ? quality : "hd";
    }

    private void restorePreviewQuality() {
        try {
            localCameraStreamService.changeQuality(previewQualityBeforePatrol);
        } catch (RuntimeException e) {
            String message = "巡检结束后恢复预览画质失败：" + e.getMessage();
            if (warning == null || warning.trim().isEmpty()) warning = message;
            log.warn(message, e);
        }
    }

    private void sprayFoliarAtMiddle(int row, int nextProgress) {
        checkCancelled();
        updateState("SPRAYING", "第" + row + "条扫描线中点喷淋叶面肥 1.5 秒", nextProgress);
        controlPanelService.pump(true);
        foliarPumpActive = true;
        RuntimeException closeFailure = null;
        try {
            waitInterruptibly(Math.max(0L, foliarSprayMs));
        } finally {
            try {
                controlPanelService.pump(false);
            } catch (RuntimeException e) {
                closeFailure = e;
                warning = "叶面肥关闭指令失败：" + e.getMessage();
            } finally {
                foliarPumpActive = false;
            }
        }
        if (closeFailure != null) {
            throw new IllegalStateException("叶面肥喷淋后未能确认关闭", closeFailure);
        }
        sprayCount++;
        updateState("SPRAYING", "第" + row + "条扫描线中点喷淋完成，叶面肥已关闭", nextProgress);
    }

    private void returnToRightBottomOrigin(boolean endedAtTop, long verticalMs) {
        if (endedAtTop) {
            updateState("RETURNING", "巡检结束，正在下移返回底部", 94);
            moveVertical("backward", verticalMs);
            waitInterruptibly(settleMs);
        }
        checkCancelled();
        long returnRightMs = railPositionService.remainingRightMs();
        if (returnRightMs > 0L) {
            updateState("RETURNING", "已回到底部，正在右移返回初始位置", 97);
            moveHorizontal("right", returnRightMs);
            waitInterruptibly(settleMs);
        }
        patrolService.markRightOrigin();
    }

    private void preflight() throws Exception {
        if (!mqttService.hasAvailableTransport()) {
            throw new IllegalStateException("MQTT与Modbus备用链路均未连接");
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
        preparePatrolCamera();
    }

    private void preparePatrolCamera() {
        String fourKFailure;
        try {
            localCameraStreamService.changeQuality("4k");
            if (!localCameraStreamService.awaitReady(60000L)) {
                throw new IllegalStateException("4K本地视频流未就绪");
            }
            Map<String, Object> camera = localCameraStreamService.verifyCurrentQuality();
            if (!Boolean.TRUE.equals(camera.get("qualityVerified"))
                    || !Integer.valueOf(3840).equals(camera.get("actualWidth"))
                    || !Integer.valueOf(2160).equals(camera.get("actualHeight"))) {
                throw new IllegalStateException("4K真实分辨率校验失败：当前 " + resolution(camera));
            }
            updateState("READY", "4K真实分辨率与控制设备已就绪", 5);
            return;
        } catch (RuntimeException e) {
            fourKFailure = e.getMessage();
            log.warn("4K patrol stream unavailable; falling back to verified HD: {}", fourKFailure);
        }

        localCameraStreamService.changeQuality("hd");
        if (!localCameraStreamService.awaitReady(30000L)) {
            throw new IllegalStateException("4K不可用，高清备用视频流也未就绪");
        }
        Map<String, Object> camera = localCameraStreamService.verifyCurrentQuality();
        if (!Boolean.TRUE.equals(camera.get("qualityVerified"))
                || integerValue(camera.get("actualHeight")) < 720) {
            throw new IllegalStateException("高清备用视频流校验失败：当前 " + resolution(camera));
        }
        warning = "摄像头未提供真实4K，已自动使用 " + resolution(camera) + " 高清画面继续巡检";
        updateState("READY", resolution(camera) + " 高清画面与控制设备已就绪", 5);
    }

    private String resolution(Map<String, Object> camera) {
        Object width = camera.get("actualWidth");
        Object height = camera.get("actualHeight");
        return (width == null ? "--" : width) + "x" + (height == null ? "--" : height);
    }

    private int integerValue(Object value) {
        return value instanceof Number ? ((Number) value).intValue() : 0;
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

    private void focusAndCapture(int row, String point, String pointLabel, int nextProgress) {
        checkCancelled();
        updateState("STOPPING_FOR_CAPTURE", "第" + row + "条扫描线" + pointLabel
                + "正在停止全部电机", nextProgress);
        stopMotionForCapture();
        checkCancelled();
        updateState("FOCUSING", "第" + row + "条扫描线" + pointLabel
                + "已停止移动，等待画面稳定与自动对焦", nextProgress);
        waitInterruptibly(Math.max(0L, settleMs) + Math.max(0L, focusWaitMs));
        checkCancelled();

        // The browser stream trails the physical camera. Requiring a segment
        // published after the focus wait prevents saving a stale moving frame.
        long focusedFrameAfter = System.currentTimeMillis();
        updateState("CAPTURING", "第" + row + "条扫描线" + pointLabel
                + "对焦完成，正在抓拍", nextProgress);
        PatrolCaptureGroup group = new PatrolCaptureGroup(row, point, pointLabel);
        for (int frame = 0; frame < safeBurstFrames(); frame++) {
            checkCancelled();
            if (frame > 0) {
                focusedFrameAfter = System.currentTimeMillis();
                waitInterruptibly(Math.max(0L, burstIntervalMs));
            }
            group.addFrame(captureFrame(row, point, frame + 1, focusedFrameAfter));
        }
        captureGroups.add(group);
    }

    private void stopMotionForCapture() {
        String railResponse = null;
        for (int attempt = 0; attempt < 2 && railResponse == null; attempt++) {
            railResponse = patrolService.control("stop");
            if (railResponse == null && attempt == 0) {
                waitInterruptibly(150L);
            }
        }
        if (railResponse == null) {
            throw new IllegalStateException("轨道停止指令重试后仍未收到响应，已取消抓拍");
        }
        try {
            controlPanelService.stopMotion();
        } catch (RuntimeException e) {
            throw new IllegalStateException("升降电机停止确认失败，已取消抓拍", e);
        }
    }

    private Path captureFrame(int row, String point, int burstIndex, long notBeforeEpochMs) {
        checkCancelled();
        int sequence = fileSequence.incrementAndGet();
        Path file = outputDirectory().resolve(capturePrefix + "_line" + String.format("%02d", row)
                + "_" + point + "_f" + String.format("%02d", burstIndex)
                + "_" + String.format("%06d", sequence) + ".jpg");
        localCameraStreamService.captureLatestFrameAfter(file, notBeforeEpochMs);
        captureCount++;
        warning = null;
        return file;
    }

    private int safeBurstFrames() {
        return Math.max(3, Math.min(7, burstFrames));
    }

    private void moveHorizontal(String direction, long durationMs) {
        checkCancelled();
        // Never let a timed step cross the leftward soft limit. The right end
        // stays fully reachable, so only left travel is trimmed here.
        long allowedMs = railPositionService.clampHorizontalDuration(direction, durationMs);
        if ("left".equals(direction) && allowedMs <= 0L) {
            throw new IllegalStateException("已达到左侧 " + Math.round(safeHorizontalCoverageRatio() * 100.0)
                    + "% 软限位，无法继续向左巡检");
        }
        // start() accepted the operator's right-bottom origin confirmation;
        // authorize only this bounded movement step.
        // The direction write can spend time waiting for an MQTT acknowledgement.
        // Count that interval inside this bounded step instead of adding it to
        // the requested travel time.
        long deadline = System.currentTimeMillis() + allowedMs;
        String response = patrolService.control(direction, true);
        if (response == null) {
            throw new IllegalStateException("轨道" + directionLabel(direction) + "指令未收到响应");
        }
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
                try { controlPanelService.pump(false); } catch (RuntimeException ignored) { }
                foliarPumpActive = false;
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

    private static final class RecoveredCaptureBatch {
        private final String prefix;
        private final List<PatrolCaptureGroup> groups;

        private RecoveredCaptureBatch(String prefix, List<PatrolCaptureGroup> groups) {
            this.prefix = prefix;
            this.groups = groups;
        }
    }
}
