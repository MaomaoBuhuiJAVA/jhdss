package com.jhds.controller;

import com.alibaba.fastjson.JSONArray;
import com.jhds.common.Result;
import com.jhds.config.YsjProperties;
import com.jhds.service.EzvizService;
import com.jhds.service.EzvizService.EncodeTarget;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Api(tags = "萤石摄像头模块")
@RestController
@RequestMapping("/api/camera")
public class CameraController {

    @Autowired
    private EzvizService ezvizService;
    @Autowired
    private YsjProperties ysjProperties;

    @ApiOperation("获取摄像头FLV播放地址")
    @GetMapping("/play-url")
    public Result<String> getPlayUrl(
            @RequestParam(required = false) String deviceSerial,
            @RequestParam(required = false) Integer channelNo,
            @RequestParam(required = false) Integer protocol) {
        try {
            String url = ezvizService.getPlayUrl(resolveDeviceSerial(deviceSerial),
                    channelNo == null ? ysjProperties.getChannelNo() : channelNo,
                    protocol == null ? ysjProperties.getProtocol() : protocol);
            return Result.ok(url);
        } catch (RuntimeException e) {
            return Result.error(502, "摄像头播放地址获取失败：" + errorMessage(e));
        }
    }

    @ApiOperation("检测萤石账号、设备绑定和播放地址")
    @GetMapping("/stream-check")
    public Result<Map<String, Object>> checkStream(
            @RequestParam(required = false) String deviceSerial,
            @RequestParam(required = false) Integer channelNo,
            @RequestParam(required = false) Integer protocol) {
        Map<String, Object> status = ezvizService.checkStream(
                resolveDeviceSerial(deviceSerial),
                channelNo == null ? ysjProperties.getChannelNo() : channelNo,
                protocol == null ? ysjProperties.getProtocol() : protocol);
        return Result.ok(status);
    }

    @ApiOperation("修改摄像头视频编码类型")
    @PutMapping("/encode-type")
    public Result<Void> changeEncodeType(
            @RequestParam(required = false) String deviceSerial,
            @RequestParam(defaultValue = "H264") String encodeType,
            @RequestParam(required = false) Integer streamType,
            @RequestParam(required = false) Integer channelNo) {
        try {
            ezvizService.changeEncodeType(resolveDeviceSerial(deviceSerial), encodeType,
                    streamType == null ? ysjProperties.getStreamType() : streamType,
                    channelNo == null ? ysjProperties.getChannelNo() : channelNo);
            return Result.ok();
        } catch (RuntimeException e) {
            return Result.error(502, "摄像头编码切换失败：" + errorMessage(e));
        }
    }

    @ApiOperation("查询摄像头视频编码格式")
    @GetMapping("/encode-type")
    public Result<Map<String, Object>> queryEncodeType(
            @RequestParam String deviceSerial,
            @RequestParam(required = false) Integer channelNo,
            @RequestParam(defaultValue = "1") Integer streamType) {
        Integer videoCode = ezvizService.getEncodeType(deviceSerial, channelNo, streamType);
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("deviceSerial", deviceSerial);
        result.put("channelNo", channelNo);
        result.put("streamType", streamType);
        result.put("videoCode", videoCode);
        String label = "未知";
        if (videoCode != null) {
            switch (videoCode) {
                case 0: label = "私有H264"; break;
                case 1: label = "H.264"; break;
                case 5: label = "H.265"; break;
                case 6: label = "SMART264"; break;
                case 7: label = "SMART265"; break;
            }
        }
        result.put("label", label);
        boolean isH264 = videoCode != null && (videoCode == 0 || videoCode == 1 || videoCode == 6);
        result.put("isH264", isH264);
        // 萤石云未提供"查询当前编码类型"的公开接口，此处返回的是本地缓存的最近一次设置编码
        // source=local-cached 表示本地有设置记录；source=unknown 表示从未通过本系统设置过
        result.put("source", videoCode == null ? "unknown" : "local-cached");
        return Result.ok(result);
    }

    @ApiOperation("获取账号下所有摄像头设备列表")
    @GetMapping("/devices")
    public Result<JSONArray> getDevices() {
        JSONArray devices = ezvizService.getDeviceList();
        return Result.ok(devices);
    }

    @ApiOperation("获取NVR设备下的通道列表")
    @GetMapping("/{deviceSerial}/channels")
    public Result<JSONArray> getChannels(@PathVariable String deviceSerial) {
        JSONArray channels = ezvizService.getCameraList(deviceSerial);
        return Result.ok(channels);
    }

    @ApiOperation("批量修改摄像头视频编码类型（支持通道）")
    @PostMapping("/encode-type/batch")
    public Result<Map<String, String>> batchChangeEncodeType(@RequestBody BatchEncodeRequest request) {
        Map<String, String> results;
        if (request.getTargets() != null && !request.getTargets().isEmpty()) {
            results = ezvizService.batchChangeEncodeTypeByTargets(
                    request.getTargets(), request.getEncodeType(), request.getStreamType());
        } else {
            results = ezvizService.batchChangeEncodeType(
                    request.getDeviceSerials(), request.getEncodeType(), request.getStreamType());
        }
        return Result.ok(results);
    }

    static class BatchEncodeRequest {
        private List<String> deviceSerials;
        private List<EncodeTarget> targets;
        private String encodeType = "H264";
        private Integer streamType = 1;

        public List<String> getDeviceSerials() { return deviceSerials; }
        public void setDeviceSerials(List<String> deviceSerials) { this.deviceSerials = deviceSerials; }
        public List<EncodeTarget> getTargets() { return targets; }
        public void setTargets(List<EncodeTarget> targets) { this.targets = targets; }
        public String getEncodeType() { return encodeType; }
        public void setEncodeType(String encodeType) { this.encodeType = encodeType; }
        public Integer getStreamType() { return streamType; }
        public void setStreamType(Integer streamType) { this.streamType = streamType; }
    }

    private String resolveDeviceSerial(String requested) {
        String serial = requested == null || requested.trim().isEmpty()
                ? ysjProperties.getDeviceSerial() : requested;
        if (serial == null || serial.trim().isEmpty()) {
            throw new IllegalArgumentException("请配置 YS7_DEVICE_SERIAL");
        }
        return serial.trim();
    }

    private String errorMessage(RuntimeException error) {
        return error.getMessage() == null ? "未知错误" : error.getMessage();
    }
}
