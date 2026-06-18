-- 为 task_execution 表增加 deleted 逻辑删除字段
use mdatax;
ALTER TABLE task_execution
    ADD COLUMN deleted TINYINT DEFAULT 0 COMMENT '逻辑删除：0未删除，1已删除' AFTER max_attempts;

-- 为已有数据设置默认值
UPDATE task_execution SET deleted = 0 WHERE deleted IS NULL;
