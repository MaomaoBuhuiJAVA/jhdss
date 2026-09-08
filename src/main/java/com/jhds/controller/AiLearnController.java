package com.jhds.controller;

import com.jhds.common.Result;
import com.jhds.service.AiLearnVideoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.MediaType;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;

import java.util.List;
import java.util.Map;

/**
 * AI 学习模块 - 视频分析结果接口。
 * 卡片内容由数据库中的视频资料和图片目录驱动，视频文件名用于匹配资料组。
 */
@RestController
@RequestMapping("/api/ai-learn")
public class AiLearnController {

    @Autowired
    private AiLearnVideoService aiLearnVideoService;

    @Autowired
    private com.jhds.service.YoloInferenceService yoloInferenceService;

    @GetMapping("/analyze")
    public Result<List<Map<String, Object>>> analyze(
            @RequestParam(value = "videoName", required = false) String videoName) {
        return Result.ok(aiLearnVideoService.analyze(videoName));
    }

    @PostMapping(value = "/detect", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<Map<String, Object>> detect(@RequestParam("file") MultipartFile file,
                                              @RequestParam(value = "confidence", required = false) Double confidence) {
        try {
            return Result.ok(yoloInferenceService.detect(file, confidence));
        } catch (IllegalArgumentException e) {
            return Result.error(400, e.getMessage());
        } catch (IllegalStateException e) {
            return Result.error(503, e.getMessage());
        } catch (IOException e) {
            return Result.error(500, e.getMessage());
        }
    }

    @GetMapping("/evaluation")
    public Result<Map<String, Object>> evaluation() {
        try {
            return Result.ok(yoloInferenceService.evaluation());
        } catch (IOException e) {
            return Result.error(404, e.getMessage());
        }
    }
}
