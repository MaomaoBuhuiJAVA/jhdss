package com.jhds.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jhds.common.Constants;
import com.jhds.common.WeatherStationProtocol;
import com.jhds.common.ModbusUtil;
import com.jhds.entity.SoilSensorData;
import com.jhds.entity.WeatherSensorData;
import com.jhds.entity.WeatherThresholdConfig;
import com.jhds.mapper.SoilSensorDataMapper;
import com.jhds.mapper.WeatherSensorDataMapper;
import com.jhds.mapper.WeatherThresholdConfigMapper;
import com.jhds.mapper.WeatherStationProtocolMapper;
import com.jhds.entity.WeatherStationProtocolEntity;
import com.jhds.service.mqtt.MqttService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.SimpleDateFormat;
import java.util.*;

@Slf4j
@Service
public class WeatherService {

    private static final long HEX_TIMEOUT_MS = 3000;

    @Autowired
    private WeatherSensorDataMapper weatherSensorDataMapper;
    @Autowired
    private SoilSensorDataMapper soilSensorDataMapper;
    @Autowired
    private MqttService mqttService;
    @Autowired
    private ControlLogService controlLogService;
    @Autowired
    private WeatherStationProtocolMapper protocolMapper;
    @Autowired
    private WeatherThresholdConfigMapper thresholdConfigMapper;
    @Autowired
    private AlarmService alarmService;
    @Autowired
    private org.springframework.data.redis.core.RedisTemplate<String, Object> redisTemplate;

    // battery 查询频率控制：每 5 次轮询查一次
    private int batteryQueryCounter = 0;

    public WeatherSensorData getCurrent() {
        List<WeatherSensorData> list = weatherSensorDataMapper.selectList(
                new LambdaQueryWrapper<WeatherSensorData>()
                        .orderByDesc(WeatherSensorData::getRecordTime)
                        .last("LIMIT 1"));
        return list.isEmpty() ? null : list.get(0);
    }

