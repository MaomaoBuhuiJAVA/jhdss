-- Page-local AI alert content migration for an existing jhds database.
-- Safe to rerun: rows are inserted only when alert_key is missing.
USE jhds;

CREATE TABLE IF NOT EXISTS page_alert_content (
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
(alert_key, title, summary, modal_title, description, images_json, enabled, sort_order)
SELECT 'dashboard-graft', '嫁接苗异常告警', '⚠️嫁接苗存在异常特征，请及时处理！', '嫁接苗异常告警', '⚠️嫁接苗存在异常特征，请及时处理！', '["/jhds/images/alerts/graft-union-anomaly.png","/jhds/images/alerts/graft-cut-anomaly.png"]', 1, 1
WHERE NOT EXISTS (SELECT 1 FROM page_alert_content WHERE alert_key = 'dashboard-graft');

INSERT INTO page_alert_content
(alert_key, title, summary, modal_title, description, images_json, enabled, sort_order)
SELECT 'insect-pest', '发现害虫！', '点击查看 AI 巡检详情', '发现害虫！', '发现5只果蝇、1只桃红颈天牛，同时部分害虫无法捕获，叶面有失绿斑，且有部分红色斑点，疑似红蜘蛛', '["/jhds/images/alerts/fruit-fly-detection.png","/jhds/images/alerts/red-spider-suspected.png","/jhds/images/alerts/longhorn-beetle-detection.png"]', 1, 2
WHERE NOT EXISTS (SELECT 1 FROM page_alert_content WHERE alert_key = 'insect-pest');

INSERT INTO page_alert_content
(alert_key, title, summary, modal_title, description, images_json, enabled, sort_order)
SELECT 'patrol-flower', '⚠️花朵数量严重超标！', '点击查看 AI 巡检详情', '花朵数量严重超标', '', '["/jhds/images/alerts/flower-overload-c2.png","/jhds/images/alerts/flower-overload-c1.png"]', 1, 3
WHERE NOT EXISTS (SELECT 1 FROM page_alert_content WHERE alert_key = 'patrol-flower');
