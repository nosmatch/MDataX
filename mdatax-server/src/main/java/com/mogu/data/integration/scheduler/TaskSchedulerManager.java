package com.mogu.data.integration.scheduler;

import com.mogu.data.integration.entity.SqlTask;
import com.mogu.data.integration.entity.SqlTaskWorkflow;
import com.mogu.data.integration.entity.SyncTask;

/**
 * 任务调度管理器接口。
 *
 * <p>定义统一的调度操作契约，屏蔽底层调度引擎差异。
 * 当前支持两种实现：
 * <ul>
 *   <li>{@link SyncTaskSchedulerManager} / {@link SqlTaskSchedulerManager} — 本地 Spring 调度</li>
 *   <li>{@code DolphinSchedulerManager} — DolphinScheduler 分布式调度</li>
 * </ul>
 *
 * @author fengzhu
 */
public interface TaskSchedulerManager {

    // ==================== 独立任务调度 ====================

    /**
     * 注册同步任务的定时调度。
     *
     * @param task 同步任务
     */
    void scheduleSyncTask(SyncTask task);

    /**
     * 注册 SQL 任务的定时调度。
     *
     * @param task SQL 任务
     */
    void scheduleSqlTask(SqlTask task);

    /**
     * 取消同步任务的定时调度。
     *
     * @param taskId 任务ID
     */
    void cancelSyncTask(Long taskId);

    /**
     * 取消 SQL 任务的定时调度。
     *
     * @param taskId 任务ID
     */
    void cancelSqlTask(Long taskId);

    /**
     * 删除同步任务在调度器中的资源（下线并清理工作流定义）。
     *
     * @param taskId 任务ID
     */
    void deleteSyncTask(Long taskId);

    /**
     * 删除 SQL 任务在调度器中的资源（下线并清理工作流定义）。
     *
     * @param taskId 任务ID
     */
    void deleteSqlTask(Long taskId);

    /**
     * 重新调度同步任务（先取消再注册）。
     *
     * @param task 同步任务
     */
    void rescheduleSyncTask(SyncTask task);

    /**
     * 重新调度 SQL 任务（先取消再注册）。
     *
     * @param task SQL 任务
     */
    void rescheduleSqlTask(SqlTask task);

    // ==================== 统一任务调度（新增） ====================

    /**
     * 注册统一任务的定时调度。
     *
     * @param task 统一任务
     */
    void scheduleTask(com.mogu.data.integration.entity.Task task);

    /**
     * 取消统一任务的定时调度。
     *
     * @param taskId 任务ID
     */
    void cancelTask(Long taskId);

    /**
     * 重新调度统一任务（先取消再注册）。
     *
     * @param task 统一任务
     */
    void rescheduleTask(com.mogu.data.integration.entity.Task task);

    /**
     * 手动触发统一任务。
     *
     * @param task 统一任务
     * @return 实例标识符
     */
    String triggerTask(com.mogu.data.integration.entity.Task task);

    // ==================== Workflow（DAG）调度 ====================

    /**
     * 注册 SQL 任务工作流的定时调度。
     *
     * @param workflow SQL 任务工作流
     */
    void scheduleWorkflow(SqlTaskWorkflow workflow);

    /**
     * 取消 SQL 任务工作流的定时调度。
     *
     * @param workflowId 工作流ID
     */
    void cancelWorkflow(Long workflowId);

    /**
     * 删除 SQL 任务工作流在调度器中的资源。
     *
     * @param workflowId 工作流ID
     */
    void deleteWorkflow(Long workflowId);

    /**
     * 重新调度 SQL 任务工作流（先取消再注册）。
     *
     * @param workflow SQL 任务工作流
     */
    void rescheduleWorkflow(SqlTaskWorkflow workflow);

    // ==================== 手动触发 ====================

    /**
     * 手动触发 SQL 任务。
     *
     * @param task SQL 任务
     * @return 实例标识符
     */
    Long triggerSqlTask(SqlTask task);

    /**
     * 手动触发同步任务。
     *
     * @param task 同步任务
     * @return 实例标识符
     */
    Long triggerSyncTask(SyncTask task);

    /**
     * 手动触发工作流。
     *
     * @param workflow SQL 任务工作流
     * @return 实例标识符
     */
    String triggerWorkflow(SqlTaskWorkflow workflow);

    // ==================== 实例操作 ====================

    /**
     * 停止工作流实例。
     *
     * @param instanceId 实例标识符
     */
    void stopWorkflowInstance(String instanceId);

    /**
     * 暂停工作流实例。
     *
     * @param instanceId 实例标识符
     */
    void pauseWorkflowInstance(String instanceId);

    /**
     * 重试工作流实例的失败任务。
     *
     * @param instanceId 实例标识符
     */
    void retryWorkflowInstance(String instanceId);

    /**
     * 查询工作流实例列表。
     *
     * @param workflow SQL 任务工作流
     * @param pageNum  页码
     * @param pageSize 页大小
     * @return 实例列表 JSON 字符串
     */
    String listWorkflowInstances(SqlTaskWorkflow workflow, int pageNum, int pageSize);

    // ==================== 实例查询 ====================

    /**
     * 查询实例详情。
     *
     * @param instanceId 实例标识符
     * @return 实例详情 JSON 字符串
     */
    String getInstanceDetail(String instanceId);

    /**
     * 查询实例的任务列表。
     *
     * @param instanceId 实例标识符
     * @return 任务列表 JSON 字符串
     */
    String getInstanceTasks(String instanceId);

    /**
     * 查询 SQL 任务实例列表。
     *
     * @param taskId SQL 任务 ID
     * @param pageNum 页码
     * @param pageSize 页大小
     * @return 实例列表 JSON 字符串
     */
    String getSqlTaskInstances(Long taskId, int pageNum, int pageSize);

    /**
     * 查询同步任务实例列表。
     *
     * @param taskId 同步任务 ID
     * @param pageNum 页码
     * @param pageSize 页大小
     * @return 实例列表 JSON 字符串
     */
    String getSyncTaskInstances(Long taskId, int pageNum, int pageSize);
}
