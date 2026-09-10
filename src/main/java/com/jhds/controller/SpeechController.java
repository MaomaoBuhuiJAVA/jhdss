package com.jhds.controller;

import com.jhds.common.Result;
import com.jhds.service.CameraSpeechBroadcastService;
import com.jhds.service.XfyunTtsService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.concurrent.TimeUnit;

@Api(tags = "讯飞在线语音合成")
@RestController
@RequestMapping("/api/speech")
public class SpeechController {

    @Autowired
    private XfyunTtsService xfyunTtsService;
    @Autowired
    private CameraSpeechBroadcastService cameraSpeechBroadcastService;

    @ApiOperation("获取在线语音合成状态")
    @GetMapping("/status")
    public Result<Map<String, Object>> status() {
        return Result.ok(xfyunTtsService.status());
    }

    @ApiOperation("将巡检播报内容合成为WAV音频")
    @PostMapping(value = "/synthesize", produces = "audio/wav")
    public ResponseEntity<byte[]> synthesize(@RequestBody Map<String, Object> body) {
        String text = body == null || body.get("text") == null ? null : String.valueOf(body.get("text"));
        byte[] wav = xfyunTtsService.synthesize(text);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(1, TimeUnit.HOURS).cachePrivate())
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=patrol-voice.wav")
                .contentType(MediaType.parseMediaType("audio/wav"))
                .contentLength(wav.length)
                .body(wav);
    }

    @ApiOperation("通过摄像头扬声器播放预设语音播报")
    @PostMapping("/broadcast")
    public Result<Void> broadcast(@RequestBody Map<String, Object> body) {
        String text = body == null || body.get("text") == null ? null : String.valueOf(body.get("text"));
        try {
            cameraSpeechBroadcastService.broadcast(text);
            return Result.ok();
        } catch (IllegalArgumentException e) {
            return Result.error(400, e.getMessage());
        } catch (RuntimeException e) {
            return Result.error(502, "摄像头语音播报失败：" + rootMessage(e));
        }
    }

    private String rootMessage(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null) current = current.getCause();
        String message = current.getMessage();
        return message == null || message.trim().isEmpty() ? "未知错误" : message;
    }
}
