package com.mogu.data.schedulerx.controller;

import com.mogu.data.schedulerx.dto.CallbackRequest;
import com.mogu.data.schedulerx.entity.TaskInstance;
import com.mogu.data.schedulerx.enums.EventType;
import com.mogu.data.schedulerx.enums.TaskInstanceStatus;
import com.mogu.data.schedulerx.event.TaskEvent;
import com.mogu.data.schedulerx.event.TaskEventPublisher;
import com.mogu.data.schedulerx.service.TaskInstanceService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;

/**
 * 任务回调 Controller
 *
 * @author fengzhu
 */
@Slf4j
@Validated
@RestController
@RequestMapping("/api/v1/callback")
public class CallbackController {

    @Autowired
    private TaskInstanceService taskInstanceService;
    @Autowired
    private TaskEventPublisher eventPublisher;

    /**
     * 任务执行完成回调
     */
    @PostMapping("/{taskInstanceId}")
    public Object callback(@PathVariable String taskInstanceId, @Valid @RequestBody CallbackRequest request) {
        log.info("[CallbackController] 收到回调: taskInstanceId={}, status={}", taskInstanceId, request.getStatus());

        TaskInstance taskInstance = taskInstanceService.lambdaQuery()
                .eq(TaskInstance::getTaskInstanceId, taskInstanceId)
                .one();
        if (taskInstance == null) {
            throw new IllegalArgumentException("任务实例不存在: " + taskInstanceId);
        }

        // 校验状态：只有 RUNNING 状态的任务才能接收回调
        if (!TaskInstanceStatus.RUNNING.name().equals(taskInstance.getStatus())) {
            log.warn("[CallbackController] 任务状态不是 RUNNING，忽略回调: taskInstanceId={}, currentStatus={}",
                    taskInstanceId, taskInstance.getStatus());
            return java.util.Collections.singletonMap("message", "任务已完成，忽略回调");
        }

        // 根据 status 发布对应事件
        EventType eventType;
        switch (request.getStatus().toUpperCase()) {
            case "SUCCESS":
                eventType = EventType.SUCCESS;
                break;
            case "FAILURE":
            case "FAILED":
                eventType = EventType.FAILURE;
                break;
            case "TIMEOUT":
                eventType = EventType.TIMEOUT;
                break;
            default:
                throw new IllegalArgumentException("不支持的状态: " + request.getStatus());
        }

        TaskEvent event = new TaskEvent(
                taskInstanceId,
                taskInstance.getDagInstanceId(),
                eventType
        );
        event.setOutput(request.getOutput());
        event.setDurationMs(request.getDurationMs());
        event.setErrorMsg(request.getLogs());

        eventPublisher.publish(event);

        return java.util.Collections.singletonMap("success", true);
    }

}
