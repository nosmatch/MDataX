package com.mogu.data.integration.scheduler;

import com.mogu.data.integration.entity.SqlTask;
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
}
