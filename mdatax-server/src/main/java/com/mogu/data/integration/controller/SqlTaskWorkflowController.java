package com.mogu.data.integration.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mogu.data.common.Result;
import com.mogu.data.integration.entity.SqlTaskWorkflow;
import com.mogu.data.integration.entity.WorkflowInstance;
import com.mogu.data.integration.scheduler.TaskSchedulerManager;
import com.mogu.data.integration.service.SqlTaskLogService;
import com.mogu.data.integration.service.SqlTaskWorkflowService;
import com.mogu.data.integration.service.WorkflowInstanceService;
import com.mogu.data.integration.vo.InstanceVO;
import com.mogu.data.integration.vo.TaskInstanceVO;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * SQL任务工作流（DAG）管理控制器
 *
 * @author fengzhu
 */
@Slf4j
@RestController
@RequestMapping("/sql-task-workflow")
@RequiredArgsConstructor
public class SqlTaskWorkflowController {

    private final SqlTaskWorkflowService workflowService;
    private final TaskSchedulerManager schedulerManager;
    private final WorkflowInstanceService instanceService;
    private final SqlTaskLogService sqlTaskLogService;
    private final ObjectMapper objectMapper;

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
    public Result<String> execute(@PathVariable Long id) {
        SqlTaskWorkflow workflow = workflowService.getById(id);
        if (workflow == null) {
            return Result.error("工作流不存在");
        }
        if (workflow.getStatus() == null || workflow.getStatus() != 1) {
            return Result.error("工作流已停用，无法执行");
        }
        if (workflow.getDsProcessCode() == null && workflow.getSchedulerxDagId() == null) {
            return Result.error("工作流未同步到调度器，无法执行");
        }
        String instanceId = schedulerManager.triggerWorkflow(workflow);
        if (instanceId == null) {
            return Result.error("工作流未同步到调度器，无法执行");
        }
        // 兼容 DS 模式记录实例
        try {
            Long dsId = Long.valueOf(instanceId);
            instanceService.recordManualStart(id, dsId);
        } catch (NumberFormatException e) {
            // SchedulerX 模式，实例由 SchedulerX 自行管理
        }
        return Result.success(instanceId);
    }

    @GetMapping("/{id}/instances")
    public Result<Page<InstanceVO>> listInstances(
            @PathVariable Long id,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize) {
        SqlTaskWorkflow workflow = workflowService.getById(id);
        if (workflow == null) {
            return Result.error("工作流不存在");
        }
        if (workflow.getDsProcessCode() == null && workflow.getSchedulerxDagId() == null) {
            return Result.error("工作流未同步到调度器");
        }
        String json = schedulerManager.listWorkflowInstances(workflow, pageNum, pageSize);
        return parseInstancePage(json, pageNum, pageSize);
    }

    @GetMapping("/instances/{instanceId}")
    public Result<InstanceVO> instanceDetail(@PathVariable String instanceId) {
        String json = schedulerManager.getInstanceDetail(instanceId);
        if (json == null || json.isEmpty()) {
            return Result.error("实例不存在或查询失败");
        }
        try {
            InstanceVO vo = objectMapper.readValue(json, InstanceVO.class);
            return Result.success(vo);
        } catch (Exception e) {
            log.error("解析实例详情失败: instanceId={}", instanceId, e);
            return Result.error("解析实例数据失败");
        }
    }

    @GetMapping("/instances/{instanceId}/tasks")
    public Result<List<TaskInstanceVO>> instanceTasks(@PathVariable String instanceId) {
        String json = schedulerManager.getInstanceTasks(instanceId);
        if (json == null || json.isEmpty()) {
            return Result.error("实例不存在或查询失败");
        }
        try {
            List<TaskInstanceVO> list = objectMapper.readValue(json,
                    new TypeReference<List<TaskInstanceVO>>() {});
            return Result.success(list);
        } catch (Exception e) {
            log.error("解析实例任务列表失败: instanceId={}", instanceId, e);
            return Result.error("解析任务数据失败");
        }
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

    @PostMapping("/instances/{instanceId}/stop")
    public Result<Void> stopInstance(@PathVariable String instanceId) {
        schedulerManager.stopWorkflowInstance(instanceId);
        return Result.success();
    }

    @PostMapping("/instances/{instanceId}/pause")
    public Result<Void> pauseInstance(@PathVariable String instanceId) {
        schedulerManager.pauseWorkflowInstance(instanceId);
        return Result.success();
    }

    @PostMapping("/instances/{instanceId}/retry")
    public Result<Void> retryInstance(@PathVariable String instanceId) {
        schedulerManager.retryWorkflowInstance(instanceId);
        return Result.success();
    }

    /**
     * 解析 SchedulerX / DS 返回的实例分页 JSON
     */
    private Result<Page<InstanceVO>> parseInstancePage(String json, int pageNum, int pageSize) {
        if (json == null || json.isEmpty()) {
            Page<InstanceVO> emptyPage = new Page<>(pageNum, pageSize);
            emptyPage.setRecords(Collections.emptyList());
            emptyPage.setTotal(0);
            return Result.success(emptyPage);
        }
        try {
            InstancePageWrapper wrapper = objectMapper.readValue(json, InstancePageWrapper.class);
            List<InstanceVO> records = wrapper.getRecords() != null ? wrapper.getRecords() : Collections.emptyList();
            long total = wrapper.getTotal() != null ? wrapper.getTotal() : 0L;
            Page<InstanceVO> page = new Page<>(pageNum, pageSize);
            page.setRecords(records);
            page.setTotal(total);
            return Result.success(page);
        } catch (Exception e) {
            log.error("解析实例列表失败, json={}", json, e);
            return Result.error("解析实例数据失败");
        }
    }

    @Data
    private static class InstancePageWrapper {
        private List<InstanceVO> records;
        private Long total;
        private Long current;
        private Long size;
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
