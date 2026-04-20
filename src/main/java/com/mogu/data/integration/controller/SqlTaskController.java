package com.mogu.data.integration.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mogu.data.common.Result;
import com.mogu.data.integration.entity.SqlTask;
import com.mogu.data.integration.entity.SqlTaskLog;
import com.mogu.data.integration.scheduler.SqlTaskSchedulerManager;
import com.mogu.data.integration.service.SqlTaskEngineService;
import com.mogu.data.integration.service.SqlTaskLogService;
import com.mogu.data.integration.service.SqlTaskService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;

/**
 * SQL任务管理控制器
 *
 * @author fengzhu
 */
@RestController
@RequestMapping("/sql-task")
@RequiredArgsConstructor
public class SqlTaskController {

    private final SqlTaskService sqlTaskService;
    private final SqlTaskEngineService sqlTaskEngineService;
    private final SqlTaskLogService sqlTaskLogService;
    private final SqlTaskSchedulerManager schedulerManager;

    @GetMapping("/page")
    public Result<Page<SqlTask>> page(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) String keyword) {
        return Result.success(sqlTaskService.pageTasks(keyword, page, size));
    }

    @GetMapping("/{id}")
    public Result<SqlTask> getById(@PathVariable Long id) {
        SqlTask task = sqlTaskService.getById(id);
        if (task == null || task.getDeleted() != null && task.getDeleted() == 1) {
            return Result.error("任务不存在");
        }
        return Result.success(task);
    }

    @PostMapping
    public Result<Void> create(@Valid @RequestBody TaskCreateRequest request) {
        SqlTask task = new SqlTask();
        task.setTaskName(request.getTaskName());
        task.setSqlContent(request.getSqlContent());
        task.setTargetTable(request.getTargetTable());
        task.setCronExpression(request.getCronExpression());
        sqlTaskService.createTask(task);
        return Result.success();
    }

    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody TaskUpdateRequest request) {
        SqlTask task = new SqlTask();
        task.setId(id);
        task.setTaskName(request.getTaskName());
        task.setSqlContent(request.getSqlContent());
        task.setTargetTable(request.getTargetTable());
        task.setCronExpression(request.getCronExpression());
        task.setStatus(request.getStatus());
        sqlTaskService.updateTask(task);
        // 重新调度
        SqlTask updated = sqlTaskService.getById(id);
        if (updated.getStatus() != null && updated.getStatus() == 1
                && updated.getCronExpression() != null && !updated.getCronExpression().isEmpty()) {
            schedulerManager.reschedule(updated.getId(), updated.getCronExpression());
        } else {
            schedulerManager.cancel(updated.getId());
        }
        return Result.success();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        schedulerManager.cancel(id);
        sqlTaskService.removeById(id);
        return Result.success();
    }

    @PostMapping("/{id}/toggle")
    public Result<Void> toggleStatus(@PathVariable Long id) {
        SqlTask task = sqlTaskService.toggleStatus(id);
        if (task.getStatus() != null && task.getStatus() == 1
                && task.getCronExpression() != null && !task.getCronExpression().isEmpty()) {
            schedulerManager.schedule(id, task.getCronExpression());
        } else {
            schedulerManager.cancel(id);
        }
        return Result.success();
    }

    @PostMapping("/{id}/execute")
    public Result<Void> execute(@PathVariable Long id) {
        sqlTaskEngineService.execute(id);
        return Result.success();
    }

    @GetMapping("/{id}/logs")
    public Result<Page<SqlTaskLog>> logs(
            @PathVariable Long id,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size) {
        return Result.success(sqlTaskLogService.pageLogs(id, page, size));
    }

    @Data
    public static class TaskCreateRequest {
        @NotBlank(message = "任务名称不能为空")
        private String taskName;
        @NotBlank(message = "SQL内容不能为空")
        private String sqlContent;
        private String targetTable;
        private String cronExpression;
    }

    @Data
    public static class TaskUpdateRequest {
        private String taskName;
        private String sqlContent;
        private String targetTable;
        private String cronExpression;
        private Integer status;
    }

}
