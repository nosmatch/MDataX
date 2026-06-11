-- =============================================
-- SchedulerX 集成：为 sync_task / sql_task 增加 schedulerx_dag_id 字段
-- 创建时间: 2026-06-01
-- =============================================

USE mdatax;

-- sync_task 表增加 schedulerx_dag_id
ALTER TABLE sync_task
    ADD COLUMN schedulerx_dag_id VARCHAR(64) DEFAULT NULL COMMENT 'SchedulerX DAG ID' AFTER ds_task_code;

-- sql_task 表增加 schedulerx_dag_id
ALTER TABLE sql_task
    ADD COLUMN schedulerx_dag_id VARCHAR(64) DEFAULT NULL COMMENT 'SchedulerX DAG ID' AFTER ds_task_code;

-- sql_task_workflow 表增加 schedulerx_dag_id
ALTER TABLE sql_task_workflow
    ADD COLUMN schedulerx_dag_id VARCHAR(64) DEFAULT NULL COMMENT 'SchedulerX DAG ID' AFTER ds_schedule_id;
