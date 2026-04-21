package com.mogu.data.integration.controller;

import com.mogu.data.common.Result;
import com.mogu.data.integration.dolphinscheduler.DolphinSchedulerProperties;
import com.mogu.data.integration.dto.InternalExecuteRequest;
import com.mogu.data.integration.engine.TaskEngine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 内部任务执行回调接口
 *
 * <p>由 DolphinScheduler Worker 调用，触发 MDataX 任务执行。
 * 此接口不经过 JWT 鉴权，仅通过共享密钥校验。
 *
 * @author fengzhu
 */
@Slf4j
@RestController
@RequestMapping("/internal/task")
@RequiredArgsConstructor
public class InternalTaskController {

    private final DolphinSchedulerProperties props;
    private final TaskEngine taskEngine;

    @PostMapping("/execute")
    public Result<Void> execute(@RequestBody InternalExecuteRequest request) {
        // 安全校验
        if (!props.getCallbackSecret().equals(request.getSecret())) {
            log.warn("内部任务执行接口密钥校验失败");
            return Result.error(403, "Unauthorized");
        }

        log.info("收到 DS 回调执行请求: taskType={}, taskId={}, instanceId={}",
                request.getTaskType(), request.getTaskId(), request.getInstanceId());

        try {
            if ("SYNC".equals(request.getTaskType())) {
                taskEngine.executeSyncTask(request.getTaskId());
            } else if ("SQL".equals(request.getTaskType())) {
                taskEngine.executeSqlTask(request.getTaskId());
            } else {
                return Result.error(400, "Unknown taskType: " + request.getTaskType());
            }
            return Result.success();
        } catch (Exception e) {
            log.error("任务执行失败: taskType={}, taskId={}", request.getTaskType(), request.getTaskId(), e);
            // 返回非 200，DS 会判定节点失败，按配置重试
            return Result.error(500, e.getMessage());
        }
    }
}
