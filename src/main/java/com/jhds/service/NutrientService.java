package com.jhds.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jhds.common.Constants;
import com.jhds.entity.*;
import com.jhds.mapper.*;
import com.jhds.service.mqtt.MqttService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
public class NutrientService {

    @Autowired
    private SoilSensorDataMapper soilSensorDataMapper;
    @Autowired
    private EquipmentMapper equipmentMapper;
    @Autowired
    private IrrigationScheduleMapper scheduleMapper;
    @Autowired
    private IrrigationRecordMapper irrigationRecordMapper;
    @Autowired
    private MqttService mqttService;
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    private static final String MODE_KEY = Constants.REDIS_KEY_PREFIX + "nutrient:mode";
    private static final String IRRIGATING_KEY = Constants.REDIS_KEY_PREFIX + "nutrient:irrigating";
    private static final String LAST_IRRIGATE_DATE_KEY = Constants.REDIS_KEY_PREFIX + "nutrient:last_date";

    public SoilSensorData getSoilData() {
        List<SoilSensorData> list = soilSensorDataMapper.selectList(
                new LambdaQueryWrapper<SoilSensorData>()
                        .orderByDesc(SoilSensorData::getRecordTime)
                        .last("LIMIT 1"));
        return list.isEmpty() ? null : list.get(0);
    }

    public List<SoilSensorData> getSoilHistory(int days) {
        java.util.Calendar cal = java.util.Calendar.getInstance();
        cal.add(java.util.Calendar.DAY_OF_YEAR, -days);
        return soilSensorDataMapper.selectList(
                new LambdaQueryWrapper<SoilSensorData>()
                        .ge(SoilSensorData::getRecordTime, cal.getTime())
                        .orderByAsc(SoilSensorData::getRecordTime));
    }

    public List<Equipment> getPumps() {
        return equipmentMapper.selectList(null);
    }

    public String controlPump(String alias, Integer status) {
        String value = status == 1 ? "open" : "close";
        return mqttService.sendCommand(alias, value, false);
    }

    public void switchMode(String mode) {
        redisTemplate.opsForValue().set(MODE_KEY, mode);
    }

    public String getMode() {
        Object mode = redisTemplate.opsForValue().get(MODE_KEY);
        return mode != null ? mode.toString() : Constants.MODE_MANUAL;
    }

    public void saveSchedule(IrrigationSchedule schedule) {
        scheduleMapper.insert(schedule);
    }

    public List<IrrigationSchedule> getSchedules() {
        return scheduleMapper.selectList(
                new LambdaQueryWrapper<IrrigationSchedule>()
                        .orderByAsc(IrrigationSchedule::getScheduleTime));
    }

    public List<IrrigationRecord> getRecords() {
        return irrigationRecordMapper.selectList(
                new LambdaQueryWrapper<IrrigationRecord>()
                        .orderByDesc(IrrigationRecord::getCreatedAt)
                        .last("LIMIT 50"));
    }

    public Map<String, Object> getStats() {
        Map<String, Object> stats = new HashMap<>();
        List<IrrigationRecord> todayRecords = irrigationRecordMapper.selectList(
                new LambdaQueryWrapper<IrrigationRecord>()
                        .ge(IrrigationRecord::getCreatedAt, getTodayStart()));
        int count = todayRecords.size();
        stats.put("todayCount", count);
        stats.put("totalWater", todayRecords.stream()
                .mapToInt(r -> r.getDuration() / 60 * 15)
                .sum());
        return stats;
    }

    private Date getTodayStart() {
        java.util.Calendar cal = java.util.Calendar.getInstance();
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0);
        cal.set(java.util.Calendar.MINUTE, 0);
        cal.set(java.util.Calendar.SECOND, 0);
        return cal.getTime();
    }

    public void autoIrrigate() {
        String mode = getMode();
        if (!Constants.MODE_AUTO.equals(mode)) return;

        if (!mqttService.isConnected()) return;

        // 检查是否正在灌溉中 → 处理关泵
        Object irrigating = redisTemplate.opsForValue().get(IRRIGATING_KEY);
        if (irrigating != null) {
            long startTime = Long.parseLong(irrigating.toString());
            long elapsed = System.currentTimeMillis() - startTime;
            int durationMinutes = getIrrigatingDuration();
            if (elapsed >= durationMinutes * 60 * 1000L) {
                mqttService.sendCommand("PUMP_IRRIGATE", "close", true);
                redisTemplate.delete(IRRIGATING_KEY);
                redisTemplate.delete(IRRIGATING_KEY + ":duration");
                logIrrigationRecord("auto", "PUMP_IRRIGATE", durationMinutes * 60);
            }
            return;
        }

        // 检查是否需要开泵灌溉
        List<IrrigationSchedule> schedules = scheduleMapper.selectList(
                new LambdaQueryWrapper<IrrigationSchedule>()
                        .eq(IrrigationSchedule::getEnabled, 1));
        LocalTime now = LocalTime.now();
        for (IrrigationSchedule schedule : schedules) {
            LocalTime schedTime = schedule.getScheduleTime();
            if (now.getHour() != schedTime.getHour() || now.getMinute() != schedTime.getMinute()) {
                continue;
            }
            if (!isFrequencyAllowed(schedule.getFrequency())) {
                continue;
            }
            mqttService.sendCommand("PUMP_IRRIGATE", "open", true);
            redisTemplate.opsForValue().set(IRRIGATING_KEY,
                    String.valueOf(System.currentTimeMillis()),
                    schedule.getDuration() + 5, TimeUnit.MINUTES);
            redisTemplate.opsForValue().set(IRRIGATING_KEY + ":duration",
                    schedule.getDuration(), 30, TimeUnit.MINUTES);
            redisTemplate.opsForValue().set(LAST_IRRIGATE_DATE_KEY,
                    LocalDate.now().toString(), 7, TimeUnit.DAYS);
            break;
        }
    }

    private boolean isFrequencyAllowed(String frequency) {
        if (frequency == null || "daily".equals(frequency)) return true;
        if ("alternate".equals(frequency)) {
            Object lastDate = redisTemplate.opsForValue().get(LAST_IRRIGATE_DATE_KEY);
            if (lastDate != null && lastDate.toString().equals(LocalDate.now().toString())) {
                return false;
            }
            String yesterday = LocalDate.now().minusDays(1).toString();
            return lastDate == null || !lastDate.toString().equals(yesterday);
        }
        return false;
    }

    private int getIrrigatingDuration() {
        Object d = redisTemplate.opsForValue().get(IRRIGATING_KEY + ":duration");
        return d != null ? Integer.parseInt(d.toString()) : 10;
    }

    private void logIrrigationRecord(String mode, String pumpAlias, int duration) {
        IrrigationRecord record = new IrrigationRecord();
        record.setMode(mode);
        record.setPumpAlias(pumpAlias);
        record.setDuration(duration);
        record.setCreatedAt(new Date());
        irrigationRecordMapper.insert(record);
    }
}
