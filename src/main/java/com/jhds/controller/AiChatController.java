package com.jhds.controller;

import com.alibaba.fastjson.JSONObject;
import com.jhds.common.ContextOverflowException;
import com.jhds.common.Result;
import com.jhds.service.AiChatContextService;
import com.jhds.service.AiKnowledgeService;
import com.jhds.service.DashScopeApiService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@RestController
@RequestMapping("/api/ai")
public class AiChatController {

    private static final Logger log = LoggerFactory.getLogger(AiChatController.class);

    private final ExecutorService executor = Executors.newCachedThreadPool();

    @Autowired
    private DashScopeApiService dashScopeApiService;

    @Autowired
    private AiChatContextService contextService;

    @Autowired
    private AiKnowledgeService aiKnowledgeService;

    @GetMapping("/knowledge")
    public Result<String> knowledge(@RequestParam String query) {
        String answer = aiKnowledgeService.findAnswer(query);
        return Result.ok(answer);
    }

    @GetMapping("/knowledge/list")
    public Result<List<Map<String, Object>>> knowledgeList() {
        return Result.ok(aiKnowledgeService.listEntries());
    }

    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamChat(@RequestBody Map<String, String> body, HttpServletRequest request) {
        String sessionId = request.getSession().getId();
        String msg = body.get("msg");
        String image = body.get("image");
        boolean memory = Boolean.parseBoolean(body.getOrDefault("memory", "true"));

        if ((msg == null || msg.trim().isEmpty()) && (image == null || image.isEmpty())) {
            msg = "你好";
        }
        if (msg == null || msg.trim().isEmpty()) {
            msg = "请描述这张图片";
        }

        String finalMsg = msg;
        String finalImage = image;

        SseEmitter emitter = new SseEmitter(120000L);

        executor.execute(() -> {
            try {
                // The editable keyword catalogue is checked before the remote model.
                // This keeps configured agricultural answers deterministic and available
                // even when the external AI service is unavailable.
                if (finalImage == null || finalImage.trim().isEmpty()) {
                    String knowledgeAnswer = aiKnowledgeService.findAnswer(finalMsg);
                    if (knowledgeAnswer != null && !knowledgeAnswer.trim().isEmpty()) {
                        if (memory) {
                            contextService.addUserMessage(sessionId, finalMsg);
                        }
                        streamKnowledgeAnswer(knowledgeAnswer, emitter);
                        if (memory) {
                            contextService.addAssistantMessage(sessionId, knowledgeAnswer);
                        }
                        return;
                    }
                }
                if (memory) {
                    doStreamChatWithMemory(sessionId, finalMsg, finalImage, emitter);
                } else {
                    doStreamChatOnce(finalMsg, finalImage, emitter);
                }
            } catch (Exception e) {
                log.error("Stream chat error", e);
                try {
                    emitter.send(SseEmitter.event()
                            .name("error")
                            .data("处理请求时发生错误: " + e.getMessage()));
                    emitter.complete();
                } catch (IOException ex) {
                    emitter.completeWithError(ex);
                }
            }
        });

        return emitter;
    }

    private void streamKnowledgeAnswer(String answer, SseEmitter emitter) {
        try {
            int chunkSize = 10;
            for (int i = 0; i < answer.length(); i += chunkSize) {
                int end = Math.min(answer.length(), i + chunkSize);
                emitter.send(SseEmitter.event().name("message").data(answer.substring(i, end)));
            }
            emitter.send(SseEmitter.event().name("done").data("[DONE]"));
            emitter.complete();
        } catch (IOException e) {
            emitter.completeWithError(e);
        }
    }

