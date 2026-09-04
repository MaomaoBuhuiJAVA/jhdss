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
import java.time.LocalDateTime;
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
    private SystemSettingMapper systemSettingMapper;
    @Autowired
    private MqttService mqttService;
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    private static final String MODE_KEY = Constants.REDIS_KEY_PREFIX + "nutrient:mode";
    private static final String IRRIGATING_KEY = Constants.REDIS_KEY_PREFIX + "nutrient:irrigating";
    private static final String LAST_IRRIGATE_DATE_KEY = Constants.REDIS_KEY_PREFIX + "nutrient:last_date";
    private static final String NUTRIENT_MODE_SETTING = "nutrient.mode";
    private static final String PUMP_START_KEY_PREFIX = Constants.REDIS_KEY_PREFIX + "nutrient:pump:start:";

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
        return equipmentMapper.selectList(new LambdaQueryWrapper<Equipment>()
                .eq(Equipment::getType, 1)
                .orderByAsc(Equipment::getId));
    }

    public String controlPump(String alias, Integer status) {
        if (alias == null || alias.trim().isEmpty() || status == null || (status != 0 && status != 1)) {
            throw new IllegalArgumentException("设备或开关状态无效");
        }
        Equipment equipment = equipmentMapper.selectByAlias(alias);
        if (equipment == null) {
            throw new IllegalArgumentException("设备不存在: " + alias);
        }
        int previousStatus = equipment.getStatus() == null ? 0 : equipment.getStatus();
        String value = status == 1 ? "open" : "close";
        String response = mqttService.sendCommand(alias, value, false);

        // Local/demo devices intentionally have no MQTT command. Their status is
        // still persisted so the web UI and database remain in sync.
        if (response == null) {
            if (equipment != null && (equipment.getOpenCode() == null || equipment.getOpenCode().trim().isEmpty())
                    && (equipment.getCloseCode() == null || equipment.getCloseCode().trim().isEmpty())) {
                equipment.setStatus(status);
                equipmentMapper.updateById(equipment);
                response = "LOCAL_SAVED";
            } else {
                // Do not persist a hardware state when the DTU/controller did
                // not acknowledge the command. The UI can then roll back the
                // switch and show the real communication failure.
                throw new IllegalStateException("设备未响应，数据库状态未修改");
            }
        }
        if (response != null) {
            recordPumpTransition(alias, previousStatus, status);
        }
        return response;
    }

    public void switchMode(String mode) {
        if (!Constants.MODE_MANUAL.equals(mode) && !Constants.MODE_AUTO.equals(mode) && !"ai".equals(mode)) {
            throw new IllegalArgumentException("不支持的配液模式");
        }
        SystemSetting setting = systemSettingMapper.selectById(NUTRIENT_MODE_SETTING);
        if (setting == null) {
            setting = new SystemSetting();
            setting.setSettingKey(NUTRIENT_MODE_SETTING);
            setting.setSettingValue(mode);
            systemSettingMapper.insert(setting);
        } else {
            setting.setSettingValue(mode);
            systemSettingMapper.updateById(setting);
        }
        redisTemplate.opsForValue().set(MODE_KEY, mode);
    }

    public String getMode() {
        SystemSetting setting = systemSettingMapper.selectById(NUTRIENT_MODE_SETTING);
        if (setting != null && setting.getSettingValue() != null && !setting.getSettingValue().trim().isEmpty()) {
            String mode = setting.getSettingValue();
            redisTemplate.opsForValue().set(MODE_KEY, mode);
            return mode;
        }
        Object mode = redisTemplate.opsForValue().get(MODE_KEY);
        return mode != null ? mode.toString() : Constants.MODE_MANUAL;
    }

    public void saveSchedule(IrrigationSchedule schedule) {
        if (schedule == null || schedule.getScheduleTime() == null) {
            throw new IllegalArgumentException("请选择灌溉时间");
        }
        if (schedule.getDuration() == null || schedule.getDuration() <= 0) {
            throw new IllegalArgumentException("灌溉时长必须大于 0");
        }
        if (schedule.getFrequency() == null || schedule.getFrequency().trim().isEmpty()) {
            schedule.setFrequency("daily");
        }
        if (schedule.getEnabled() == null) schedule.setEnabled(1);
        if (schedule.getId() == null) scheduleMapper.insert(schedule);
        else scheduleMapper.updateById(schedule);
    }

    public void deleteSchedule(Long id) {
        if (id == null) throw new IllegalArgumentException("计划 ID 不能为空");
        scheduleMapper.deleteById(id);
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
        long totalSeconds = todayRecords.stream()
                .mapToLong(r -> r.getDuration() == null ? 0L : Math.max(0, r.getDuration()))
                .sum();
        stats.put("totalWater", Math.round(totalSeconds / 60.0 * 15.0));
        stats.put("pumpA", runtimeMinutes(todayRecords, "PUMP_A"));
        stats.put("pumpB", runtimeMinutes(todayRecords, "PUMP_B"));
        stats.put("pumpAcid", runtimeMinutes(todayRecords, "PUMP_ACID"));
        stats.put("pumpBase", runtimeMinutes(todayRecords, "PUMP_BASE"));
        stats.put("nextIrrigationTime", getNextIrrigationTime());
        stats.put("recentRecords", getRecords().subList(0, Math.min(8, getRecords().size())));
        return stats;
    }

    private long runtimeMinutes(List<IrrigationRecord> records, String alias) {
        long seconds = records.stream()
                .filter(record -> alias.equals(record.getPumpAlias()))
                .mapToLong(record -> record.getDuration() == null ? 0L : Math.max(0, record.getDuration()))
                .sum();
        return Math.round(seconds / 60.0);
    }

    /** Returns the next enabled schedule using the actual persisted schedule rows. */
    private String getNextIrrigationTime() {
        List<IrrigationSchedule> schedules = getSchedules();
        if (schedules.isEmpty()) return null;
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime next = null;
        for (IrrigationSchedule schedule : schedules) {
            if (schedule.getEnabled() != null && schedule.getEnabled() == 0) continue;
            if (schedule.getScheduleTime() == null) continue;
            LocalDate date = now.toLocalDate();
            LocalDateTime candidate = LocalDateTime.of(date, schedule.getScheduleTime());
            if (!candidate.isAfter(now)) candidate = candidate.plusDays(1);
            for (int i = 0; i < 8 && !isScheduleDateAllowed(schedule, candidate.toLocalDate()); i++) {
                candidate = candidate.plusDays(1);
            }
            if (!isScheduleDateAllowed(schedule, candidate.toLocalDate())) continue;
            if (next == null || candidate.isBefore(next)) next = candidate;
        }
        return next == null ? null : next.toLocalTime().toString().substring(0, 5);
    }

    private boolean isScheduleDateAllowed(IrrigationSchedule schedule, LocalDate date) {
        String frequency = schedule.getFrequency();
        if (frequency == null || frequency.trim().isEmpty() || "daily".equals(frequency) || "custom".equals(frequency)) {
            return true;
        }
        if ("mon_wed_fri".equals(frequency)) {
            java.time.DayOfWeek day = date.getDayOfWeek();
            return day == java.time.DayOfWeek.MONDAY
                    || day == java.time.DayOfWeek.WEDNESDAY
                    || day == java.time.DayOfWeek.FRIDAY;
        }
        if ("alternate".equals(frequency)) {
            List<IrrigationRecord> records = irrigationRecordMapper.selectList(
                    new LambdaQueryWrapper<IrrigationRecord>()
                            .eq(IrrigationRecord::getPumpAlias, "PUMP_IRRIGATE")
                            .orderByDesc(IrrigationRecord::getCreatedAt)
                            .last("LIMIT 1"));
            if (records.isEmpty() || records.get(0).getCreatedAt() == null) return true;
            LocalDate lastDate = records.get(0).getCreatedAt().toInstant()
                    .atZone(java.time.ZoneId.systemDefault()).toLocalDate();
            return !date.isBefore(lastDate.plusDays(2));
        }
        return true;
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
        if ("mon_wed_fri".equals(frequency)) {
            java.time.DayOfWeek day = LocalDate.now().getDayOfWeek();
            return day == java.time.DayOfWeek.MONDAY
                    || day == java.time.DayOfWeek.WEDNESDAY
                    || day == java.time.DayOfWeek.FRIDAY;
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

    private void recordPumpTransition(String alias, int previousStatus, int status) {
        String key = PUMP_START_KEY_PREFIX + alias;
        if (status == 1) {
            if (previousStatus != 1 && redisTemplate.opsForValue().get(key) == null) {
                redisTemplate.opsForValue().set(key, String.valueOf(System.currentTimeMillis()), 2, TimeUnit.DAYS);
            }
            return;
        }
        Object started = redisTemplate.opsForValue().get(key);
        redisTemplate.delete(key);
        if (started == null) return;
        try {
            long elapsedSeconds = Math.max(1L,
                    (System.currentTimeMillis() - Long.parseLong(started.toString())) / 1000L);
            logIrrigationRecord("manual", alias,
                    (int) Math.min(Integer.MAX_VALUE, elapsedSeconds));
        } catch (NumberFormatException ignored) {
            // A malformed transient Redis value must not prevent the pump command.
        }
    }
}
