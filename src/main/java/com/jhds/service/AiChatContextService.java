package com.jhds.service;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
public class AiChatContextService {

    private static final Logger log = LoggerFactory.getLogger(AiChatContextService.class);

    private static final String CONTEXT_PREFIX = "jhds:ai:context:";
    private static final long TTL_MINUTES = 20;
    private static final int MAX_MESSAGES = 50;

    private static final String SYSTEM_PROMPT = "你是jhds智慧农业AI助手，专注于农业领域的知识问答和技术支持。\n\n"
            + "【通用知识问答】\n"
            + "你的核心职责是回答与农业相关的问题，包括但不限于：\n"
            + "- 农作物种植技术、病虫害防治\n"
            + "- 农业环境监测与设备管理\n"
            + "- 土壤肥料、灌溉技术\n"
            + "- 温室大棚管理、设施农业\n"
            + "- 智慧农业、数字农业\n\n"
            + "重要规则：\n"
            + "1. 当用户问\"你是谁\"或类似问题时，回答：\"我是jhds智慧农业AI助手，专注于农业领域的知识问答和技术支持。\"\n"
            + "2. 当用户提出非农业领域的问题时，礼貌地拒绝回答。\n"
            + "3. 回答农业问题时，请用专业、准确、易懂的语言。\n"
            + "4. 如果遇到不确定的农业问题，诚实地说明不确定，不要编造信息。\n\n"
            + "【图片识别】\n"
            + "你还具备图像识别能力，当用户上传农作物、病虫害、设备等图片时，可以分析图片内容并提供专业判断。";

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    public List<JSONObject> getContext(String sessionId) {
        String key = CONTEXT_PREFIX + sessionId;
        List<String> jsonList = stringRedisTemplate.opsForList().range(key, 0, -1);
        List<JSONObject> messages = new ArrayList<>();

        messages.add(new JSONObject() {{
            put("role", "system");
            put("content", SYSTEM_PROMPT);
        }});

        if (jsonList == null || jsonList.isEmpty()) {
            return messages;
        }

        if (jsonList.size() > MAX_MESSAGES) {
            jsonList = jsonList.subList(jsonList.size() - MAX_MESSAGES, jsonList.size());
        }

        for (String json : jsonList) {
            try {
                messages.add(JSON.parseObject(json));
            } catch (Exception e) {
                log.warn("Deserialize context message failed: {}", json, e);
            }
        }
        return messages;
    }

    public void addUserMessage(String sessionId, String content) {
        addMessage(sessionId, "user", content, null);
    }

    public void addUserMessage(String sessionId, String content, String imageBase64) {
        addMessage(sessionId, "user", content, imageBase64);
    }

    public void addAssistantMessage(String sessionId, String content) {
        addMessage(sessionId, "assistant", content, null);
    }

    private void addMessage(String sessionId, String role, String content, String imageBase64) {
        String key = CONTEXT_PREFIX + sessionId;
        try {
            JSONObject msg = new JSONObject();
            msg.put("role", role);
            msg.put("content", content);
            if (imageBase64 != null && !imageBase64.isEmpty()) {
                msg.put("images", new String[]{imageBase64});
            }
            stringRedisTemplate.opsForList().rightPush(key, msg.toJSONString());
            stringRedisTemplate.opsForList().trim(key, -MAX_MESSAGES, -1);
            stringRedisTemplate.expire(key, TTL_MINUTES, TimeUnit.MINUTES);
        } catch (Exception e) {
            log.error("Failed to save message to Redis", e);
        }
    }

    public void clearContext(String sessionId) {
        stringRedisTemplate.delete(CONTEXT_PREFIX + sessionId);
    }

    public List<JSONObject> buildMessages(String userMessage, String imageBase64) {
        List<JSONObject> messages = new ArrayList<>();

        JSONObject systemMsg = new JSONObject();
        systemMsg.put("role", "system");
        systemMsg.put("content", SYSTEM_PROMPT);
        messages.add(systemMsg);

        JSONObject userMsg = new JSONObject();
        userMsg.put("role", "user");
        userMsg.put("content", userMessage);
        if (imageBase64 != null && !imageBase64.isEmpty()) {
            userMsg.put("images", new String[]{imageBase64});
        }
        messages.add(userMsg);

        return messages;
    }
}
