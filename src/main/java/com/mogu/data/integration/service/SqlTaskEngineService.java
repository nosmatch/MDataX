package com.mogu.data.integration.service;

import com.mogu.data.integration.entity.SqlTask;
import com.mogu.data.integration.entity.SqlTaskLog;
import com.mogu.data.integration.mapper.SqlTaskMapper;
import com.mogu.data.query.service.ClickHouseQueryService;
import com.mogu.data.query.vo.ExecuteOptions;
import com.mogu.data.query.vo.QueryResultVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * SQL 任务执行引擎
 *
 * <p>
 * 复用 {@link ClickHouseQueryService} 统一执行引擎，支持 SELECT 和 DML/DDL。
 *
 * @author fengzhu
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SqlTaskEngineService {

    private final ClickHouseQueryService clickHouseQueryService;
    private final SqlTaskMapper sqlTaskMapper;
    private final SqlTaskLogService sqlTaskLogService;

    /**
     * 执行 SQL 任务
     */
    public void execute(Long taskId) {
        execute(taskId, null);
    }

    public void execute(Long taskId, Long dsInstanceId) {
        SqlTask task = sqlTaskMapper.selectById(taskId);
        if (task == null || task.getDeleted() != null && task.getDeleted() == 1) {
            throw new IllegalArgumentException("任务不存在");
        }

        SqlTaskLog taskLog = sqlTaskLogService.startLog(taskId, dsInstanceId);
        try {
            String sql = task.getSqlContent();
            if (sql == null || sql.trim().isEmpty()) {
                throw new IllegalArgumentException("SQL 内容为空");
            }

            // 任务模式：不限制返回条数，不限制超时（或放宽），允许写入
            ExecuteOptions options = ExecuteOptions.builder()
                    .readonly(false)
                    .maxRows(0)
                    .timeoutSeconds(30)
                    .targetTable(task.getTargetTable())
                    .build();

            QueryResultVO result = clickHouseQueryService.execute(sql.trim(), options);

            String message = String.format("执行成功，影响/返回 %d 行，耗时 %d ms",
                    result.getRowCount(), result.getExecutionTime());
            sqlTaskLogService.finishLog(taskLog.getId(), "SUCCESS", message);
            log.info("SQL 任务执行成功: taskId={}, 行数={}, 耗时={}ms",
                    taskId, result.getRowCount(), result.getExecutionTime());
        } catch (Exception e) {
            log.error("SQL 任务执行失败: taskId={}", taskId, e);
            sqlTaskLogService.finishLog(taskLog.getId(), "FAILED", e.getMessage());
            throw new RuntimeException("SQL 任务执行失败: " + e.getMessage(), e);
        }
    }

}
