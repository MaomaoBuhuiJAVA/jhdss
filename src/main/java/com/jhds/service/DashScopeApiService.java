package com.jhds.service;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.Consumer;

@Service
public class DashScopeApiService {

    private static final Logger log = LoggerFactory.getLogger(DashScopeApiService.class);

    // @Value("${dashscope.api-key}")
    @Value("${DASHSCOPE_API_KEY:}")
    private String apiKey;

    @Value("${dashscope.base-url}")
    private String baseUrl;

    @Value("${dashscope.model:kimi-k2.6}")
    private String model;

    @Value("${dashscope.options.temperature:0.1}")
    private double temperature;

    @Value("${dashscope.options.max-tokens:2048}")
    private int maxTokens;

    private final RestTemplate restTemplate = new RestTemplate();

    public String getModel() {
        return model;
    }

    public void streamChat(List<JSONObject> ollamaMessages, Consumer<String> onChunk, Runnable onDone, Consumer<Exception> onError) {
        try {
            JSONObject body = buildRequestBody(ollamaMessages, true);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);

            RequestEntity<String> request = new RequestEntity<>(body.toJSONString(), headers, HttpMethod.POST, URI.create(baseUrl + "/compatible-mode/v1/chat/completions"));

            ResponseEntity<Resource> response = restTemplate.exchange(request, Resource.class);

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(response.getBody().getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.startsWith("data: ")) {
                        String data = line.substring(6).trim();
                        if ("[DONE]".equals(data)) {
                            break;
                        }
                        try {
                            JSONObject json = JSON.parseObject(data);
                            JSONArray choices = json.getJSONArray("choices");
                            if (choices != null && !choices.isEmpty()) {
                                JSONObject delta = choices.getJSONObject(0).getJSONObject("delta");
                                if (delta != null) {
                                    String content = delta.getString("content");
                                    if (content != null && !content.isEmpty()) {
                                        onChunk.accept(content);
                                    }
                                }
                            }
                        } catch (Exception e) {
                            log.warn("Parse DashScope chunk failed: {}", line, e);
                        }
                    }
                }
                onDone.run();
            }
        } catch (Exception e) {
            log.error("DashScope stream chat error", e);
            onError.accept(e);
        }
    }

    public String chat(List<JSONObject> ollamaMessages) {
        try {
            JSONObject body = buildRequestBody(ollamaMessages, false);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);

            RequestEntity<String> request = new RequestEntity<>(body.toJSONString(), headers, HttpMethod.POST, URI.create(baseUrl + "/compatible-mode/v1/chat/completions"));

            ResponseEntity<String> response = restTemplate.exchange(request, String.class);

            JSONObject json = JSON.parseObject(response.getBody());
            JSONArray choices = json.getJSONArray("choices");
            if (choices != null && !choices.isEmpty()) {
                JSONObject message = choices.getJSONObject(0).getJSONObject("message");
                if (message != null) {
                    return message.getString("content");
                }
            }
            return "";
        } catch (Exception e) {
            log.error("DashScope chat error", e);
            return "抱歉，请求AI服务失败：" + e.getMessage();
        }
    }

    private JSONObject buildRequestBody(List<JSONObject> ollamaMessages, boolean stream) {
        JSONObject body = new JSONObject();
        body.put("model", model);
        body.put("stream", stream);
        body.put("temperature", temperature);
        body.put("max_tokens", maxTokens);

        JSONArray dashScopeMessages = new JSONArray();
        for (JSONObject ollamaMsg : ollamaMessages) {
            dashScopeMessages.add(convertMessage(ollamaMsg));
        }
        body.put("messages", dashScopeMessages);

        return body;
    }

    private Object convertMessage(JSONObject ollamaMsg) {
        JSONObject result = new JSONObject();
        result.put("role", ollamaMsg.getString("role"));

        String content = ollamaMsg.getString("content");
        JSONArray images = ollamaMsg.getJSONArray("images");

        if (images != null && !images.isEmpty()) {
            JSONArray contentArray = new JSONArray();

            JSONObject textPart = new JSONObject();
            textPart.put("type", "text");
            textPart.put("text", content != null ? content : "");
            contentArray.add(textPart);

            for (int i = 0; i < images.size(); i++) {
                String imgBase64 = images.getString(i);
                JSONObject imagePart = new JSONObject();
                imagePart.put("type", "image_url");
                JSONObject imageUrl = new JSONObject();
                String url = imgBase64.startsWith("data:") ? imgBase64 : "data:image/jpeg;base64," + imgBase64;
                imageUrl.put("url", url);
                imagePart.put("image_url", imageUrl);
                contentArray.add(imagePart);
            }

            result.put("content", contentArray);
        } else {
            result.put("content", content != null ? content : "");
        }

        return result;
    }
}
