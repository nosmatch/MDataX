package com.mogu.data.schedulerx.engine;

import com.mogu.data.schedulerx.entity.TaskInstance;
import com.mogu.data.schedulerx.enums.EventType;
import com.mogu.data.schedulerx.enums.TaskInstanceStatus;
import com.mogu.data.schedulerx.event.TaskEvent;
import com.mogu.data.schedulerx.event.TaskEventPublisher;
import com.mogu.data.schedulerx.service.TaskInstanceService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 超时检测器
 *
 * @author fengzhu
 */
@Slf4j
@Component
public class TimeoutChecker {

    @Autowired
    private TaskInstanceService taskInstanceService;
    @Autowired
    private TaskEventPublisher eventPublisher;

    /**
     * 每 5 秒检查一次
     */
    @Scheduled(fixedRate = 5000)
    public void check() {
        List<TaskInstance> runningTasks = taskInstanceService.lambdaQuery()
                .eq(TaskInstance::getStatus, TaskInstanceStatus.RUNNING.name())
                .list();

        LocalDateTime now = LocalDateTime.now();
        for (TaskInstance task : runningTasks) {
            try {
                if (isTimeout(task, now)) {
                    log.warn("[TimeoutChecker] 任务超时: taskInstanceId={}, startTime={}",
                            task.getTaskInstanceId(), task.getStartTime());

                    TaskEvent event = new TaskEvent(
                            task.getTaskInstanceId(),
                            task.getDagInstanceId(),
                            EventType.TIMEOUT
                    );
                    event.setErrorMsg("任务执行超时");
                    eventPublisher.publish(event);
                }
            } catch (Exception e) {
                log.error("[TimeoutChecker] 检查任务超时异常: taskInstanceId={}",
                        task.getTaskInstanceId(), e);
            }
        }
    }

    /**
     * 判断是否超时
     */
    private boolean isTimeout(TaskInstance task, LocalDateTime now) {
        if (task.getStartTime() == null) {
            return false;
        }

        // 默认超时 10 分钟
        int timeoutSeconds = 600;
        if (task.getDurationMs() != null && task.getDurationMs() > 0) {
            // 如果有配置的超时时间，使用配置的值
            // 这里简化处理：实际应该从 dag_task 配置中获取
        }

        // 计算已经执行的时间（秒）
        long elapsedSeconds = java.time.Duration.between(task.getStartTime(), now).getSeconds();
        return elapsedSeconds > timeoutSeconds;
    }

}
