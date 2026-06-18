package com.mogu.data.integration.controller;

import com.mogu.data.common.Result;
import com.mogu.data.integration.service.TaskExecutionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 任务执行看板控制器
 *
 * @author fengzhu
 */
@Slf4j
@RestController
@RequestMapping("/task-execution-board")
@RequiredArgsConstructor
public class TaskExecutionBoardController {

    private final TaskExecutionService taskExecutionService;

    /**
     * 获取今日执行统计
     */
    @GetMapping("/today-statistics")
    public Result<Map<String, Object>> getTodayStatistics(
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime) {
        Map<String, Object> statistics = taskExecutionService.getStatistics(startTime, endTime);
        return Result.success(statistics);
    }
}
