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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='巡逻任务表';

-- 巡逻记录表
DROP TABLE IF EXISTS patrol_record;
CREATE TABLE patrol_record (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
    task_id BIGINT DEFAULT NULL COMMENT '关联任务ID',
    image_url VARCHAR(500) DEFAULT NULL COMMENT '拍摄图片路径',
    track_position VARCHAR(20) DEFAULT NULL COMMENT '轨道位置',
    shoot_time DATETIME DEFAULT NULL COMMENT '拍摄时间',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='巡逻拍摄记录表';

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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='气象站传感器数据表';

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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='土壤传感器数据表';

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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='设备表';

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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='灌溉计划表';

-- 灌溉记录表
DROP TABLE IF EXISTS irrigation_record;
CREATE TABLE irrigation_record (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
    mode VARCHAR(10) NOT NULL COMMENT '模式 manual/auto',
    pump_alias VARCHAR(50) DEFAULT NULL COMMENT '泵别名',
    duration INT DEFAULT 0 COMMENT '运行时长（秒）',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '执行时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='灌溉记录表';

-- 虫情记录表
DROP TABLE IF EXISTS insect_record;
CREATE TABLE insect_record (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
    image_url VARCHAR(500) DEFAULT NULL COMMENT '虫体图片路径',
    species VARCHAR(50) NOT NULL COMMENT '虫体种类',
    count INT DEFAULT 0 COMMENT '虫体数量',
    record_date DATE NOT NULL COMMENT '记录日期',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_record_date (record_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='虫情记录表';

-- 虫情记录表扩展字段（如已有表则执行以下ALTER）
-- 如果表已存在但缺少字段,请手动执行:
-- ALTER TABLE insect_record ADD COLUMN device_id VARCHAR(50) DEFAULT NULL COMMENT '设备编号' AFTER image_url;
-- ALTER TABLE insect_record ADD COLUMN thumb_url VARCHAR(500) DEFAULT NULL COMMENT '缩略图路径' AFTER image_url;
-- ALTER TABLE insect_record ADD COLUMN ai_engine VARCHAR(50) DEFAULT NULL COMMENT 'AI识别引擎' AFTER count;
-- ALTER TABLE insect_record ADD COLUMN record_time DATETIME DEFAULT NULL COMMENT '记录时间' AFTER record_date;

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
    status TINYINT DEFAULT 0 COMMENT '0=未处理 1=已处理',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发生时间',
    handled_at DATETIME DEFAULT NULL COMMENT '处理时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='报警记录表';

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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='控制日志表';

-- 初始设备数据
INSERT INTO equipment (name, alias, type, open_code, close_code, status) VALUES
('营养液A泵', 'PUMP_A', 0, '', '', 0),
('营养液B泵', 'PUMP_B', 0, '', '', 0),
('酸液泵', 'PUMP_ACID', 0, '', '', 0),
('碱液泵', 'PUMP_BASE', 0, '', '', 0),
('灌溉泵', 'PUMP_IRRIGATE', 0, '', '', 0),
('搅拌泵', 'PUMP_MIX', 0, '', '', 0),
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='气象站传感器协议配置';

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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='气象站报警阈值配置';

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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='植株基础档案';

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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='植株年度档案';

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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='植株物候记录';

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
    UNIQUE KEY uk_cultivation_plant_year_month (plant_id, year, month),
    INDEX idx_cultivation_plant_year (plant_id, year)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='植株栽培管理记录';

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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='植株病虫害与逆境记录';

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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='植株生长观测记录';
