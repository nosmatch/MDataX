-- 初始化执行ID生成器
-- 执行此脚本来设置ID生成器的起始值

USE mdatax;

-- 创建ID生成器表
CREATE TABLE IF NOT EXISTS id_generator (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '自增ID',
    prefix VARCHAR(16) NOT NULL COMMENT 'ID前缀（如EXEC）',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    UNIQUE KEY uk_prefix_create_time (prefix, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='ID生成器表';

-- 设置起始值为1（可选，如果希望从特定数字开始）
-- ALTER TABLE id_generator AUTO_INCREMENT = 1;

-- 验证表结构
DESC id_generator;

SELECT 'ID生成器表初始化完成' AS status;