    private void doStreamChatWithMemory(String sessionId, String msg, String image, SseEmitter emitter) {
        contextService.addUserMessage(sessionId, msg, image);
        try {
            List<JSONObject> messages = contextService.getContext(sessionId);
            StringBuilder fullResponse = new StringBuilder();

            dashScopeApiService.streamChat(messages,
                chunk -> {
                    fullResponse.append(chunk);
                    try {
                        emitter.send(SseEmitter.event()
                                .name("message")
                                .data(chunk));
                    } catch (IOException e) {
                        throw new RuntimeException("SSE send failed", e);
                    }
                },
                () -> {
                    contextService.addAssistantMessage(sessionId, fullResponse.toString());
                    try {
                        emitter.send(SseEmitter.event()
                                .name("done")
                                .data("[DONE]"));
                        emitter.complete();
                    } catch (IOException e) {
                        log.error("Failed to send done event", e);
                    }
                },
                error -> {
                    try {
                        emitter.send(SseEmitter.event()
                                .name("error")
                                .data("AI服务异常: " + error.getMessage()));
                        emitter.complete();
                    } catch (IOException e) {
                        log.error("Failed to send error event", e);
                    }
                }
            );
        } catch (ContextOverflowException e) {
            log.warn("上下文溢出，自动清空历史后重试: {}", e.getMessage());
            contextService.clearContext(sessionId);
            contextService.addUserMessage(sessionId, msg, image);
            try {
                emitter.send(SseEmitter.event()
                        .name("notice")
                        .data("上下文已满，已自动开启新对话"));
            } catch (IOException ex) {
                log.error("Failed to send notice event", ex);
            }
            doStreamChatWithMemory(sessionId, msg, image, emitter);
        }
    }

    private void doStreamChatOnce(String msg, String image, SseEmitter emitter) {
        List<JSONObject> messages = contextService.buildMessages(msg, image);

        dashScopeApiService.streamChat(messages,
            chunk -> {
                try {
                    emitter.send(SseEmitter.event()
                            .name("message")
                            .data(chunk));
                } catch (IOException e) {
                    throw new RuntimeException("SSE send failed", e);
                }
            },
            () -> {
                try {
                    emitter.send(SseEmitter.event()
                            .name("done")
                            .data("[DONE]"));
                    emitter.complete();
                } catch (IOException e) {
                    log.error("Failed to send done event", e);
                }
            },
            error -> {
                try {
                    emitter.send(SseEmitter.event()
                            .name("error")
                            .data("AI服务异常: " + error.getMessage()));
                    emitter.complete();
                } catch (IOException e) {
                    log.error("Failed to send error event", e);
                }
            }
        );
    }

    @PostMapping("/chat")
    public Result<String> chat(@RequestBody Map<String, String> body, HttpServletRequest request) {
        String sessionId = request.getSession().getId();
        String msg = body.get("msg");
        String image = body.get("image");
        boolean memory = Boolean.parseBoolean(body.getOrDefault("memory", "true"));

        if ((msg == null || msg.trim().isEmpty()) && (image == null || image.isEmpty())) {
            msg = "你好";
        }
        if (msg == null || msg.trim().isEmpty()) {
            msg = "请描述这张图片";
        }

        if (memory) {
            contextService.addUserMessage(sessionId, msg, image);
            try {
                List<JSONObject> messages = contextService.getContext(sessionId);
                String result = dashScopeApiService.chat(messages);
                contextService.addAssistantMessage(sessionId, result);
                return Result.ok(result);
            } catch (ContextOverflowException e) {
                log.warn("上下文溢出，自动清空历史后重试: {}", e.getMessage());
                contextService.clearContext(sessionId);
                contextService.addUserMessage(sessionId, msg, image);
                List<JSONObject> freshMessages = contextService.getContext(sessionId);
                String result = dashScopeApiService.chat(freshMessages);
                contextService.addAssistantMessage(sessionId, result);
                return Result.ok(result);
            }
        } else {
            List<JSONObject> messages = contextService.buildMessages(msg, image);
            String result = dashScopeApiService.chat(messages);
            return Result.ok(result);
        }
    }

    @PostMapping("/clear")
    public Result<Void> clearContext(HttpServletRequest request) {
        String sessionId = request.getSession().getId();
        contextService.clearContext(sessionId);
        return Result.ok();
    }
}
