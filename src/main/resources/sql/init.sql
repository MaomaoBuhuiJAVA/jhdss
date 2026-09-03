CREATE DATABASE IF NOT EXISTS jhds DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
USE jhds;

-- 巡逻任务表
DROP TABLE IF EXISTS patrol_task;
CREATE TABLE patrol_task (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
    task_name VARCHAR(100) NOT NULL COMMENT '任务名称',
    execute_time TIME NOT NULL COMMENT '执行时间',
    patrol_range VARCHAR(50) DEFAULT 'all' COMMENT '巡检范围',
    status TINYINT DEFAULT 0 COMMENT '状态 0=待执行 1=执行中 2=已完成 3=已停用',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT NULL COMMENT '更新时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='页面：AI轨道巡检；区域：巡检任务列表与新建任务表单；数据：任务名称、执行时间、巡检范围和执行状态。';

-- 巡逻记录表
DROP TABLE IF EXISTS patrol_record;
CREATE TABLE patrol_record (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
    task_id BIGINT DEFAULT NULL COMMENT '关联任务ID',
    image_url VARCHAR(500) DEFAULT NULL COMMENT '拍摄图片路径',
    track_position VARCHAR(20) DEFAULT NULL COMMENT '轨道位置',
    shoot_time DATETIME DEFAULT NULL COMMENT '拍摄时间',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='页面：AI轨道巡检；区域：视频下方巡检记录与AI识别结果；数据：轨道位置、抓拍图片、拍摄时间、AI状态和AI结果。';

-- 巡逻记录表升级 - AI识别字段
ALTER TABLE patrol_record
  ADD COLUMN IF NOT EXISTS ai_result TEXT COMMENT 'AI识别结果' AFTER track_position,
  ADD COLUMN IF NOT EXISTS ai_status TINYINT DEFAULT 0 COMMENT 'AI状态 0=待识别 1=识别中 2=完成 3=失败' AFTER ai_result;

-- 气象站传感器数据表
DROP TABLE IF EXISTS weather_sensor_data;
CREATE TABLE weather_sensor_data (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
    temperature DECIMAL(5,1) DEFAULT NULL COMMENT '环境温度 °C',
    humidity DECIMAL(5,1) DEFAULT NULL COMMENT '环境湿度 %',
    wind_speed DECIMAL(5,1) DEFAULT NULL COMMENT '风速 m/s',
    rainfall DECIMAL(5,1) DEFAULT NULL COMMENT '降雨量 mm',
    wind_direction VARCHAR(10) DEFAULT NULL COMMENT '风向',
    record_time DATETIME NOT NULL COMMENT '记录时间',
    INDEX idx_record_time (record_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='页面：气象站、数据大屏；区域：气象指标卡、48小时趋势图和大屏气象缩略卡；数据：温湿度、风速风向、雨量、光照、紫外线、电量和采集时间。当前直接读取最新/历史记录，空表时页面保留初始值。';

-- 土壤传感器数据表
DROP TABLE IF EXISTS soil_sensor_data;
CREATE TABLE soil_sensor_data (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
    soil_temp DECIMAL(5,1) DEFAULT NULL COMMENT '土壤温度 °C',
    soil_humidity DECIMAL(5,1) DEFAULT NULL COMMENT '土壤湿度 %',
    soil_ec DECIMAL(5,2) DEFAULT NULL COMMENT '土壤EC mS/cm',
    soil_ph DECIMAL(4,1) DEFAULT NULL COMMENT '土壤pH',
    soil_salt DECIMAL(10,2) DEFAULT NULL COMMENT '土壤盐分',
    soil_nitrogen DECIMAL(10,2) DEFAULT NULL COMMENT '土壤氮 mg/kg',
    soil_phosphorus DECIMAL(10,2) DEFAULT NULL COMMENT '土壤磷 mg/kg',
    soil_potassium DECIMAL(10,2) DEFAULT NULL COMMENT '土壤钾 mg/kg',
    record_time DATETIME NOT NULL COMMENT '记录时间',
    INDEX idx_record_time (record_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='页面：营养液配液；区域：土壤传感器与48小时趋势图；数据：土壤温湿度、EC、pH、盐分、氮磷钾和采集时间。页面从本表读取全部指标与历史趋势。';

-- 设备表
DROP TABLE IF EXISTS equipment;
CREATE TABLE equipment (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
    name VARCHAR(100) NOT NULL COMMENT '设备名称',
    alias VARCHAR(50) NOT NULL COMMENT '设备别名',
    type INT DEFAULT 0 COMMENT '类型',
    open_code VARCHAR(200) DEFAULT NULL COMMENT '开启命令码',
    close_code VARCHAR(200) DEFAULT NULL COMMENT '关闭命令码',
    return_open_code VARCHAR(200) DEFAULT NULL COMMENT '开启返回码',
    return_close_code VARCHAR(200) DEFAULT NULL COMMENT '关闭返回码',
    status TINYINT DEFAULT 0 COMMENT '当前状态 0=关闭 1=开启'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='页面：物联设备、营养液配液；区域：大棚控制面板和手动模式泵控制卡片；数据：设备名称/别名、控制指令和开关状态。物联设备页仅显示别名以GH开头的设备，配液页显示PUMP设备。';

-- 灌溉计划表
DROP TABLE IF EXISTS irrigation_schedule;
CREATE TABLE irrigation_schedule (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
    schedule_time TIME NOT NULL COMMENT '灌溉时间',
    duration INT DEFAULT 10 COMMENT '单次时长（分钟）',
    frequency VARCHAR(20) DEFAULT 'daily' COMMENT '频率 daily/alternate/custom',
    enabled TINYINT DEFAULT 1 COMMENT '0=禁用 1=启用',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT NULL COMMENT '更新时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='页面：营养液配液；区域：自动模式的自动灌溉计划表单；数据：计划时间、运行时长、频率和启用状态。页面保存、回显和后台调度均读取本表。';

-- 灌溉记录表
DROP TABLE IF EXISTS irrigation_record;
CREATE TABLE irrigation_record (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
    mode VARCHAR(10) NOT NULL COMMENT '模式 manual/auto',
    pump_alias VARCHAR(50) DEFAULT NULL COMMENT '泵别名',
    duration INT DEFAULT 0 COMMENT '运行时长（秒）',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '执行时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='页面：营养液配液；区域：灌溉统计卡与AI决策日志；数据：手动/自动/AI模式、泵别名、运行时长和执行时间。页面聚合显示本日次数、运行时长、估算用水量和最近记录。';

-- 虫情记录表
DROP TABLE IF EXISTS insect_record;
CREATE TABLE insect_record (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
    device_id VARCHAR(50) DEFAULT NULL COMMENT '设备编号',
    image_url VARCHAR(500) DEFAULT NULL COMMENT '虫体图片路径',
    thumb_url VARCHAR(500) DEFAULT NULL COMMENT '缩略图路径',
    species VARCHAR(50) NOT NULL COMMENT '虫体种类',
    count INT DEFAULT 0 COMMENT '虫体数量',
    ai_engine VARCHAR(50) DEFAULT NULL COMMENT 'AI识别引擎',
    record_date DATE NOT NULL COMMENT '记录日期',
    record_time DATETIME DEFAULT NULL COMMENT '记录时间',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_record_date (record_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='页面：虫情灯；区域：本地记录图库、今日统计和AI识别巡检种类统计；数据：虫体图片、种类、数量、设备、AI引擎和采集时间。告警弹窗与外部照片标签页不读取本表。';

-- 已存在的旧数据库会在应用启动时自动补齐虫情记录扩展字段。

-- ============================================================
-- 气象站表升级（如已创建旧表，执行以下 ALTER）
-- ============================================================
ALTER TABLE weather_sensor_data
  ADD COLUMN IF NOT EXISTS light_intensity DECIMAL(8,1) DEFAULT NULL COMMENT '光照强度 Lux',
  ADD COLUMN IF NOT EXISTS uv_intensity DECIMAL(5,1) DEFAULT NULL COMMENT '紫外线强度 uW/cm²',
  ADD COLUMN IF NOT EXISTS uv_index DECIMAL(3,1) DEFAULT NULL COMMENT '紫外线指数',
  ADD COLUMN IF NOT EXISTS battery_status TINYINT DEFAULT NULL COMMENT '设备电量 0正常 1需更换',
  ADD COLUMN IF NOT EXISTS hourly_rainfall DECIMAL(5,1) DEFAULT NULL COMMENT '每小时雨量 mm',
  ADD COLUMN IF NOT EXISTS daily_rainfall DECIMAL(5,1) DEFAULT NULL COMMENT '每天雨量 mm',
  MODIFY COLUMN wind_direction DECIMAL(5,1) DEFAULT NULL COMMENT '风向角度 0~360°';
-- 注：MySQL 不支持 ADD COLUMN IF NOT EXISTS，需在客户端执行时去掉 IF NOT EXISTS
-- 分步执行:
-- ALTER TABLE weather_sensor_data ADD COLUMN light_intensity DECIMAL(8,1) DEFAULT NULL COMMENT '光照强度 Lux';
-- ALTER TABLE weather_sensor_data ADD COLUMN uv_intensity DECIMAL(5,1) DEFAULT NULL COMMENT '紫外线强度 uW/cm²';
-- ALTER TABLE weather_sensor_data ADD COLUMN uv_index DECIMAL(3,1) DEFAULT NULL COMMENT '紫外线指数';
-- ALTER TABLE weather_sensor_data ADD COLUMN battery_status TINYINT DEFAULT NULL COMMENT '设备电量 0正常 1需更换';
-- ALTER TABLE weather_sensor_data ADD COLUMN hourly_rainfall DECIMAL(5,1) DEFAULT NULL COMMENT '每小时雨量 mm';
-- ALTER TABLE weather_sensor_data ADD COLUMN daily_rainfall DECIMAL(5,1) DEFAULT NULL COMMENT '每天雨量 mm';
-- ALTER TABLE weather_sensor_data MODIFY COLUMN wind_direction DECIMAL(5,1) DEFAULT NULL COMMENT '风向角度 0~360°';

-- 报警记录表
DROP TABLE IF EXISTS alarm_record;
CREATE TABLE alarm_record (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
    title VARCHAR(200) NOT NULL COMMENT '报警标题',
    description TEXT DEFAULT NULL COMMENT '报警描述',
    level VARCHAR(10) NOT NULL COMMENT '级别 urgent/important/normal',
    source_module VARCHAR(20) NOT NULL COMMENT '来源模块 patrol/weather/nutrient/insect',
    location VARCHAR(50) DEFAULT NULL COMMENT '位置',
    status TINYINT NOT NULL DEFAULT 0 COMMENT '处置状态 0=待处理 1=已解决（兼容旧已处理值） 2=处理中',
    handling_memo TEXT DEFAULT NULL COMMENT '处置说明',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发生时间',
    handled_at DATETIME DEFAULT NULL COMMENT '处理时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='页面：报警中心；区域：报警列表、状态与处置说明、来源分布；数据：标题、描述、级别、来源、位置、处置状态、说明与时间。页面通过API读取和修改本表；气象阈值等后台服务也会写入本表。';

-- 初始报警中心演示记录
INSERT INTO alarm_record (title, description, level, source_module, location, status, created_at) VALUES
('发现虫害几棵', 'AI图像识别检测到种植架1、2出现蚜虫聚集，建议立即进行植保处理', 'urgent', 'insect', '种植架1、2', 0, '2026-03-30 13:30:00'),
('植株叶面存在杂点', '轨道巡检摄像头检测到A2区域植株叶面出现不明杂点，疑似病害早期', 'important', 'patrol', 'A2区域', 0, '2026-03-30 12:45:00'),
('土壤湿度偏低', '土壤湿度传感器显示当前湿度45%，略低于设定阈值50%', 'normal', 'nutrient', NULL, 0, '2026-03-30 11:20:00'),
('风速超过3级', '气象站监测到当前风速3.2m/s，建议检查大棚通风口', 'normal', 'weather', NULL, 0, '2026-03-30 10:15:00'),
('营养液EC值异常', '土壤EC值达到2.1mS/cm，超出正常范围1.5-2.0，需调整配液比例', 'important', 'nutrient', NULL, 0, '2026-03-30 09:30:00'),
('轨道巡检设备离线', 'AI轨道巡检模块通信中断，已持续5分钟，请检查网络连接', 'urgent', 'patrol', NULL, 0, '2026-03-30 08:45:00'),
('大棚温度过高', '大棚1温度达到38°C，超过预警阈值35°C，建议开启通风降温', 'important', 'iot', '大棚1', 0, '2026-03-30 14:10:00'),
('二氧化碳浓度偏低', '大棚2内CO₂浓度降至280ppm，低于光合作用适宜值，建议增施CO₂', 'normal', 'iot', '大棚2', 0, '2026-03-30 13:50:00'),
('光照强度不足', '连续阴天导致大棚内光照强度仅8000lux，建议开启补光灯', 'normal', 'iot', NULL, 0, '2026-03-30 07:30:00'),
('水泵异常停机', '灌溉系统B水泵电流异常自动停机，需检查电机和电路', 'urgent', 'nutrient', '灌溉系统B', 0, '2026-03-30 06:15:00');

-- 页面专用 AI 告警内容（图片 URL 以 JSON 数组保存，页面通过 /api/page-alerts 读取）
DROP TABLE IF EXISTS page_alert_content;
CREATE TABLE page_alert_content (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
    alert_key VARCHAR(80) NOT NULL UNIQUE COMMENT '页面告警键',
    title VARCHAR(200) NOT NULL COMMENT '告警卡片标题',
    summary VARCHAR(500) DEFAULT NULL COMMENT '卡片摘要',
    modal_title VARCHAR(200) DEFAULT NULL COMMENT '详情弹窗标题',
    description TEXT DEFAULT NULL COMMENT '详情说明',
    images_json TEXT DEFAULT NULL COMMENT '详情图片URL JSON数组',
    enabled TINYINT NOT NULL DEFAULT 1 COMMENT '是否展示 0否1是',
    sort_order INT NOT NULL DEFAULT 0 COMMENT '展示顺序',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT NULL COMMENT '更新时间',
    INDEX idx_page_alert_enabled (enabled, sort_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='页面：数据大屏、虫情灯、AI轨道巡检；区域：页面专用告警卡片和详情弹窗；数据：标题、说明、静态图片URL和展示开关。';

INSERT INTO page_alert_content
(alert_key, title, summary, modal_title, description, images_json, enabled, sort_order) VALUES
('dashboard-graft', '嫁接苗异常告警', '⚠️嫁接苗存在异常特征，请及时处理！', '嫁接苗异常告警', '⚠️嫁接苗存在异常特征，请及时处理！', '["/jhds/images/alerts/graft-union-anomaly.png","/jhds/images/alerts/graft-cut-anomaly.png"]', 1, 1),
('insect-pest', '发现害虫！', '点击查看 AI 巡检详情', '发现害虫！', '发现5只果蝇、1只桃红颈天牛，同时部分害虫无法捕获，叶面有失绿斑，且有部分红色斑点，疑似红蜘蛛', '["/jhds/images/alerts/fruit-fly-detection.png","/jhds/images/alerts/red-spider-suspected.png","/jhds/images/alerts/longhorn-beetle-detection.png"]', 1, 2),
('patrol-flower', '⚠️花朵数量严重超标！', '点击查看 AI 巡检详情', '花朵数量严重超标', '', '["/jhds/images/alerts/flower-overload-c2.png","/jhds/images/alerts/flower-overload-c1.png"]', 1, 3);

-- 控制日志表
DROP TABLE IF EXISTS control_log;
CREATE TABLE control_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
    device_alias VARCHAR(50) NOT NULL COMMENT '设备别名',
    device_name VARCHAR(100) DEFAULT NULL COMMENT '设备名称',
    value VARCHAR(20) DEFAULT NULL COMMENT '命令值 open/close',
    automatic TINYINT DEFAULT 0 COMMENT '0=手动 1=自动',
    send_command VARCHAR(200) DEFAULT NULL COMMENT '发送的Modbus帧',
    return_command VARCHAR(200) DEFAULT NULL COMMENT '返回结果',
    success TINYINT DEFAULT 0 COMMENT '0=失败 1=成功',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='后台运维审计日志；用途：MQTT设备控制、气象和土壤Modbus采集的指令/响应记录；数据：设备、控制值、指令/响应、成功状态、手自动来源和时间。当前无前端页面直接展示本表。';

-- 初始设备数据
INSERT INTO equipment (name, alias, type, open_code, close_code, status) VALUES
('营养液A泵', 'PUMP_A', 1, '', '', 0),
('营养液B泵', 'PUMP_B', 1, '', '', 0),
('酸液泵', 'PUMP_ACID', 1, '', '', 0),
('碱液泵', 'PUMP_BASE', 1, '', '', 0),
('灌溉泵', 'PUMP_IRRIGATE', 1, '', '', 0),
('搅拌泵', 'PUMP_MIX', 1, '', '', 0),
('二氧化碳气肥', 'PUMP_CO2', 1, '', '', 0),
('灌溉循环泵', 'PUMP_CIRCULATION', 1, '', '', 0),
('氯化钙叶面肥', 'PUMP_CALCIUM', 1, '', '', 0),
('轨道电机方向', 'MOTOR_DIRECTION', 0, '', '', 0),
('轨道电机运行', 'MOTOR_STATE', 0, '', '', 0),
('轨道巡检摄像头', 'CAM_PATROL', 0, '', '', 0),
('气象站', 'WEATHER_STATION', 0, '', '', 0),
('土壤传感器', 'SOIL_SENSOR', 0, '', '', 0),
('虫情灯', 'INSECT_LAMP', 0, '', '', 0);

-- 气象站传感器协议配置
DROP TABLE IF EXISTS weather_station_protocol;
CREATE TABLE weather_station_protocol (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
    sensor_key VARCHAR(50) NOT NULL UNIQUE COMMENT '传感器标识',
    display_name VARCHAR(50) NOT NULL COMMENT '显示名称',
    command_hex VARCHAR(100) NOT NULL COMMENT 'Modbus指令',
    unit VARCHAR(20) DEFAULT NULL COMMENT '单位',
    sort_order INT DEFAULT 0 COMMENT '排序号',
    enabled TINYINT DEFAULT 1 COMMENT '是否启用',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT NULL COMMENT '更新时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='后台气象采集配置；用途：WeatherService按启用项读取Modbus指令、单位和排序后采集并写入weather_sensor_data；数据：传感器标识、显示名称、指令、单位、排序和启用状态。当前无页面直接编辑或展示本表。';

INSERT INTO weather_station_protocol (sensor_key, display_name, command_hex, unit, sort_order) VALUES
('TEMPERATURE', '温度', '09 03 00 03 00 02 35 43', '℃', 1),
('HUMIDITY', '湿度', '09 03 00 04 00 02 84 82', '%', 2),
('WIND_SPEED', '风速', '09 03 00 01 00 02 94 83', 'm/s', 3),
('WIND_DIRECTION', '风向', '09 03 00 00 00 02 C5 43', '°', 4),
('TOTAL_RAINFALL', '总累计雨量', '09 03 00 05 00 02 D5 42', 'mm', 5),
('HOURLY_RAINFALL', '每小时雨量', '09 03 00 06 00 02 25 42', 'mm', 6),
('DAILY_RAINFALL', '每天雨量', '09 03 00 07 00 02 74 82', 'mm', 7),
('LIGHT_INTENSITY', '光照强度', '09 03 00 08 00 02 44 81', 'Lux', 8),
('UV_INTENSITY', '紫外线强度', '09 03 00 0A 00 02 E5 41', 'uW/cm²', 9),
('UV_INDEX', '紫外线指数', '09 03 00 0B 00 02 B4 81', 'UVI', 10),
('BATTERY', '设备电量', '09 03 00 0C 00 02 05 40', '', 11);

-- 气象站报警阈值配置表
DROP TABLE IF EXISTS weather_threshold_config;
CREATE TABLE weather_threshold_config (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
    temp_min DECIMAL(5,1) DEFAULT NULL COMMENT '温度下限 °C',
    temp_max DECIMAL(5,1) DEFAULT NULL COMMENT '温度上限 °C',
    humidity_min DECIMAL(5,1) DEFAULT NULL COMMENT '湿度下限 %',
    humidity_max DECIMAL(5,1) DEFAULT NULL COMMENT '湿度上限 %',
    wind_speed_max DECIMAL(5,1) DEFAULT NULL COMMENT '风速上限 m/s',
    total_rainfall_max DECIMAL(5,1) DEFAULT NULL COMMENT '累计雨量上限 mm',
    hourly_rainfall_max DECIMAL(5,1) DEFAULT NULL COMMENT '小时雨量上限 mm',
    daily_rainfall_max DECIMAL(5,1) DEFAULT NULL COMMENT '日雨量上限 mm',
    light_min DECIMAL(8,1) DEFAULT NULL COMMENT '光照下限 Lux',
    light_max DECIMAL(8,1) DEFAULT NULL COMMENT '光照上限 Lux',
    uv_intensity_max DECIMAL(5,1) DEFAULT NULL COMMENT '紫外线强度上限 uW/cm²',
    uv_index_max DECIMAL(3,1) DEFAULT NULL COMMENT '紫外线指数上限',
    battery_alarm TINYINT DEFAULT 1 COMMENT '低电量报警 0=关 1=开',
    enabled TINYINT DEFAULT 1 COMMENT '总开关',
    updated_at DATETIME DEFAULT NULL COMMENT '更新时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='页面：气象站；区域：报警阈值设置面板；数据：温湿度、风速、雨量、光照、紫外线和低电量报警阈值及开关。页面直接读取/保存，后台据此生成alarm_record。';

INSERT INTO weather_threshold_config
(temp_min, temp_max, humidity_min, humidity_max, wind_speed_max,
 total_rainfall_max, hourly_rainfall_max, daily_rainfall_max,
 light_min, light_max, uv_intensity_max, uv_index_max, battery_alarm)
VALUES
(5.0, 40.0, 30.0, 90.0, 10.0,
 100.0, 20.0, 50.0,
 5000.0, 100000.0, 200.0, 8.0, 1);

-- 初始灌溉计划
INSERT INTO irrigation_schedule (schedule_time, duration, frequency, enabled) VALUES
('08:00', 10, 'daily', 1),
('14:00', 10, 'daily', 1),
('18:00', 10, 'daily', 1);

-- 系统持久化设置（例如营养液模式）。页面改动同时会写入此表，Redis 重启后仍可恢复。
CREATE TABLE IF NOT EXISTS system_setting (
    setting_key VARCHAR(100) PRIMARY KEY COMMENT '设置键',
    setting_value TEXT DEFAULT NULL COMMENT '设置值',
    updated_at DATETIME DEFAULT NULL COMMENT '更新时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='应用持久化设置；页面：营养液配液；数据：手动、自动或AI模式。';

INSERT INTO system_setting (setting_key, setting_value, updated_at)
VALUES ('nutrient.mode', 'manual', NOW())
ON DUPLICATE KEY UPDATE setting_value = setting_value;

-- ============================================================
-- 数据大屏持久化内容
-- 这些表使用 IF NOT EXISTS，已有数据库请执行 dashboard-persistence.sql。
-- ============================================================
CREATE TABLE IF NOT EXISTS dashboard_greenhouse (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
    name VARCHAR(100) NOT NULL COMMENT '大棚名称',
    greenhouse_type VARCHAR(100) DEFAULT NULL COMMENT '大棚类型',
    crop_name VARCHAR(100) DEFAULT NULL COMMENT '作物名称',
    area VARCHAR(50) DEFAULT NULL COMMENT '种植面积',
    plant_count INT DEFAULT NULL COMMENT '定植株数',
    planting_date DATE DEFAULT NULL COMMENT '定植日期',
    is_primary TINYINT NOT NULL DEFAULT 0 COMMENT '是否当前大棚 0否1是',
    sort_order INT NOT NULL DEFAULT 0 COMMENT '展示顺序',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT NULL COMMENT '更新时间',
    INDEX idx_dashboard_greenhouse_primary (is_primary, sort_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='页面：数据大屏；区域：大棚信息；数据：名称、类型、作物、面积、株数和定植日期。';

CREATE TABLE IF NOT EXISTS dashboard_farm_operation (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
    operation_name VARCHAR(100) NOT NULL COMMENT '农事操作名称',
    operation_date DATE DEFAULT NULL COMMENT '操作日期',
    icon_class VARCHAR(100) DEFAULT NULL COMMENT 'Remix图标类名',
    color_theme VARCHAR(20) DEFAULT NULL COMMENT '图标颜色主题',
    sort_order INT NOT NULL DEFAULT 0 COMMENT '展示顺序',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT NULL COMMENT '更新时间',
    INDEX idx_dashboard_operation_sort (sort_order, operation_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='页面：数据大屏；区域：农事操作；数据：已完成农事名称、日期、图标和展示顺序。';

CREATE TABLE IF NOT EXISTS dashboard_todo (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
    week_label VARCHAR(50) NOT NULL COMMENT '时间标签',
    task_name VARCHAR(100) NOT NULL COMMENT '农事类别',
    action_name VARCHAR(100) NOT NULL COMMENT '待办操作',
    sort_order INT NOT NULL DEFAULT 0 COMMENT '展示顺序',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT NULL COMMENT '更新时间',
    INDEX idx_dashboard_todo_sort (sort_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='页面：数据大屏；区域：待办农事；数据：时间、农事类别、待办操作和展示顺序。';

CREATE TABLE IF NOT EXISTS dashboard_market_feedback (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
    title VARCHAR(200) NOT NULL COMMENT '反馈标题',
    summary VARCHAR(255) DEFAULT NULL COMMENT '列表摘要',
    modal_title VARCHAR(200) DEFAULT NULL COMMENT '弹窗标题',
    content TEXT DEFAULT NULL COMMENT '反馈详情',
    enabled TINYINT NOT NULL DEFAULT 1 COMMENT '是否展示 0否1是',
    sort_order INT NOT NULL DEFAULT 0 COMMENT '展示顺序',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT NULL COMMENT '更新时间',
    INDEX idx_dashboard_market_enabled (enabled, sort_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='页面：数据大屏；区域：市场反馈弹窗；数据：反馈标题、摘要、详情和是否展示。';

INSERT INTO dashboard_greenhouse
(name, greenhouse_type, crop_name, area, plant_count, planting_date, is_primary, sort_order)
SELECT '种植架1', '玻璃体棚', '樱桃', '1000 m²', 1200, '2026-03-28', 1, 1
WHERE NOT EXISTS (SELECT 1 FROM dashboard_greenhouse);

INSERT INTO dashboard_farm_operation
(operation_name, operation_date, icon_class, color_theme, sort_order)
SELECT '控温通气', '2026-03-28', 'ri-temp-hot-line', 'blue', 1
WHERE NOT EXISTS (SELECT 1 FROM dashboard_farm_operation WHERE operation_name = '控温通气' AND operation_date = '2026-03-28');
INSERT INTO dashboard_farm_operation
(operation_name, operation_date, icon_class, color_theme, sort_order)
SELECT '浇水', '2026-03-27', 'ri-drop-line', 'cyan', 2
WHERE NOT EXISTS (SELECT 1 FROM dashboard_farm_operation WHERE operation_name = '浇水' AND operation_date = '2026-03-27' AND sort_order = 2);
INSERT INTO dashboard_farm_operation
(operation_name, operation_date, icon_class, color_theme, sort_order)
SELECT '施肥', '2026-03-27', 'ri-seedling-line', 'green', 3
WHERE NOT EXISTS (SELECT 1 FROM dashboard_farm_operation WHERE operation_name = '施肥' AND operation_date = '2026-03-27');
INSERT INTO dashboard_farm_operation
(operation_name, operation_date, icon_class, color_theme, sort_order)
SELECT '灌根', '2026-03-26', 'ri-bug-line', 'purple', 4
WHERE NOT EXISTS (SELECT 1 FROM dashboard_farm_operation WHERE operation_name = '灌根' AND operation_date = '2026-03-26');

INSERT INTO dashboard_todo (week_label, task_name, action_name, sort_order)
SELECT '第6周', '农事', '浇水', 1
WHERE NOT EXISTS (SELECT 1 FROM dashboard_todo WHERE week_label = '第6周' AND task_name = '农事' AND action_name = '浇水');
INSERT INTO dashboard_todo (week_label, task_name, action_name, sort_order)
SELECT '第1周', '农事', '浇水', 2
WHERE NOT EXISTS (SELECT 1 FROM dashboard_todo WHERE week_label = '第1周' AND task_name = '农事' AND action_name = '浇水' AND sort_order = 2);
INSERT INTO dashboard_todo (week_label, task_name, action_name, sort_order)
SELECT '第1周', '施肥', '植保', 3
WHERE NOT EXISTS (SELECT 1 FROM dashboard_todo WHERE week_label = '第1周' AND task_name = '施肥' AND action_name = '植保');

INSERT INTO dashboard_market_feedback
(title, summary, modal_title, content, enabled, sort_order)
SELECT '2025第四批次樱桃市场评价中等', '消费者评价数据已更新', '消费者反映风味欠佳',
       '据NFC追溯得到的消费者评价数据，56%消费者反映该批次樱桃糖度较低；33%消费者反映消费者反映该批次樱桃酸度过高，9%消费者反映该批次樱桃硬度较低。', 1, 1
WHERE NOT EXISTS (SELECT 1 FROM dashboard_market_feedback WHERE title = '2025第四批次樱桃市场评价中等');

-- 初始土壤数据。实际传感器采集的数据会持续写入本表并成为页面最新值。
INSERT INTO soil_sensor_data
(soil_temp, soil_humidity, soil_ec, soil_ph, soil_salt, soil_nitrogen, soil_phosphorus, soil_potassium, record_time)
VALUES (23.6, 47.0, 0.40, 6.5, 0.079, 101.0, 13.0, 167.0, NOW());

-- ============================================================
-- 植株历年档案模块
-- 这些表使用 IF NOT EXISTS，重复执行初始化脚本不会清空已有档案。
-- ============================================================
CREATE TABLE IF NOT EXISTS plant_info (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
    plant_name VARCHAR(100) NOT NULL COMMENT '植株名称',
    scientific_name VARCHAR(150) DEFAULT NULL COMMENT '学名',
    family_genus VARCHAR(100) DEFAULT NULL COMMENT '科属',
    variety VARCHAR(100) DEFAULT NULL COMMENT '品种',
    source_type VARCHAR(50) DEFAULT NULL COMMENT '来源类型',
    source_channel VARCHAR(200) DEFAULT NULL COMMENT '来源渠道',
    plant_date DATE DEFAULT NULL COMMENT '定植日期',
    plant_location VARCHAR(100) DEFAULT NULL COMMENT '种植位置',
    soil_type VARCHAR(100) DEFAULT NULL COMMENT '土壤类型',
    substrate_ratio VARCHAR(200) DEFAULT NULL COMMENT '基质配比',
    light_env VARCHAR(200) DEFAULT NULL COMMENT '光照环境',
    planting_spec VARCHAR(200) DEFAULT NULL COMMENT '种植规格',
    main_photo VARCHAR(500) DEFAULT NULL COMMENT '主图片',
    remark TEXT DEFAULT NULL COMMENT '备注',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT NULL COMMENT '更新时间',
    INDEX idx_plant_info_name (plant_name),
    INDEX idx_plant_info_variety (variety)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='页面：历年档案；区域：植株档案侧栏和基础档案；数据：植物基本资料、种植条件、主图和备注。';

CREATE TABLE IF NOT EXISTS plant_year_record (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
    plant_id BIGINT NOT NULL COMMENT '植株ID',
    year INT NOT NULL COMMENT '年度',
    growth_grade VARCHAR(30) DEFAULT NULL COMMENT '生长评级',
    annual_summary TEXT DEFAULT NULL COMMENT '年度总结',
    problem_review TEXT DEFAULT NULL COMMENT '问题复盘',
    improvement_suggestion TEXT DEFAULT NULL COMMENT '改进建议',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT NULL COMMENT '更新时间',
    UNIQUE KEY uk_plant_year (plant_id, year),
    INDEX idx_year_record_plant (plant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='页面：历年档案；区域：年度选项卡和年终总结；数据：年度生长评级、年度总结、问题复盘和改进建议。';

CREATE TABLE IF NOT EXISTS plant_phenology (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
    plant_id BIGINT NOT NULL COMMENT '植株ID',
    year INT NOT NULL COMMENT '年度',
    stage VARCHAR(50) DEFAULT NULL COMMENT '物候阶段',
    phase VARCHAR(100) DEFAULT NULL COMMENT '阶段名称',
    event_date DATE DEFAULT NULL COMMENT '发生日期',
    description TEXT DEFAULT NULL COMMENT '描述',
    photo_url VARCHAR(500) DEFAULT NULL COMMENT '图片',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT NULL COMMENT '更新时间',
    INDEX idx_phenology_plant_year (plant_id, year),
    INDEX idx_phenology_event_date (event_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='页面：历年档案；区域：物候年历；数据：生育阶段、物候期、发生日期、观察描述和图片地址。';

CREATE TABLE IF NOT EXISTS plant_cultivation (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
    plant_id BIGINT NOT NULL COMMENT '植株ID',
    year INT NOT NULL COMMENT '年度',
    month TINYINT NOT NULL COMMENT '月份',
    water_frequency VARCHAR(100) DEFAULT NULL COMMENT '浇水频次',
    fertilize VARCHAR(255) DEFAULT NULL COMMENT '施肥',
    pruning VARCHAR(255) DEFAULT NULL COMMENT '修剪',
    trellis VARCHAR(255) DEFAULT NULL COMMENT '搭架/绑蔓',
    weeding VARCHAR(255) DEFAULT NULL COMMENT '除草',
    repot VARCHAR(255) DEFAULT NULL COMMENT '换盆/移栽',
    other VARCHAR(255) DEFAULT NULL COMMENT '其他操作',
    remark TEXT DEFAULT NULL COMMENT '备注',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT NULL COMMENT '更新时间',
    INDEX idx_cultivation_plant_year (plant_id, year)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='页面：历年档案；区域：栽培管理；数据：月度灌溉、施肥、修剪、搭架、除草、换盆和备注。';

CREATE TABLE IF NOT EXISTS plant_pest_disease (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
    plant_id BIGINT NOT NULL COMMENT '植株ID',
    year INT NOT NULL COMMENT '年度',
    record_type VARCHAR(50) DEFAULT NULL COMMENT '记录类型',
    pest_name VARCHAR(100) DEFAULT NULL COMMENT '病虫害名称',
    occur_date DATE DEFAULT NULL COMMENT '发生日期',
    symptom TEXT DEFAULT NULL COMMENT '症状',
    severity VARCHAR(30) DEFAULT NULL COMMENT '严重程度',
    measure_type VARCHAR(50) DEFAULT NULL COMMENT '措施类型',
    measure TEXT DEFAULT NULL COMMENT '处理措施',
    effect TEXT DEFAULT NULL COMMENT '处理效果',
    photo_url VARCHAR(500) DEFAULT NULL COMMENT '图片',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT NULL COMMENT '更新时间',
    INDEX idx_pest_plant_year (plant_id, year),
    INDEX idx_pest_occur_date (occur_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='页面：历年档案；区域：病虫害逆境；数据：发生名称/时间、症状、严重程度、处理措施、效果和图片。';

CREATE TABLE IF NOT EXISTS plant_growth_record (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
    plant_id BIGINT NOT NULL COMMENT '植株ID',
    year INT NOT NULL COMMENT '年度',
    record_date DATE DEFAULT NULL COMMENT '观测日期',
    height_cm DECIMAL(8,2) DEFAULT NULL COMMENT '株高厘米',
    crown_width_cm DECIMAL(8,2) DEFAULT NULL COMMENT '冠幅厘米',
    leaf_count INT DEFAULT NULL COMMENT '叶片数',
    flower_count INT DEFAULT NULL COMMENT '花朵数',
    fruit_count INT DEFAULT NULL COMMENT '果实数',
    photo_url VARCHAR(500) DEFAULT NULL COMMENT '图片',
    photo_no VARCHAR(100) DEFAULT NULL COMMENT '图片编号',
    remark TEXT DEFAULT NULL COMMENT '备注',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT NULL COMMENT '更新时间',
    INDEX idx_growth_plant_year (plant_id, year),
    INDEX idx_growth_record_date (record_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='页面：历年档案；区域：生长观测；数据：观测日期、株高、冠幅、叶花果数量、照片和备注。';
