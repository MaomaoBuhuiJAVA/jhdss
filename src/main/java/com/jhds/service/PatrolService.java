package com.jhds.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jhds.common.Constants;
import com.jhds.entity.PatrolRecord;
import com.jhds.entity.PatrolTask;
import com.jhds.mapper.PatrolRecordMapper;
import com.jhds.mapper.PatrolTaskMapper;
import com.jhds.service.mqtt.MqttService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
public class PatrolService {

    @Autowired
    private PatrolTaskMapper patrolTaskMapper;
    @Autowired
    private PatrolRecordMapper patrolRecordMapper;
    @Autowired
    private MqttService mqttService;
    @Autowired
    private DashScopeApiService dashScopeApiService;

    @Value("${patrol.capture-path:./captures}")
    private String capturePath;

    private static final int MAX_CAPTURES = 10;

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    private static String removeEmoji(String text) {
        if (text == null) return null;
        return text.codePoints()
                .filter(cp -> cp <= 0xFFFF)
                .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
                .toString().trim();
    }

    private static final String AI_PROMPT = "你是一个轨道巡检AI助手，请分析这张农业大棚巡检图片，从以下几个方面给出判断：\n"
            + "1) 作物生长状态（是否健康、有无缺素症状）\n"
            + "2) 病虫害迹象（叶片是否有病斑、虫洞、变色等）\n"
            + "3) 设备异常（灌溉设备、传感器、轨道等是否正常）\n"
            + "4) 环境异常（温湿度是否异常、光照是否合适等）\n"
            + "请用简洁专业的语言描述，按条目列出。";

    @PostConstruct
    public void init() {
        try {
            Files.createDirectories(Paths.get(capturePath));
        } catch (IOException e) {
            log.error("Failed to create capture directory: {}", capturePath, e);
        }
    }

    public String saveCaptureImage(String imageBase64) {
        try {
            byte[] imageBytes = Base64.getDecoder().decode(imageBase64);
            String fileName = "capture_" + LocalDateTime.now().format(DATE_FORMAT) + "_"
                    + UUID.randomUUID().toString().substring(0, 8) + ".jpg";
            Path filePath = Paths.get(capturePath, fileName);
            try (FileOutputStream fos = new FileOutputStream(filePath.toFile())) {
                fos.write(imageBytes);
            }
            log.info("Saved capture image: {}", filePath);
            cleanupOldCaptures();
            return "/jhds/captures/" + fileName;
        } catch (Exception e) {
            log.error("Failed to save capture image", e);
            return null;
        }
    }

    private void cleanupOldCaptures() {
        File dir = new File(capturePath);
        File[] files = dir.listFiles((d, name) -> name.startsWith("capture_") && name.endsWith(".jpg"));
        if (files == null || files.length <= MAX_CAPTURES) return;
        Arrays.sort(files, (a, b) -> Long.compare(a.lastModified(), b.lastModified()));
        for (int i = 0; i < files.length - MAX_CAPTURES; i++) {
            if (files[i].delete()) {
                log.info("Deleted old capture: {}", files[i].getName());
            }
        }
    }

    public void startAiAnalysis(PatrolRecord record, String imageBase64,
                                java.util.function.Consumer<String> onChunk,
                                Runnable onDone,
                                java.util.function.Consumer<Exception> onError) {
        com.alibaba.fastjson.JSONObject userMsg = new com.alibaba.fastjson.JSONObject();
        userMsg.put("role", "user");
        userMsg.put("content", "请分析这张巡检图片，给出专业判断。");
        if (imageBase64 != null && !imageBase64.isEmpty()) {
            userMsg.put("images", new String[]{imageBase64});
        }

        java.util.List<com.alibaba.fastjson.JSONObject> messages = new java.util.ArrayList<>();
        com.alibaba.fastjson.JSONObject systemMsg = new com.alibaba.fastjson.JSONObject();
        systemMsg.put("role", "system");
        systemMsg.put("content", AI_PROMPT);
        messages.add(systemMsg);
        messages.add(userMsg);

        record.setAiStatus(Constants.AiStatus.ANALYZING);
        patrolRecordMapper.updateById(record);

        StringBuilder fullResult = new StringBuilder();

        dashScopeApiService.streamChat(messages,
            chunk -> {
                fullResult.append(chunk);
                onChunk.accept(chunk);
            },
            () -> {
                record.setAiResult(removeEmoji(fullResult.toString()));
                record.setAiStatus(Constants.AiStatus.COMPLETED);
                patrolRecordMapper.updateById(record);
                onDone.run();
            },
            error -> {
                record.setAiResult(removeEmoji("分析失败: " + error.getMessage()));
                record.setAiStatus(Constants.AiStatus.FAILED);
                patrolRecordMapper.updateById(record);
                onError.accept(error);
            }
        );
    }

    public String control(String dir) {
        String alias;
        String value;
        switch (dir) {
            case "left":
                alias = "MOTOR_DIRECTION";
                value = "open";
                break;
            case "right":
                alias = "MOTOR_DIRECTION";
                value = "close";
                break;
            case "stop":
                alias = "MOTOR_STATE";
                value = "close";
                break;
            default:
                log.warn("Unknown patrol direction: {}", dir);
                return null;
        }
        String response = mqttService.sendCommand(alias, value, false);
        if (response == null || !"MOTOR_DIRECTION".equals(alias)) {
            return response;
        }

        // Some controller revisions require a separate run coil after the
        // direction coil is selected. Only send it when MOTOR_STATE.open_code
        // (or MOTOR_STATE_OPEN_HEX) is actually configured; the JinHua sheet
        // uses a single direction frame, so an absent run frame remains valid.
        String runResponse = mqttService.sendCommand("MOTOR_STATE", "open", false);
        return runResponse == null ? response : runResponse;
    }

    public void addTask(PatrolTask task) {
        task.setStatus(Constants.TaskStatus.PENDING);
        patrolTaskMapper.insert(task);
    }

    public List<PatrolTask> getTasks() {
        return patrolTaskMapper.selectList(
                new LambdaQueryWrapper<PatrolTask>()
                        .orderByAsc(PatrolTask::getExecuteTime));
    }

    public void deleteTask(Long id) {
        patrolTaskMapper.deleteById(id);
    }

    public void saveRecord(PatrolRecord record) {
        patrolRecordMapper.insert(record);
    }

    public List<PatrolRecord> getRecords() {
        return patrolRecordMapper.selectList(
                new LambdaQueryWrapper<PatrolRecord>()
                        .orderByDesc(PatrolRecord::getShootTime)
                        .last("LIMIT 20"));
    }

    public void executePendingTasks() {
        if (!mqttService.isConnected()) return;
        LocalTime now = LocalTime.now();
        List<PatrolTask> pendingTasks = patrolTaskMapper.selectList(
                new LambdaQueryWrapper<PatrolTask>()
                        .eq(PatrolTask::getStatus, Constants.TaskStatus.PENDING));
        for (PatrolTask task : pendingTasks) {
            LocalTime taskTime = task.getExecuteTime();
            if (taskTime.getHour() == now.getHour() && taskTime.getMinute() == now.getMinute()) {
                task.setStatus(Constants.TaskStatus.RUNNING);
                patrolTaskMapper.updateById(task);
                mqttService.sendCommand("CAM_PATROL", "capture", true);
                task.setStatus(Constants.TaskStatus.COMPLETED);
                patrolTaskMapper.updateById(task);
            }
        }
    }
}
