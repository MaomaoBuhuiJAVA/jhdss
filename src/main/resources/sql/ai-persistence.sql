-- AI问答和AI学习资料的增量持久化表。
-- 应用启动时会幂等创建并从 photo/1-3 导入缺失的视频资料。
CREATE TABLE IF NOT EXISTS ai_knowledge_entry (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
    keywords TEXT NOT NULL COMMENT '关键词，使用逗号、换行或竖线分隔',
    answer TEXT NOT NULL COMMENT '关键词对应回答',
    enabled TINYINT NOT NULL DEFAULT 1 COMMENT '是否启用 0否1是',
    sort_order INT NOT NULL DEFAULT 0 COMMENT '匹配顺序，数字越小越优先',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT NULL COMMENT '更新时间',
    INDEX idx_ai_knowledge_enabled (enabled, sort_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='页面：AI农业助手；区域：关键词问答；数据：关键词、回答和启用状态。';

CREATE TABLE IF NOT EXISTS ai_learn_video (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
    video_name VARCHAR(191) NOT NULL UNIQUE COMMENT '上传视频文件名',
    folder_key VARCHAR(50) NOT NULL COMMENT '资料目录标识',
    group_title VARCHAR(200) NOT NULL COMMENT '知识卡片分组标题',
    enabled TINYINT NOT NULL DEFAULT 1 COMMENT '是否启用 0否1是',
    sort_order INT NOT NULL DEFAULT 0 COMMENT '视频排序',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT NULL COMMENT '更新时间',
    INDEX idx_ai_learn_video_enabled (enabled, sort_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='页面：AI学习；区域：上传视频匹配；数据：视频文件名、资料目录和分组。';

CREATE TABLE IF NOT EXISTS ai_learn_card (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
    video_id BIGINT NOT NULL COMMENT '关联视频ID',
    image_url VARCHAR(500) NOT NULL COMMENT '知识卡片图片路径',
    card_title VARCHAR(255) NOT NULL COMMENT '知识卡片标题',
    description TEXT DEFAULT NULL COMMENT '图片对应讲解',
    enabled TINYINT NOT NULL DEFAULT 1 COMMENT '是否启用 0否1是',
    sort_order INT NOT NULL DEFAULT 0 COMMENT '卡片排序',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT NULL COMMENT '更新时间',
    INDEX idx_ai_learn_card_video (video_id, enabled, sort_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='页面：AI学习；区域：知识卡片；数据：图片、标题、讲解和展示状态。';
