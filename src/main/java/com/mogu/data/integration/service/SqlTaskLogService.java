package com.mogu.data.integration.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mogu.data.integration.entity.SqlTaskLog;
import com.mogu.data.integration.mapper.SqlTaskLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * SQL任务日志服务
 *
 * @author fengzhu
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SqlTaskLogService extends ServiceImpl<SqlTaskLogMapper, SqlTaskLog> {

    public Page<SqlTaskLog> pageLogs(Long taskId, long page, long size) {
        LambdaQueryWrapper<SqlTaskLog> wrapper = new LambdaQueryWrapper<>();
        if (taskId != null) {
            wrapper.eq(SqlTaskLog::getTaskId, taskId);
        }
        wrapper.orderByDesc(SqlTaskLog::getCreateTime);
        return page(new Page<>(page, size), wrapper);
    }

    public SqlTaskLog startLog(Long taskId) {
        SqlTaskLog log = new SqlTaskLog();
        log.setTaskId(taskId);
        log.setStartTime(LocalDateTime.now());
        log.setStatus("RUNNING");
        save(log);
        return log;
    }

    public void finishLog(Long logId, String status, String message) {
        SqlTaskLog log = getById(logId);
        if (log == null) return;
        log.setEndTime(LocalDateTime.now());
        log.setStatus(status);
        log.setMessage(message);
        updateById(log);
    }

}
