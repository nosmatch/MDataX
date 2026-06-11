package com.mogu.data.schedulerx.engine;

import com.mogu.data.schedulerx.entity.TaskInstance;

/**
 * 任务执行器接口
 *
 * @author fengzhu
 */
public interface TaskExecutor {

    /**
     * 执行任务
     *
     * @param taskInstance 任务实例
     */
    void execute(TaskInstance taskInstance);

}
