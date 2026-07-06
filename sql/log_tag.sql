-- AI 标签表（ddl）
-- 执行前确认数据库为 share2

CREATE TABLE IF NOT EXISTS log_tag (
    id          BIGINT  AUTO_INCREMENT PRIMARY KEY,
    log_id      BIGINT       NOT NULL COMMENT '博客ID',
    tag_name    VARCHAR(50)  NOT NULL COMMENT '标签名称',
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_log_id (log_id),
    INDEX idx_tag_name (tag_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI标签表';

-- ES log 索引需要新增 tags 字段（keyword 数组类型）：
-- PUT log/_mapping
-- { "properties": { "tags": { "type": "keyword" } } }