    public List<WeatherSensorData> getHistory(int days) {
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_YEAR, -days);
        String startTime = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(cal.getTime());
        return weatherSensorDataMapper.selectHistory(startTime);
    }

    public void collectSensorData() {
        WeatherSensorData data = new WeatherSensorData();
        int count = 0;

        try {
            mqttService.lockSequential();

            Map<String, String> cmdMap = new HashMap<>();
            try {
                List<WeatherStationProtocolEntity> list = protocolMapper.selectList(
                        new LambdaQueryWrapper<WeatherStationProtocolEntity>()
                                .eq(WeatherStationProtocolEntity::getEnabled, 1));
                for (WeatherStationProtocolEntity e : list) {
                    cmdMap.put(e.getSensorKey(), e.getCommandHex());
                }
            } catch (Exception e) {
                log.warn("Failed to load protocol from DB", e);
            }

            for (WeatherStationProtocol type : WeatherStationProtocol.values()) {
                boolean shouldQueryBattery = true;
                if (type == WeatherStationProtocol.BATTERY) {
                    batteryQueryCounter++;
                    shouldQueryBattery = batteryQueryCounter % 5 == 0;
                    if (!shouldQueryBattery) continue;
                }

                String cmdHex = cmdMap.get(type.name());
                if (cmdHex == null) {
                    log.warn("No command configured for sensor: {}", type.name());
                    continue;
                }
                String hexResp = mqttService.sendHexSync(cmdHex, HEX_TIMEOUT_MS);
                String displayName = type.getDisplayName();

                if (hexResp == null) {
                    log.warn("No response for: {}", displayName);
                    controlLogService.log("WEATHER_STATION", "气象站", displayName, 1, cmdHex, null, 0);
                    continue;
                }

                String normalized = hexResp.trim().replaceAll("\\s+", " ");
                String[] respBytes = normalized.split(" ");
                if (respBytes.length >= 4) {
                    int byteCount = Integer.parseInt(respBytes[2], 16);
                    int frameLen = byteCount + 5;
                    if (respBytes.length > frameLen) {
                        respBytes = java.util.Arrays.copyOf(respBytes, frameLen);
                        normalized = String.join(" ", respBytes);
                    }
                }
                String[] cmdParts = cmdHex.split(" ");
                String[] normParts = normalized.split(" ");
                if (normParts.length < 2 || !normParts[1].equals(cmdParts[1])) {
                    log.warn("Response function code mismatch for {}: expected '{}', got '{}'",
                            displayName, cmdParts[1], normParts.length > 1 ? normParts[1] : "?");
                    controlLogService.log("WEATHER_STATION", "气象站", displayName, 1, cmdHex, normalized, 0);
                    continue;
                }

                if (!ModbusUtil.verifyCRC(normalized)) {
                    log.warn("CRC mismatch for {}: {}", displayName, normalized);
                    controlLogService.log("WEATHER_STATION", "气象站", displayName, 1, cmdHex, normalized, 0);
                    continue;
                }

                type.setValue(data, normalized);
                count++;

                controlLogService.log("WEATHER_STATION", "气象站", displayName, 1, cmdHex, normalized, 1);
            }
        } catch (Exception e) {
            log.error("collectSensorData error", e);
        } finally {
            mqttService.unlockSequential();
        }

        if (count > 0) {
            data.setRecordTime(new Date());
            weatherSensorDataMapper.insert(data);
            log.info("Weather data collected: {} fields, temperature={}, humidity={}",
                    count, data.getTemperature(), data.getHumidity());
            checkThresholds(data);
        } else {
            log.warn("No weather data collected this cycle");
        }

        cleanOldWeatherData(48);
    }

    private void checkThresholds(WeatherSensorData data) {
        try {
            WeatherThresholdConfig cfg = thresholdConfigMapper.selectById(1);
            if (cfg == null || cfg.getEnabled() == null || cfg.getEnabled() != 1) return;

            String weatherLocation = "气象站";

            checkOne("TEMPERATURE_HIGH", "温度过高", "环境温度 %.1f°C 超过上限 %.1f°C",
                    data.getTemperature(), null, cfg.getTempMax(), Constants.ALARM_IMPORTANT);
            checkOne("TEMPERATURE_LOW", "温度过低", "环境温度 %.1f°C 低于下限 %.1f°C",
                    data.getTemperature(), cfg.getTempMin(), null, Constants.ALARM_IMPORTANT);
            checkOne("HUMIDITY_HIGH", "湿度过高", "环境湿度 %.1f%% 超过上限 %.1f%%",
                    data.getHumidity(), null, cfg.getHumidityMax(), Constants.ALARM_NORMAL);
            checkOne("HUMIDITY_LOW", "湿度过低", "环境湿度 %.1f%% 低于下限 %.1f%%",
                    data.getHumidity(), cfg.getHumidityMin(), null, Constants.ALARM_NORMAL);
            checkOne("WIND_SPEED_HIGH", "风速过大", "风速 %.1fm/s 超过上限 %.1fm/s",
                    data.getWindSpeed(), null, cfg.getWindSpeedMax(), Constants.ALARM_NORMAL);
            checkOne("RAINFALL_HIGH", "累计雨量过大", "累计雨量 %.1fmm 超过上限 %.1fmm",
                    data.getRainfall(), null, cfg.getTotalRainfallMax(), Constants.ALARM_NORMAL);
            checkOne("HOURLY_RAIN_HIGH", "小时雨量过大", "小时雨量 %.1fmm 超过上限 %.1fmm",
                    data.getHourlyRainfall(), null, cfg.getHourlyRainfallMax(), Constants.ALARM_NORMAL);
            checkOne("DAILY_RAIN_HIGH", "日雨量过大", "日雨量 %.1fmm 超过上限 %.1fmm",
                    data.getDailyRainfall(), null, cfg.getDailyRainfallMax(), Constants.ALARM_NORMAL);
            checkOne("LIGHT_HIGH", "光照过强", "光照强度 %.0fLux 超过上限 %.0fLux",
                    data.getLightIntensity(), null, cfg.getLightMax(), Constants.ALARM_NORMAL);
            checkOne("LIGHT_LOW", "光照不足", "光照强度 %.0fLux 低于下限 %.0fLux",
                    data.getLightIntensity(), cfg.getLightMin(), null, Constants.ALARM_NORMAL);
            checkOne("UV_HIGH", "紫外线强度过高", "紫外线强度 %.1fuW/cm² 超过上限 %.1fuW/cm²",
                    data.getUvIntensity(), null, cfg.getUvIntensityMax(), Constants.ALARM_NORMAL);
            checkOne("UV_INDEX_HIGH", "紫外线指数过高", "紫外线指数 %.1f 超过上限 %.1f",
                    data.getUvIndex() != null ? new java.math.BigDecimal(data.getUvIndex().doubleValue()) : null,
                    null, cfg.getUvIndexMax(), Constants.ALARM_NORMAL);

            if (cfg.getBatteryAlarm() != null && cfg.getBatteryAlarm() == 1
                    && data.getBatteryStatus() != null && data.getBatteryStatus() == 1) {
                String key = Constants.REDIS_KEY_PREFIX + "weather:alarm:BATTERY_LOW";
                Boolean exists = redisTemplate.hasKey(key);
                if (exists == null || !exists) {
                    alarmService.createAlarm("设备电量不足",
                            "气象站设备电量不足，需要更换电池", Constants.ALARM_IMPORTANT, "weather", weatherLocation);
                    redisTemplate.opsForValue().set(key, "1", java.time.Duration.ofMinutes(30));
                }
            }
        } catch (Exception e) {
            log.warn("checkThresholds error", e);
        }
    }

    private void checkOne(String alarmKey, String title, String descTmpl,
                          java.math.BigDecimal value, java.math.BigDecimal min, java.math.BigDecimal max,
                          String level) {
        if (value == null) return;
        boolean triggered = false;
        if (min != null && value.compareTo(min) < 0) triggered = true;
        if (max != null && value.compareTo(max) > 0) triggered = true;
        if (!triggered) return;

        String key = Constants.REDIS_KEY_PREFIX + "weather:alarm:" + alarmKey;
        Boolean exists = redisTemplate.hasKey(key);
        if (exists != null && exists) return;

        String desc = String.format(descTmpl, value.doubleValue(),
                (max != null ? max : min).doubleValue());
        alarmService.createAlarm(title, desc, level, "weather", "气象站");
        redisTemplate.opsForValue().set(key, "1", java.time.Duration.ofMinutes(30));
    }

    private void cleanOldWeatherData(int hours) {
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.HOUR_OF_DAY, -hours);
        int deleted = weatherSensorDataMapper.delete(
                new LambdaQueryWrapper<WeatherSensorData>()
                        .lt(WeatherSensorData::getRecordTime, cal.getTime()));
        if (deleted > 0) {
            log.info("Cleaned {} weather records older than {} hours", deleted, hours);
        }
    }

    public void collectSoilData() {
        String response = mqttService.sendCommand("SOIL_SENSOR", "query", true);
        if (response == null) return;
        SoilSensorData data = parseSoilData(response);
        if (data != null) {
            data.setRecordTime(new Date());
            soilSensorDataMapper.insert(data);
        }
    }

    private SoilSensorData parseSoilData(String response) {
        try {
            com.alibaba.fastjson.JSONObject json = com.alibaba.fastjson.JSON.parseObject(response);
            SoilSensorData data = new SoilSensorData();
            data.setSoilTemp(json.getBigDecimal("soilTemp"));
            data.setSoilHumidity(json.getBigDecimal("soilHumidity"));
            data.setSoilEc(json.getBigDecimal("soilEc"));
            data.setSoilPh(json.getBigDecimal("soilPh"));
            if (data.getSoilTemp() == null) return null;
            return data;
        } catch (Exception e) {
            return null;
        }
    }

    public void collectSoilSensorDataByHex() {
        try {
            mqttService.lockSequential();

            String cmdHex = null;
            try {
                WeatherStationProtocolEntity entity = protocolMapper.selectOne(
                        new LambdaQueryWrapper<WeatherStationProtocolEntity>()
                                .eq(WeatherStationProtocolEntity::getSensorKey, "SOIL_SENSOR")
                                .eq(WeatherStationProtocolEntity::getEnabled, 1));
                if (entity != null) {
                    cmdHex = entity.getCommandHex();
                }
            } catch (Exception e) {
                log.warn("Failed to load SOIL_SENSOR protocol from DB", e);
            }

            if (cmdHex == null) {
                log.warn("No SOIL_SENSOR protocol config found");
                return;
            }

            String hexResp = mqttService.sendHexSync(cmdHex, HEX_TIMEOUT_MS);
            if (hexResp == null) {
                log.warn("No response for SOIL_SENSOR");
                controlLogService.log("SOIL_SENSOR", "多合一土壤传感器", "查询", 1, cmdHex, null, 0);
                return;
            }

            String normalized = hexResp.trim().replaceAll("\\s+", " ");
            String[] respBytes = normalized.split(" ");
            if (respBytes.length >= 4) {
                int byteCount = Integer.parseInt(respBytes[2], 16);
                int frameLen = byteCount + 5;
                if (respBytes.length > frameLen) {
                    respBytes = java.util.Arrays.copyOf(respBytes, frameLen);
                    normalized = String.join(" ", respBytes);
                }
            }
            String[] cmdParts = cmdHex.split(" ");
            String[] normParts = normalized.split(" ");
            if (normParts.length < 2 || !normParts[1].equals(cmdParts[1])) {
                log.warn("Response function code mismatch for SOIL_SENSOR: expected '{}', got '{}'",
                        cmdParts[1], normParts.length > 1 ? normParts[1] : "?");
                controlLogService.log("SOIL_SENSOR", "多合一土壤传感器", "查询", 1, cmdHex, normalized, 0);
                return;
            }

            if (!ModbusUtil.verifyCRC(normalized)) {
                log.warn("CRC mismatch for SOIL_SENSOR: {}", normalized);
                controlLogService.log("SOIL_SENSOR", "多合一土壤传感器", "查询", 1, cmdHex, normalized, 0);
                return;
            }

            SoilSensorData data = parseSoilSensorHexResponse(normalized);
            if (data != null) {
                data.setRecordTime(new Date());
                soilSensorDataMapper.insert(data);
                log.info("Soil sensor data collected via hex: temp={}, humidity={}, ec={}, ph={}",
                        data.getSoilTemp(), data.getSoilHumidity(), data.getSoilEc(), data.getSoilPh());
                controlLogService.log("SOIL_SENSOR", "多合一土壤传感器", "查询", 1, cmdHex, normalized, 1);
            }
        } catch (Exception e) {
            log.error("collectSoilSensorDataByHex error", e);
        } finally {
            mqttService.unlockSequential();
        }
    }

    private SoilSensorData parseSoilSensorHexResponse(String hexResp) {
        try {
            byte[] bytes = ModbusUtil.hexToBytes(hexResp);
            if (bytes.length < 21) return null;

            SoilSensorData data = new SoilSensorData();
            data.setSoilTemp(BigDecimal.valueOf(ModbusUtil.parseRegister(bytes, 1)).divide(BigDecimal.TEN, 1, RoundingMode.HALF_UP));
            data.setSoilHumidity(BigDecimal.valueOf(ModbusUtil.parseRegister(bytes, 2)));
            data.setSoilEc(BigDecimal.valueOf(ModbusUtil.parseRegister(bytes, 3)));
            data.setSoilSalt(BigDecimal.valueOf(ModbusUtil.parseRegister(bytes, 4)));
            data.setSoilNitrogen(BigDecimal.valueOf(ModbusUtil.parseRegister(bytes, 5)));
            data.setSoilPhosphorus(BigDecimal.valueOf(ModbusUtil.parseRegister(bytes, 6)));
            data.setSoilPotassium(BigDecimal.valueOf(ModbusUtil.parseRegister(bytes, 7)));
            data.setSoilPh(BigDecimal.valueOf(ModbusUtil.parseRegister(bytes, 8)).divide(BigDecimal.TEN, 1, RoundingMode.HALF_UP));
            return data;
        } catch (Exception e) {
            log.warn("Failed to parse soil sensor hex response: {}", hexResp, e);
            return null;
        }
    }
}
