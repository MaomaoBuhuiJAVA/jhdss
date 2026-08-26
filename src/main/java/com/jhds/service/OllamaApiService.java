package com.jhds.service;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.jhds.common.ContextOverflowException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.Consumer;

@Service
public class OllamaApiService {

    private static final Logger log = LoggerFactory.getLogger(OllamaApiService.class);

    @Value("${ollama.base-url:http://localhost:11434}")
    private String baseUrl;

    @Value("${ollama.model:qwen2.5vl:7b}")
    private String model;

    @Value("${ollama.options.temperature:0.1}")
    private double temperature;

    @Value("${ollama.options.num-predict:2048}")
    private int numPredict;

    @Value("${ollama.options.num-ctx:16384}")
    private int numCtx;

    public String getModel() {
        return model;
    }

    public void streamChat(List<JSONObject> messages, Consumer<String> onChunk, Runnable onDone, Consumer<Exception> onError) {
        HttpURLConnection conn = null;
        try {
            JSONObject body = new JSONObject();
            body.put("model", model);
            body.put("messages", messages);
            body.put("stream", true);

            JSONObject options = new JSONObject();
            options.put("temperature", temperature);
            options.put("num_predict", numPredict);
            options.put("num_ctx", numCtx);
            body.put("options", options);

            URL url = new URL(baseUrl + "/api/chat");
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);
            conn.setConnectTimeout(30000);
            conn.setReadTimeout(60000);

            try (OutputStream os = conn.getOutputStream()) {
                os.write(body.toJSONString().getBytes(StandardCharsets.UTF_8));
                os.flush();
            }

            int responseCode = conn.getResponseCode();
            if (responseCode != 200) {
                String errorBody = readStream(conn.getErrorStream());
                if (isContextOverflowError(errorBody)) {
                    throw new ContextOverflowException("上下文溢出：" + extractOverflowMessage(errorBody));
                }
                throw new IOException("Ollama API error " + responseCode + ": " + errorBody);
            }

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                StringBuilder fullContent = new StringBuilder();
                while ((line = reader.readLine()) != null) {
                    if (line.trim().isEmpty()) continue;
                    try {
                        JSONObject json = JSON.parseObject(line);
                        JSONObject msg = json.getJSONObject("message");
                        if (msg != null) {
                            String content = msg.getString("content");
                            if (content != null && !content.isEmpty()) {
                                fullContent.append(content);
                                onChunk.accept(content);
                            }
                        }
                        if (json.getBooleanValue("done")) {
                            break;
                        }
                    } catch (Exception e) {
                        log.warn("Parse Ollama chunk failed: {}", line, e);
                    }
                }
                onDone.run();
            }
        } catch (ContextOverflowException e) {
            throw e;
        } catch (Exception e) {
            log.error("Ollama stream chat error", e);
            onError.accept(e);
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    public String chat(List<JSONObject> messages) {
        HttpURLConnection conn = null;
        try {
            JSONObject body = new JSONObject();
            body.put("model", model);
            body.put("messages", messages);
            body.put("stream", false);

            JSONObject options = new JSONObject();
            options.put("temperature", temperature);
            options.put("num_predict", numPredict);
            options.put("num_ctx", numCtx);
            body.put("options", options);

            URL url = new URL(baseUrl + "/api/chat");
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);
            conn.setConnectTimeout(30000);
            conn.setReadTimeout(60000);

            try (OutputStream os = conn.getOutputStream()) {
                os.write(body.toJSONString().getBytes(StandardCharsets.UTF_8));
                os.flush();
            }

            int responseCode = conn.getResponseCode();
            if (responseCode != 200) {
                String errorBody = readStream(conn.getErrorStream());
                if (isContextOverflowError(errorBody)) {
                    throw new ContextOverflowException("上下文溢出：" + extractOverflowMessage(errorBody));
                }
                throw new IOException("Ollama API error " + responseCode + ": " + errorBody);
            }

            String responseBody = readStream(conn.getInputStream());
            JSONObject json = JSON.parseObject(responseBody);
            JSONObject msg = json.getJSONObject("message");
            return msg != null ? msg.getString("content") : "";
        } catch (Exception e) {
            log.error("Ollama chat error", e);
            return "抱歉，请求AI服务失败：" + e.getMessage();
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    private boolean isContextOverflowError(String errorBody) {
        try {
            JSONObject outer = JSON.parseObject(errorBody);
            String innerStr = outer.getString("error");
            if (innerStr != null) {
                JSONObject inner = JSON.parseObject(innerStr);
                JSONObject innerError = inner.getJSONObject("error");
                if (innerError != null) {
                    return "exceed_context_size_error".equals(innerError.getString("type"));
                }
            }
        } catch (Exception ignored) {}
        return errorBody.contains("exceed_context_size_error");
    }

    private String extractOverflowMessage(String errorBody) {
        try {
            JSONObject outer = JSON.parseObject(errorBody);
            String innerStr = outer.getString("error");
            if (innerStr != null) {
                JSONObject inner = JSON.parseObject(innerStr);
                JSONObject innerError = inner.getJSONObject("error");
                if (innerError != null && innerError.getString("message") != null) {
                    return innerError.getString("message");
                }
            }
        } catch (Exception ignored) {}
        return "请求超出模型上下文限制";
    }

    private String readStream(InputStream stream) throws IOException {
        if (stream == null) return "";
        BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            sb.append(line);
        }
        return sb.toString();
    }
}
