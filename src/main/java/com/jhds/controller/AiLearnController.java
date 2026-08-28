package com.jhds.controller;

import com.jhds.common.Result;
import com.jhds.service.AiLearnVideoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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

    @GetMapping("/analyze")
    public Result<List<Map<String, Object>>> analyze(
            @RequestParam(value = "videoName", required = false) String videoName) {
        return Result.ok(aiLearnVideoService.analyze(videoName));
    }
}
