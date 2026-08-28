package com.jhds.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.Locale;

/**
 * Applies small additive migrations needed by the locally running application.
 * The initializer never drops tables or overwrites user-managed records.
 */
@Component
public class DatabaseSchemaInitializer {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @PostConstruct
    public void initialize() {
        createSystemSettingTable();
        createAlarmRecordTable();
        createWeatherThresholdTable();
        ensureColumn("alarm_record", "handling_memo", "TEXT DEFAULT NULL COMMENT '处置说明'");
        ensureWeatherColumns();
        ensureInsectColumns();
        ensureCultivationAllowsMultipleRows();
        createPageAlertContentTable();
        seedPageAlertContent();
        createDashboardTables();
        seedDashboardData();
        seedDashboardAlarms();
        seedPatrolTasks();
        seedNutrientData();
        seedInsectData();
        createAiContentTables();
        seedAiKnowledge();
        seedAiLearnVideos();
    }

    private void createSystemSettingTable() {
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS system_setting ("
                + "setting_key VARCHAR(100) PRIMARY KEY COMMENT '设置键',"
                + "setting_value TEXT DEFAULT NULL COMMENT '设置值',"
                + "updated_at DATETIME DEFAULT NULL COMMENT '更新时间'"
                + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='应用持久化设置；页面：营养液配液；数据：手动、自动或AI模式。'");
        int count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM system_setting WHERE setting_key = ?", Integer.class, "nutrient.mode");
        if (count == 0) {
            jdbcTemplate.update("INSERT INTO system_setting (setting_key, setting_value, updated_at) VALUES (?, ?, NOW())",
                    "nutrient.mode", "manual");
        }
    }

    private void createAlarmRecordTable() {
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS alarm_record ("
                + "id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',"
                + "title VARCHAR(200) NOT NULL COMMENT '报警标题',"
                + "description TEXT DEFAULT NULL COMMENT '报警描述',"
                + "level VARCHAR(10) NOT NULL COMMENT '级别 urgent/important/normal',"
                + "source_module VARCHAR(20) NOT NULL COMMENT '来源模块',"
                + "location VARCHAR(50) DEFAULT NULL COMMENT '位置',"
                + "status TINYINT NOT NULL DEFAULT 0 COMMENT '处置状态 0待处理1已解决2处理中',"
                + "handling_memo TEXT DEFAULT NULL COMMENT '处置说明',"
                + "created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发生时间',"
                + "handled_at DATETIME DEFAULT NULL COMMENT '处理时间',"
                + "INDEX idx_alarm_dashboard_current (status, created_at)"
                + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='页面：报警中心、数据大屏；数据：告警详情、处置状态和说明。'");
    }

    /** Creates the weather page's editable threshold table for older databases. */
    private void createWeatherThresholdTable() {
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS weather_threshold_config ("
                + "id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',"
                + "temp_min DECIMAL(5,1) DEFAULT NULL COMMENT '温度下限 °C',"
                + "temp_max DECIMAL(5,1) DEFAULT NULL COMMENT '温度上限 °C',"
                + "humidity_min DECIMAL(5,1) DEFAULT NULL COMMENT '湿度下限 %',"
                + "humidity_max DECIMAL(5,1) DEFAULT NULL COMMENT '湿度上限 %',"
                + "wind_speed_max DECIMAL(5,1) DEFAULT NULL COMMENT '风速上限 m/s',"
                + "total_rainfall_max DECIMAL(5,1) DEFAULT NULL COMMENT '累计雨量上限 mm',"
                + "hourly_rainfall_max DECIMAL(5,1) DEFAULT NULL COMMENT '小时雨量上限 mm',"
                + "daily_rainfall_max DECIMAL(5,1) DEFAULT NULL COMMENT '日雨量上限 mm',"
                + "light_min DECIMAL(8,1) DEFAULT NULL COMMENT '光照下限 Lux',"
                + "light_max DECIMAL(8,1) DEFAULT NULL COMMENT '光照上限 Lux',"
                + "uv_intensity_max DECIMAL(5,1) DEFAULT NULL COMMENT '紫外线强度上限 uW/cm²',"
                + "uv_index_max DECIMAL(3,1) DEFAULT NULL COMMENT '紫外线指数上限',"
                + "battery_alarm TINYINT DEFAULT 1 COMMENT '低电量报警 0关1开',"
                + "enabled TINYINT DEFAULT 1 COMMENT '总开关',"
                + "updated_at DATETIME DEFAULT NULL COMMENT '更新时间'"
                + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='页面：气象站；区域：报警阈值设置；数据：可编辑的气象预警阈值。'");
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM weather_threshold_config WHERE id = 1", Integer.class);
        if (count == null || count == 0) {
            jdbcTemplate.update("INSERT INTO weather_threshold_config (id, temp_min, temp_max, humidity_min, humidity_max, "
                            + "wind_speed_max, total_rainfall_max, hourly_rainfall_max, daily_rainfall_max, light_min, light_max, "
                            + "uv_intensity_max, uv_index_max, battery_alarm, enabled, updated_at) "
                            + "VALUES (1, 5.0, 40.0, 30.0, 90.0, 10.0, 100.0, 20.0, 50.0, 5000.0, 100000.0, 200.0, 8.0, 1, 1, NOW())");
        }
    }

    private void createPageAlertContentTable() {
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS page_alert_content ("
                + "id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',"
                + "alert_key VARCHAR(80) NOT NULL UNIQUE COMMENT '页面告警键',"
                + "title VARCHAR(200) NOT NULL COMMENT '告警卡片标题',"
                + "summary VARCHAR(500) DEFAULT NULL COMMENT '卡片摘要',"
                + "modal_title VARCHAR(200) DEFAULT NULL COMMENT '详情弹窗标题',"
                + "description TEXT DEFAULT NULL COMMENT '详情说明',"
                + "images_json TEXT DEFAULT NULL COMMENT '详情图片URL JSON数组',"
                + "enabled TINYINT NOT NULL DEFAULT 1 COMMENT '是否展示 0否1是',"
                + "sort_order INT NOT NULL DEFAULT 0 COMMENT '展示顺序',"
                + "created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',"
                + "updated_at DATETIME DEFAULT NULL COMMENT '更新时间',"
                + "INDEX idx_page_alert_enabled (enabled, sort_order)"
                + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='页面：数据大屏、虫情灯、AI轨道巡检；区域：页面专用告警卡片和详情弹窗；数据：标题、说明、静态图片URL和展示开关。'");
    }

    /** Seeds only missing keys so edits made directly in MySQL survive restarts. */
    private void seedPageAlertContent() {
        seedPageAlert(
                "dashboard-graft",
                "嫁接苗异常告警",
                "⚠️嫁接苗存在异常特征，请及时处理！",
                "嫁接苗异常告警",
                "⚠️嫁接苗存在异常特征，请及时处理！",
                "[\"/jhds/images/alerts/graft-union-anomaly.png\",\"/jhds/images/alerts/graft-cut-anomaly.png\"]",
                1);
        seedPageAlert(
                "insect-pest",
                "发现害虫！",
                "点击查看 AI 巡检详情",
                "发现害虫！",
                "发现5只果蝇、1只桃红颈天牛，同时部分害虫无法捕获，叶面有失绿斑，且有部分红色斑点，疑似红蜘蛛",
                "[\"/jhds/images/alerts/fruit-fly-detection.png\",\"/jhds/images/alerts/red-spider-suspected.png\",\"/jhds/images/alerts/longhorn-beetle-detection.png\"]",
                2);
        seedPageAlert(
                "patrol-flower",
                "⚠️花朵数量严重超标！",
                "点击查看 AI 巡检详情",
                "花朵数量严重超标",
                "",
                "[\"/jhds/images/alerts/flower-overload-c2.png\",\"/jhds/images/alerts/flower-overload-c1.png\"]",
                3);
    }

    private void seedPageAlert(String key, String title, String summary, String modalTitle,
                               String description, String imagesJson, int sortOrder) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM page_alert_content WHERE alert_key = ?", Integer.class, key);
        if (count != null && count > 0) {
            return;
        }
        jdbcTemplate.update("INSERT INTO page_alert_content "
                        + "(alert_key, title, summary, modal_title, description, images_json, enabled, sort_order) "
                        + "VALUES (?, ?, ?, ?, ?, ?, 1, ?)",
                key, title, summary, modalTitle, description, imagesJson, sortOrder);
    }

    private void ensureInsectColumns() {
        if (!tableExists("insect_record")) {
            return;
        }
        ensureColumn("insect_record", "device_id", "VARCHAR(50) DEFAULT NULL COMMENT '设备编号'");
        ensureColumn("insect_record", "thumb_url", "VARCHAR(500) DEFAULT NULL COMMENT '缩略图路径'");
        ensureColumn("insect_record", "ai_engine", "VARCHAR(50) DEFAULT NULL COMMENT 'AI识别引擎'");
        ensureColumn("insect_record", "record_time", "DATETIME DEFAULT NULL COMMENT '记录时间'");
    }

    /** Existing installations may have been created before the extra weather fields were added. */
    private void ensureWeatherColumns() {
        if (!tableExists("weather_sensor_data")) {
            return;
        }
        ensureColumn("weather_sensor_data", "light_intensity", "DECIMAL(8,1) DEFAULT NULL COMMENT '光照强度 Lux'");
        ensureColumn("weather_sensor_data", "uv_intensity", "DECIMAL(5,1) DEFAULT NULL COMMENT '紫外线强度 uW/cm²'");
        ensureColumn("weather_sensor_data", "uv_index", "DECIMAL(3,1) DEFAULT NULL COMMENT '紫外线指数'");
        ensureColumn("weather_sensor_data", "battery_status", "TINYINT DEFAULT NULL COMMENT '设备电量 0正常 1需更换'");
        ensureColumn("weather_sensor_data", "hourly_rainfall", "DECIMAL(5,1) DEFAULT NULL COMMENT '每小时雨量 mm'");
        ensureColumn("weather_sensor_data", "daily_rainfall", "DECIMAL(5,1) DEFAULT NULL COMMENT '每天雨量 mm'");
    }

    /** Drops only the obsolete plant/month unique key; existing cultivation rows are retained. */
    private void ensureCultivationAllowsMultipleRows() {
        if (!tableExists("plant_cultivation")) {
            return;
        }
        List<String> uniqueIndexes = jdbcTemplate.queryForList(
                "SELECT DISTINCT s.INDEX_NAME FROM information_schema.STATISTICS s "
                        + "WHERE s.TABLE_SCHEMA = DATABASE() AND s.TABLE_NAME = 'plant_cultivation' "
                        + "AND s.NON_UNIQUE = 0 AND s.INDEX_NAME <> 'PRIMARY' "
                        + "AND s.INDEX_NAME IN (SELECT s2.INDEX_NAME FROM information_schema.STATISTICS s2 "
                        + "WHERE s2.TABLE_SCHEMA = DATABASE() AND s2.TABLE_NAME = 'plant_cultivation' "
                        + "AND s2.COLUMN_NAME = 'plant_id') "
                        + "AND EXISTS (SELECT 1 FROM information_schema.STATISTICS m "
                        + "WHERE m.TABLE_SCHEMA = DATABASE() AND m.TABLE_NAME = 'plant_cultivation' "
                        + "AND m.INDEX_NAME = s.INDEX_NAME AND m.COLUMN_NAME = 'month')",
                String.class);
        for (String indexName : uniqueIndexes) {
            if (indexName == null || indexName.trim().isEmpty()) {
                continue;
            }
            String safeName = indexName.replace("`", "``");
            jdbcTemplate.execute("ALTER TABLE plant_cultivation DROP INDEX `" + safeName + "`");
        }
    }

    private void createDashboardTables() {
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS dashboard_greenhouse ("
                + "id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',"
                + "name VARCHAR(100) NOT NULL COMMENT '大棚名称',"
                + "greenhouse_type VARCHAR(100) DEFAULT NULL COMMENT '大棚类型',"
                + "crop_name VARCHAR(100) DEFAULT NULL COMMENT '作物名称',"
                + "area VARCHAR(50) DEFAULT NULL COMMENT '种植面积',"
                + "plant_count INT DEFAULT NULL COMMENT '定植株数',"
                + "planting_date DATE DEFAULT NULL COMMENT '定植日期',"
                + "is_primary TINYINT NOT NULL DEFAULT 0 COMMENT '是否当前大棚 0否1是',"
                + "sort_order INT NOT NULL DEFAULT 0 COMMENT '展示顺序',"
                + "created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',"
                + "updated_at DATETIME DEFAULT NULL COMMENT '更新时间',"
                + "INDEX idx_dashboard_greenhouse_primary (is_primary, sort_order)"
                + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='页面：数据大屏；区域：大棚信息；数据：名称、类型、作物、面积、株数和定植日期。'");

        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS dashboard_farm_operation ("
                + "id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',"
                + "operation_name VARCHAR(100) NOT NULL COMMENT '农事操作名称',"
                + "operation_date DATE DEFAULT NULL COMMENT '操作日期',"
                + "icon_class VARCHAR(100) DEFAULT NULL COMMENT 'Remix图标类名',"
                + "color_theme VARCHAR(20) DEFAULT NULL COMMENT '图标颜色主题',"
                + "sort_order INT NOT NULL DEFAULT 0 COMMENT '展示顺序',"
                + "created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',"
                + "updated_at DATETIME DEFAULT NULL COMMENT '更新时间',"
                + "INDEX idx_dashboard_operation_sort (sort_order, operation_date)"
                + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='页面：数据大屏；区域：农事操作；数据：已完成农事名称、日期、图标和展示顺序。'");

        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS dashboard_todo ("
                + "id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',"
                + "week_label VARCHAR(50) NOT NULL COMMENT '时间标签',"
                + "task_name VARCHAR(100) NOT NULL COMMENT '农事类别',"
                + "action_name VARCHAR(100) NOT NULL COMMENT '待办操作',"
                + "sort_order INT NOT NULL DEFAULT 0 COMMENT '展示顺序',"
                + "created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',"
                + "updated_at DATETIME DEFAULT NULL COMMENT '更新时间',"
                + "INDEX idx_dashboard_todo_sort (sort_order)"
                + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='页面：数据大屏；区域：待办农事；数据：时间、农事类别、待办操作和展示顺序。'");

        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS dashboard_market_feedback ("
                + "id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',"
                + "title VARCHAR(200) NOT NULL COMMENT '反馈标题',"
                + "summary VARCHAR(255) DEFAULT NULL COMMENT '列表摘要',"
                + "modal_title VARCHAR(200) DEFAULT NULL COMMENT '弹窗标题',"
                + "content TEXT DEFAULT NULL COMMENT '反馈详情',"
                + "enabled TINYINT NOT NULL DEFAULT 1 COMMENT '是否展示 0否1是',"
                + "sort_order INT NOT NULL DEFAULT 0 COMMENT '展示顺序',"
                + "created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',"
                + "updated_at DATETIME DEFAULT NULL COMMENT '更新时间',"
                + "INDEX idx_dashboard_market_enabled (enabled, sort_order)"
                + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='页面：数据大屏；区域：市场反馈弹窗；数据：反馈标题、摘要、详情和是否展示。'");
    }

    private void seedDashboardData() {
        Integer seeded = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM system_setting WHERE setting_key = ?", Integer.class, "dashboard.seeded");
        if (seeded != null && seeded > 0) {
            return;
        }

        seedDashboardGreenhouse();
        seedDashboardFarmOperations();
        seedDashboardTodos();
        seedDashboardMarketFeedback();
        jdbcTemplate.update("INSERT INTO system_setting (setting_key, setting_value, updated_at) VALUES (?, ?, NOW())",
                "dashboard.seeded", "true");
    }

    private void seedDashboardGreenhouse() {
        Integer rows = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM dashboard_greenhouse", Integer.class);
        if (rows != null && rows > 0) {
            return;
        }
        jdbcTemplate.update("INSERT INTO dashboard_greenhouse "
                        + "(name, greenhouse_type, crop_name, area, plant_count, planting_date, is_primary, sort_order) "
                        + "VALUES (?, ?, ?, ?, ?, ?, 1, 1)",
                "种植架1", "玻璃体棚", "樱桃", "1000 m²", 1200, java.sql.Date.valueOf("2026-03-28"));
    }

    private void seedDashboardFarmOperations() {
        Integer rows = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM dashboard_farm_operation", Integer.class);
        if (rows != null && rows > 0) {
            return;
        }
        List<Object[]> records = Arrays.asList(
                new Object[]{"控温通气", java.sql.Date.valueOf("2026-03-28"), "ri-temp-hot-line", "blue", 1},
                new Object[]{"浇水", java.sql.Date.valueOf("2026-03-27"), "ri-drop-line", "cyan", 2},
                new Object[]{"施肥", java.sql.Date.valueOf("2026-03-27"), "ri-seedling-line", "green", 3},
                new Object[]{"灌根", java.sql.Date.valueOf("2026-03-26"), "ri-bug-line", "purple", 4}
        );
        for (Object[] record : records) {
            jdbcTemplate.update("INSERT INTO dashboard_farm_operation "
                            + "(operation_name, operation_date, icon_class, color_theme, sort_order) VALUES (?, ?, ?, ?, ?)",
                    record);
        }
    }

    private void seedDashboardTodos() {
        Integer rows = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM dashboard_todo", Integer.class);
        if (rows != null && rows > 0) {
            return;
        }
        List<Object[]> records = Arrays.asList(
                new Object[]{"第6周", "农事", "浇水", 1},
                new Object[]{"第1周", "农事", "浇水", 2},
                new Object[]{"第1周", "施肥", "植保", 3}
        );
        for (Object[] record : records) {
            jdbcTemplate.update("INSERT INTO dashboard_todo (week_label, task_name, action_name, sort_order) "
                            + "VALUES (?, ?, ?, ?)",
                    record);
        }
    }

    private void seedDashboardMarketFeedback() {
        Integer rows = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM dashboard_market_feedback", Integer.class);
        if (rows != null && rows > 0) {
            return;
        }
        jdbcTemplate.update("INSERT INTO dashboard_market_feedback "
                        + "(title, summary, modal_title, content, enabled, sort_order) VALUES (?, ?, ?, ?, 1, 1)",
                "2025第四批次樱桃市场评价中等", "消费者评价数据已更新", "消费者反映风味欠佳",
                "据NFC追溯得到的消费者评价数据，56%消费者反映该批次樱桃糖度较低；33%消费者反映消费者反映该批次樱桃酸度过高，9%消费者反映该批次樱桃硬度较低。");
    }

    private void seedDashboardAlarms() {
        // Seed each original demonstration alarm independently. This restores
        // missing rows in an existing database without overwriting edits or
        // re-adding rows that a user has deliberately removed.
        seedAlarm("发现虫害几棵", "AI图像识别检测到种植架1、2出现蚜虫聚集，建议立即进行植保处理",
                "urgent", "insect", "种植架1、种植架2", "2026-03-30 13:30:00");
        seedAlarm("植株叶面存在杂点", "轨道巡检摄像头检测到A2区域植株叶面出现不明杂点，疑似病害早期",
                "important", "patrol", "A2区域", "2026-03-30 12:45:00");
        seedAlarm("土壤湿度偏低", "土壤湿度传感器显示当前湿度45%，略低于设定阈值50%",
                "normal", "nutrient", null, "2026-03-30 11:20:00");
        seedAlarm("风速超过3级", "气象站监测到当前风速3.2m/s，建议检查大棚通风口",
                "normal", "weather", null, "2026-03-30 10:15:00");
        seedAlarm("营养液EC值异常", "土壤EC值达到2.1mS/cm，超出正常范围1.5-2.0，需调整配液比例",
                "important", "nutrient", null, "2026-03-30 09:30:00");
        seedAlarm("轨道巡检设备离线", "AI轨道巡检模块通信中断，已持续5分钟，请检查网络连接",
                "urgent", "patrol", null, "2026-03-30 08:45:00");
        seedAlarm("大棚温度过高", "大棚1温度达到38°C，超过预警阈值35°C，建议开启通风降温",
                "important", "iot", "大棚1", "2026-03-30 14:10:00");
        seedAlarm("二氧化碳浓度偏低", "大棚2内CO₂浓度降至280ppm，低于光合作用适宜值，建议增施CO₂",
                "normal", "iot", "大棚2", "2026-03-30 13:50:00");
        seedAlarm("光照强度不足", "连续阴天导致大棚内光照强度仅8000lux，建议开启补光灯",
                "normal", "iot", null, "2026-03-30 07:30:00");
        seedAlarm("水泵异常停机", "灌溉系统B水泵电流异常自动停机，需检查电机和电路",
                "urgent", "nutrient", "灌溉系统B", "2026-03-30 06:15:00");
        markSeeded("dashboard.alarm-seeded");
    }

    /** Restore the original patrol examples only when their exact rows are absent. */
    private void seedPatrolTasks() {
        if (!tableExists("patrol_task")) {
            return;
        }
        List<Object[]> tasks = Arrays.asList(
                new Object[]{"晨间全面巡检", "08:00:00", "all", 1},
                new Object[]{"午间生长监测", "12:00:00", "all", 2},
                new Object[]{"晚间状态巡查", "18:00:00", "all", 3}
        );
        for (Object[] task : tasks) {
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM patrol_task WHERE task_name = ? AND execute_time = ?",
                    Integer.class, task[0], task[1]);
            if (count == null || count == 0) {
                jdbcTemplate.update("INSERT INTO patrol_task (task_name, execute_time, patrol_range, status) VALUES (?, ?, ?, 0)",
                        task[0], task[1], task[2]);
            }
        }
    }

    private void seedAlarm(String title, String description, String level, String sourceModule,
                           String location, String createdAt) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM alarm_record WHERE title = ? AND created_at = ?",
                Integer.class, title, Timestamp.valueOf(createdAt));
        if (count != null && count > 0) {
            return;
        }
        jdbcTemplate.update("INSERT INTO alarm_record "
                        + "(title, description, level, source_module, location, status, created_at) "
                        + "VALUES (?, ?, ?, ?, ?, 0, ?)",
                title, description, level, sourceModule, location, Timestamp.valueOf(createdAt));
    }

    private void ensureColumn(String table, String column, String definition) {
        if (!tableExists(table)) {
            return;
        }
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.COLUMNS "
                        + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = ?",
                Integer.class, table, column);
        if (count != null && count == 0) {
            jdbcTemplate.execute("ALTER TABLE " + table + " ADD COLUMN " + column + " " + definition);
        }
    }

    private void seedNutrientData() {
        if (!tableExists("equipment") || !tableExists("soil_sensor_data")) {
            return;
        }
        if (isSeeded("seed.nutrient.v1")) return;
        List<Object[]> devices = Arrays.asList(
                new Object[]{"营养液A泵", "PUMP_A"},
                new Object[]{"营养液B泵", "PUMP_B"},
                new Object[]{"酸液泵", "PUMP_ACID"},
                new Object[]{"碱液泵", "PUMP_BASE"},
                new Object[]{"灌溉泵", "PUMP_IRRIGATE"},
                new Object[]{"搅拌泵", "PUMP_MIX"},
                new Object[]{"二氧化碳气肥", "PUMP_CO2"},
                new Object[]{"灌溉循环泵", "PUMP_CIRCULATION"},
                new Object[]{"氯化钙叶面肥", "PUMP_CALCIUM"}
        );
        for (Object[] device : devices) {
            Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM equipment WHERE alias = ?", Integer.class, device[1]);
            if (count != null && count == 0) {
                jdbcTemplate.update("INSERT INTO equipment (name, alias, type, open_code, close_code, status) VALUES (?, ?, 1, '', '', 0)",
                        device[0], device[1]);
            } else {
                jdbcTemplate.update("UPDATE equipment SET type = 1 WHERE alias = ?", device[1]);
            }
        }

        Integer soilRows = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM soil_sensor_data", Integer.class);
        if (soilRows != null && soilRows == 0) {
            jdbcTemplate.update("INSERT INTO soil_sensor_data "
                            + "(soil_temp, soil_humidity, soil_ec, soil_ph, soil_salt, soil_nitrogen, soil_phosphorus, soil_potassium, record_time) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    new BigDecimal("23.6"), new BigDecimal("47.0"), new BigDecimal("0.40"), new BigDecimal("6.5"),
                    new BigDecimal("0.079"), new BigDecimal("101.0"), new BigDecimal("13.0"), new BigDecimal("167.0"),
                    Timestamp.valueOf(LocalDateTime.now()));
        }
        markSeeded("seed.nutrient.v1");
    }

    private void seedInsectData() {
        if (!tableExists("insect_record")) {
            return;
        }
        if (isSeeded("seed.insect.v1")) return;
        Integer rows = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM insect_record", Integer.class);
        if (rows != null && rows > 0) {
            markSeeded("seed.insect.v1");
            return;
        }

        Timestamp now = Timestamp.valueOf(LocalDateTime.now());
        java.sql.Date date = java.sql.Date.valueOf(now.toLocalDateTime().toLocalDate());
        List<Object[]> records = Arrays.asList(
                new Object[]{"AI_PATROL", "/jhds/images/alerts/fruit-fly-detection.png", "果蝇", 5},
                new Object[]{"AI_PATROL", "/jhds/images/alerts/red-spider-suspected.png", "红蜘蛛（疑似）", 0},
                new Object[]{"AI_PATROL", "/jhds/images/alerts/longhorn-beetle-detection.png", "桃红颈天牛", 1}
        );
        for (Object[] record : records) {
            jdbcTemplate.update("INSERT INTO insect_record "
                            + "(device_id, image_url, thumb_url, species, count, ai_engine, record_date, record_time) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                    record[0], record[1], record[1], record[2], record[3], "AI识别巡检", date, now);
        }
        markSeeded("seed.insect.v1");
    }

    private void createAiContentTables() {
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS ai_knowledge_entry ("
                + "id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',"
                + "keywords TEXT NOT NULL COMMENT '关键词，逗号、换行或竖线分隔',"
                + "answer TEXT NOT NULL COMMENT '关键词对应回答',"
                + "enabled TINYINT NOT NULL DEFAULT 1 COMMENT '是否启用 0否1是',"
                + "sort_order INT NOT NULL DEFAULT 0 COMMENT '匹配顺序',"
                + "created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',"
                + "updated_at DATETIME DEFAULT NULL COMMENT '更新时间',"
                + "INDEX idx_ai_knowledge_enabled (enabled, sort_order)"
                + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='页面：AI农业助手；区域：关键词问答；数据：关键词、回答和启用状态。'");
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS ai_learn_video ("
                + "id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',"
                + "video_name VARCHAR(255) NOT NULL UNIQUE COMMENT '上传视频文件名',"
                + "folder_key VARCHAR(50) NOT NULL COMMENT '资料目录标识',"
                + "group_title VARCHAR(200) NOT NULL COMMENT '知识卡片分组标题',"
                + "enabled TINYINT NOT NULL DEFAULT 1 COMMENT '是否启用 0否1是',"
                + "sort_order INT NOT NULL DEFAULT 0 COMMENT '视频排序',"
                + "created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',"
                + "updated_at DATETIME DEFAULT NULL COMMENT '更新时间',"
                + "INDEX idx_ai_learn_video_enabled (enabled, sort_order)"
                + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='页面：AI学习；区域：上传视频匹配；数据：视频文件名、资料目录和分组。'");
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS ai_learn_card ("
                + "id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',"
                + "video_id BIGINT NOT NULL COMMENT '关联视频ID',"
                + "image_url VARCHAR(500) NOT NULL COMMENT '知识卡片图片路径',"
                + "card_title VARCHAR(255) NOT NULL COMMENT '知识卡片标题',"
                + "description TEXT DEFAULT NULL COMMENT '图片对应讲解',"
                + "enabled TINYINT NOT NULL DEFAULT 1 COMMENT '是否启用 0否1是',"
                + "sort_order INT NOT NULL DEFAULT 0 COMMENT '卡片排序',"
                + "created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',"
                + "updated_at DATETIME DEFAULT NULL COMMENT '更新时间',"
                + "INDEX idx_ai_learn_card_video (video_id, enabled, sort_order)"
                + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='页面：AI学习；区域：知识卡片；数据：图片、标题、讲解和展示状态。'");
    }

    /** Seeds the editable keyword catalogue once; subsequent MySQL edits are preserved. */
    private void seedAiKnowledge() {
        if (isSeeded("seed.ai.knowledge.v1")) {
            return;
        }
        List<Object[]> entries = Arrays.asList(
                new Object[]{"褐斑病,褐斑病爆发,褐斑病发生条件",
                        "温湿度是褐斑病的核心诱因：叶片结露或湿润6小时以上、降雨超过2毫米时容易侵染；病菌最活跃温度为20-25℃。连续阴雨、高温高湿、通风透光差、排水不良或树势衰弱都会加重病害，应及时清理病叶、改善通风并按植保方案用药。", 1},
                new Object[]{"白粉病,白粉病爆发,白粉病发生条件",
                        "樱桃白粉病由专性寄生真菌引起，最适温度20-25℃，相对湿度超过70%有利于发展；暖干日加凉湿夜最易流行。幼嫩叶片和幼果更易感病，应改善通风透光，避免过密和不当喷灌，并在春季初侵染期及时防治。", 2},
                new Object[]{"c1,c1角落区,c1角落区环境,三号种植区,三号种植区环境",
                        "三号种植区当前环境总体良好，但C1角落区可能存在高湿环境。实时数据约为大气温度22.65℃、湿度95.95%、土壤温度19.31℃、土壤湿度26.42%、光照35007lux、二氧化碳707.67ppm；褐斑病喜低温高湿，请及时到现场检查。", 3},
                new Object[]{"调整,调整三号种植区c1角落区,调整c1角落区,硬件",
                        "建议降低三号种植区C1角落区的大气湿度：调高天窗角度、提速内循环机，改善该区域通风透气条件。", 4},
                new Object[]{"采收,采收标准",
                        "物联网全域监测显示，4号种植区约95.6%的樱桃达到采收标准，可集中采收。建议清晨或傍晚低温时段连同果柄轻采，现场剔除病果、虫果和残果并按大小分级。", 5},
                new Object[]{"保存,采后处理,采后,处理",
                        "樱桃采后应在2小时内预冷，将果温降至0-2℃，再进行分级、防震包装和0-4℃冷链运输；同步做好吸水保鲜纸、NFC温度标签和采后树体施肥、修剪管理。", 6},
                new Object[]{"二号种植区,二号种植区环境",
                        "警告：二号种植区疑似光合速率较低。当前二氧化碳约167.46ppm，可能影响光合速率；请结合温湿度、土壤和光照数据及时到现场检查并改善通风、补充二氧化碳。", 7}
        );
        for (Object[] entry : entries) {
            jdbcTemplate.update("INSERT INTO ai_knowledge_entry (keywords, answer, enabled, sort_order) VALUES (?, ?, 1, ?)", entry);
        }
        markSeeded("seed.ai.knowledge.v1");
    }

    /** Imports the repository's photo/1..3 folders into editable video/card rows. */
    private void seedAiLearnVideos() {
        Path photoRoot = Paths.get("photo").toAbsolutePath().normalize();
        if (!Files.isDirectory(photoRoot)) {
            return;
        }
        try {
            List<Path> folders = Files.list(photoRoot)
                    .filter(Files::isDirectory)
                    .sorted(Comparator.comparing(Path::getFileName))
                    .collect(Collectors.toList());
            for (Path folder : folders) {
                String folderKey = folder.getFileName().toString();
                Path video = Files.list(folder)
                        .filter(path -> path.getFileName().toString().toLowerCase().matches(".*\\.(mp4|webm|mov|avi|mkv)$"))
                        .findFirst().orElse(null);
                if (video == null) {
                    continue;
                }
                String videoName = video.getFileName().toString();
                Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM ai_learn_video WHERE video_name = ?", Integer.class, videoName);
                Long videoId;
                if (count == null || count == 0) {
                    String groupTitle = "1".equals(folderKey) ? "樱桃苗木培育与定植"
                            : ("2".equals(folderKey) ? "缺素诊断与仪器操作" : "病虫害预警与防治");
                    jdbcTemplate.update("INSERT INTO ai_learn_video (video_name, folder_key, group_title, enabled, sort_order) VALUES (?, ?, ?, 1, ?)",
                            videoName, folderKey, groupTitle, parseSort(folderKey));
                }
                videoId = jdbcTemplate.queryForObject("SELECT id FROM ai_learn_video WHERE video_name = ?", Long.class, videoName);
                seedAiLearnCards(folder, folderKey, videoId);
            }
        } catch (IOException e) {
            // A missing optional photo directory should not prevent the web service from starting.
        }
    }

    private void seedAiLearnCards(Path folder, String folderKey, Long videoId) {
        Integer existing = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM ai_learn_card WHERE video_id = ?", Integer.class, videoId);
        if (existing != null && existing > 0) {
            return;
        }
        try {
            List<Path> images = Files.list(folder)
                    .filter(path -> path.getFileName().toString().toLowerCase().matches(".*\\.(png|jpg|jpeg|gif)$"))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString(), String.CASE_INSENSITIVE_ORDER))
                    .collect(Collectors.toList());
            List<String> descriptions = readLearningDescriptions(folder);
            for (int i = 0; i < images.size(); i++) {
                String fileName = images.get(i).getFileName().toString();
                String title = "第" + (i + 1) + "项学习要点";
                String description = i < descriptions.size() ? descriptions.get(i) : "视频对应的农业学习资料。";
                jdbcTemplate.update("INSERT INTO ai_learn_card (video_id, image_url, card_title, description, enabled, sort_order) VALUES (?, ?, ?, ?, 1, ?)",
                        videoId, "/jhds/ai-learn-media/" + folderKey + "/" + fileName, title, description, i + 1);
            }
        } catch (IOException e) {
            // Ignore one malformed folder and keep other learning materials available.
        }
    }

    private List<String> readLearningDescriptions(Path folder) throws IOException {
        Path txt = Files.list(folder)
                .filter(path -> path.getFileName().toString().toLowerCase().endsWith(".txt"))
                .findFirst().orElse(null);
        if (txt == null) {
            return new ArrayList<>();
        }
        List<String> lines = Files.readAllLines(txt, StandardCharsets.UTF_8);
        List<String> descriptions = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String raw : lines) {
            String line = raw == null ? "" : raw.trim();
            if (line.isEmpty()) {
                if (current.length() > 0) {
                    descriptions.add(current.toString());
                    current.setLength(0);
                }
                continue;
            }
            if (line.matches("^[0-9]+[.、].*") || line.matches("^[一二三四五六七八九十]+号：.*")) {
                if (current.length() > 0) {
                    descriptions.add(current.toString());
                    current.setLength(0);
                }
                int split = line.indexOf('、');
                if (split < 0) split = line.indexOf('.');
                if (split < 0) split = line.indexOf('：');
                if (split >= 0 && split + 1 < line.length()) {
                    current.append(line.substring(split + 1).trim());
                }
            } else {
                if (current.length() > 0) current.append('\n');
                current.append(line);
            }
        }
        if (current.length() > 0) descriptions.add(current.toString());
        return descriptions;
    }

    private int parseSort(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return 99;
        }
    }

    private boolean isSeeded(String key) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM system_setting WHERE setting_key = ?", Integer.class, key);
        return count != null && count > 0;
    }

    private boolean tableExists(String table) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ?",
                Integer.class, table);
        return count != null && count > 0;
    }

    private void markSeeded(String key) {
        jdbcTemplate.update("INSERT INTO system_setting (setting_key, setting_value, updated_at) VALUES (?, ?, NOW()) "
                        + "ON DUPLICATE KEY UPDATE setting_value = VALUES(setting_value), updated_at = VALUES(updated_at)",
                key, "done");
    }
}
