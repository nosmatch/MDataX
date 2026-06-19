-- ========================================
-- MDataX 任务管理模块表结构
-- 创建时间: 2026-06-12
-- 说明: 统一任务抽象层、执行记录、依赖关系管理
-- ========================================

-- ------------------------------
-- 1. 统一任务表
-- ------------------------------
CREATE TABLE task (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '任务ID',
    task_code VARCHAR(64) UNIQUE NOT NULL COMMENT '任务编码（TASK-8位随机）',
    task_name VARCHAR(128) NOT NULL COMMENT '任务名称',
    task_type VARCHAR(16) NOT NULL COMMENT '任务类型：SQL/SYNC/QUALITY',
    description VARCHAR(512) COMMENT '任务描述',

    -- 责任人信息
    owner_user_id BIGINT NOT NULL COMMENT '责任人ID',
    owner_user_name VARCHAR(64) COMMENT '责任人姓名（冗余）',
    create_user_id BIGINT NOT NULL COMMENT '创建人ID',

    -- 优先级与分类
    priority TINYINT DEFAULT 5 COMMENT '优先级 1-10，5为默认',
    tags VARCHAR(256) COMMENT '标签，逗号分隔：核心,日报,临时',

    -- 调度信息
    cron_expression VARCHAR(64) COMMENT 'Cron表达式',
    status TINYINT DEFAULT 0 COMMENT '状态 0:停用 1:启用 2:草稿',

    -- 工作流关联（可选）
    workflow_id BIGINT COMMENT '所属工作流ID（如果属于某个工作流）',

    -- 调度器关联
    ds_process_code BIGINT COMMENT 'DolphinScheduler 工作流编码',
    ds_schedule_id INT COMMENT 'DolphinScheduler 调度ID',
    ds_task_code BIGINT COMMENT 'DolphinScheduler 任务编码',
    schedulerx_dag_id VARCHAR(64) COMMENT 'SchedulerX DAG ID',

    -- 重试配置
    retry_times INT DEFAULT 0 COMMENT '重试次数',
    retry_interval INT DEFAULT 0 COMMENT '重试间隔（秒）',
    timeout_seconds INT DEFAULT 0 COMMENT '超时时间（秒），0表示不限制',

    -- 审计字段
    deleted TINYINT DEFAULT 0 COMMENT '逻辑删除',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    INDEX idx_owner (owner_user_id),
    INDEX idx_type_status (task_type, status),
    INDEX idx_workflow (workflow_id),
    INDEX idx_cron (status, cron_expression),
    INDEX idx_task_code (task_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='统一任务表';


-- ------------------------------
-- 2. SQL任务详情表
-- ------------------------------
CREATE TABLE task_sql_detail (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    task_id BIGINT UNIQUE NOT NULL COMMENT '关联 task.id',
    sql_content MEDIUMTEXT NOT NULL COMMENT 'SQL内容',
    target_datasource_id BIGINT COMMENT '目标数据源ID（如果有）',

    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    FOREIGN KEY (task_id) REFERENCES task(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='SQL任务详情';


-- ------------------------------
-- 3. 同步任务详情表
-- ------------------------------
CREATE TABLE task_sync_detail (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    task_id BIGINT UNIQUE NOT NULL COMMENT '关联 task.id',
    source_datasource_id BIGINT NOT NULL COMMENT '源数据源ID',
    source_table VARCHAR(128) NOT NULL COMMENT '源表名',
    target_datasource_id BIGINT NOT NULL COMMENT '目标数据源ID',
    target_table VARCHAR(128) NOT NULL COMMENT '目标表名',
    sync_type VARCHAR(16) NOT NULL COMMENT '同步类型：FULL/INCR',
    time_field VARCHAR(64) COMMENT '增量时间字段',
    where_condition VARCHAR(512) COMMENT '同步条件过滤',

    last_sync_time DATETIME COMMENT '上次同步时间（冗余，便于查询）',

    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    FOREIGN KEY (task_id) REFERENCES task(id) ON DELETE CASCADE,
    INDEX idx_source (source_datasource_id, source_table),
    INDEX idx_target (target_datasource_id, target_table)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='同步任务详情';


-- ------------------------------
-- 4. 质量监控任务详情表
-- ------------------------------
CREATE TABLE task_quality_detail (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    task_id BIGINT UNIQUE NOT NULL COMMENT '关联 task.id',
    rule_template VARCHAR(32) NOT NULL COMMENT '规则模板：NULL_CHECK/ROW_COUNT_CHECK/ROW_COUNT_FLUCTUATION/UNIQUE_CHECK/ENUM_CHECK/REGEX_CHECK/NUMERIC_RANGE_CHECK/DATE_RANGE_CHECK/BUSINESS_RULE',
    database_name VARCHAR(128) NOT NULL COMMENT '数据库名',
    table_name VARCHAR(128) NOT NULL COMMENT '表名',
    table_id BIGINT NOT NULL COMMENT '关联的表ID（metadata_table表）',
    column_name VARCHAR(128) COMMENT '字段名（字段级规则）',
    check_params TEXT COMMENT '检查参数JSON',

    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    FOREIGN KEY (task_id) REFERENCES task(id) ON DELETE CASCADE,
    INDEX idx_table (table_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='质量监控任务详情';


-- ------------------------------
-- 5. 任务执行记录表（Job）
-- ------------------------------
CREATE TABLE task_execution (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    execution_id VARCHAR(64) UNIQUE NOT NULL COMMENT '执行ID（8位数字）',
    task_id BIGINT NOT NULL COMMENT '关联 task.id',

    -- 触发信息
    trigger_type VARCHAR(16) NOT NULL COMMENT '触发方式：SCHEDULE/MANUAL/DEPENDENCY/API',
    trigger_user_id BIGINT COMMENT '触发用户ID（手动触发时）',
    trigger_user_name VARCHAR(64) COMMENT '触发用户姓名（冗余）',
    parent_execution_id VARCHAR(64) COMMENT '父执行ID（如果是依赖触发）',

    -- 调度器实例信息
    scheduler_instance_id VARCHAR(128) COMMENT '调度器实例ID（DS/SchedulerX）',
    workflow_instance_id BIGINT COMMENT '工作流实例ID（如果是工作流触发）',

    -- 执行信息
    start_time DATETIME NOT NULL COMMENT '开始时间',
    end_time DATETIME COMMENT '结束时间',
    status VARCHAR(16) NOT NULL COMMENT '状态：PENDING/RUNNING/SUCCESS/FAILED/TIMEOUT/KILLED',
    duration_ms BIGINT COMMENT '执行耗时（毫秒）',

    -- 结果信息
    affected_rows INT COMMENT '影响行数（SQL任务）',
    sync_count INT COMMENT '同步记录数（同步任务）',
    log_url VARCHAR(512) COMMENT '日志文件URL',
    error_msg TEXT COMMENT '错误信息',

    -- 重试信息
    attempt_number TINYINT DEFAULT 1 COMMENT '尝试次数',
    max_attempts TINYINT DEFAULT 1 COMMENT '最大尝试次数',

    deleted TINYINT DEFAULT 0 COMMENT '逻辑删除：0未删除，1已删除',

    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,

    INDEX idx_task_time (task_id, start_time),
    INDEX idx_status_time (status, start_time),
    INDEX idx_trigger (trigger_type, start_time),
    INDEX idx_user (trigger_user_id, start_time),
    INDEX idx_scheduler (scheduler_instance_id),
    INDEX idx_execution_id (execution_id),

    FOREIGN KEY (task_id) REFERENCES task(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='任务执行记录（Job）';


-- ------------------------------
-- 5. 任务依赖关系表
-- ------------------------------
CREATE TABLE IF NOT EXISTS task_dependency (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    upstream_task_id BIGINT NOT NULL COMMENT '上游任务ID',
    downstream_task_id BIGINT NOT NULL COMMENT '下游任务ID',

    dependency_type VARCHAR(16) DEFAULT 'SUCCESS' COMMENT '依赖类型：SUCCESS/FAILED/ANY',
    condition_expression VARCHAR(256) COMMENT '条件表达式（可选）：如 UPSTREAM_ROW_COUNT > 0',

    -- 延迟执行
    delay_seconds INT DEFAULT 0 COMMENT '上游完成后延迟多少秒再触发',

    -- 审计
    create_user_id BIGINT NOT NULL COMMENT '创建人ID',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,

    UNIQUE KEY uk_dependency (upstream_task_id, downstream_task_id),
    INDEX idx_downstream (downstream_task_id),
    INDEX idx_upstream (upstream_task_id),

    FOREIGN KEY (upstream_task_id) REFERENCES task(id) ON DELETE CASCADE,
    FOREIGN KEY (downstream_task_id) REFERENCES task(id) ON DELETE CASCADE,
    CONSTRAINT chk_no_self_dependency CHECK (upstream_task_id <> downstream_task_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='任务依赖关系（支持跨工作流）';


-- ------------------------------
-- 6. 任务标签关联表（可选，支持多选标签）
-- ------------------------------
CREATE TABLE task_tag_relation (
    task_id BIGINT NOT NULL,
    tag_name VARCHAR(32) NOT NULL,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (task_id, tag_name),
    INDEX idx_tag (tag_name, task_id),

    FOREIGN KEY (task_id) REFERENCES task(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='任务标签关联表';
