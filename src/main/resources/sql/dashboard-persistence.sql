-- Dashboard persistence migration
-- Safe for an existing jhds database: it creates only missing tables and
-- inserts the original dashboard content only when the matching row is absent.
USE jhds;

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

-- Migrate the two original dashboard alerts into the shared alarm table only
-- when their stable title and time are absent. The dashboard then reads its
-- current rows from alarm_record instead of keeping a second static copy.
INSERT INTO alarm_record (title, description, level, source_module, location, status, created_at)
SELECT '发现虫害几棵', 'AI图像识别检测到种植架1、2出现蚜虫聚集，建议立即进行植保处理', 'urgent', 'insect', '种植架1、种植架2', 0, '2026-03-30 13:30:00'
WHERE NOT EXISTS (SELECT 1 FROM alarm_record WHERE title = '发现虫害几棵' AND created_at = '2026-03-30 13:30:00');

INSERT INTO alarm_record (title, description, level, source_module, location, status, created_at)
SELECT '植株叶面存在杂点', '轨道巡检摄像头检测到A2区域植株叶面出现不明杂点，疑似病害早期', 'important', 'patrol', 'A2', 0, '2026-03-30 12:45:00'
WHERE NOT EXISTS (SELECT 1 FROM alarm_record WHERE title = '植株叶面存在杂点' AND created_at = '2026-03-30 12:45:00');
