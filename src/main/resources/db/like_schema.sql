-- 点赞功能 DDL
-- 修改 log.love 字段为 INT
ALTER TABLE log MODIFY COLUMN love INT DEFAULT 0 NOT NULL COMMENT '点赞数';

-- 游标分页索引（覆盖 WHERE deleted=1 + ORDER BY create_time DESC）
ALTER TABLE log ADD INDEX idx_deleted_createtime (deleted, create_time);

-- log_pic 查询索引（批量查图、详情查图、删除配图）
ALTER TABLE log_pic ADD INDEX idx_log_id (log_id);

-- 创建 like_record 表（物理删除）
CREATE TABLE IF NOT EXISTS like_record (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id INT NOT NULL COMMENT '用户ID',
    log_id INT NOT NULL COMMENT '博客ID',
    create_time DATETIME NOT NULL COMMENT '点赞时间',
    UNIQUE KEY uk_user_log (user_id, log_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='点赞记录表';

CREATE TABLE mq_failure_record (
                                   id          INT AUTO_INCREMENT PRIMARY KEY,
                                   fail_type   VARCHAR(20)  NOT NULL COMMENT 'PRODUCER / CONSUMER',
                                   error_msg   TEXT         COMMENT '异常堆栈',
                                   operation_desc VARCHAR(500) NOT NULL COMMENT '操作描述，用于数据恢复',
                                   create_time DATETIME     DEFAULT CURRENT_TIMESTAMP
) COMMENT 'MQ失败记录';
