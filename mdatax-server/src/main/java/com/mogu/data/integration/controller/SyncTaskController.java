package com.mogu.data.integration.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mogu.data.common.LoginUser;
import com.mogu.data.common.Result;
import com.mogu.data.integration.entity.SyncTask;
import com.mogu.data.integration.entity.SyncTaskLog;
import com.mogu.data.integration.entity.Task;
import com.mogu.data.integration.entity.TaskSyncDetail;
import com.mogu.data.integration.service.SyncEngineService;
import com.mogu.data.integration.service.SyncTaskLogService;
import com.mogu.data.integration.service.SyncTaskService;
import com.mogu.data.integration.service.TaskService;
import com.mogu.data.integration.vo.SyncTaskVO;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.util.List;

/**
 * 同步任务管理控制器（兼容层）
 *
 * <p>新的同步任务统一通过 {@link TaskService} 落到 {@code task} + {@code task_sync_detail} 表。
 * 本控制器保留旧端点以保证部分页面兼容，但创建/更新/删除/启用/执行均代理到统一任务服务。
 *
 * @author fengzhu
 */
@RestController
@RequestMapping("/sync-task")
@RequiredArgsConstructor
public class SyncTaskController {

    private final SyncTaskService syncTaskService;
    private final SyncEngineService syncEngineService;
    private final SyncTaskLogService syncTaskLogService;
    private final TaskService taskService;

    @GetMapping("/page")
    public Result<Page<SyncTaskVO>> page(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) String keyword) {
        return Result.success(syncTaskService.pageTasks(keyword, page, size));
    }

    @GetMapping("/{id}")
    public Result<SyncTask> getById(@PathVariable Long id) {
        SyncTask task = syncTaskService.getById(id);
        if (task == null || task.getDeleted() != null && task.getDeleted() == 1) {
            return Result.error("任务不存在");
        }
        return Result.success(task);
    }

    @PostMapping
    public Result<Long> create(@Valid @RequestBody TaskCreateRequest request) {
        Task task = new Task();
        task.setTaskName(request.getTaskName());
        task.setTaskType("SYNC");
        task.setCronExpression(request.getCronExpression());
        task.setWorkflowId(request.getWorkflowId());

        Long currentUserId = LoginUser.currentUserId();
        task.setOwnerUserId(currentUserId);
        task.setCreateUserId(currentUserId);

        TaskSyncDetail detail = new TaskSyncDetail();
        detail.setSourceDatasourceId(request.getDatasourceId());
        detail.setSourceTable(request.getSourceTable());
        detail.setTargetDatasourceId(request.getTargetDatasourceId());
        detail.setTargetTable(request.getTargetTable());
        detail.setSyncType(request.getSyncType());
        detail.setTimeField(request.getTimeField());
        detail.setWhereCondition(request.getWhereCondition());

        Long taskId = taskService.createTask(task, detail);
        return Result.success(taskId);
    }

    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody TaskUpdateRequest request) {
        Task task = new Task();
        task.setId(id);
        task.setTaskName(request.getTaskName());
        task.setCronExpression(request.getCronExpression());
        task.setWorkflowId(request.getWorkflowId());

        TaskSyncDetail detail = new TaskSyncDetail();
        detail.setSourceDatasourceId(request.getDatasourceId());
        detail.setSourceTable(request.getSourceTable());
        detail.setTargetDatasourceId(request.getTargetDatasourceId());
        detail.setTargetTable(request.getTargetTable());
        detail.setSyncType(request.getSyncType());
        detail.setTimeField(request.getTimeField());
        detail.setWhereCondition(request.getWhereCondition());

        taskService.updateTask(task, detail);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        taskService.deleteTask(id);
        return Result.success();
    }

    @PostMapping("/{id}/toggle")
    public Result<Void> toggleStatus(@PathVariable Long id) {
        taskService.toggleTaskStatus(id);
        return Result.success();
    }

    @GetMapping("/datasource/{id}/tables")
    public Result<List<String>> listTables(@PathVariable("id") Long datasourceId) {
        return Result.success(syncTaskService.listTables(datasourceId));
    }

    @PostMapping("/{id}/execute")
    public Result<Long> execute(@PathVariable Long id) {
        Long executionId = taskService.executeTask(id, LoginUser.currentUserId());
        return Result.success(executionId);
    }

    @GetMapping("/{id}/logs")
    public Result<Page<SyncTaskLog>> logs(
            @PathVariable Long id,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size) {
        return Result.success(syncTaskLogService.pageLogs(id, page, size));
    }

    @Data
    public static class TaskCreateRequest {
        @NotBlank(message = "任务名称不能为空")
        private String taskName;
        @NotNull(message = "源数据源不能为空")
        private Long datasourceId;
        @NotBlank(message = "来源表不能为空")
        private String sourceTable;
        @NotNull(message = "目标数据源不能为空")
        private Long targetDatasourceId;
        @NotBlank(message = "目标表不能为空")
        private String targetTable;
        @NotBlank(message = "同步类型不能为空")
        private String syncType;
        private String timeField;
        private String cronExpression;
        private Long workflowId;
        private String whereCondition;
    }

    @Data
    public static class TaskUpdateRequest {
        private String taskName;
        private Long datasourceId;
        private String sourceTable;
        private Long targetDatasourceId;
        private String targetTable;
        private String syncType;
        private String timeField;
        private String cronExpression;
        private Long workflowId;
        private String whereCondition;
    }

}
