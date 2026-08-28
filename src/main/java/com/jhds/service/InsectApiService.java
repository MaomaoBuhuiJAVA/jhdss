package com.jhds.service;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.jhds.config.InsectApiProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class InsectApiService {

    private static final String REDIS_TOKEN_KEY = "jhds:insect:token";
    private static final long TOKEN_REFRESH_THRESHOLD = 6 * 24 * 60 * 60;

    @Autowired
    private InsectApiProperties apiProperties;
    @Autowired
    private RestTemplate restTemplate;
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    public String login() {
        String cached = null;
        try {
            cached = (String) redisTemplate.opsForValue().get(REDIS_TOKEN_KEY);
        } catch (Exception e) {
            log.warn("Redis unavailable, skip cache read", e);
        }
        if (cached != null) return cached;

        JSONObject body = new JSONObject();
        body.put("username", apiProperties.getUsername());
        String md5Pwd = DigestUtils.md5DigestAsHex(
                apiProperties.getPassword().getBytes(StandardCharsets.UTF_8));
        body.put("password", md5Pwd);

        try {
            JSONObject resp = post("/api/v2/login", null, body);
            if (resp != null && resp.getIntValue("status") == 1) {
                String token = resp.getJSONObject("data").getString("token");
                try {
                    redisTemplate.opsForValue().set(REDIS_TOKEN_KEY, token,
                            TOKEN_REFRESH_THRESHOLD, TimeUnit.SECONDS);
                } catch (Exception e) {
                    // The external API can still be used when Redis is temporarily down.
                    log.warn("Redis unavailable, skip insect token cache write: {}", e.getMessage());
                }
                log.info("Insect API login success");
                return token;
            }
            log.warn("Insect API login failed: {}", resp);
        } catch (Exception e) {
            log.error("Insect API login error", e);
        }
        return null;
    }

    private JSONObject post(String path, String token, Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (token != null) {
            headers.set("token", token);
        }
        HttpEntity<Object> entity = new HttpEntity<>(body, headers);

        try {
            String url = apiProperties.getBaseUrl() + path;
            ResponseEntity<String> resp = restTemplate.exchange(
                    url, HttpMethod.POST, entity, String.class);
            return JSON.parseObject(resp.getBody());
        } catch (Exception e) {
            log.error("API call failed: {} {}", path, e.getMessage());
            return null;
        }
    }

    private JSONObject postWithAuth(String path, Object body) {
        String token = login();
        if (token == null) return null;

        JSONObject resp = post(path, token, body);
        if (resp != null && resp.getIntValue("status") == 0
                && resp.getString("msg") != null
                && resp.getString("msg").contains("登录超时")) {
            try {
                redisTemplate.delete(REDIS_TOKEN_KEY);
            } catch (Exception e) {
                log.warn("Redis unavailable, skip insect token cache delete: {}", e.getMessage());
            }
            token = login();
            if (token != null) {
                resp = post(path, token, body);
            }
        }
        return resp;
    }

    public JSONObject getDeviceList(String type) {
        JSONObject body = new JSONObject();
        body.put("type", type);
        return postWithAuth("/api/v2/device", body);
    }

    public JSONObject getLatestPhotos(String did) {
        JSONObject body = new JSONObject();
        body.put("did", did);
        return postWithAuth("/api/v2/picnow", body);
    }

    public JSONObject getPhotoHistory(String did, String startTime, String endTime,
                                       Integer page, Integer num) {
        JSONObject body = new JSONObject();
        body.put("did", did);
        body.put("startTime", startTime);
        body.put("endTime", endTime);
        int p = page != null ? page : 1;
        int n = num != null ? num : 10;
        return postWithAuth("/api/v2/picRecord?pages=" + p + "&num=" + n, body);
    }

    public JSONObject getRealtimeData(String did) {
        JSONObject body = new JSONObject();
        body.put("did", did);
        return postWithAuth("/api/v2/data", body);
    }

    public JSONObject getDataHistory(String did, String startTime, String endTime) {
        JSONObject body = new JSONObject();
        body.put("did", did);
        body.put("startTime", startTime);
        body.put("endTime", endTime);
        return postWithAuth("/api/v2/dataRecord", body);
    }

    public JSONObject getControlStatus(String did) {
        JSONObject body = new JSONObject();
        body.put("did", did);
        return postWithAuth("/api/v2/opnow", body);
    }

    public JSONObject getControlParams(String did) {
        JSONObject body = new JSONObject();
        body.put("did", did);
        return postWithAuth("/api/v2/ctrl", body);
    }

    public JSONObject sendControl(String did, String cmd, String groupname, String opname) {
        JSONObject body = new JSONObject();
        body.put("did", did);
        body.put("cmd", cmd);
        body.put("groupname", groupname);
        body.put("opname", opname);
        return postWithAuth("/api/v2/op", body);
    }

    public JSONObject getWarnings(String did, String type, String name,
                                   Integer page, Integer num) {
        JSONObject body = new JSONObject();
        if (did != null) body.put("did", did);
        if (type != null) body.put("type", type);
        if (name != null) body.put("name", name);
        String query = "";
        if (page != null) query += "?pages=" + page;
        if (num != null) query += (query.isEmpty() ? "?" : "&") + "num=" + num;
        return postWithAuth("/api/v2/warningRecord" + query, body);
    }

    public JSONObject getTrafficCardData(String did) {
        JSONObject body = new JSONObject();
        if (did != null) body.put("did", did);
        return postWithAuth("/api/v2/trafficCardInquiry", body);
    }
}
