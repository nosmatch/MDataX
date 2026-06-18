-- 为 sx_task_instance 表增加 timeout_seconds 字段
-- 用于 TimeoutChecker 读取任务超时时间，替代之前的硬编码 600 秒
use mdatax;
ALTER TABLE sx_task_instance
    ADD COLUMN timeout_seconds INT DEFAULT NULL COMMENT '超时时间（秒）' AFTER duration_ms;
