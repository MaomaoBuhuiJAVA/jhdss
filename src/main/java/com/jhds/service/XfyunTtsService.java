package com.jhds.service;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okio.ByteString;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PreDestroy;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Service
public class XfyunTtsService {

    private static final int SAMPLE_RATE = 16000;
    private static final int MAX_TEXT_LENGTH = 160;
    private static final int MAX_CACHE_ENTRIES = 80;
    private static final Set<String> FIXED_PATROL_MESSAGES = new HashSet<>(Arrays.asList(
            "自动巡检启动失败，请检查设备连接",
            "收到停止指令，正在停止轨道电机、升降电机和摄像云台",
            "巡检发生异常，已执行紧急停止，请检查设备连接",
            "自动巡检已停止，请重新确认设备位置后再启动",
            "收到急停指令，正在停止全部设备",
            "自动巡检完成，设备已停止，当前位置已回到底部安全高度",
            "巡检任务已提交，正在准备设备",
            "正在检查摄像头、轨道电机和升降控制面板，请稍候",
            "设备检查完成，自动巡检准备就绪",

            "气肥已开启",
            "气肥已关闭",
            "循环灌溉泵已开启",
            "循环灌溉泵已关闭",
            "叶面肥已开启",
            "叶面肥已关闭",
            "MQTT服务连接成功",
            "蓝牙服务连接成功"
    ));

    @Value("${xfyun.tts.enabled:false}")
    private boolean enabled;
    @Value("${xfyun.tts.app-id:}")
    private String appId;
    @Value("${xfyun.tts.api-key:}")
    private String apiKey;
    @Value("${xfyun.tts.api-secret:}")
    private String apiSecret;
    @Value("${xfyun.tts.websocket-url:https://tts-api.xfyun.cn/v2/tts}")
    private String websocketUrl;
    @Value("${xfyun.tts.voice-name:x4_yezi}")
    private String voiceName;
    @Value("${xfyun.tts.speed:50}")
    private int speed;
    @Value("${xfyun.tts.volume:100}")
    private int volume;
    @Value("${xfyun.tts.pitch:50}")
    private int pitch;
    @Value("${xfyun.tts.timeout-ms:15000}")
    private long timeoutMs;

