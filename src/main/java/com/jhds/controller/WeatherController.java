package com.jhds.controller;

import com.jhds.common.Constants;
import com.jhds.common.Result;
import com.jhds.entity.WeatherSensorData;
import com.jhds.entity.WeatherThresholdConfig;
import com.jhds.mapper.WeatherThresholdConfigMapper;
import com.jhds.service.WeatherService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Api(tags = "气象站传感器模块")
@RestController
@RequestMapping("/api/weather")
public class WeatherController {

    @Autowired
    private WeatherService weatherService;
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;
    @Autowired
    private WeatherThresholdConfigMapper thresholdConfigMapper;

    @ApiOperation("获取当前气象数据")
    @GetMapping("/current")
    public Result<WeatherSensorData> getCurrent() {
        return Result.ok(weatherService.getCurrent());
    }

    @ApiOperation("获取历史数据折线图")
    @GetMapping("/history")
    public Result<List<WeatherSensorData>> getHistory(@RequestParam(defaultValue = "2") int days) {
        return Result.ok(weatherService.getHistory(days));
    }

    @ApiOperation("获取报警阈值配置")
    @GetMapping("/threshold")
    public Result<WeatherThresholdConfig> getThreshold() {
        WeatherThresholdConfig cfg = thresholdConfigMapper.selectById(1);
        if (cfg == null) {
            cfg = new WeatherThresholdConfig();
            cfg.setTempMin(new java.math.BigDecimal("5.0"));
            cfg.setTempMax(new java.math.BigDecimal("40.0"));
            cfg.setHumidityMin(new java.math.BigDecimal("30.0"));
            cfg.setHumidityMax(new java.math.BigDecimal("90.0"));
            cfg.setWindSpeedMax(new java.math.BigDecimal("10.0"));
            cfg.setTotalRainfallMax(new java.math.BigDecimal("100.0"));
            cfg.setHourlyRainfallMax(new java.math.BigDecimal("20.0"));
            cfg.setDailyRainfallMax(new java.math.BigDecimal("50.0"));
            cfg.setLightMin(new java.math.BigDecimal("5000.0"));
            cfg.setLightMax(new java.math.BigDecimal("100000.0"));
            cfg.setUvIntensityMax(new java.math.BigDecimal("200.0"));
            cfg.setUvIndexMax(new java.math.BigDecimal("8.0"));
            cfg.setBatteryAlarm(1);
            cfg.setEnabled(1);
        }
        return Result.ok(cfg);
    }

    @ApiOperation("更新报警阈值配置")
    @PutMapping("/threshold")
    public Result<Void> updateThreshold(@RequestBody WeatherThresholdConfig config) {
        config.setId(1L);
        config.setUpdatedAt(new Date());
        if (thresholdConfigMapper.selectById(1) != null) {
            thresholdConfigMapper.updateById(config);
        } else {
            thresholdConfigMapper.insert(config);
        }
        return Result.ok();
    }

    @ApiOperation("获取设备心跳状态")
    @GetMapping("/heartbeat")
    public Result<Map<String, Object>> getHeartbeat() {
        Map<String, Object> result = new HashMap<>();
        Object value = redisTemplate.opsForValue().get(Constants.REDIS_HEARTBEAT_KEY);
        long now = System.currentTimeMillis();
        long threshold = 60000;

        if (value != null) {
            long lastHeartbeat = Long.parseLong(value.toString());
            long diff = now - lastHeartbeat;
            boolean online = diff < threshold;

            result.put("lastHeartbeat", lastHeartbeat);
            result.put("online", online);
            result.put("relativeTime", formatRelative(diff));
        } else {
            result.put("lastHeartbeat", null);
            result.put("online", false);
            result.put("relativeTime", "无心跳数据");
        }
        return Result.ok(result);
    }

    private String formatRelative(long diffMs) {
        long sec = diffMs / 1000;
        if (sec < 5) return "刚刚";
        if (sec < 60) return sec + "秒前";
        long min = sec / 60;
        if (min < 60) return min + "分钟前";
        long hour = min / 60;
        if (hour < 24) return hour + "小时前";
        return (hour / 24) + "天前";
    }
}
