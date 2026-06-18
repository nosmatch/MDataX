package com.mogu.data.integration.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mogu.data.common.Result;
import com.mogu.data.integration.entity.TaskExecution;
import com.mogu.data.integration.service.TaskExecutionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

/**
 * 任务执行记录控制器
 *
 * @author fengzhu
 */
@Slf4j
@RestController
@RequestMapping("/task-execution")
@RequiredArgsConstructor
public class TaskExecutionController {

    private final TaskExecutionService taskExecutionService;

    /**
     * 分页查询执行记录
     */
    @GetMapping("/page")
    public Result<Page<TaskExecution>> pageExecutions(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) Long taskId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String triggerType,
            @RequestParam(required = false) Long triggerUserId,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime) {
        Page<TaskExecution> result = taskExecutionService.pageExecutions(taskId, status, triggerType,
                triggerUserId, startTime, endTime, page, size);
        return Result.success(result);
    }

    /**
     * 获取执行详情
     */
    @GetMapping("/{executionId}")
    public Result<TaskExecution> getExecutionDetail(@PathVariable String executionId) {
        TaskExecution execution = taskExecutionService.lambdaQuery()
                .eq(TaskExecution::getExecutionId, executionId)
                .one();
        if (execution == null) {
            return Result.error("执行记录不存在");
        }
        return Result.success(execution);
    }

    /**
     * 终止运行中的执行
     */
    @PostMapping("/{executionId}/kill")
    public Result<Void> killExecution(@PathVariable String executionId) {
        taskExecutionService.killExecution(executionId);
        return Result.success();
    }

    /**
     * 重试失败的执行
     */
    @PostMapping("/{executionId}/retry")
    public Result<String> retryExecution(@PathVariable String executionId) {
        String newExecutionId = taskExecutionService.retryExecution(executionId);
        return Result.success(newExecutionId);
    }
}