    private final ConcurrentHashMap<String, byte[]> audioCache = new ConcurrentHashMap<>();
    private final OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(6, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .build();

    public byte[] synthesize(String sourceText) {
        String text = validateMessage(sourceText);
        requireConfigured();
        byte[] cached = audioCache.get(text);
        if (cached != null) return cached;

        final CompletableFuture<byte[]> result = new CompletableFuture<>();
        final ByteArrayOutputStream pcm = new ByteArrayOutputStream();
        Request request = new Request.Builder().url(buildAuthorizedUrl()).build();
        WebSocket socket = httpClient.newWebSocket(request, new WebSocketListener() {
            @Override
            public void onOpen(WebSocket webSocket, Response response) {
                webSocket.send(buildRequest(text));
            }

            @Override
            public void onMessage(WebSocket webSocket, String message) {
                try {
                    JSONObject root = JSON.parseObject(message);
                    int code = root.getIntValue("code");
                    if (code != 0) {
                        String detail = root.getString("message");
                        result.completeExceptionally(new IllegalStateException(
                                "讯飞语音合成失败（" + code + "）：" + (detail == null ? "未知错误" : detail)));
                        webSocket.close(1000, "tts error");
                        return;
                    }
                    JSONObject data = root.getJSONObject("data");
                    if (data == null) return;
                    String audio = data.getString("audio");
                    if (audio != null && !audio.isEmpty()) {
                        byte[] chunk = Base64.getDecoder().decode(audio);
                        pcm.write(chunk, 0, chunk.length);
                    }
                    if (data.getIntValue("status") == 2) {
                        byte[] wav = wrapPcmAsWav(pcm.toByteArray());
                        if (audioCache.size() >= MAX_CACHE_ENTRIES) audioCache.clear();
                        audioCache.put(text, wav);
                        result.complete(wav);
                        webSocket.close(1000, "complete");
                    }
                } catch (Exception e) {
                    result.completeExceptionally(e);
                    webSocket.cancel();
                }
            }

            @Override
            public void onMessage(WebSocket webSocket, ByteString bytes) {
                onMessage(webSocket, bytes.utf8());
            }

            @Override
            public void onFailure(WebSocket webSocket, Throwable error, Response response) {
                result.completeExceptionally(new IllegalStateException("讯飞语音服务连接失败", error));
            }
        });

        try {
            return result.get(Math.max(3000L, timeoutMs), TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            socket.cancel();
            throw new IllegalStateException(rootMessage(e), e);
        }
    }

    public Map<String, Object> status() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("enabled", enabled);
        result.put("configured", configured());
        result.put("voiceName", voiceName);
        result.put("provider", "iFlytek");
        return result;
    }

    public String validateMessage(String sourceText) {
        String text = sourceText == null ? "" : sourceText.trim().replaceAll("\\s+", " ");
        if (text.isEmpty()) throw new IllegalArgumentException("播报内容不能为空");
        if (text.length() > MAX_TEXT_LENGTH) throw new IllegalArgumentException("播报内容不能超过160个字符");
        if (!isAllowedSpeechMessage(text)) throw new IllegalArgumentException("播报内容不属于预设指令");
        return text;
    }

    private boolean isAllowedSpeechMessage(String text) {
        if (FIXED_PATROL_MESSAGES.contains(text)) return true;
        return text.matches("开始执行(快速巡检|标准巡检|精细巡检|自动巡检)方案，请确保轨道和种植架周围无人")
                || text.matches("轨道电机启动，正在向左移动至第[1-7]条扫描线")
                || text.matches("到达第[1-7]条扫描线(底部|中点|顶部)，正在抓拍巡检图像")
                || text.matches("升降电机启动，正在沿第[1-7]条扫描线上移至(中点|顶部)")
                || text.matches("升降电机反向启动，第[1-7]条扫描线正在返回底部");
    }

    private void requireConfigured() {
        if (!enabled) throw new IllegalStateException("讯飞在线语音合成未启用");
        if (!configured()) throw new IllegalStateException("讯飞在线语音合成凭据未配置完整");
    }

    private boolean configured() {
        return hasText(appId) && hasText(apiKey) && hasText(apiSecret) && hasText(websocketUrl);
    }

    private String buildAuthorizedUrl() {
        try {
            URI endpoint = URI.create(websocketUrl.trim());
            String host = endpoint.getHost();
            String path = endpoint.getRawPath();
            SimpleDateFormat format = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.US);
            format.setTimeZone(TimeZone.getTimeZone("GMT"));
            String date = format.format(new java.util.Date());
            String signatureSource = "host: " + host + "\n"
                    + "date: " + date + "\n"
                    + "GET " + path + " HTTP/1.1";

            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(apiSecret.trim().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String signature = Base64.getEncoder().encodeToString(
                    mac.doFinal(signatureSource.getBytes(StandardCharsets.UTF_8)));
            String authorizationSource = "api_key=\"" + apiKey.trim()
                    + "\", algorithm=\"hmac-sha256\", headers=\"host date request-line\", signature=\""
                    + signature + "\"";
            String authorization = Base64.getEncoder().encodeToString(
                    authorizationSource.getBytes(StandardCharsets.UTF_8));

            HttpUrl base = HttpUrl.parse(websocketUrl.trim());
            if (base == null) throw new IllegalArgumentException("讯飞语音服务地址无效");
            return base.newBuilder()
                    .addQueryParameter("authorization", authorization)
                    .addQueryParameter("date", date)
                    .addQueryParameter("host", host)
                    .build().toString();
        } catch (Exception e) {
            throw new IllegalStateException("讯飞语音鉴权地址生成失败", e);
        }
    }

    private String buildRequest(String text) {
        JSONObject root = new JSONObject();
        JSONObject common = new JSONObject();
        common.put("app_id", appId.trim());
        root.put("common", common);

        JSONObject business = new JSONObject();
        business.put("aue", "raw");
        business.put("auf", "audio/L16;rate=" + SAMPLE_RATE);
        business.put("vcn", voiceName);
        business.put("tte", "UTF8");
        business.put("speed", clamp(speed));
        business.put("volume", clamp(volume));
        business.put("pitch", clamp(pitch));
        root.put("business", business);

        JSONObject data = new JSONObject();
        data.put("status", 2);
        data.put("text", Base64.getEncoder().encodeToString(text.getBytes(StandardCharsets.UTF_8)));
        root.put("data", data);
        return root.toJSONString();
    }

    private byte[] wrapPcmAsWav(byte[] pcm) {
        int dataLength = pcm.length;
        ByteBuffer header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN);
        header.put("RIFF".getBytes(StandardCharsets.US_ASCII));
        header.putInt(36 + dataLength);
        header.put("WAVE".getBytes(StandardCharsets.US_ASCII));
        header.put("fmt ".getBytes(StandardCharsets.US_ASCII));
        header.putInt(16);
        header.putShort((short) 1);
        header.putShort((short) 1);
        header.putInt(SAMPLE_RATE);
        header.putInt(SAMPLE_RATE * 2);
        header.putShort((short) 2);
        header.putShort((short) 16);
        header.put("data".getBytes(StandardCharsets.US_ASCII));
        header.putInt(dataLength);
        byte[] wav = new byte[44 + dataLength];
        System.arraycopy(header.array(), 0, wav, 0, 44);
        System.arraycopy(pcm, 0, wav, 44, dataLength);
        return wav;
    }

    private int clamp(int value) {
        return Math.max(0, Math.min(100, value));
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private String rootMessage(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null) current = current.getCause();
        String message = current.getMessage();
        return message == null || message.trim().isEmpty() ? "讯飞语音合成失败" : message;
    }

    @PreDestroy
    public void shutdown() {
        httpClient.dispatcher().executorService().shutdown();
        httpClient.connectionPool().evictAll();
    }
}
