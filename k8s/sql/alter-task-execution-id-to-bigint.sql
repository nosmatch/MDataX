-- 修改执行ID为整型
-- 执行时间: 2026-06-19

ALTER TABLE task_execution
MODIFY COLUMN execution_id BIGINT UNIQUE NOT NULL COMMENT '执行ID（整型，雪花算法生成）';

ALTER TABLE task_execution
MODIFY COLUMN parent_execution_id BIGINT COMMENT '父执行ID（如果是依赖触发）';
