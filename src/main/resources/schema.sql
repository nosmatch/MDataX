-- MDataX 元数据库初始化脚本
-- 前置条件：mdatax 数据库已创建
-- CREATE DATABASE mdatax DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
--
-- 使用说明：
--   1. 首次部署：手动执行此脚本创建所有表
--   2. 表结构变更：在 schema-update/ 目录下创建增量脚本，按需手动执行
--   3. 禁止 DROP TABLE：生产环境数据不可丢失
--
-- 当前脚本已移除 DROP TABLE，仅保留 CREATE TABLE IF NOT EXISTS

-- 用户表
CREATE TABLE IF NOT EXISTS sys_user (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '用户ID',
    username VARCHAR(64) NOT NULL UNIQUE COMMENT '用户名',
    password VARCHAR(128) NOT NULL COMMENT '密码',
    nickname VARCHAR(64) DEFAULT NULL COMMENT '昵称',
    email VARCHAR(128) DEFAULT NULL COMMENT '邮箱',
    status TINYINT DEFAULT 1 COMMENT '状态：0-禁用 1-启用',
    deleted TINYINT DEFAULT 0 COMMENT '删除标记：0-未删除 1-已删除',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统用户表';

-- 角色表
CREATE TABLE IF NOT EXISTS sys_role (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '角色ID',
    role_name VARCHAR(64) NOT NULL UNIQUE COMMENT '角色名称',
    role_code VARCHAR(64) NOT NULL UNIQUE COMMENT '角色编码',
    description VARCHAR(255) DEFAULT NULL COMMENT '描述',
    role_type TINYINT DEFAULT 1 COMMENT '角色类型：1-普通角色 2-个人角色',
    status TINYINT DEFAULT 1 COMMENT '状态：0-禁用 1-启用',
    deleted TINYINT DEFAULT 0 COMMENT '删除标记',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统角色表';

-- 用户角色关联表
CREATE TABLE IF NOT EXISTS sys_user_role (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT 'ID',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    role_id BIGINT NOT NULL COMMENT '角色ID',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    UNIQUE KEY uk_user_role (user_id, role_id),
    INDEX idx_role_id (role_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户角色关联表';

-- 角色权限表（表级权限）
CREATE TABLE IF NOT EXISTS sys_role_permission (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT 'ID',
    role_id BIGINT NOT NULL COMMENT '角色ID',
    table_name VARCHAR(128) NOT NULL COMMENT '表名（数据库.表名格式）',
    permission_type VARCHAR(16) NOT NULL COMMENT '权限类型：READ-读 WRITE-写',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_role_table (role_id, table_name, permission_type),
    INDEX idx_role_id (role_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色权限表';

-- 插入默认管理员
INSERT INTO sys_user (username, password, nickname, email, status) VALUES
('admin', '$2a$10$XJ7lH.xaSI/a6Jt6kmATw.WsdU/OyunA6JTdbbiDbTKPkUxyaR6M2', '管理员', 'admin@mdatax.com', 1)
ON DUPLICATE KEY UPDATE id = id;

-- 插入默认角色
INSERT INTO sys_role (role_name, role_code, description, status) VALUES
('管理员', 'admin', '系统管理员，拥有所有权限', 1),
('数据分析师', 'analyst', '数据分析人员，可查询数据', 1)
ON DUPLICATE KEY UPDATE id = id;

-- 操作日志表
CREATE TABLE IF NOT EXISTS sys_operation_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '日志ID',
    user_id BIGINT DEFAULT NULL COMMENT '用户ID',
    username VARCHAR(64) DEFAULT NULL COMMENT '用户名',
    operation VARCHAR(255) NOT NULL COMMENT '操作描述',
    method VARCHAR(255) DEFAULT NULL COMMENT '请求方法',
    params TEXT DEFAULT NULL COMMENT '请求参数',
    result TEXT DEFAULT NULL COMMENT '操作结果',
    ip VARCHAR(64) DEFAULT NULL COMMENT 'IP地址',
    duration BIGINT DEFAULT NULL COMMENT '执行时长(ms)',
    status TINYINT DEFAULT 1 COMMENT '状态：0-失败 1-成功',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_user_id (user_id),
    INDEX idx_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='操作日志表';

-- 数据源配置表
CREATE TABLE IF NOT EXISTS datasource (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '数据源ID',
    name VARCHAR(64) NOT NULL COMMENT '数据源名称',
    type VARCHAR(32) NOT NULL COMMENT '类型：MYSQL、CLICKHOUSE、ELASTICSEARCH、KAFKA、LOCAL_EXCEL',
    host VARCHAR(128) DEFAULT NULL COMMENT '主机地址（或文件路径）',
    port INT DEFAULT NULL COMMENT '端口',
    database_name VARCHAR(64) DEFAULT NULL COMMENT '数据库名',
    username VARCHAR(64) DEFAULT NULL COMMENT '用户名',
    password VARCHAR(128) DEFAULT NULL COMMENT '密码',
    extra_config TEXT DEFAULT NULL COMMENT '类型特定配置（JSON格式）',
    status TINYINT DEFAULT 1 COMMENT '状态：0-禁用 1-启用',
    deleted TINYINT DEFAULT 0 COMMENT '删除标记',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='数据源配置表';

-- 同步任务表
CREATE TABLE IF NOT EXISTS sync_task (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '任务ID',
    task_name VARCHAR(128) NOT NULL COMMENT '任务名称',
    datasource_id BIGINT NOT NULL COMMENT '数据源ID',
    source_table VARCHAR(128) NOT NULL COMMENT '来源表',
    target_table VARCHAR(128) NOT NULL COMMENT '目标表（ClickHouse）',
    sync_type VARCHAR(32) NOT NULL COMMENT '同步类型：FULL-全量 INCREMENTAL-增量',
    time_field VARCHAR(64) DEFAULT NULL COMMENT '增量时间字段',
    cron_expression VARCHAR(64) DEFAULT NULL COMMENT 'Cron表达式',
    status TINYINT DEFAULT 0 COMMENT '状态：0-停用 1-启用',
    last_sync_time DATETIME DEFAULT NULL COMMENT '最后同步时间',
    deleted TINYINT DEFAULT 0 COMMENT '删除标记',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_datasource_id (datasource_id),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='同步任务表';

-- 同步任务执行记录表
CREATE TABLE IF NOT EXISTS sync_task_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '日志ID',
    task_id BIGINT NOT NULL COMMENT '任务ID',
    start_time DATETIME DEFAULT NULL COMMENT '开始时间',
    end_time DATETIME DEFAULT NULL COMMENT '结束时间',
    status VARCHAR(32) DEFAULT NULL COMMENT '状态：RUNNING-运行中 SUCCESS-成功 FAILED-失败',
    message TEXT DEFAULT NULL COMMENT '执行消息',
    row_count BIGINT DEFAULT NULL COMMENT '同步行数',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_task_id (task_id),
    INDEX idx_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='同步任务执行记录表';

-- SQL开发任务表
CREATE TABLE IF NOT EXISTS sql_task (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '任务ID',
    task_name VARCHAR(128) NOT NULL COMMENT '任务名称',
    sql_content TEXT NOT NULL COMMENT 'SQL内容',
    description VARCHAR(512) DEFAULT NULL COMMENT '任务描述',
    cron_expression VARCHAR(64) DEFAULT NULL COMMENT 'Cron表达式',
    status TINYINT DEFAULT 0 COMMENT '状态：0-停用 1-启用',
    create_user_id BIGINT DEFAULT NULL COMMENT '创建者ID',
    deleted TINYINT DEFAULT 0 COMMENT '删除标记',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='SQL开发任务表';

-- SQL任务执行记录表
CREATE TABLE IF NOT EXISTS sql_task_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '日志ID',
    task_id BIGINT NOT NULL COMMENT '任务ID',
    start_time DATETIME DEFAULT NULL COMMENT '开始时间',
    end_time DATETIME DEFAULT NULL COMMENT '结束时间',
    status VARCHAR(32) DEFAULT NULL COMMENT '状态',
    message TEXT DEFAULT NULL COMMENT '执行消息',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_task_id (task_id),
    INDEX idx_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='SQL任务执行记录表';

-- 元数据-表
CREATE TABLE IF NOT EXISTS metadata_table (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT 'ID',
    database_name VARCHAR(64) NOT NULL COMMENT '数据库名',
    table_name VARCHAR(128) NOT NULL COMMENT '表名',
    table_comment VARCHAR(255) DEFAULT NULL COMMENT '表注释',
    engine VARCHAR(64) DEFAULT NULL COMMENT '引擎',
    total_rows BIGINT DEFAULT NULL COMMENT '数据行数',
    total_bytes BIGINT DEFAULT NULL COMMENT '数据大小(字节)',
    owner_id BIGINT DEFAULT 1 COMMENT '责任人ID',
    last_data_update_time DATETIME DEFAULT NULL COMMENT '数据最近更新时间',
    deleted TINYINT DEFAULT 0 COMMENT '删除标记',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_db_table (database_name, table_name),
    INDEX idx_database_name (database_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='元数据-表';

-- 元数据-字段
CREATE TABLE IF NOT EXISTS metadata_column (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT 'ID',
    table_id BIGINT NOT NULL COMMENT '表ID',
    column_name VARCHAR(128) NOT NULL COMMENT '字段名',
    data_type VARCHAR(64) NOT NULL COMMENT '数据类型',
    column_comment VARCHAR(255) DEFAULT NULL COMMENT '字段注释',
    is_nullable VARCHAR(8) DEFAULT NULL COMMENT '是否可空',
    column_default VARCHAR(255) DEFAULT NULL COMMENT '默认值',
    ordinal_position INT DEFAULT NULL COMMENT '字段顺序',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_table_column (table_id, column_name),
    INDEX idx_table_id (table_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='元数据-字段';

-- 用户表访问记录
CREATE TABLE IF NOT EXISTS user_table_visit (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT 'ID',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    database_name VARCHAR(64) NOT NULL COMMENT '数据库名',
    table_name VARCHAR(128) NOT NULL COMMENT '表名',
    visit_count INT DEFAULT 1 COMMENT '访问次数',
    last_visit_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '最后访问时间',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_user_table (user_id, database_name, table_name),
    INDEX idx_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表访问记录';

-- 表访问历史记录（人工操作：SQL查询、表预览等）
CREATE TABLE IF NOT EXISTS table_access_history (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT 'ID',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    username VARCHAR(64) DEFAULT NULL COMMENT '用户名',
    table_id BIGINT DEFAULT NULL COMMENT '表ID（关联 metadata_table）',
    database_name VARCHAR(64) NOT NULL COMMENT '数据库名',
    table_name VARCHAR(128) NOT NULL COMMENT '表名',
    access_type VARCHAR(16) NOT NULL COMMENT '访问类型：READ-读 WRITE-写',
    access_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '访问时间',
    ip VARCHAR(64) DEFAULT NULL COMMENT 'IP地址',
    INDEX idx_table_id_access_time (table_id, access_time),
    INDEX idx_user_id (user_id),
    INDEX idx_access_time (access_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='表访问历史记录';

-- 权限申请表
CREATE TABLE IF NOT EXISTS permission_apply (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '申请ID',
    applicant_id BIGINT NOT NULL COMMENT '申请人用户ID',
    applicant_name VARCHAR(64) DEFAULT NULL COMMENT '申请人用户名',
    table_id BIGINT DEFAULT NULL COMMENT '目标表ID',
    database_name VARCHAR(64) NOT NULL COMMENT '目标库名',
    table_name VARCHAR(128) NOT NULL COMMENT '目标表名',
    table_comment VARCHAR(255) DEFAULT NULL COMMENT '表注释',
    apply_type VARCHAR(16) NOT NULL COMMENT '申请权限类型：READ-读 WRITE-写',
    apply_reason VARCHAR(500) DEFAULT NULL COMMENT '申请理由',
    status TINYINT NOT NULL DEFAULT 0 COMMENT '状态：0-待审批 1-已通过 2-已拒绝',
    owner_id BIGINT NOT NULL COMMENT '责任人ID（审批人）',
    owner_name VARCHAR(64) DEFAULT NULL COMMENT '责任人用户名',
    approve_time DATETIME DEFAULT NULL COMMENT '审批时间',
    approve_comment VARCHAR(500) DEFAULT NULL COMMENT '审批意见',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '申请时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_applicant_status (applicant_id, status),
    INDEX idx_owner_status (owner_id, status),
    INDEX idx_table_status (database_name, table_name, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='表权限申请表';

-- 任务协作者表
CREATE TABLE IF NOT EXISTS task_collaborator (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT 'ID',
    task_id BIGINT NOT NULL COMMENT '任务ID',
    task_type VARCHAR(16) NOT NULL COMMENT '任务类型：SYNC-同步任务 SQL-SQL任务',
    user_id BIGINT NOT NULL COMMENT '协作者用户ID',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    UNIQUE KEY uk_task_user (task_id, task_type, user_id),
    INDEX idx_task (task_id, task_type),
    INDEX idx_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='任务协作者表';
