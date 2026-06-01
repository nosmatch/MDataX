package com.mogu.data.dashboard.controller;

import com.mogu.data.common.LoginUser;
import com.mogu.data.common.Result;
import com.mogu.data.dashboard.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 首页工作台控制器
 *
 * @author fengzhu
 */
@RestController
@RequestMapping("/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping("/stats")
    public Result<DashboardService.StatsVO> stats() {
        return Result.success(dashboardService.getStats());
    }

    @GetMapping("/recent-tasks")
    public Result<List<DashboardService.RecentTaskVO>> recentTasks() {
        return Result.success(dashboardService.getRecentTasks(10));
    }

    @GetMapping("/recent-visits")
    public Result<List<DashboardService.RecentVisitVO>> recentVisits() {
        Long userId = LoginUser.currentUserId();
        return Result.success(dashboardService.getRecentVisits(userId, 10));
    }

}
