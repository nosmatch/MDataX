package com.mogu.data.integration.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mogu.data.common.LoginUser;
import com.mogu.data.common.Result;
import com.mogu.data.integration.entity.SqlTask;
import com.mogu.data.integration.entity.SqlTaskLog;
import com.mogu.data.integration.scheduler.SqlTaskSchedulerManager;
import com.mogu.data.integration.service.SqlTaskEngineService;
import com.mogu.data.integration.service.SqlTaskLogService;
import com.mogu.data.integration.service.SqlTaskService;
import com.mogu.data.metadata.entity.MetadataColumn;
import com.mogu.data.metadata.entity.MetadataTable;
import com.mogu.data.metadata.service.MetadataTableService;
import com.mogu.data.query.vo.TableInfoVO;
import com.mogu.data.system.service.PermissionService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

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
    private final PermissionService permissionService;
    private final MetadataTableService metadataTableService;

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

    /**
     * 获取当前用户有写权限的表列表（含字段）
     */
    @GetMapping("/tables")
    public Result<List<TableInfoVO>> getWritableTables() {
        Long userId = LoginUser.currentUserId();
        if (userId == null) {
            return Result.success(Collections.emptyList());
        }

        Set<String> writable = permissionService.getWritableTables(userId);
        if (writable == null || writable.isEmpty()) {
            return Result.success(Collections.emptyList());
        }

        List<MetadataTable> all = metadataTableService.listAllTables();
        if (all == null || all.isEmpty()) {
            return Result.success(Collections.emptyList());
        }

        List<TableInfoVO> voList = all.stream()
                .filter(t -> writable.contains("*")
                        || writable.contains(t.getDatabaseName() + "." + t.getTableName()))
                .map(t -> {
                    TableInfoVO vo = new TableInfoVO();
                    vo.setId(t.getId());
                    vo.setDatabaseName(t.getDatabaseName());
                    vo.setTableName(t.getTableName());
                    vo.setTableComment(t.getTableComment());
                    List<MetadataColumn> columns = metadataTableService.getColumns(t.getId());
                    vo.setColumns(columns);
                    return vo;
                })
                .collect(Collectors.toList());

        return Result.success(voList);
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
