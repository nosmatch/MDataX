package com.mogu.data.integration.engine;

/**
 * 任务执行引擎接口。
 *
 * <p>定义统一的任务执行契约，将"何时触发"与"如何执行"解耦。
 * 当前实现为本地 JVM 执行（{@link LocalTaskEngine}），
 * 后续可扩展为远程 Worker 调用而不影响上层调度逻辑。
 *
 * @author fengzhu
 */
public interface TaskEngine {

    /**
     * 执行同步任务。
     *
     * <p>由调度器触发，实际执行 MySQL → ClickHouse 的数据同步。
     *
     * @param taskId 同步任务ID
     */
    void executeSyncTask(Long taskId);

    /**
     * 执行 SQL 任务。
     *
     * <p>由调度器触发，实际执行 SQL 并在 ClickHouse 中运行。
     *
     * @param taskId SQL 任务ID
     */
    void executeSqlTask(Long taskId);
}
