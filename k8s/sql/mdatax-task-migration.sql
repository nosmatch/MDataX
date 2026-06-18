-- ========================================
-- MDataX 任务管理模块数据迁移脚本
-- 创建时间: 2026-06-12
-- 说明: 将 sql_task 和 sync_task 数据迁移到新的统一表结构
-- ========================================

-- ------------------------------
-- 迁移 SQL 任务
-- ------------------------------
INSERT INTO task (
    task_code, task_name, task_type, description,
    owner_user_id, owner_user_name, create_user_id,
    priority, tags,
    cron_expression, status,
    workflow_id,
    ds_process_code, ds_schedule_id, ds_task_code, schedulerx_dag_id,
    retry_times, retry_interval, timeout_seconds,
    deleted, create_time, update_time
)
SELECT
    CONCAT('TASK-', LPAD(id, 8, '0')) AS task_code,
    task_name,
    'SQL' AS task_type,
    description,
    create_user_id AS owner_user_id,
    (SELECT username FROM sys_user WHERE id = sql_task.create_user_id LIMIT 1) AS owner_user_name,
    create_user_id,
    5 AS priority,  -- 默认优先级
    NULL AS tags,  -- SQL任务没有标签字段
    cron_expression,
    status,
    workflow_id,
    ds_process_code,
    ds_schedule_id,
    ds_task_code,
    schedulerx_dag_id,
    retry_times,
    retry_interval,
    0 AS timeout_seconds,  -- SQL任务没有超时配置
    deleted,
    create_time,
    update_time
FROM sql_task
WHERE deleted = 0
ON DUPLICATE KEY UPDATE
    task_name = VALUES(task_name),
    description = VALUES(description),
    cron_expression = VALUES(cron_expression),
    status = VALUES(status),
    update_time = VALUES(update_time);


-- ------------------------------
-- 迁移 SQL 任务详情
-- ------------------------------
INSERT INTO task_sql_detail (task_id, sql_content, target_datasource_id, create_time, update_time)
SELECT
    t.id AS task_id,
    s.sql_content,
    NULL AS target_datasource_id,  -- SQL任务没有目标数据源字段
    s.create_time,
    s.update_time
FROM sql_task s
INNER JOIN task t ON t.task_code = CONCAT('TASK-', LPAD(s.id, 8, '0'))
    AND t.task_type = 'SQL'
    AND t.deleted = s.deleted
WHERE s.deleted = 0
ON DUPLICATE KEY UPDATE
    sql_content = VALUES(sql_content),
    update_time = VALUES(update_time);


-- ------------------------------
-- 迁移同步任务
-- ------------------------------
INSERT INTO task (
    task_code, task_name, task_type, description,
    owner_user_id, owner_user_name, create_user_id,
    priority, tags,
    cron_expression, status,
    workflow_id,
    ds_process_code, ds_schedule_id, ds_task_code, schedulerx_dag_id,
    retry_times, retry_interval, timeout_seconds,
    deleted, create_time, update_time
)
SELECT
    CONCAT('TASK-', LPAD(id + 10000000, 8, '0')) AS task_code,  -- 避免与SQL任务ID冲突
    task_name,
    'SYNC' AS task_type,
    NULL AS description,  -- Sync任务没有描述字段
    create_user_id AS owner_user_id,
    (SELECT username FROM sys_user WHERE id = sync_task.create_user_id LIMIT 1) AS owner_user_name,
    create_user_id,
    5 AS priority,  -- 默认优先级
    NULL AS tags,  -- Sync任务没有标签字段
    cron_expression,
    status,
    workflow_id,
    ds_process_code,
    ds_schedule_id,
    ds_task_code,
    schedulerx_dag_id,
    retry_times,
    retry_interval,
    0 AS timeout_seconds,  -- Sync任务没有超时配置
    deleted,
    create_time,
    update_time
FROM sync_task
WHERE deleted = 0
ON DUPLICATE KEY UPDATE
    task_name = VALUES(task_name),
    cron_expression = VALUES(cron_expression),
    status = VALUES(status),
    update_time = VALUES(update_time);


-- ------------------------------
-- 迁移同步任务详情
-- ------------------------------
INSERT INTO task_sync_detail (
    task_id, source_datasource_id, source_table,
    target_datasource_id, target_table, sync_type, time_field, where_condition,
    last_sync_time, create_time, update_time
)
SELECT
    t.id AS task_id,
    s.datasource_id AS source_datasource_id,
    s.source_table,
    s.datasource_id AS target_datasource_id,  -- 同步任务只有单一数据源ID
    s.target_table,
    s.sync_type,
    s.time_field,
    NULL AS where_condition,  -- Sync任务没有where条件字段
    s.last_sync_time,
    s.create_time,
    s.update_time
FROM sync_task s
INNER JOIN task t ON t.task_code = CONCAT('TASK-', LPAD(s.id + 10000000, 8, '0'))
    AND t.task_type = 'SYNC'
    AND t.deleted = s.deleted
