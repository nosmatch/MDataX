-- =============================================
-- 质量监控模块表结构初始化脚本
-- 创建时间: 2026-05-29
-- =============================================

USE mdatax;

-- =============================================
-- 1. 质量规则表
-- =============================================
CREATE TABLE IF NOT EXISTS quality_rule (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '规则ID',
    rule_name VARCHAR(128) NOT NULL COMMENT '规则名称',
    rule_type VARCHAR(16) NOT NULL COMMENT '规则类型：TABLE-表级/COLUMN-字段级',
    rule_template VARCHAR(64) NOT NULL COMMENT '规则模板',
    table_id BIGINT DEFAULT NULL COMMENT '关联的表ID',
    table_name VARCHAR(128) DEFAULT NULL COMMENT '表名（冗余字段）',
    `database` VARCHAR(64) DEFAULT NULL COMMENT '数据库名（冗余字段）',
    column_name VARCHAR(128) DEFAULT NULL COMMENT '字段名（字段级规则必填）',
    check_sql TEXT DEFAULT NULL COMMENT '检查SQL（自定义规则使用）',
    check_params TEXT DEFAULT NULL COMMENT '检查参数JSON',
    priority INT DEFAULT 2 COMMENT '优先级：1-高 2-中 3-低',
    weight DECIMAL(3,2) DEFAULT 1.00 COMMENT '权重（用于计算质量分数，0-2）',
    enabled TINYINT DEFAULT 1 COMMENT '是否启用：0-禁用 1-启用',
    check_mode VARCHAR(32) DEFAULT 'MANUAL' COMMENT '检查触发方式：REALTIME/SCHEDULED/MANUAL',
    schedule_cron VARCHAR(128) DEFAULT NULL COMMENT '定时检查的Cron表达式',
    alert_on_failure TINYINT DEFAULT 0 COMMENT '失败时是否告警：0-否 1-是',
    description VARCHAR(512) DEFAULT NULL COMMENT '规则说明',
    created_by VARCHAR(64) DEFAULT NULL COMMENT '创建人',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_table_id (table_id),
    INDEX idx_rule_template (rule_template),
    INDEX idx_enabled (enabled),
    INDEX idx_check_mode (check_mode),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='质量规则表';

-- =============================================
-- 2. 质量检查结果表
-- =============================================
CREATE TABLE IF NOT EXISTS quality_check_result (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '结果ID',
    rule_id BIGINT NOT NULL COMMENT '关联的规则ID',
    check_type VARCHAR(16) DEFAULT 'MANUAL' COMMENT '检查类型：REALTIME/SCHEDULED/MANUAL',
    check_time DATETIME DEFAULT NULL COMMENT '检查时间',
    status VARCHAR(16) DEFAULT NULL COMMENT '状态：PASS/FAIL/WARN',
    actual_value VARCHAR(512) DEFAULT NULL COMMENT '实际值',
    expected_value VARCHAR(512) DEFAULT NULL COMMENT '期望值',
    error_message TEXT DEFAULT NULL COMMENT '错误信息',
    check_duration INT DEFAULT NULL COMMENT '检查耗时（毫秒）',
    triggered_by VARCHAR(64) DEFAULT NULL COMMENT '触发人',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_rule_id (rule_id),
    INDEX idx_check_time (check_time),
    INDEX idx_status (status),
    INDEX idx_check_type (check_type),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='质量检查结果表';

-- =============================================
-- 3. 质量报告表
-- =============================================
CREATE TABLE IF NOT EXISTS quality_report (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '报告ID',
    table_id BIGINT DEFAULT NULL COMMENT '关联的表ID',
    report_date DATE DEFAULT NULL COMMENT '报告日期',
    quality_score INT DEFAULT NULL COMMENT '质量分数（0-100）',
    quality_level VARCHAR(16) DEFAULT NULL COMMENT '质量等级：EXCELLENT/GOOD/PASS/FAIL',
    total_rules INT DEFAULT 0 COMMENT '总规则数',
    passed_rules INT DEFAULT 0 COMMENT '通过规则数',
    failed_rules INT DEFAULT 0 COMMENT '失败规则数',
    warning_rules INT DEFAULT 0 COMMENT '警告规则数',
    last_check_time DATETIME DEFAULT NULL COMMENT '最后一次检查时间',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_table_id (table_id),
    INDEX idx_report_date (report_date),
    INDEX idx_quality_level (quality_level),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='质量报告表';

-- =============================================
-- 4. 质量告警规则表
-- =============================================
CREATE TABLE IF NOT EXISTS quality_alert_rule (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '告警规则ID',
    alert_name VARCHAR(128) NOT NULL COMMENT '告警名称',
    rule_id BIGINT DEFAULT NULL COMMENT '关联的质量规则ID（为空则表示全局告警）',
    table_id BIGINT DEFAULT NULL COMMENT '关联的表ID（为空则表示所有表）',
    alert_condition VARCHAR(32) NOT NULL COMMENT '告警条件：FAIL/WARN/SCORE_BELOW',
    alert_threshold INT DEFAULT NULL COMMENT '告警阈值（如质量分数低于60）',
    alert_channels TEXT DEFAULT NULL COMMENT '告警渠道JSON：["DINGTALK", "EMAIL"]',
    alert_recipients TEXT DEFAULT NULL COMMENT '告警接收人列表JSON',
    alert_cc TEXT DEFAULT NULL COMMENT '邮件抄送列表JSON',
    alert_silence INT DEFAULT 60 COMMENT '告警抑制时长（分钟）',
    alert_enabled TINYINT DEFAULT 1 COMMENT '是否启用：0-禁用 1-启用',
    alert_upgrade_enabled TINYINT DEFAULT 0 COMMENT '是否启用告警升级：0-否 1-是',
    alert_upgrade_recipients TEXT DEFAULT NULL COMMENT '升级接收人列表JSON',
    alert_upgrade_after INT DEFAULT NULL COMMENT '升级时间（小时）',
    description VARCHAR(512) DEFAULT NULL COMMENT '告警说明',
    created_by VARCHAR(64) DEFAULT NULL COMMENT '创建人',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_rule_id (rule_id),
    INDEX idx_table_id (table_id),
    INDEX idx_alert_condition (alert_condition),
    INDEX idx_alert_enabled (alert_enabled),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='质量告警规则表';

-- =============================================
-- 5. 质量告警历史表
-- =============================================
CREATE TABLE IF NOT EXISTS quality_alert_history (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '历史ID',
    alert_rule_id BIGINT DEFAULT NULL COMMENT '关联的告警规则ID',
    table_id BIGINT DEFAULT NULL COMMENT '关联的表ID',
    check_result_id BIGINT DEFAULT NULL COMMENT '关联的检查结果ID',
    alert_time DATETIME DEFAULT NULL COMMENT '告警时间',
    alert_title VARCHAR(255) DEFAULT NULL COMMENT '告警标题',
    alert_content TEXT DEFAULT NULL COMMENT '告警内容',
    alert_channel VARCHAR(32) DEFAULT NULL COMMENT '告警渠道：DINGTALK/EMAIL/WEWORK',
    alert_status VARCHAR(16) DEFAULT NULL COMMENT '告警状态：SENT/FAILED/SILENCED',
    sent_time DATETIME DEFAULT NULL COMMENT '发送时间',
    error_message TEXT DEFAULT NULL COMMENT '错误信息',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_alert_rule_id (alert_rule_id),
    INDEX idx_table_id (table_id),
    INDEX idx_check_result_id (check_result_id),
    INDEX idx_alert_status (alert_status),
    INDEX idx_alert_time (alert_time),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='质量告警历史表';

-- 验证
SHOW TABLES LIKE 'quality_%';
