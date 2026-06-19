-- 测试任务执行记录数据
-- 用于验证统计功能
USE mdatax;

-- 首先确保ID生成器表存在并初始化
CREATE TABLE IF NOT EXISTS id_generator (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '自增ID',
    prefix VARCHAR(16) NOT NULL COMMENT 'ID前缀（如EXEC）',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 首先确保有一些任务数据
INSERT INTO task (id, task_code, task_name, task_type, description, owner_user_id, owner_user_name, create_user_id, priority, cron_expression, status, deleted, create_time, update_time)
VALUES
(1, 'TASK-0001', '测试SQL任务', 'SQL', '用于测试的SQL任务', 1, 'admin', 1, 5, '0 0 * * *', 1, 0, NOW(), NOW()),
(2, 'TASK-0002', '测试同步任务', 'SYNC', '用于测试的同步任务', 1, 'admin', 1, 5, '0 1 * * *', 1, 0, NOW(), NOW())
ON DUPLICATE KEY UPDATE task_name=VALUES(task_name);

-- 插入今天的执行记录（不同状态）
INSERT INTO task_execution (execution_id, task_id, trigger_type, trigger_user_id, scheduler_instance_id, start_time, end_time, status, duration_ms, affected_rows, sync_count, attempt_number, max_attempts, deleted, create_time)
VALUES
-- 成功的执行记录
('00000001', 1, 'MANUAL', 1, NULL, DATE_FORMAT(NOW(), '%Y-%m-%d 09:00:00'), DATE_FORMAT(NOW(), '%Y-%m-%d 09:05:30'), 'SUCCESS', 330000, 1000, NULL, 1, 1, 0, NOW()),
('00000002', 2, 'SCHEDULE', NULL, 'scheduler-1', DATE_FORMAT(NOW(), '%Y-%m-%d 10:00:00'), DATE_FORMAT(NOW(), '%Y-%m-%d 10:15:45'), 'SUCCESS', 945000, NULL, 5000, 1, 1, 0, NOW()),

-- 失败的执行记录
('00000003', 1, 'MANUAL', 1, NULL, DATE_FORMAT(NOW(), '%Y-%m-%d 11:00:00'), DATE_FORMAT(NOW(), '%Y-%m-%d 11:02:15'), 'FAILED', 135000, NULL, NULL, 1, 1, 0, NOW()),
('00000004', 2, 'SCHEDULE', NULL, 'scheduler-2', DATE_FORMAT(NOW(), '%Y-%m-%d 12:00:00'), DATE_FORMAT(NOW(), '%Y-%m-%d 12:01:30'), 'FAILED', 90000, NULL, NULL, 1, 1, 0, NOW()),

-- 运行中的执行记录
('00000005', 1, 'MANUAL', 1, NULL, DATE_FORMAT(NOW(), '%Y-%m-%d 13:00:00'), NULL, 'RUNNING', NULL, NULL, NULL, 1, 1, 0, NOW()),
('00000006', 2, 'SCHEDULE', NULL, 'scheduler-3', DATE_FORMAT(NOW(), '%Y-%m-%d 14:00:00'), NULL, 'RUNNING', NULL, NULL, NULL, 1, 1, 0, NOW()),

-- 超时的执行记录
('00000007', 1, 'MANUAL', 1, NULL, DATE_FORMAT(NOW(), '%Y-%m-%d 15:00:00'), DATE_FORMAT(NOW(), '%Y-%m-%d 16:01:00'), 'TIMEOUT', 3660000, NULL, NULL, 1, 1, 0, NOW()),

-- 已终止的执行记录
('00000008', 2, 'MANUAL', 1, NULL, DATE_FORMAT(NOW(), '%Y-%m-%d 17:00:00'), DATE_FORMAT(NOW(), '%Y-%m-%d 17:00:30'), 'KILLED', 30000, NULL, NULL, 1, 1, 0, NOW()),

-- 等待中的执行记录
('00000009', 1, 'DEPENDENCY', NULL, NULL, DATE_FORMAT(NOW(), '%Y-%m-%d 18:00:00'), NULL, 'PENDING', NULL, NULL, NULL, 1, 1, 0, NOW())
ON DUPLICATE KEY UPDATE status=VALUES(status);

-- 插入昨天的执行记录
INSERT INTO task_execution (execution_id, task_id, trigger_type, trigger_user_id, scheduler_instance_id, start_time, end_time, status, duration_ms, affected_rows, sync_count, attempt_number, max_attempts, deleted, create_time)
VALUES
('00000010', 1, 'SCHEDULE', NULL, 'scheduler-4', DATE_SUB(DATE_FORMAT(NOW(), '%Y-%m-%d 09:00:00'), INTERVAL 1 DAY), DATE_SUB(DATE_FORMAT(NOW(), '%Y-%m-%d 09:05:30'), INTERVAL 1 DAY), 'SUCCESS', 330000, 1000, NULL, 1, 1, 0, NOW()),
('00000011', 2, 'MANUAL', 1, NULL, DATE_SUB(DATE_FORMAT(NOW(), '%Y-%m-%d 10:00:00'), INTERVAL 1 DAY), DATE_SUB(DATE_FORMAT(NOW(), '%Y-%m-%d 10:02:00'), INTERVAL 1 DAY), 'FAILED', 120000, NULL, NULL, 1, 1, 0, NOW()),
('00000012', 1, 'SCHEDULE', NULL, 'scheduler-5', DATE_SUB(DATE_FORMAT(NOW(), '%Y-%m-%d 11:00:00'), INTERVAL 1 DAY), DATE_SUB(DATE_FORMAT(NOW(), '%Y-%m-%d 11:10:00'), INTERVAL 1 DAY), 'SUCCESS', 600000, 2000, NULL, 1, 1, 0, NOW())
ON DUPLICATE KEY UPDATE status=VALUES(status);

-- 查询验证数据
SELECT
    '数据验证' AS description,
    COUNT(*) AS total_records,
    SUM(CASE WHEN status = 'SUCCESS' THEN 1 ELSE 0 END) AS success_count,
    SUM(CASE WHEN status = 'FAILED' THEN 1 ELSE 0 END) AS failed_count,
    SUM(CASE WHEN status = 'RUNNING' THEN 1 ELSE 0 END) AS running_count,
    SUM(CASE WHEN status = 'PENDING' THEN 1 ELSE 0 END) AS pending_count,
    SUM(CASE WHEN status = 'TIMEOUT' THEN 1 ELSE 0 END) AS timeout_count,
    SUM(CASE WHEN status = 'KILLED' THEN 1 ELSE 0 END) AS killed_count,
    MIN(start_time) AS earliest_start,
    MAX(start_time) AS latest_start
FROM task_execution
WHERE deleted = 0;
