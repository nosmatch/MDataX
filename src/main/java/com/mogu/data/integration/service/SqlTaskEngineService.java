package com.mogu.data.integration.service;

import com.mogu.data.integration.entity.SqlTask;
import com.mogu.data.integration.entity.SqlTaskLog;
import com.mogu.data.integration.mapper.SqlTaskMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * SQL任务执行引擎
 *
 * @author fengzhu
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SqlTaskEngineService {

    @Qualifier("clickHouseJdbcTemplate")
    private final JdbcTemplate jdbcTemplate;
    private final SqlTaskMapper sqlTaskMapper;
    private final SqlTaskLogService sqlTaskLogService;

    /**
     * 执行SQL任务
     */
    public void execute(Long taskId) {
        SqlTask task = sqlTaskMapper.selectById(taskId);
        if (task == null || task.getDeleted() != null && task.getDeleted() == 1) {
            throw new IllegalArgumentException("任务不存在");
        }

        SqlTaskLog taskLog = sqlTaskLogService.startLog(taskId);
        try {
            String sql = task.getSqlContent();
            if (sql == null || sql.trim().isEmpty()) {
                throw new IllegalArgumentException("SQL内容为空");
            }

            long start = System.currentTimeMillis();
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql.trim());
            long executionTime = System.currentTimeMillis() - start;

            int rowCount = rows.size();
            List<String> columns = rows.isEmpty() ? new ArrayList<>() : new ArrayList<>(rows.get(0).keySet());

            String message = String.format("执行成功，返回 %d 行，%d 列，耗时 %d ms", rowCount, columns.size(), executionTime);
            sqlTaskLogService.finishLog(taskLog.getId(), "SUCCESS", message);
            log.info("SQL任务执行成功: taskId={}, 行数={}, 耗时={}ms", taskId, rowCount, executionTime);
        } catch (Exception e) {
            log.error("SQL任务执行失败: taskId={}", taskId, e);
            sqlTaskLogService.finishLog(taskLog.getId(), "FAILED", e.getMessage());
            throw new RuntimeException("SQL任务执行失败: " + e.getMessage(), e);
        }
    }

}
