package com.mogu.data.integration.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mogu.data.common.Result;
import com.mogu.data.integration.entity.Task;
import com.mogu.data.integration.entity.TaskDependency;
import com.mogu.data.integration.entity.TaskSqlDetail;
import com.mogu.data.integration.entity.TaskSyncDetail;
import com.mogu.data.integration.service.TaskService;
import com.mogu.data.integration.service.TaskDependencyService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.time.LocalDateTime;

/**
 * 统一任务管理控制器
 *
 * @author fengzhu
 */
@Slf4j
@RestController
@RequestMapping("/task")
@RequiredArgsConstructor
public class TaskController {

    private final TaskService taskService;
    private final TaskDependencyService taskDependencyService;
    private final com.mogu.data.integration.mapper.TaskSqlDetailMapper taskSqlDetailMapper;
    private final com.mogu.data.integration.mapper.TaskSyncDetailMapper taskSyncDetailMapper;
    private final com.mogu.data.integration.service.TaskExecutionService taskExecutionService;

    // ==================== 任务 CRUD ====================

    /**
     * 分页查询任务列表
     */
    @GetMapping("/page")
    public Result<Page<Task>> pageTasks(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) String taskType,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) Long ownerUserId,
            @RequestParam(required = false) Integer priority,
            @RequestParam(required = false) String tags,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime,
            @RequestParam(required = false) String lastExecutionStatus) {
        Page<Task> result = taskService.pageTasks(keyword, taskType, status, ownerUserId,
                priority, tags, startTime, endTime, lastExecutionStatus, page, size);
        return Result.success(result);
    }

    /**
     * 创建任务
     */
    @PostMapping
    public Result<Long> createTask(@Valid @RequestBody TaskCreateRequest request) {
        Task task = new Task();
        task.setTaskName(request.getTaskName());
        task.setTaskType(request.getTaskType());
        task.setDescription(request.getDescription());
        task.setOwnerUserId(request.getOwnerUserId());
        task.setOwnerUserName(request.getOwnerUserName());
        task.setCreateUserId(request.getCreateUserId());
        task.setPriority(request.getPriority());
        task.setTags(request.getTags());
        task.setCronExpression(request.getCronExpression());
        task.setStatus(request.getStatus());
        task.setRetryTimes(request.getRetryTimes());
        task.setRetryInterval(request.getRetryInterval());
        task.setTimeoutSeconds(request.getTimeoutSeconds());

        Object detail = null;
        if ("SQL".equals(request.getTaskType())) {
            TaskSqlDetail sqlDetail = new TaskSqlDetail();
            sqlDetail.setSqlContent(request.getSqlContent());
            sqlDetail.setTargetDatasourceId(request.getTargetDatasourceId());
            detail = sqlDetail;
        } else if ("SYNC".equals(request.getTaskType())) {
            TaskSyncDetail syncDetail = new TaskSyncDetail();
            syncDetail.setSourceDatasourceId(request.getSourceDatasourceId());
            syncDetail.setSourceTable(request.getSourceTable());
            syncDetail.setTargetDatasourceId(request.getTargetDatasourceIdForSync());
            syncDetail.setTargetTable(request.getTargetTable());
            syncDetail.setSyncType(request.getSyncType());
            syncDetail.setTimeField(request.getTimeField());
            syncDetail.setWhereCondition(request.getWhereCondition());
            detail = syncDetail;
        }

        Long taskId = taskService.createTask(task, detail);
        return Result.success(taskId);
    }

    /**
     * 更新任务
     */
    @PutMapping("/{id}")
    public Result<Void> updateTask(@PathVariable Long id, @Valid @RequestBody TaskUpdateRequest request) {
        Task task = new Task();
        task.setId(id);
        task.setTaskName(request.getTaskName());
        task.setDescription(request.getDescription());
        task.setOwnerUserId(request.getOwnerUserId());
        task.setOwnerUserName(request.getOwnerUserName());
        task.setPriority(request.getPriority());
        task.setTags(request.getTags());
        task.setCronExpression(request.getCronExpression());
        task.setRetryTimes(request.getRetryTimes());
        task.setRetryInterval(request.getRetryInterval());
        task.setTimeoutSeconds(request.getTimeoutSeconds());

        Object detail = null;
        // 根据任务类型判断详情类型，先查询原任务类型
        Task existingTask = taskService.getById(id);
        if (existingTask == null) {
            return Result.error("任务不存在");
        }

        if ("SQL".equals(existingTask.getTaskType())) {
            TaskSqlDetail sqlDetail = new TaskSqlDetail();
            sqlDetail.setSqlContent(request.getSqlContent());
            sqlDetail.setTargetDatasourceId(request.getTargetDatasourceId());
            detail = sqlDetail;
        } else if ("SYNC".equals(existingTask.getTaskType())) {
            TaskSyncDetail syncDetail = new TaskSyncDetail();
            syncDetail.setSourceDatasourceId(request.getSourceDatasourceId());
            syncDetail.setSourceTable(request.getSourceTable());
            syncDetail.setTargetDatasourceId(request.getTargetDatasourceIdForSync());
            syncDetail.setTargetTable(request.getTargetTable());
            syncDetail.setSyncType(request.getSyncType());
            syncDetail.setTimeField(request.getTimeField());
            syncDetail.setWhereCondition(request.getWhereCondition());
            detail = syncDetail;
        }

        taskService.updateTask(task, detail);
        return Result.success();
    }

    /**
     * 删除任务
     */
    @DeleteMapping("/{id}")
    public Result<Void> deleteTask(@PathVariable Long id) {
        taskService.deleteTask(id);
        return Result.success();
    }

    /**
     * 切换任务状态（启用/停用）
     */
    @PostMapping("/{id}/toggle")
    public Result<Void> toggleTask(@PathVariable Long id) {
        taskService.toggleTaskStatus(id);
        return Result.success();
    }

    // ==================== 任务详情 ====================

    /**
     * 获取任务详情
     */
    @GetMapping("/{id}")
    public Result<TaskDetailVO> getTaskDetail(@PathVariable Long id) {
        Task task = taskService.getById(id);
        if (task == null) {
            return Result.error("任务不存在");
        }

        TaskDetailVO vo = new TaskDetailVO();
        vo.setTask(task);

        // 加载详情
        if ("SQL".equals(task.getTaskType())) {
            com.mogu.data.integration.entity.TaskSqlDetail sqlDetail =
                    taskSqlDetailMapper.selectByTaskId(id);
            vo.setDetail(sqlDetail);
        } else if ("SYNC".equals(task.getTaskType())) {
            com.mogu.data.integration.entity.TaskSyncDetail syncDetail =
                    taskSyncDetailMapper.selectByTaskId(id);
            vo.setDetail(syncDetail);
        }

        return Result.success(vo);
    }

    /**
     * 获取任务执行统计
     */
    @GetMapping("/{id}/statistics")
    public Result<TaskStatisticsVO> getTaskStatistics(@PathVariable Long id) {
        Task task = taskService.getById(id);
        if (task == null) {
            return Result.error("任务不存在");
        }

        TaskStatisticsVO vo = new TaskStatisticsVO();

        // 总执行次数
        long totalExecutions = taskExecutionService.lambdaQuery()
                .eq(com.mogu.data.integration.entity.TaskExecution::getTaskId, id)
                .count();
        vo.setTotalExecutions(totalExecutions);

        // 成功次数
        long successCount = taskExecutionService.lambdaQuery()
                .eq(com.mogu.data.integration.entity.TaskExecution::getTaskId, id)
                .eq(com.mogu.data.integration.entity.TaskExecution::getStatus, "SUCCESS")
                .count();
        vo.setSuccessCount(successCount);

        // 失败次数
        long failedCount = taskExecutionService.lambdaQuery()
                .eq(com.mogu.data.integration.entity.TaskExecution::getTaskId, id)
                .eq(com.mogu.data.integration.entity.TaskExecution::getStatus, "FAILED")
                .count();
        vo.setFailedCount(failedCount);

        // 成功率
        if (totalExecutions > 0) {
            vo.setSuccessRate(java.math.BigDecimal.valueOf(successCount * 100.0 / totalExecutions)
                    .setScale(2, java.math.RoundingMode.HALF_UP));
        } else {
            vo.setSuccessRate(java.math.BigDecimal.ZERO);
        }

        // 平均耗时
        Long avgDurationMs = taskExecutionService.getBaseMapper().selectAvgDurationMsByTaskId(id);
        if (avgDurationMs != null && avgDurationMs > 0) {
            vo.setAvgDurationMs(avgDurationMs);
        }

        // 最近执行时间
        com.mogu.data.integration.entity.TaskExecution recentExecution = taskExecutionService.lambdaQuery()
                .eq(com.mogu.data.integration.entity.TaskExecution::getTaskId, id)
                .orderByDesc(com.mogu.data.integration.entity.TaskExecution::getStartTime)
                .last("LIMIT 1")
                .one();
        if (recentExecution != null) {
            vo.setLastExecutionTime(recentExecution.getStartTime());
            vo.setLastExecutionStatus(recentExecution.getStatus());
        }

        return Result.success(vo);
    }

    // ==================== 手动执行 ====================

    /**
     * 手动触发任务执行
     */
    @PostMapping("/{id}/execute")
    public Result<String> executeTask(
            @PathVariable Long id,
            @RequestBody(required = false) ManualExecuteRequest request) {
        Long triggerUserId = request != null ? request.getTriggerUserId() : null;
        String executionId = taskService.executeTask(id, triggerUserId);
        return Result.success(executionId);
    }

    // ==================== 依赖管理 ====================

    /**
     * 获取任务DAG数据（仅当前任务上下游一层）
     */
    @GetMapping("/{id}/dag")
    public Result<TaskDependencyService.TaskDagVO> getTaskDag(@PathVariable Long id) {
        TaskDependencyService.TaskDagVO vo = taskDependencyService.getTaskDag(id);
        return Result.success(vo);
    }

    /**
     * 获取任务的依赖关系
     */
    @GetMapping("/{id}/dependencies")
    public Result<TaskDependencyService.TaskDependencyVO> getTaskDependencies(@PathVariable Long id) {
        TaskDependencyService.TaskDependencyVO vo = taskDependencyService.getTaskDependencies(id);
        return Result.success(vo);
    }

    /**
     * 添加上游依赖
     */
    @PostMapping("/{id}/dependencies")
    public Result<Void> addDependency(
            @PathVariable Long id,  // 下游任务ID
            @Valid @RequestBody AddDependencyRequest request) {
        TaskDependencyService.AddDependencyRequest req = new TaskDependencyService.AddDependencyRequest();
        req.setUpstreamTaskId(request.getUpstreamTaskId());
        req.setDependencyType(request.getDependencyType());
        req.setConditionExpression(request.getConditionExpression());
        req.setDelaySeconds(request.getDelaySeconds());
        req.setCreateUserId(request.getCreateUserId());

        taskDependencyService.addDependency(id, req);
        return Result.success();
    }

    /**
     * 删除依赖关系
     */
    @DeleteMapping("/{id}/dependencies/{dependencyId}")
    public Result<Void> removeDependency(@PathVariable Long id, @PathVariable Long dependencyId) {
        taskDependencyService.removeDependency(dependencyId);
        return Result.success();
    }

    /**
     * 搜索可添加依赖的任务
     */
    @GetMapping("/search-for-dependency")
    public Result<Page<Task>> searchTasksForDependency(
            @RequestParam Long currentTaskId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String taskType,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size) {

        // 构建查询条件
        com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Task> wrapper =
            new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<>();

        // 排除当前任务
        wrapper.ne(Task::getId, currentTaskId);

        // 排除已删除的任务
        wrapper.eq(Task::getDeleted, 0);

        // 关键词搜索（任务名称、任务编码）
        if (org.springframework.util.StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(Task::getTaskName, keyword)
                    .or().like(Task::getTaskCode, keyword));
        }

        // 任务类型筛选
        if (org.springframework.util.StringUtils.hasText(taskType)) {
            wrapper.eq(Task::getTaskType, taskType);
        }

        // 按创建时间倒序排列
        wrapper.orderByDesc(Task::getCreateTime);

        // 分页查询
        Page<Task> pageResult = new Page<>(page, size);
        Page<Task> result = taskService.page(pageResult, wrapper);

        return Result.success(result);
    }

    // ==================== VO ====================

    @Data
    public static class TaskDetailVO {
        private Task task;
        private Object detail;  // TaskSqlDetail 或 TaskSyncDetail
    }

    @Data
    public static class TaskCreateRequest {
        @NotBlank(message = "任务名称不能为空")
        private String taskName;

        @NotBlank(message = "任务类型不能为空")
        private String taskType;

        private String description;

        @NotNull(message = "责任人不能为空")
        private Long ownerUserId;

        private String ownerUserName;

        @NotNull(message = "创建人不能为空")
        private Long createUserId;

        private Integer priority = 5;

        private String tags;

        private String cronExpression;

        private Integer status = 2;  // 默认草稿

        private Integer retryTimes = 0;

        private Integer retryInterval = 0;

        private Integer timeoutSeconds = 0;

        // SQL任务字段
        private String sqlContent;
        private Long targetDatasourceId;

        // 同步任务字段
        private Long sourceDatasourceId;
        private String sourceTable;
        private Long targetDatasourceIdForSync;  // 同步任务的目标数据源
        private String targetTable;
        private String syncType;
        private String timeField;
        private String whereCondition;
    }

    @Data
    public static class TaskUpdateRequest {
        private String taskName;
        private String description;
        private Long ownerUserId;
        private String ownerUserName;
        private Integer priority;
        private String tags;
        private String cronExpression;
        private Integer retryTimes;
        private Integer retryInterval;
        private Integer timeoutSeconds;

        // SQL任务字段
        private String sqlContent;
        private Long targetDatasourceId;

        // 同步任务字段
        private Long sourceDatasourceId;
        private String sourceTable;
        private Long targetDatasourceIdForSync;
        private String targetTable;
        private String syncType;
        private String timeField;
        private String whereCondition;
    }

    @Data
    public static class ManualExecuteRequest {
        private Long triggerUserId;
    }

    @Data
    public static class AddDependencyRequest {
        @NotNull(message = "上游任务不能为空")
        private Long upstreamTaskId;

        private String dependencyType = "SUCCESS";

        private String conditionExpression;

        private Integer delaySeconds = 0;

        @NotNull(message = "创建人不能为空")
        private Long createUserId;
    }

    @Data
    public static class TaskStatisticsVO {
        private Long totalExecutions;      // 总执行次数
        private Long successCount;          // 成功次数
        private Long failedCount;           // 失败次数
        private java.math.BigDecimal successRate;  // 成功率（百分比）
        private Long avgDurationMs;        // 平均耗时（毫秒）
        private java.time.LocalDateTime lastExecutionTime;  // 最近执行时间
        private String lastExecutionStatus; // 最近执行状态
    }
}
