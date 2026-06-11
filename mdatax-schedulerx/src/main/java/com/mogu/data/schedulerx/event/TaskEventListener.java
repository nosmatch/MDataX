package com.mogu.data.schedulerx.event;

import com.mogu.data.schedulerx.engine.DagEngine;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * 任务事件监听器
 *
 * @author fengzhu
 */
@Component
public class TaskEventListener {

    @Autowired
    private DagEngine dagEngine;

    @Async("schedulerEventExecutor")
    @EventListener
    public void onTaskEvent(TaskEvent event) {
        dagEngine.onTaskComplete(event);
    }

}
