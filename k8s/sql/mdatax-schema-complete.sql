-- =============================================
-- MDataX 数据库完整初始化脚本
-- 版本: 1.0.0
-- 创建时间: 2026-04-24
-- 包含所有 22 个表
-- =============================================

-- 创建数据库（如果不存在）
CREATE DATABASE IF NOT EXISTS mdatax
DEFAULT CHARACTER SET utf8mb4
COLLATE utf8mb4_unicode_ci;

USE mdatax;

-- =============================================
-- 1. 用户表
-- =============================================
CREATE TABLE IF NOT EXISTS sys_user (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '用户ID',
    username VARCHAR(64) NOT NULL UNIQUE COMMENT '用户名',
    password VARCHAR(255) NOT NULL COMMENT '密码（BCrypt加密）',
    email VARCHAR(128) COMMENT '邮箱',
    phone VARCHAR(32) COMMENT '手机号',
    real_name VARCHAR(64) COMMENT '真实姓名',
    status TINYINT DEFAULT 1 COMMENT '状态：0-禁用 1-启用',
    deleted TINYINT DEFAULT 0 COMMENT '删除标记：0-未删除 1-已删除',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_username (username),
    INDEX idx_email (email),
    INDEX idx_deleted (deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户表';

-- 插入默认管理员账号（用户名: admin, 密码: admin123）
INSERT INTO sys_user (username, password, real_name, status) VALUES
('admin', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', '系统管理员', 1)
ON DUPLICATE KEY UPDATE username=username;

-- =============================================
-- 2. 角色表
-- =============================================
CREATE TABLE IF NOT EXISTS sys_role (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '角色ID',
    role_name VARCHAR(64) NOT NULL COMMENT '角色名称',
    role_code VARCHAR(64) NOT NULL UNIQUE COMMENT '角色编码',
    description VARCHAR(512) COMMENT '角色描述',
    role_type TINYINT DEFAULT 1 COMMENT '角色类型：1-系统角色 2-自定义角色',
    status TINYINT DEFAULT 1 COMMENT '状态：0-禁用 1-启用',
    deleted TINYINT DEFAULT 0 COMMENT '删除标记：0-未删除 1-已删除',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_role_code (role_code),
    INDEX idx_deleted (deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='角色表';

-- 插入默认角色
INSERT INTO sys_role (role_name, role_code, description, role_type) VALUES
('超级管理员', 'SUPER_ADMIN', '拥有所有权限', 1),
('开发者', 'DEVELOPER', '可以开发SQL任务和数据同步任务', 1),
('访客', 'VISITOR', '只读权限', 1)
ON DUPLICATE KEY UPDATE role_code=role_code;

-- =============================================
-- 3. 用户角色关系表
-- =============================================
CREATE TABLE IF NOT EXISTS sys_user_role (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT 'ID',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    role_id BIGINT NOT NULL COMMENT '角色ID',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    UNIQUE KEY uk_user_role (user_id, role_id),
    INDEX idx_user_id (user_id),
    INDEX idx_role_id (role_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户角色关系表';

-- 为管理员分配超级管理员角色
INSERT INTO sys_user_role (user_id, role_id)
SELECT u.id, r.id
FROM sys_user u, sys_role r
WHERE u.username = 'admin' AND r.role_code = 'SUPER_ADMIN'
ON DUPLICATE KEY UPDATE user_id=user_id;

-- =============================================
-- 4. 角色权限关系表
-- =============================================
CREATE TABLE IF NOT EXISTS sys_role_permission (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT 'ID',
    role_id BIGINT NOT NULL COMMENT '角色ID',
    permission VARCHAR(255) NOT NULL COMMENT '权限标识',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    UNIQUE KEY uk_role_permission (role_id, permission),
    INDEX idx_role_id (role_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='角色权限关系表';

-- =============================================
-- 5. 数据源表
-- =============================================
CREATE TABLE IF NOT EXISTS datasource (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '数据源ID',
    name VARCHAR(128) NOT NULL COMMENT '数据源名称',
    type VARCHAR(32) NOT NULL COMMENT '数据源类型：mysql、clickhouse等',
    host VARCHAR(255) NOT NULL COMMENT '主机地址',
    port INT NOT NULL COMMENT '端口',
    database_name VARCHAR(128) COMMENT '数据库名',
    username VARCHAR(128) COMMENT '用户名',
    password VARCHAR(512) COMMENT '密码（加密）',
    params TEXT COMMENT '额外参数（JSON格式）',
    description VARCHAR(512) COMMENT '描述',
    status TINYINT DEFAULT 1 COMMENT '状态：0-停用 1-启用',
    create_user_id BIGINT COMMENT '创建者ID',
    deleted TINYINT DEFAULT 0 COMMENT '删除标记：0-未删除 1-已删除',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_type (type),
    INDEX idx_deleted (deleted),
    INDEX idx_create_user_id (create_user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='数据源表';

-- =============================================
-- 6. 元数据表
-- =============================================
CREATE TABLE IF NOT EXISTS metadata_table (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '表ID',
    database_name VARCHAR(128) NOT NULL COMMENT '数据库名',
    table_name VARCHAR(255) NOT NULL COMMENT '表名',
    table_comment VARCHAR(512) COMMENT '表注释',
    engine VARCHAR(32) COMMENT '存储引擎',
    total_rows BIGINT DEFAULT 0 COMMENT '总行数',
    total_bytes BIGINT DEFAULT 0 COMMENT '总字节数',
    owner_id BIGINT COMMENT '所有者ID',
    last_data_update_time DATETIME COMMENT '数据最近更新时间',
    deleted TINYINT DEFAULT 0 COMMENT '删除标记：0-未删除 1-已删除',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_db_table (database_name, table_name),
    INDEX idx_table_name (table_name),
    INDEX idx_deleted (deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='元数据表';

-- =============================================
-- 7. 元数据列表
-- =============================================
CREATE TABLE IF NOT EXISTS metadata_column (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '列ID',
    table_id BIGINT NOT NULL COMMENT '所属表ID',
    column_name VARCHAR(128) NOT NULL COMMENT '列名',
    column_type VARCHAR(128) COMMENT '列类型',
    column_comment VARCHAR(512) COMMENT '列注释',
    is_primary_key TINYINT DEFAULT 0 COMMENT '是否主键：0-否 1-是',
    is_nullable TINYINT DEFAULT 1 COMMENT '是否可空：0-否 1-是',
    default_value VARCHAR(255) COMMENT '默认值',
    sort_order INT DEFAULT 0 COMMENT '排序号',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_table_column (table_id, column_name),
    INDEX idx_table_id (table_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='元数据列表';

-- =============================================
-- 8. 用户表访问记录表
-- =============================================
CREATE TABLE IF NOT EXISTS user_table_visit (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT 'ID',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    table_id BIGINT NOT NULL COMMENT '表ID',
    visit_count INT DEFAULT 1 COMMENT '访问次数',
    last_visit_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '最后访问时间',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_user_table (user_id, table_id),
    INDEX idx_user_id (user_id),
    INDEX idx_table_id (table_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户表访问记录表';

-- =============================================
-- 9. 表访问历史表
-- =============================================
CREATE TABLE IF NOT EXISTS table_access_history (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT 'ID',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    table_id BIGINT NOT NULL COMMENT '表ID',
    access_type VARCHAR(32) COMMENT '访问类型：select、insert、update、delete',
    sql_content TEXT COMMENT 'SQL内容',
    execute_time INT COMMENT '执行时长（毫秒）',
    affect_rows INT COMMENT '影响行数',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '访问时间',
    INDEX idx_user_id (user_id),
    INDEX idx_table_id (table_id),
    INDEX idx_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='表访问历史表';

-- =============================================
-- 10. SQL 开发任务表
-- =============================================
CREATE TABLE IF NOT EXISTS sql_task (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '任务ID',
    name VARCHAR(128) NOT NULL COMMENT '任务名称',
    description VARCHAR(512) COMMENT '任务描述',
    sql_content TEXT NOT NULL COMMENT 'SQL内容',
    datasource_id BIGINT COMMENT '数据源ID',
    ds_task_code BIGINT COMMENT 'DolphinScheduler 任务编码',
    ds_process_code BIGINT COMMENT 'DolphinScheduler 流程定义编码',
    ds_task_definition_id BIGINT COMMENT 'DolphinScheduler 任务定义ID',
    cron_expression VARCHAR(128) COMMENT 'Cron表达式',
    schedule_status TINYINT DEFAULT 0 COMMENT '调度状态：0-未启用 1-已启用 2-已暂停',
    last_execute_time DATETIME COMMENT '最后执行时间',
    last_execute_status VARCHAR(32) COMMENT '最后执行状态',
    create_user_id BIGINT COMMENT '创建者ID',
    status TINYINT DEFAULT 1 COMMENT '状态：0-停用 1-启用',
    deleted TINYINT DEFAULT 0 COMMENT '删除标记：0-未删除 1-已删除',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_ds_task_code (ds_task_code),
    INDEX idx_schedule_status (schedule_status),
    INDEX idx_create_user_id (create_user_id),
    INDEX idx_deleted (deleted),
    INDEX idx_datasource_id (datasource_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='SQL开发任务表';

-- =============================================
-- 11. SQL 任务依赖关系表
-- =============================================
CREATE TABLE IF NOT EXISTS sql_task_dependency (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT 'ID',
    task_id BIGINT NOT NULL COMMENT '任务ID',
    dependency_task_id BIGINT NOT NULL COMMENT '依赖的任务ID',
    dependency_type VARCHAR(32) DEFAULT 'SUCCESS' COMMENT '依赖类型：SUCCESS-成功后执行、ALL-全部完成后执行',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    UNIQUE KEY uk_task_dependency (task_id, dependency_task_id),
    INDEX idx_task_id (task_id),
    INDEX idx_dependency_task_id (dependency_task_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='SQL任务依赖关系表';

-- =============================================
-- 12. SQL 任务工作流表
-- =============================================
CREATE TABLE IF NOT EXISTS sql_task_workflow (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '工作流ID',
    name VARCHAR(128) NOT NULL COMMENT '工作流名称',
    description VARCHAR(512) COMMENT '工作流描述',
    ds_process_code BIGINT COMMENT 'DolphinScheduler 流程定义编码',
    create_user_id BIGINT COMMENT '创建者ID',
    status TINYINT DEFAULT 1 COMMENT '状态：0-停用 1-启用',
    deleted TINYINT DEFAULT 0 COMMENT '删除标记：0-未删除 1-已删除',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_ds_process_code (ds_process_code),
    INDEX idx_create_user_id (create_user_id),
    INDEX idx_deleted (deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='SQL任务工作流表';

-- =============================================
-- 13. 工作流实例表
-- =============================================
CREATE TABLE IF NOT EXISTS sql_task_workflow_instance (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '实例ID',
    workflow_id BIGINT NOT NULL COMMENT '工作流ID',
    ds_process_instance_id BIGINT COMMENT 'DolphinScheduler 流程实例ID',
    status VARCHAR(32) COMMENT '状态：RUNNING-运行中、SUCCESS-成功、FAILURE-失败',
    start_time DATETIME COMMENT '开始时间',
    end_time DATETIME COMMENT '结束时间',
    duration INT COMMENT '执行时长（秒）',
    error_msg TEXT COMMENT '错误信息',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_workflow_id (workflow_id),
    INDEX idx_ds_process_instance_id (ds_process_instance_id),
    INDEX idx_status (status),
    INDEX idx_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='工作流实例表';

-- =============================================
-- 14. SQL 任务执行日志表
-- =============================================
CREATE TABLE IF NOT EXISTS sql_task_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '日志ID',
    task_id BIGINT NOT NULL COMMENT '任务ID',
    workflow_instance_id BIGINT COMMENT '工作流实例ID',
    ds_task_instance_id BIGINT COMMENT 'DolphinScheduler 任务实例ID',
    execute_type VARCHAR(32) COMMENT '执行类型：MANUAL-手动、SCHEDULE-调度',
    status VARCHAR(32) COMMENT '状态：RUNNING-运行中、SUCCESS-成功、FAILURE-失败',
    sql_content TEXT COMMENT '执行的SQL',
    start_time DATETIME COMMENT '开始时间',
    end_time DATETIME COMMENT '结束时间',
    duration INT COMMENT '执行时长（毫秒）',
    affect_rows INT COMMENT '影响行数',
    error_msg TEXT COMMENT '错误信息',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_task_id (task_id),
    INDEX idx_workflow_instance_id (workflow_instance_id),
    INDEX idx_status (status),
    INDEX idx_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='SQL任务执行日志表';

-- =============================================
-- 15. 数据同步任务表
-- =============================================
CREATE TABLE IF NOT EXISTS sync_task (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '任务ID',
    name VARCHAR(128) NOT NULL COMMENT '任务名称',
    description VARCHAR(512) COMMENT '任务描述',
    source_datasource_id BIGINT NOT NULL COMMENT '源数据源ID',
    source_table VARCHAR(255) NOT NULL COMMENT '源表名',
    target_datasource_id BIGINT NOT NULL COMMENT '目标数据源ID',
    target_table VARCHAR(255) NOT NULL COMMENT '目标表名',
    sync_mode VARCHAR(32) DEFAULT 'FULL' COMMENT '同步模式：FULL-全量 INCREMENT-增量',
    sync_type VARCHAR(32) DEFAULT 'TABLE' COMMENT '同步类型：TABLE-表 DATABASE-数据库',
    cron_expression VARCHAR(128) COMMENT 'Cron表达式',
    schedule_status TINYINT DEFAULT 0 COMMENT '调度状态：0-未启用 1-已启用 2-已暂停',
    ds_task_code BIGINT COMMENT 'DolphinScheduler 任务编码',
    ds_process_code BIGINT COMMENT 'DolphinScheduler 流程定义编码',
    column_mapping TEXT COMMENT '字段映射（JSON格式）',
    filter_condition VARCHAR(512) COMMENT '过滤条件',
    last_execute_time DATETIME COMMENT '最后执行时间',
    last_execute_status VARCHAR(32) COMMENT '最后执行状态',
    create_user_id BIGINT COMMENT '创建者ID',
    status TINYINT DEFAULT 1 COMMENT '状态：0-停用 1-启用',
    deleted TINYINT DEFAULT 0 COMMENT '删除标记：0-未删除 1-已删除',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_source_datasource_id (source_datasource_id),
    INDEX idx_target_datasource_id (target_datasource_id),
    INDEX idx_ds_task_code (ds_task_code),
    INDEX idx_schedule_status (schedule_status),
    INDEX idx_create_user_id (create_user_id),
    INDEX idx_deleted (deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='数据同步任务表';

-- =============================================
-- 16. 同步任务执行日志表
-- =============================================
CREATE TABLE IF NOT EXISTS sync_task_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '日志ID',
    task_id BIGINT NOT NULL COMMENT '任务ID',
    ds_task_instance_id BIGINT COMMENT 'DolphinScheduler 任务实例ID',
    execute_type VARCHAR(32) COMMENT '执行类型：MANUAL-手动、SCHEDULE-调度',
    status VARCHAR(32) COMMENT '状态：RUNNING-运行中、SUCCESS-成功、FAILURE-失败',
    start_time DATETIME COMMENT '开始时间',
    end_time DATETIME COMMENT '结束时间',
    duration INT COMMENT '执行时长（毫秒）',
    sync_rows BIGINT COMMENT '同步行数',
    error_msg TEXT COMMENT '错误信息',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_task_id (task_id),
    INDEX idx_status (status),
    INDEX idx_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='同步任务执行日志表';

-- =============================================
-- 17. 任务协作者表
-- =============================================
CREATE TABLE IF NOT EXISTS task_collaborator (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT 'ID',
    task_type VARCHAR(32) NOT NULL COMMENT '任务类型：sql_task、sync_task',
    task_id BIGINT NOT NULL COMMENT '任务ID',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    permission VARCHAR(32) NOT NULL COMMENT '权限：read、write、execute',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    UNIQUE KEY uk_task_user (task_type, task_id, user_id),
    INDEX idx_user_id (user_id),
    INDEX idx_task_type (task_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='任务协作者表';

-- =============================================
-- 18. 权限申请表
-- =============================================
CREATE TABLE IF NOT EXISTS permission_apply (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '申请ID',
    table_name VARCHAR(255) NOT NULL COMMENT '表名',
    apply_type VARCHAR(32) NOT NULL COMMENT '申请类型：read、write',
    reason VARCHAR(512) COMMENT '申请原因',
    status VARCHAR(32) DEFAULT 'PENDING' COMMENT '状态：PENDING-待审批 APPROVED-已通过 REJECTED-已拒绝',
    approver_id BIGINT COMMENT '审批人ID',
    approval_time DATETIME COMMENT '审批时间',
    approval_comment VARCHAR(512) COMMENT '审批意见',
    applicant_id BIGINT NOT NULL COMMENT '申请人ID',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '申请时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_table_name (table_name),
    INDEX idx_applicant_id (applicant_id),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='权限申请表';

-- =============================================
-- 19. 报表定义表
-- =============================================
CREATE TABLE IF NOT EXISTS report (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '报表ID',
    name VARCHAR(128) NOT NULL COMMENT '报表名称',
    description VARCHAR(512) COMMENT '报表描述',
    status TINYINT DEFAULT 1 COMMENT '状态：0-停用 1-启用',
    create_user_id BIGINT COMMENT '创建者ID',
    deleted TINYINT DEFAULT 0 COMMENT '删除标记：0-未删除 1-已删除',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_create_user_id (create_user_id),
    INDEX idx_deleted (deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='报表定义表';

-- =============================================
-- 20. 报表图表配置表
-- =============================================
CREATE TABLE IF NOT EXISTS report_chart (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '图表ID',
    report_id BIGINT NOT NULL COMMENT '所属报表ID',
    sql_content TEXT NOT NULL COMMENT 'SQL内容',
    chart_type VARCHAR(32) NOT NULL COMMENT '图表类型：line-折线图 bar-柱状图 pie-饼图',
    title VARCHAR(128) COMMENT '图表标题',
    x_axis_field VARCHAR(128) COMMENT 'X轴字段名',
    y_axis_field VARCHAR(128) COMMENT 'Y轴字段名',
    sort_order INT DEFAULT 0 COMMENT '排序号（数字越小越靠前）',
    layout_span INT DEFAULT 12 COMMENT '布局跨列数（1-12，12为全宽）',
    deleted TINYINT DEFAULT 0 COMMENT '删除标记：0-未删除 1-已删除',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_report_id (report_id),
    INDEX idx_sort (report_id, sort_order),
    INDEX idx_deleted (deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='报表图表配置表';

-- =============================================
-- 21. 操作日志表
-- =============================================
CREATE TABLE IF NOT EXISTS sys_operation_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '日志ID',
    user_id BIGINT COMMENT '操作用户ID',
    username VARCHAR(64) COMMENT '用户名',
    module VARCHAR(64) COMMENT '模块名称',
    operation VARCHAR(64) COMMENT '操作类型',
    method VARCHAR(128) COMMENT '方法名',
    params TEXT COMMENT '请求参数',
    ip VARCHAR(64) COMMENT 'IP地址',
    status TINYINT DEFAULT 1 COMMENT '状态：0-失败 1-成功',
    error_msg VARCHAR(512) COMMENT '错误信息',
    execute_time INT COMMENT '执行时长（毫秒）',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_user_id (user_id),
    INDEX idx_module (module),
    INDEX idx_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='操作日志表';

-- =============================================
-- 22. 系统配置表
-- =============================================
CREATE TABLE IF NOT EXISTS system_config (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '配置ID',
    config_key VARCHAR(128) NOT NULL UNIQUE COMMENT '配置键',
    config_value TEXT COMMENT '配置值',
    description VARCHAR(512) COMMENT '配置描述',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_config_key (config_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统配置表';

-- 插入默认配置
INSERT INTO system_config (config_key, config_value, description) VALUES
('jwt.expiration', '604800', 'JWT 过期时间（秒）7天'),
('ds.callback.secret', 'mdatax-ds-callback-secret-change-in-production', 'DS 回调密钥'),
('system.name', 'MDataX', '系统名称'),
('system.version', '1.0.0', '系统版本')
ON DUPLICATE KEY UPDATE config_key=config_key;

COMMIT;

-- =============================================
-- 表统计
-- =============================================
-- 执行以下语句查看所有表：
-- SHOW TABLES;
-- 预期输出：22 个表
