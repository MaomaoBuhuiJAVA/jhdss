package com.jhds.service;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.jhds.config.YsjProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class EzvizService {

    private static final String TOKEN_URL = "https://open.ys7.com/api/lapp/token/get";
    private static final String LIVE_URL = "https://open.ys7.com/api/lapp/v2/live/address/get";
    private static final String ENCODE_TYPE_URL = "https://open.ys7.com/api/v3/device/video/encodeType";
    private static final String DEVICE_LIST_URL = "https://open.ys7.com/api/lapp/device/list";
    private static final String CAMERA_LIST_URL = "https://open.ys7.com/api/lapp/device/camera/list";
    private static final String REDIS_KEY = "jhds:ys7:access_token";
    /** 设备编码本地缓存前缀: jhds:ys7:encode:{deviceSerial}:{channelNo}:{streamType} */
    private static final String ENCODE_CACHE_PREFIX = "jhds:ys7:encode";

    @Autowired
    private YsjProperties ysjProperties;
    @Autowired
    private RestTemplate restTemplate;
    @Autowired
    private StringRedisTemplate redisTemplate;

    public String getAccessToken() {
        String cached = redisTemplate.opsForValue().get(REDIS_KEY);
        if (cached != null) {
            return cached;
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("appKey", ysjProperties.getAppKey());
        body.add("appSecret", ysjProperties.getAppSecret());

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    TOKEN_URL, HttpMethod.POST, request, String.class);
            JSONObject json = JSONObject.parseObject(response.getBody());
            String code = json.getString("code");
            if (!"200".equals(code)) {
                log.error("Failed to get YS7 accessToken: {}", json);
                throw new RuntimeException("获取萤石AccessToken失败: " + json.getString("msg"));
            }
            JSONObject data = json.getJSONObject("data");
            String accessToken = data.getString("accessToken");
            long expireTime = data.getLongValue("expireTime");
            long ttl = (expireTime - System.currentTimeMillis()) / 1000;
            if (ttl > 0) {
                redisTemplate.opsForValue().set(REDIS_KEY, accessToken, ttl, TimeUnit.SECONDS);
            }
            return accessToken;
        } catch (Exception e) {
            log.error("Error getting YS7 accessToken", e);
            throw new RuntimeException("获取萤石AccessToken异常", e);
        }
    }

    public void changeEncodeType(String deviceSerial, String encodeType, Integer streamType) {
        changeEncodeType(deviceSerial, encodeType, streamType, null);
    }

    public void changeEncodeType(String deviceSerial, String encodeType, Integer streamType, Integer channelNo) {
        String accessToken = getAccessToken();
        int ch = channelNo == null ? 1 : channelNo;
        int st = streamType == null ? 1 : streamType;

        // 萤石云官方文档: https://open.ys7.com/help/2377 （视频编码格式切换）
        // 请求方式: PUT  https://open.ys7.com/api/v3/device/video/encodeType
        // header: accessToken / deviceSerial / localIndex(资源即通道号, 默认1)
        // body  : encodeType(H264/H265) / streamType(1主码流 2子码流)
        // 返回  : meta.code 为数字 200 表示成功
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.set("accessToken", accessToken);
        headers.set("deviceSerial", deviceSerial);
        headers.set("localIndex", String.valueOf(ch));

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("encodeType", encodeType);
        body.add("streamType", String.valueOf(st));

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    ENCODE_TYPE_URL, HttpMethod.PUT, request, String.class);
            JSONObject json = JSONObject.parseObject(response.getBody());
            // v3 接口返回结构为 { "meta": { "code": 200, "message": "...", "moreInfo": {} } }
            JSONObject meta = json.getJSONObject("meta");
            Integer code = meta != null ? meta.getInteger("code") : json.getInteger("code");
            boolean ok = code != null && code == 200;
            if (!ok) {
                String msg = meta != null ? meta.getString("message") : json.getString("msg");
                log.error("Failed to change YS7 encodeType: {}", json);
                throw new RuntimeException("修改设备编码失败: " + msg);
            }
            // 萤石云未提供"查询当前编码"的接口，本地缓存最近一次设置的编码，供查询接口读取
            redisTemplate.opsForValue().set(encodeCacheKey(deviceSerial, ch, st), encodeType);
            log.info("Changed encodeType for {} (ch{}) to {}, streamType={}", deviceSerial, ch, encodeType, st);
        } catch (Exception e) {
            log.error("Error changing YS7 encodeType for device: {} ch{}", deviceSerial, ch, e);
            throw new RuntimeException("修改设备编码异常", e);
        }
    }

    /**
     * 查询设备视频编码类型。
     *
     * 说明：萤石云开放平台仅提供"设置"编码的接口（PUT /api/v3/device/video/encodeType），
     * 并未公开"查询当前编码类型"的 GET 接口——实测同 URL 用 GET/POST 均返回 405
     * （错误信息："客户端请求中的方法被禁止"），device/list 与 camera/list 返回中也
     * 不含编码字段。因此本方法返回的是本地缓存的"最近一次设置编码"；从未通过本系统
     * 设置过则返回 null（未知）。
     *
     * @return videoCode：1=H.264, 5=H.265, 6=SMART264, 7=SMART265, null=未知
     */
    public Integer getEncodeType(String deviceSerial, Integer channelNo, Integer streamType) {
        int ch = channelNo == null ? 1 : channelNo;
        int st = streamType == null ? 1 : streamType;
        String cached = redisTemplate.opsForValue().get(encodeCacheKey(deviceSerial, ch, st));
        if (cached == null) {
            log.info("No local cached encodeType for {} ch{} st{} (EZVIZ has no public query API)", deviceSerial, ch, st);
            return null;
        }
        return encodeTypeToVideoCode(cached);
    }

    private String encodeCacheKey(String deviceSerial, int channelNo, int streamType) {
        return ENCODE_CACHE_PREFIX + ":" + deviceSerial + ":" + channelNo + ":" + streamType;
    }

    private Integer encodeTypeToVideoCode(String encodeType) {
        if (encodeType == null) {
            return null;
        }
        switch (encodeType.toUpperCase()) {
            case "H264":     return 1;
            case "H265":     return 5;
            case "SMART264": return 6;
            case "SMART265": return 7;
            default:         return null;
        }
    }

    public JSONArray getCameraList(String deviceSerial) {
        String accessToken = getAccessToken();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("accessToken", accessToken);
        body.add("deviceSerial", deviceSerial);

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    CAMERA_LIST_URL, HttpMethod.POST, request, String.class);
            JSONObject json = JSONObject.parseObject(response.getBody());
            String code = json.getString("code");
            if (!"200".equals(code)) {
                log.error("Failed to get YS7 camera list for {}: {}", deviceSerial, json);
                throw new RuntimeException("获取通道列表失败: " + json.getString("msg"));
            }
            JSONArray data = json.getJSONArray("data");
            // 为每个通道补充 deviceSerial
            if (data != null) {
                for (int i = 0; i < data.size(); i++) {
                    data.getJSONObject(i).put("deviceSerial", deviceSerial);
                }
            }
            return data;
        } catch (Exception e) {
            log.error("Error getting YS7 camera list for device: {}", deviceSerial, e);
            throw new RuntimeException("获取通道列表异常", e);
        }
    }

    public JSONArray getDeviceList() {
        String accessToken = getAccessToken();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("accessToken", accessToken);
        body.add("pageStart", "0");
        body.add("pageSize", "50");

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    DEVICE_LIST_URL, HttpMethod.POST, request, String.class);
            JSONObject json = JSONObject.parseObject(response.getBody());
            String code = json.getString("code");
            if (!"200".equals(code)) {
                log.error("Failed to get YS7 device list: {}", json);
                throw new RuntimeException("获取设备列表失败: " + json.getString("msg"));
            }
            return json.getJSONArray("data");
        } catch (Exception e) {
            log.error("Error getting YS7 device list", e);
            throw new RuntimeException("获取设备列表异常", e);
        }
    }

    public Map<String, String> batchChangeEncodeType(List<String> deviceSerials, String encodeType, Integer streamType) {
        Map<String, String> results = new LinkedHashMap<>();
        for (String serial : deviceSerials) {
            try {
                changeEncodeType(serial, encodeType, streamType);
                results.put(serial, "success");
            } catch (Exception e) {
                results.put(serial, e.getMessage());
            }
        }
        return results;
    }

    public Map<String, String> batchChangeEncodeTypeByTargets(List<EncodeTarget> targets, String encodeType, Integer streamType) {
        Map<String, String> results = new LinkedHashMap<>();
        for (EncodeTarget t : targets) {
            String key = t.channelNo != null ? t.deviceSerial + ":ch" + t.channelNo : t.deviceSerial;
            try {
                changeEncodeType(t.deviceSerial, encodeType, streamType, t.channelNo);
                results.put(key, "success");
            } catch (Exception e) {
                results.put(key, e.getMessage());
            }
        }
        return results;
    }

    public static class EncodeTarget {
        private String deviceSerial;
        private Integer channelNo;
        public EncodeTarget() {}
        public EncodeTarget(String deviceSerial, Integer channelNo) {
            this.deviceSerial = deviceSerial;
            this.channelNo = channelNo;
        }
        public String getDeviceSerial() { return deviceSerial; }
        public void setDeviceSerial(String deviceSerial) { this.deviceSerial = deviceSerial; }
        public Integer getChannelNo() { return channelNo; }
        public void setChannelNo(Integer channelNo) { this.channelNo = channelNo; }
    }

    public String getPlayUrl(String deviceSerial) {
        return getPlayUrl(deviceSerial, 4);
    }

    public String getPlayUrl(String deviceSerial, int protocol) {
        String accessToken = getAccessToken();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("accessToken", accessToken);
        body.add("deviceSerial", deviceSerial);
        body.add("protocol", String.valueOf(protocol));

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    LIVE_URL, HttpMethod.POST, request, String.class);
            JSONObject json = JSONObject.parseObject(response.getBody());
            String code = json.getString("code");
            if (!"200".equals(code)) {
                log.error("Failed to get YS7 playUrl: {}", json);
                throw new RuntimeException("获取萤石播放地址失败: " + json.getString("msg"));
            }
            JSONObject data = json.getJSONObject("data");
            return data.getString("url");
        } catch (Exception e) {
            log.error("Error getting YS7 playUrl for device: {}", deviceSerial, e);
            throw new RuntimeException("获取萤石播放地址异常", e);
        }
    }
}