WHERE s.deleted = 0
ON DUPLICATE KEY UPDATE
    source_datasource_id = VALUES(source_datasource_id),
    source_table = VALUES(source_table),
    target_table = VALUES(target_table),
    sync_type = VALUES(sync_type),
    time_field = VALUES(time_field),
    last_sync_time = VALUES(last_sync_time),
    update_time = VALUES(update_time);


-- ------------------------------
-- 迁移工作流内的任务依赖关系
-- ------------------------------
INSERT INTO task_dependency (
    upstream_task_id, downstream_task_id,
    dependency_type, condition_expression, delay_seconds,
    create_user_id, create_time
)
SELECT
    t_upstream.id AS upstream_task_id,
    t_downstream.id AS downstream_task_id,
    'SUCCESS' AS dependency_type,
    NULL AS condition_expression,
    0 AS delay_seconds,
    w.create_user_id,
    d.create_time
FROM sql_task_dependency d
INNER JOIN sql_task_workflow w ON d.workflow_id = w.id
-- 上游任务
INNER JOIN sql_task s_upstream ON d.depend_task_id = s_upstream.id
    AND d.depend_task_type = 'SQL'
INNER JOIN task t_upstream ON t_upstream.task_code = CONCAT('TASK-', LPAD(s_upstream.id, 8, '0'))
    AND t_upstream.task_type = 'SQL'
-- 下游任务
INNER JOIN sql_task s_downstream ON d.task_id = s_downstream.id
    AND d.task_type = 'SQL'
INNER JOIN task t_downstream ON t_downstream.task_code = CONCAT('TASK-', LPAD(s_downstream.id, 8, '0'))
    AND t_downstream.task_type = 'SQL'
WHERE w.deleted = 0
ON DUPLICATE KEY UPDATE
    dependency_type = VALUES(dependency_type),
    condition_expression = VALUES(condition_expression),
    delay_seconds = VALUES(delay_seconds);


-- ------------------------------
-- 迁移工作流实例到执行记录（仅迁移手动触发的）
-- ------------------------------
INSERT INTO task_execution (
    execution_id, task_id,
    trigger_type, trigger_user_id, trigger_user_name,
    scheduler_instance_id, workflow_instance_id,
    start_time, end_time, status, duration_ms,
    affected_rows, sync_count, log_url, error_msg,
    attempt_number, max_attempts, create_time
)
SELECT
    CONCAT('EXEC-', LPAD(id, 16, '0')) AS execution_id,
    t.id AS task_id,
    CASE
        WHEN w.trigger_type = 'MANUAL' THEN 'MANUAL'
        ELSE 'SCHEDULE'
    END AS trigger_type,
    NULL AS trigger_user_id,  -- 工作流实例没有触发用户信息
    NULL AS trigger_user_name,
    CAST(w.ds_instance_id AS CHAR) AS scheduler_instance_id,
    w.id AS workflow_instance_id,
    w.start_time,
    w.end_time,
    w.status,
    CASE
        WHEN w.end_time IS NOT NULL THEN TIMESTAMPDIFF(MICROSECOND, w.start_time, w.end_time) / 1000
        ELSE NULL
    END AS duration_ms,
    NULL AS affected_rows,
    NULL AS sync_count,
    NULL AS log_url,
    w.error_msg,
    1 AS attempt_number,
    1 AS max_attempts,
    w.create_time
FROM sql_task_workflow_instance w
INNER JOIN sql_task_workflow wf ON w.workflow_id = wf.id
INNER JOIN task t ON t.workflow_id = wf.id
WHERE wf.deleted = 0
ON DUPLICATE KEY UPDATE
    status = VALUES(status),
    end_time = VALUES(end_time),
    duration_ms = VALUES(duration_ms),
    error_msg = VALUES(error_msg);


-- ------------------------------
-- 迁移完成后的验证查询
-- ------------------------------
-- 验证任务迁移数量
SELECT
    'SQL任务迁移数量' AS item,
    (SELECT COUNT(*) FROM sql_task WHERE deleted = 0) AS old_count,
    (SELECT COUNT(*) FROM task WHERE task_type = 'SQL' AND deleted = 0) AS new_count
UNION ALL
SELECT
    '同步任务迁移数量' AS item,
    (SELECT COUNT(*) FROM sync_task WHERE deleted = 0) AS old_count,
    (SELECT COUNT(*) FROM task WHERE task_type = 'SYNC' AND deleted = 0) AS new_count
UNION ALL
SELECT
    '任务详情迁移数量' AS item,
    (SELECT COUNT(*) FROM sql_task WHERE deleted = 0) AS old_count,
    (SELECT COUNT(*) FROM task_sql_detail) AS new_count
UNION ALL
SELECT
    '同步详情迁移数量' AS item,
    (SELECT COUNT(*) FROM sync_task WHERE deleted = 0) AS old_count,
    (SELECT COUNT(*) FROM task_sync_detail) AS new_count
UNION ALL
SELECT
    '依赖关系迁移数量' AS item,
    (SELECT COUNT(*) FROM sql_task_dependency d INNER JOIN sql_task_workflow w ON d.workflow_id = w.id WHERE w.deleted = 0) AS old_count,
    (SELECT COUNT(*) FROM task_dependency) AS new_count;
