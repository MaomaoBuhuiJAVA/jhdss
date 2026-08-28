-- Alarm Center persistence migration
-- Run this on an existing jhds database. It preserves existing alarm rows.
-- Status values: 0=pending, 1=resolved (the legacy "handled" value), 2=processing.
USE jhds;

CREATE TABLE IF NOT EXISTS alarm_record (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
    title VARCHAR(200) NOT NULL COMMENT '报警标题',
    description TEXT DEFAULT NULL COMMENT '报警描述',
    level VARCHAR(10) NOT NULL COMMENT '级别 urgent/important/normal',
    source_module VARCHAR(20) NOT NULL COMMENT '来源模块 patrol/weather/nutrient/insect/iot',
    location VARCHAR(50) DEFAULT NULL COMMENT '位置',
    status TINYINT NOT NULL DEFAULT 0 COMMENT '处置状态 0=待处理 1=已解决 2=处理中',
    handling_memo TEXT DEFAULT NULL COMMENT '处置说明',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发生时间',
    handled_at DATETIME DEFAULT NULL COMMENT '处理时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='页面：报警中心；数据：告警详情、处置状态和说明。';

-- MySQL 5.7 and 8.x compatible conditional column creation.
SET @alarm_add_memo_sql = (
    SELECT IF(
        EXISTS(
            SELECT 1 FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'alarm_record'
              AND COLUMN_NAME = 'handling_memo'
        ),
        'SELECT 1',
        'ALTER TABLE alarm_record ADD COLUMN handling_memo TEXT DEFAULT NULL COMMENT ''处置说明'' AFTER status'
    )
);
PREPARE alarm_add_memo_stmt FROM @alarm_add_memo_sql;
EXECUTE alarm_add_memo_stmt;
DEALLOCATE PREPARE alarm_add_memo_stmt;

UPDATE alarm_record SET status = 0 WHERE status IS NULL;

ALTER TABLE alarm_record
    MODIFY COLUMN status TINYINT NOT NULL DEFAULT 0 COMMENT '处置状态 0=待处理 1=已解决（兼容旧已处理值） 2=处理中';

ALTER TABLE alarm_record COMMENT = '页面：报警中心；区域：报警列表、状态与处置说明、来源分布；数据：标题、描述、级别、来源、位置、处置状态、说明与时间。页面通过API读取和修改本表；气象阈值等后台服务也会写入本表。';

-- Seed the ten original Alarm Center rows once. Each row is matched by its
-- stable title and occurrence time, so rerunning this script is safe.
INSERT INTO alarm_record (title, description, level, source_module, location, status, created_at)
SELECT '发现虫害几棵', 'AI图像识别检测到种植架1、2出现蚜虫聚集，建议立即进行植保处理', 'urgent', 'insect', '种植架1、2', 0, '2026-03-30 13:30:00'
WHERE NOT EXISTS (SELECT 1 FROM alarm_record WHERE title = '发现虫害几棵' AND created_at = '2026-03-30 13:30:00');

INSERT INTO alarm_record (title, description, level, source_module, location, status, created_at)
SELECT '植株叶面存在杂点', '轨道巡检摄像头检测到A2区域植株叶面出现不明杂点，疑似病害早期', 'important', 'patrol', 'A2区域', 0, '2026-03-30 12:45:00'
WHERE NOT EXISTS (SELECT 1 FROM alarm_record WHERE title = '植株叶面存在杂点' AND created_at = '2026-03-30 12:45:00');

INSERT INTO alarm_record (title, description, level, source_module, location, status, created_at)
SELECT '土壤湿度偏低', '土壤湿度传感器显示当前湿度45%，略低于设定阈值50%', 'normal', 'nutrient', NULL, 0, '2026-03-30 11:20:00'
WHERE NOT EXISTS (SELECT 1 FROM alarm_record WHERE title = '土壤湿度偏低' AND created_at = '2026-03-30 11:20:00');

INSERT INTO alarm_record (title, description, level, source_module, location, status, created_at)
SELECT '风速超过3级', '气象站监测到当前风速3.2m/s，建议检查大棚通风口', 'normal', 'weather', NULL, 0, '2026-03-30 10:15:00'
WHERE NOT EXISTS (SELECT 1 FROM alarm_record WHERE title = '风速超过3级' AND created_at = '2026-03-30 10:15:00');

INSERT INTO alarm_record (title, description, level, source_module, location, status, created_at)
SELECT '营养液EC值异常', '土壤EC值达到2.1mS/cm，超出正常范围1.5-2.0，需调整配液比例', 'important', 'nutrient', NULL, 0, '2026-03-30 09:30:00'
WHERE NOT EXISTS (SELECT 1 FROM alarm_record WHERE title = '营养液EC值异常' AND created_at = '2026-03-30 09:30:00');

INSERT INTO alarm_record (title, description, level, source_module, location, status, created_at)
SELECT '轨道巡检设备离线', 'AI轨道巡检模块通信中断，已持续5分钟，请检查网络连接', 'urgent', 'patrol', NULL, 0, '2026-03-30 08:45:00'
WHERE NOT EXISTS (SELECT 1 FROM alarm_record WHERE title = '轨道巡检设备离线' AND created_at = '2026-03-30 08:45:00');

INSERT INTO alarm_record (title, description, level, source_module, location, status, created_at)
SELECT '大棚温度过高', '大棚1温度达到38°C，超过预警阈值35°C，建议开启通风降温', 'important', 'iot', '大棚1', 0, '2026-03-30 14:10:00'
WHERE NOT EXISTS (SELECT 1 FROM alarm_record WHERE title = '大棚温度过高' AND created_at = '2026-03-30 14:10:00');

INSERT INTO alarm_record (title, description, level, source_module, location, status, created_at)
SELECT '二氧化碳浓度偏低', '大棚2内CO₂浓度降至280ppm，低于光合作用适宜值，建议增施CO₂', 'normal', 'iot', '大棚2', 0, '2026-03-30 13:50:00'
WHERE NOT EXISTS (SELECT 1 FROM alarm_record WHERE title = '二氧化碳浓度偏低' AND created_at = '2026-03-30 13:50:00');

INSERT INTO alarm_record (title, description, level, source_module, location, status, created_at)
SELECT '光照强度不足', '连续阴天导致大棚内光照强度仅8000lux，建议开启补光灯', 'normal', 'iot', NULL, 0, '2026-03-30 07:30:00'
WHERE NOT EXISTS (SELECT 1 FROM alarm_record WHERE title = '光照强度不足' AND created_at = '2026-03-30 07:30:00');

INSERT INTO alarm_record (title, description, level, source_module, location, status, created_at)
SELECT '水泵异常停机', '灌溉系统B水泵电流异常自动停机，需检查电机和电路', 'urgent', 'nutrient', '灌溉系统B', 0, '2026-03-30 06:15:00'
WHERE NOT EXISTS (SELECT 1 FROM alarm_record WHERE title = '水泵异常停机' AND created_at = '2026-03-30 06:15:00');
