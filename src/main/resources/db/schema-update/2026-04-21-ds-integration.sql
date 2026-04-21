-- MDataX DolphinScheduler 集成数据库变更脚本
-- 执行日期: 2026-04-21
-- 前置条件: mdatax 数据库已存在

-- ==================== 1. 同步任务表扩展 ====================

use mdatax;
ALTER TABLE sync_task
    ADD COLUMN ds_process_code BIGINT DEFAULT NULL COMMENT 'DolphinScheduler 工作流定义编码' AFTER status,
    ADD COLUMN ds_schedule_id  INT    DEFAULT NULL COMMENT 'DolphinScheduler 定时调度ID' AFTER ds_process_code,
    ADD COLUMN retry_times     INT    DEFAULT 0 COMMENT '失败重试次数' AFTER ds_schedule_id,
    ADD COLUMN retry_interval  INT    DEFAULT 5 COMMENT '重试间隔(分钟)' AFTER retry_times;

-- ==================== 2. SQL 任务表扩展 ====================

ALTER TABLE sql_task
    ADD COLUMN ds_process_code BIGINT DEFAULT NULL COMMENT 'DolphinScheduler 工作流定义编码' AFTER status,
    ADD COLUMN ds_schedule_id INT DEFAULT NULL COMMENT 'DolphinScheduler 定时调度ID' AFTER ds_process_code,
    ADD COLUMN retry_times INT DEFAULT 0 COMMENT '失败重试次数' AFTER ds_schedule_id,
    ADD COLUMN retry_interval INT DEFAULT 5 COMMENT '重试间隔(分钟)' AFTER retry_times;

-- ==================== 3. 同步任务日志表扩展 ====================

ALTER TABLE sync_task_log
    ADD COLUMN ds_instance_id BIGINT DEFAULT NULL COMMENT 'DolphinScheduler 流程实例ID' AFTER task_id,
    ADD COLUMN retry_count INT DEFAULT 0 COMMENT '当前重试次数' AFTER row_count,
    ADD COLUMN trigger_type VARCHAR(20) DEFAULT 'SCHEDULE' COMMENT '触发方式: SCHEDULE-定时 MANUAL-手动 DEPENDENCY-依赖触发' AFTER retry_count;

-- ==================== 4. SQL 任务日志表扩展 ====================

ALTER TABLE sql_task_log
    ADD COLUMN ds_instance_id BIGINT DEFAULT NULL COMMENT 'DolphinScheduler 流程实例ID' AFTER task_id,
    ADD COLUMN retry_count INT DEFAULT 0 COMMENT '当前重试次数' AFTER status,
    ADD COLUMN trigger_type VARCHAR(20) DEFAULT 'SCHEDULE' COMMENT '触发方式: SCHEDULE-定时 MANUAL-手动 DEPENDENCY-依赖触发' AFTER retry_count;

-- ==================== 5. 任务依赖关系表 ====================

CREATE TABLE IF NOT EXISTS task_dependency (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT 'ID',
    upstream_task_id BIGINT NOT NULL COMMENT '上游任务ID',
    upstream_task_type VARCHAR(20) NOT NULL COMMENT '上游任务类型: SYNC/SQL',
    downstream_task_id BIGINT NOT NULL COMMENT '下游任务ID',
    downstream_task_type VARCHAR(20) NOT NULL COMMENT '下游任务类型: SYNC/SQL',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    UNIQUE KEY uk_dep (upstream_task_id, upstream_task_type, downstream_task_id, downstream_task_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='普通任务依赖关系（弱依赖，异步触发）';

-- ==================== 6. 流水线定义表 ====================

CREATE TABLE IF NOT EXISTS pipeline (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '流水线ID',
    pipeline_name VARCHAR(100) NOT NULL COMMENT '流水线名称',
    description VARCHAR(500) DEFAULT NULL COMMENT '描述',
    cron_expression VARCHAR(100) DEFAULT NULL COMMENT '入口Cron表达式',
    status TINYINT DEFAULT 0 COMMENT '状态: 0-禁用 1-启用',
    ds_process_code BIGINT DEFAULT NULL COMMENT 'DolphinScheduler 工作流编码',
    ds_schedule_id INT DEFAULT NULL COMMENT 'DolphinScheduler 定时调度ID',
    retry_times INT DEFAULT 0 COMMENT '节点失败重试次数',
    alert_config JSON DEFAULT NULL COMMENT '报警配置',
    deleted TINYINT DEFAULT 0 COMMENT '删除标记',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='流水线定义';

-- ==================== 7. 流水线节点表 ====================

CREATE TABLE IF NOT EXISTS pipeline_node (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '节点ID',
    pipeline_id BIGINT NOT NULL COMMENT '流水线ID',
    node_name VARCHAR(100) NOT NULL COMMENT '节点名称',
    node_type VARCHAR(20) NOT NULL COMMENT '节点类型: SYNC/SQL',
    task_id BIGINT NOT NULL COMMENT '关联的任务ID',
    task_type VARCHAR(20) NOT NULL COMMENT '关联的任务类型: SYNC/SQL',
    sequence_no INT NOT NULL COMMENT '节点序号（用于排序展示）',
    x_coordinate INT DEFAULT 0 COMMENT '前端X坐标',
    y_coordinate INT DEFAULT 0 COMMENT '前端Y坐标',
    UNIQUE KEY uk_pipeline_seq (pipeline_id, sequence_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='流水线节点';

-- ==================== 8. 流水线节点关系表 ====================

CREATE TABLE IF NOT EXISTS pipeline_node_relation (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT 'ID',
    pipeline_id BIGINT NOT NULL COMMENT '流水线ID',
    upstream_node_id BIGINT NOT NULL COMMENT '上游节点ID',
    downstream_node_id BIGINT NOT NULL COMMENT '下游节点ID',
    UNIQUE KEY uk_relation (pipeline_id, upstream_node_id, downstream_node_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='流水线节点依赖关系';

-- ==================== 9. 流水线执行实例表 ====================

CREATE TABLE IF NOT EXISTS pipeline_instance (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '实例ID',
    pipeline_id BIGINT NOT NULL COMMENT '流水线ID',
    ds_instance_id BIGINT DEFAULT NULL COMMENT 'DolphinScheduler 流程实例ID',
    status VARCHAR(20) DEFAULT 'RUNNING' COMMENT '状态: RUNNING/SUCCESS/FAILED',
    start_time DATETIME DEFAULT NULL COMMENT '开始时间',
    end_time DATETIME DEFAULT NULL COMMENT '结束时间',
    error_msg TEXT DEFAULT NULL COMMENT '错误信息',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='流水线执行实例';

-- ==================== 10. 流水线节点执行实例表 ====================

CREATE TABLE IF NOT EXISTS pipeline_node_instance (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '实例ID',
    pipeline_instance_id BIGINT NOT NULL COMMENT '流水线实例ID',
    node_id BIGINT NOT NULL COMMENT '节点ID',
    task_log_id BIGINT DEFAULT NULL COMMENT '关联的任务日志ID',
    status VARCHAR(20) DEFAULT 'PENDING' COMMENT '状态: PENDING/RUNNING/SUCCESS/FAILED/SKIPPED',
    start_time DATETIME DEFAULT NULL COMMENT '开始时间',
    end_time DATETIME DEFAULT NULL COMMENT '结束时间',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='流水线节点执行实例';
