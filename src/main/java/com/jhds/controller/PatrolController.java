package com.jhds.controller;

import com.jhds.common.Constants;
import com.jhds.common.Result;
import com.jhds.entity.PatrolRecord;
import com.jhds.entity.PatrolTask;
import com.jhds.service.PatrolService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Api(tags = "AI轨道巡检模块")
@RestController
@RequestMapping("/api/patrol")
public class PatrolController {

    private static final Logger log = LoggerFactory.getLogger(PatrolController.class);
    private final ExecutorService executor = Executors.newCachedThreadPool();

    @Autowired
    private PatrolService patrolService;

    @ApiOperation("控制巡检方向")
    @PostMapping("/control")
    public Result<String> control(@RequestBody Map<String, String> body) {
        String dir = body.get("dir");
        String result = patrolService.control(dir);
        if (result == null) {
            return Result.error(503, "MQTT未连接，或电机尚未配置有效的十六进制串口指令");
        }
        return Result.ok(result);
    }

    @ApiOperation("新增巡逻任务")
    @PostMapping("/task")
    public Result<Void> addTask(@RequestBody PatrolTask task) {
        patrolService.addTask(task);
        return Result.ok();
    }

    @ApiOperation("获取巡逻任务列表")
    @GetMapping("/tasks")
    public Result<List<PatrolTask>> getTasks() {
        return Result.ok(patrolService.getTasks());
    }

    @ApiOperation("删除巡逻任务")
    @DeleteMapping("/task/{id}")
    public Result<Void> deleteTask(@PathVariable Long id) {
        patrolService.deleteTask(id);
        return Result.ok();
    }

    @ApiOperation("获取拍摄记录")
    @GetMapping("/records")
    public Result<List<PatrolRecord>> getRecords() {
        return Result.ok(patrolService.getRecords());
    }

    @ApiOperation("AI拍照识别 - 截帧分析")
    @PostMapping(value = "/ai-capture", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter aiCapture(@RequestBody Map<String, String> body) {
        String image = body.get("image");
        String trackPosition = body.get("trackPosition");

        SseEmitter emitter = new SseEmitter(120000L);

        String imageUrl = patrolService.saveCaptureImage(image);
        if (imageUrl == null) {
            try {
                emitter.send(SseEmitter.event().name("error").data("图片保存失败"));
                emitter.complete();
            } catch (IOException e) {
                emitter.completeWithError(e);
            }
            return emitter;
        }

        PatrolRecord record = new PatrolRecord();
        record.setImageUrl(imageUrl);
        record.setTrackPosition(trackPosition);
        record.setShootTime(new Date());
        record.setAiStatus(Constants.AiStatus.ANALYZING);
        patrolService.saveRecord(record);

        emitter.onCompletion(() -> log.info("AI capture SSE completed for record {}", record.getId()));
        emitter.onTimeout(() -> log.warn("AI capture SSE timeout for record {}", record.getId()));

        executor.execute(() -> {
            try {
                emitter.send(SseEmitter.event().name("meta").data("{\"recordId\":" + record.getId() + ",\"imageUrl\":\"" + imageUrl + "\"}"));
            } catch (IOException e) {
                log.error("Failed to send meta event", e);
                emitter.completeWithError(e);
                return;
            }

            patrolService.startAiAnalysis(record, image,
                chunk -> {
                    try {
                        emitter.send(SseEmitter.event().name("message").data("{\"text\":" + com.alibaba.fastjson.JSON.toJSONString(chunk) + "}"));
                    } catch (IOException e) {
                        throw new RuntimeException("SSE send failed", e);
                    }
                },
                () -> {
                    try {
                        emitter.send(SseEmitter.event().name("done").data("[DONE]"));
                        emitter.complete();
                    } catch (IOException e) {
                        log.error("Failed to send done event", e);
                    }
                },
                error -> {
                    try {
                        emitter.send(SseEmitter.event().name("error").data("AI分析异常: " + error.getMessage()));
                        emitter.complete();
                    } catch (IOException e) {
                        log.error("Failed to send error event", e);
                    }
                }
            );
        });

        return emitter;
    }
}
