package com.mogu.data.integration.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mogu.data.common.Result;
import com.mogu.data.integration.dolphinscheduler.DolphinSchedulerClient;
import com.mogu.data.integration.dolphinscheduler.DolphinSchedulerProperties;
import com.mogu.data.integration.entity.SqlTaskWorkflow;
import com.mogu.data.integration.entity.WorkflowInstance;
import com.mogu.data.integration.service.SqlTaskLogService;
import com.mogu.data.integration.service.SqlTaskWorkflowService;
import com.mogu.data.integration.service.WorkflowInstanceService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * SQL任务工作流（DAG）管理控制器
 *
 * @author fengzhu
 */
@RestController
@RequestMapping("/sql-task-workflow")
@RequiredArgsConstructor
public class SqlTaskWorkflowController {

    private final SqlTaskWorkflowService workflowService;
    private final DolphinSchedulerClient dsClient;
    private final DolphinSchedulerProperties props;
    private final WorkflowInstanceService instanceService;
    private final SqlTaskLogService sqlTaskLogService;

    @GetMapping("/page")
    public Result<Page<SqlTaskWorkflow>> page(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) String keyword) {
        return Result.success(workflowService.pageWorkflows(keyword, page, size));
    }

    @GetMapping("/{id}")
    public Result<SqlTaskWorkflow> getById(@PathVariable Long id) {
        SqlTaskWorkflow workflow = workflowService.getById(id);
        if (workflow == null || workflow.getDeleted() != null && workflow.getDeleted() == 1) {
            return Result.error("工作流不存在");
        }
        return Result.success(workflow);
    }

    @PostMapping
    public Result<Void> create(@Valid @RequestBody WorkflowCreateRequest request) {
        SqlTaskWorkflow workflow = new SqlTaskWorkflow();
        workflow.setWorkflowName(request.getWorkflowName());
        workflow.setDescription(request.getDescription());
        workflow.setCronExpression(request.getCronExpression());
        workflowService.createWorkflow(workflow);
        return Result.success();
    }

    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody WorkflowUpdateRequest request) {
        SqlTaskWorkflow workflow = new SqlTaskWorkflow();
        workflow.setId(id);
        workflow.setWorkflowName(request.getWorkflowName());
        workflow.setDescription(request.getDescription());
        workflow.setCronExpression(request.getCronExpression());
        workflowService.updateWorkflow(workflow);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        workflowService.deleteWorkflow(id);
        return Result.success();
    }

    @PostMapping("/{id}/toggle")
    public Result<Void> toggleStatus(@PathVariable Long id) {
        workflowService.toggleStatus(id);
        return Result.success();
    }

    // ==================== 实例管理 ====================

    @PostMapping("/{id}/execute")
    public Result<Long> execute(@PathVariable Long id) {
        SqlTaskWorkflow workflow = workflowService.getById(id);
        if (workflow == null || workflow.getDsProcessCode() == null) {
            return Result.error("工作流未同步到调度器，无法执行");
        }
        if (workflow.getStatus() == null || workflow.getStatus() != 1) {
            return Result.error("工作流已停用，无法执行");
        }
        Long instanceId = dsClient.startProcessInstance(workflow.getDsProcessCode());
        instanceService.recordManualStart(id, instanceId);
        return Result.success(instanceId);
    }

    @GetMapping("/{id}/instances")
    public Result<String> listInstances(@PathVariable Long id) {
        SqlTaskWorkflow workflow = workflowService.getById(id);
        if (workflow == null || workflow.getDsProcessCode() == null) {
            return Result.error("工作流未同步到调度器");
        }
        String instances = dsClient.listProcessInstances(workflow.getDsProcessCode());
        return Result.success(instances);
    }

    @GetMapping("/{id}/dag")
    public Result<java.util.Map<String, Object>> getDag(@PathVariable Long id) {
        return Result.success(workflowService.getDag(id));
    }

    // ==================== 执行状态与历史 ====================

    @GetMapping("/{id}/last-execution")
    public Result<WorkflowInstance> lastExecution(@PathVariable Long id) {
        return Result.success(workflowService.getLastExecution(id));
    }

    @GetMapping("/{id}/next-execution")
    public Result<LocalDateTime> nextExecution(@PathVariable Long id) {
        return Result.success(workflowService.getNextExecutionTime(id));
    }

    @GetMapping("/{id}/history")
    public Result<Page<WorkflowInstance>> history(
            @PathVariable Long id,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size) {
        return Result.success(instanceService.pageHistory(id, page, size));
    }

    @GetMapping("/{id}/history/{instanceId}/logs")
    public Result<?> historyLogs(@PathVariable Long id, @PathVariable Long instanceId) {
        return Result.success(sqlTaskLogService.lambdaQuery()
                .eq(com.mogu.data.integration.entity.SqlTaskLog::getDsInstanceId, instanceId)
                .orderByDesc(com.mogu.data.integration.entity.SqlTaskLog::getCreateTime)
                .list());
    }

    @GetMapping("/{id}/ds-url")
    public Result<Map<String, String>> dsUrl(@PathVariable Long id) {
        SqlTaskWorkflow workflow = workflowService.getById(id);
        if (workflow == null) {
            return Result.error("工作流不存在");
        }
        String baseUrl = props.getBaseUrl();
        if (baseUrl == null || baseUrl.isEmpty()) {
            return Result.error("DolphinScheduler 地址未配置");
        }
        // DS 3.2.1 项目任务首页 URL
        String url = baseUrl.replaceAll("/api$", "").replaceAll("/$", "")
                + "/ui/projects/" + props.getProjectCode()
                + "/workflow-definition?projectName=MDataX";
        Map<String, String> result = new HashMap<>();
        result.put("dsUrl", url);
        return Result.success(result);
    }

    @PostMapping("/instances/{instanceId}/stop")
    public Result<Void> stopInstance(@PathVariable Long instanceId) {
        dsClient.stopProcessInstance(instanceId);
        return Result.success();
    }

    @PostMapping("/instances/{instanceId}/pause")
    public Result<Void> pauseInstance(@PathVariable Long instanceId) {
        dsClient.pauseProcessInstance(instanceId);
        return Result.success();
    }

    @PostMapping("/instances/{instanceId}/retry")
    public Result<Void> retryInstance(@PathVariable Long instanceId) {
        dsClient.retryFailureTask(instanceId);
        return Result.success();
    }

    @Data
    public static class WorkflowCreateRequest {
        @NotBlank(message = "工作流名称不能为空")
        private String workflowName;
        private String description;
        private String cronExpression;
    }

    @Data
    public static class WorkflowUpdateRequest {
        private String workflowName;
        private String description;
        private String cronExpression;
    }
}
