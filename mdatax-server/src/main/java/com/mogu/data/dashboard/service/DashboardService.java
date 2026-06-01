package com.mogu.data.dashboard.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mogu.data.integration.entity.SqlTask;
import com.mogu.data.integration.entity.SqlTaskLog;
import com.mogu.data.integration.entity.SyncTask;
import com.mogu.data.integration.entity.SyncTaskLog;
import com.mogu.data.integration.mapper.SqlTaskLogMapper;
import com.mogu.data.integration.mapper.SqlTaskMapper;
import com.mogu.data.integration.mapper.SyncTaskLogMapper;
import com.mogu.data.integration.mapper.SyncTaskMapper;
import com.mogu.data.metadata.entity.MetadataTable;
import com.mogu.data.metadata.entity.UserTableVisit;
import com.mogu.data.metadata.mapper.MetadataTableMapper;
import com.mogu.data.metadata.service.UserTableVisitService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 首页工作台统计服务
 *
 * @author fengzhu
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DashboardService {

    private final MetadataTableMapper metadataTableMapper;
    private final SyncTaskLogMapper syncTaskLogMapper;
    private final SqlTaskLogMapper sqlTaskLogMapper;
    private final SyncTaskMapper syncTaskMapper;
    private final SqlTaskMapper sqlTaskMapper;
    private final UserTableVisitService userTableVisitService;

    /**
     * 获取统计数据
     */
    public StatsVO getStats() {
        StatsVO vo = new StatsVO();

        // 表数量
        vo.setTableCount((long) metadataTableMapper.selectCount(
                new LambdaQueryWrapper<MetadataTable>().eq(MetadataTable::getDeleted, 0)));

        // 今日起始时间
        LocalDateTime todayStart = LocalDateTime.of(LocalDate.now(), LocalTime.MIN);

        // 今日同步任务
        long syncTotalToday = syncTaskLogMapper.selectCount(
                new LambdaQueryWrapper<SyncTaskLog>().ge(SyncTaskLog::getCreateTime, todayStart));
        long syncSuccessToday = syncTaskLogMapper.selectCount(
                new LambdaQueryWrapper<SyncTaskLog>()
                        .ge(SyncTaskLog::getCreateTime, todayStart)
                        .eq(SyncTaskLog::getStatus, "SUCCESS"));
        vo.setSyncTaskCountToday(syncTotalToday);
        vo.setSyncSuccessRate(syncTotalToday > 0 ? (int) Math.round((double) syncSuccessToday / syncTotalToday * 100) : 100);

        // 今日SQL任务
        long sqlTotalToday = sqlTaskLogMapper.selectCount(
                new LambdaQueryWrapper<SqlTaskLog>().ge(SqlTaskLog::getCreateTime, todayStart));
        long sqlSuccessToday = sqlTaskLogMapper.selectCount(
                new LambdaQueryWrapper<SqlTaskLog>()
                        .ge(SqlTaskLog::getCreateTime, todayStart)
                        .eq(SqlTaskLog::getStatus, "SUCCESS"));
        vo.setSqlTaskCountToday(sqlTotalToday);
        vo.setSqlSuccessRate(sqlTotalToday > 0 ? (int) Math.round((double) sqlSuccessToday / sqlTotalToday * 100) : 100);

        return vo;
    }

    /**
     * 获取最近运行的任务
     */
    public List<RecentTaskVO> getRecentTasks(int limit) {
        List<RecentTaskVO> result = new ArrayList<>();

        // 同步任务日志
        List<SyncTaskLog> syncLogs = syncTaskLogMapper.selectList(
                new LambdaQueryWrapper<SyncTaskLog>()
                        .orderByDesc(SyncTaskLog::getCreateTime)
                        .last("LIMIT " + limit));
        for (SyncTaskLog log : syncLogs) {
            RecentTaskVO vo = new RecentTaskVO();
            String taskName = getSyncTaskName(log.getTaskId());
            vo.setName(taskName != null ? taskName : "同步任务 #" + log.getTaskId());
            vo.setType("同步");
            vo.setStatus(log.getStatus());
            vo.setTime(log.getEndTime() != null ? log.getEndTime() : log.getStartTime());
            result.add(vo);
        }

        // SQL任务日志
        List<SqlTaskLog> sqlLogs = sqlTaskLogMapper.selectList(
                new LambdaQueryWrapper<SqlTaskLog>()
                        .orderByDesc(SqlTaskLog::getCreateTime)
                        .last("LIMIT " + limit));
        for (SqlTaskLog log : sqlLogs) {
            RecentTaskVO vo = new RecentTaskVO();
            String taskName = getSqlTaskName(log.getTaskId());
            vo.setName(taskName != null ? taskName : "SQL任务 #" + log.getTaskId());
            vo.setType("SQL");
            vo.setStatus(log.getStatus());
            vo.setTime(log.getEndTime() != null ? log.getEndTime() : log.getStartTime());
            result.add(vo);
        }

        // 按时间排序，取前 limit
        result.sort((a, b) -> {
            if (a.getTime() == null) return 1;
            if (b.getTime() == null) return -1;
            return b.getTime().compareTo(a.getTime());
        });
        if (result.size() > limit) {
            return result.subList(0, limit);
        }
        return result;
    }

    /**
     * 查询同步任务名称
     */
    private String getSyncTaskName(Long taskId) {
        if (taskId == null) return null;
        SyncTask task = syncTaskMapper.selectById(taskId);
        return task != null ? task.getTaskName() : null;
    }

    /**
     * 查询 SQL 任务名称
     */
    private String getSqlTaskName(Long taskId) {
        if (taskId == null) return null;
        SqlTask task = sqlTaskMapper.selectById(taskId);
        return task != null ? task.getTaskName() : null;
    }

    /**
     * 获取最近访问的数据表
     */
    public List<RecentVisitVO> getRecentVisits(Long userId, int limit) {
        List<UserTableVisit> visits = userTableVisitService.listRecentVisits(userId, limit);
        List<RecentVisitVO> result = new ArrayList<>();
        for (UserTableVisit v : visits) {
            RecentVisitVO vo = new RecentVisitVO();
            vo.setTableName(v.getDatabaseName() + "." + v.getTableName());
            vo.setVisitCount(v.getVisitCount());
            vo.setLastVisitTime(v.getLastVisitTime());
            result.add(vo);
        }
        return result;
    }

    @Data
    public static class StatsVO {
        private Long tableCount;
        private Long syncTaskCountToday;
        private Long sqlTaskCountToday;
        private Integer syncSuccessRate;
        private Integer sqlSuccessRate;
    }

    @Data
    public static class RecentTaskVO {
        private String name;
        private String type;
        private String status;
        private LocalDateTime time;
    }

    @Data
    public static class RecentVisitVO {
        private String tableName;
        private Integer visitCount;
        private LocalDateTime lastVisitTime;
    }

}
