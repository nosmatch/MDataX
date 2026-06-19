-- 执行ID生成器表
-- 用于生成唯一的、递增的8位数字执行ID

CREATE TABLE IF NOT EXISTS id_generator (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '自增ID',
    prefix VARCHAR(16) NOT NULL COMMENT 'ID前缀（如EXEC）',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    UNIQUE KEY uk_prefix_create_time (prefix, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='ID生成器表';

-- 插入初始记录来获取ID
-- 每次需要新的执行ID时：INSERT INTO id_generator(prefix) VALUES ('EXEC');
-- 然后使用 LAST_INSERT_ID() 获取的ID格式化为8位数字
