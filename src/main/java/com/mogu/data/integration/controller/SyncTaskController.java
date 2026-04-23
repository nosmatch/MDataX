package com.mogu.data.integration.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mogu.data.common.Result;
import com.mogu.data.integration.entity.SyncTask;
import com.mogu.data.integration.entity.SyncTaskLog;
import com.mogu.data.integration.scheduler.TaskSchedulerManager;
import com.mogu.data.integration.service.SyncEngineService;
import com.mogu.data.integration.service.SyncTaskLogService;
import com.mogu.data.integration.service.SyncTaskService;
import com.mogu.data.integration.vo.SyncTaskVO;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.util.List;

/**
 * 同步任务管理控制器
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
    private final TaskSchedulerManager schedulerManager;

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
    public Result<Void> create(@Valid @RequestBody TaskCreateRequest request) {
        SyncTask task = new SyncTask();
        task.setTaskName(request.getTaskName());
        task.setDatasourceId(request.getDatasourceId());
        task.setSourceTable(request.getSourceTable());
        task.setTargetTable(request.getTargetTable());
        task.setSyncType(request.getSyncType());
        task.setTimeField(request.getTimeField());
        task.setCronExpression(request.getCronExpression());
        task.setWorkflowId(request.getWorkflowId());
        syncTaskService.createTask(task);
        return Result.success();
    }

    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody TaskUpdateRequest request) {
        SyncTask task = new SyncTask();
        task.setId(id);
        task.setTaskName(request.getTaskName());
        task.setDatasourceId(request.getDatasourceId());
        task.setSourceTable(request.getSourceTable());
        task.setTargetTable(request.getTargetTable());
        task.setSyncType(request.getSyncType());
        task.setTimeField(request.getTimeField());
        task.setCronExpression(request.getCronExpression());
        task.setWorkflowId(request.getWorkflowId());
        syncTaskService.updateTask(task);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        syncTaskService.deleteTask(id);
        return Result.success();
    }

    @PostMapping("/{id}/toggle")
    public Result<Void> toggleStatus(@PathVariable Long id) {
        SyncTask task = syncTaskService.toggleStatus(id);
        if (task.getStatus() != null && task.getStatus() == 1
                && task.getCronExpression() != null && !task.getCronExpression().isEmpty()) {
            schedulerManager.scheduleSyncTask(task);
        } else {
            schedulerManager.cancelSyncTask(id);
        }
        return Result.success();
    }

    @GetMapping("/datasource/{id}/tables")
    public Result<List<String>> listTables(@PathVariable("id") Long datasourceId) {
        return Result.success(syncTaskService.listTables(datasourceId));
    }

    @PostMapping("/{id}/execute")
    public Result<Void> execute(@PathVariable Long id) {
        SyncTask task = syncTaskService.getById(id);
        if (task == null || task.getStatus() == null || task.getStatus() != 1) {
            return Result.error("任务已停用，无法执行");
        }
        syncEngineService.execute(id, null);
        return Result.success();
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
        @NotNull(message = "数据源不能为空")
        private Long datasourceId;
        @NotBlank(message = "来源表不能为空")
        private String sourceTable;
        @NotBlank(message = "目标表不能为空")
        private String targetTable;
        @NotBlank(message = "同步类型不能为空")
        private String syncType;
        private String timeField;
        private String cronExpression;
        private Long workflowId;
    }

    @Data
    public static class TaskUpdateRequest {
        private String taskName;
        private Long datasourceId;
        private String sourceTable;
        private String targetTable;
        private String syncType;
        private String timeField;
        private String cronExpression;
        private Long workflowId;
    }

}
