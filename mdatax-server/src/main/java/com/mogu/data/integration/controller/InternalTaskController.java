package com.mogu.data.integration.controller;

import com.mogu.data.common.Result;
import com.mogu.data.integration.dolphinscheduler.DolphinSchedulerClient;
import com.mogu.data.integration.dolphinscheduler.DolphinSchedulerProperties;
import com.mogu.data.integration.engine.TaskEngine;
import com.mogu.data.integration.entity.SqlTask;
import com.mogu.data.integration.entity.SqlTaskWorkflow;
import com.mogu.data.integration.entity.SyncTask;
import com.mogu.data.integration.entity.WorkflowInstance;
import com.mogu.data.integration.mapper.SqlTaskMapper;
import com.mogu.data.integration.mapper.SqlTaskWorkflowMapper;
import com.mogu.data.integration.mapper.SyncTaskMapper;
import com.mogu.data.integration.service.WorkflowInstanceService;
import com.mogu.data.schedulerx.client.SchedulerXClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 内部任务执行回调接口
 *
 * <p>由 DolphinScheduler Worker 或 SchedulerX 调用，触发 MDataX 任务执行。
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
    private final SqlTaskMapper sqlTaskMapper;
    private final SyncTaskMapper syncTaskMapper;
    private final SqlTaskWorkflowMapper sqlTaskWorkflowMapper;
    private final WorkflowInstanceService workflowInstanceService;
    private final DolphinSchedulerClient dsClient;
    private final SchedulerXClient schedulerXClient;

    @PostMapping("/execute")
    public Result<Void> execute(@RequestBody Map<String, Object> request) {
        // 支持两种格式：
        // 1. DolphinScheduler 格式: {taskType, taskId, secret, instanceId}
        // 2. SchedulerX 格式: {body: {taskType, taskId, secret}, taskInstanceId, dagInstanceId, callbackUrl}

        String taskType;
        Long taskId;
        String secret;
        String instanceId;
        String taskInstanceId = null;

        Object bodyObj = request.get("body");
        if (bodyObj instanceof Map) {
            // SchedulerX 格式
            Map<String, Object> body = (Map<String, Object>) bodyObj;
            taskType = (String) body.get("taskType");
            taskId = extractLong(body.get("taskId"));
            secret = (String) body.get("secret");
            instanceId = (String) body.get("instanceId");
            taskInstanceId = (String) request.get("taskInstanceId");
        } else {
            // DS 格式（或直接格式）
            taskType = (String) request.get("taskType");
            taskId = extractLong(request.get("taskId"));
            secret = (String) request.get("secret");
            instanceId = (String) request.get("instanceId");
        }

        // 安全校验
        if (secret == null || !props.getCallbackSecret().equals(secret)) {
            log.warn("内部任务执行接口密钥校验失败");
            return Result.error(403, "Unauthorized");
        }

        Long dsInstanceId = resolveInstanceId(instanceId, taskId);
        log.info("收到回调执行请求: taskType={}, taskId={}, dsInstanceId={}, taskInstanceId={}",
                taskType, taskId, dsInstanceId, taskInstanceId);

        // 记录工作流实例（定时调度触发时）
        recordWorkflowInstanceIfNeeded(taskId, dsInstanceId);

        try {
            if ("SYNC".equals(taskType)) {
                taskEngine.executeSyncTask(taskId, dsInstanceId);
            } else if ("SQL".equals(taskType)) {
                taskEngine.executeSqlTask(taskId, dsInstanceId);
            } else {
                return Result.error(400, "Unknown taskType: " + taskType);
            }
            // SchedulerX 模式下由 DagEngine 自动驱动 DAG 完成，无需轮询 DS 状态
            if (taskInstanceId == null) {
                // DS 模式：检查工作流实例是否已完成
                checkAndFinishWorkflowInstance(dsInstanceId);
            }
            // 回调 SchedulerX
            if (taskInstanceId != null) {
                schedulerXClient.callback(taskInstanceId, true, null);
            }
            return Result.success();
        } catch (Exception e) {
            log.error("任务执行失败: taskType={}, taskId={}", taskType, taskId, e);
            // 回调 SchedulerX
            if (taskInstanceId != null) {
                schedulerXClient.callback(taskInstanceId, false, e.getMessage());
            }
            // 返回非 200，DS 会判定节点失败，按配置重试
            return Result.error(500, e.getMessage());
        }
    }

    private Long extractLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        try {
            return Long.valueOf(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 反推 DS 实例ID：优先使用回调中的 instanceId，否则通过 taskId 关联 workflow 查找最新的 RUNNING 实例
     */
    private Long resolveInstanceId(String instanceIdStr, Long taskId) {
        if (instanceIdStr != null && !instanceIdStr.isEmpty()) {
            try {
                return Long.valueOf(instanceIdStr);
            } catch (NumberFormatException e) {
                log.warn("instanceId 格式非法: {}", instanceIdStr);
            }
        }
        if (taskId == null) {
            return null;
        }
        Long workflowId = resolveWorkflowIdByTaskId(taskId);
        if (workflowId == null) {
            return null;
        }
        WorkflowInstance running = workflowInstanceService.getLatestRunningByWorkflowId(workflowId);
        if (running != null) {
            log.info("通过 workflow 反推 instanceId: taskId={}, workflowId={}, dsInstanceId={}",
                    taskId, workflowId, running.getDsInstanceId());
            return running.getDsInstanceId();
        }
        return null;
    }

    /**
     * 通过 taskId 反推所属 workflowId（同时支持 SQL 和 SYNC 任务）
     */
    private Long resolveWorkflowIdByTaskId(Long taskId) {
        SqlTask sqlTask = sqlTaskMapper.selectById(taskId);
        if (sqlTask != null && sqlTask.getWorkflowId() != null) {
            return sqlTask.getWorkflowId();
        }
        SyncTask syncTask = syncTaskMapper.selectById(taskId);
        if (syncTask != null && syncTask.getWorkflowId() != null) {
            return syncTask.getWorkflowId();
        }
        return null;
    }

    private void recordWorkflowInstanceIfNeeded(Long taskId, Long dsInstanceId) {
        if (dsInstanceId == null || taskId == null) {
            return;
        }
        try {
            Long workflowId = resolveWorkflowIdByTaskId(taskId);
            if (workflowId == null) {
                return;
            }
            workflowInstanceService.recordOrFindScheduled(workflowId, dsInstanceId);
        } catch (Exception e) {
            log.warn("记录工作流实例失败: taskId={}, dsInstanceId={}", taskId, dsInstanceId, e);
        }
    }

    private void checkAndFinishWorkflowInstance(Long dsInstanceId) {
        if (dsInstanceId == null) {
            return;
        }
        Long processCode = resolveProcessCode(dsInstanceId);
        if (processCode == null) {
            log.warn("无法反推工作流 processCode，跳过状态检查: dsInstanceId={}", dsInstanceId);
            return;
        }
        try {
            String status = dsClient.getProcessInstanceStatus(processCode);
            log.info("DS 实例状态查询: dsInstanceId={}, processCode={}, status={}", dsInstanceId, processCode, status);
            if ("SUCCESS".equals(status)) {
                workflowInstanceService.finishInstance(dsInstanceId, "SUCCESS", null);
                return;
            }
            if ("FAILURE".equals(status) || "STOP".equals(status) || "KILL".equals(status)) {
                workflowInstanceService.finishInstance(dsInstanceId, "FAILED", "DS 状态: " + status);
                return;
            }
            // 中间态，延迟 5 秒异步重试一次（最后一个节点回调时 DS 通常已完成终态更新）
            new Thread(() -> {
                try {
                    Thread.sleep(5000);
                    String retryStatus = dsClient.getProcessInstanceStatus(processCode);
                    log.info("DS 实例状态重试: dsInstanceId={}, status={}", dsInstanceId, retryStatus);
                    if ("SUCCESS".equals(retryStatus)) {
                        workflowInstanceService.finishInstance(dsInstanceId, "SUCCESS", null);
                    } else if ("FAILURE".equals(retryStatus) || "STOP".equals(retryStatus) || "KILL".equals(retryStatus)) {
                        workflowInstanceService.finishInstance(dsInstanceId, "FAILED", "DS 状态: " + retryStatus);
                    }
                } catch (Exception ex) {
                    log.warn("延迟检查工作流实例状态失败: dsInstanceId={}", dsInstanceId, ex);
                }
            }).start();
        } catch (Exception e) {
            log.warn("检查工作流实例状态失败: dsInstanceId={}", dsInstanceId, e);
        }
    }

    /**
     * 通过 dsInstanceId 反推工作流的 dsProcessCode
     */
    private Long resolveProcessCode(Long dsInstanceId) {
        WorkflowInstance instance = workflowInstanceService.getByDsInstanceId(dsInstanceId);
        if (instance == null) {
            return null;
        }
        SqlTaskWorkflow workflow = sqlTaskWorkflowMapper.selectById(instance.getWorkflowId());
        if (workflow == null) {
            return null;
        }
        return workflow.getDsProcessCode();
    }
}
