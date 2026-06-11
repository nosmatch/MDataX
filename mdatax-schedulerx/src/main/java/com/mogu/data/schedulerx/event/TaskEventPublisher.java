package com.mogu.data.schedulerx.event;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * 任务事件发布器
 *
 * @author fengzhu
 */
@Component
public class TaskEventPublisher {

    @Autowired
    private ApplicationEventPublisher publisher;

    public void publish(TaskEvent event) {
        publisher.publishEvent(event);
    }

}
