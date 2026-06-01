package com.mogu.data.integration.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mogu.data.integration.entity.SyncTaskLog;
import com.mogu.data.integration.mapper.SyncTaskLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 同步任务日志服务
 *
 * @author fengzhu
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SyncTaskLogService extends ServiceImpl<SyncTaskLogMapper, SyncTaskLog> {

    public Page<SyncTaskLog> pageLogs(Long taskId, long page, long size) {
        LambdaQueryWrapper<SyncTaskLog> wrapper = new LambdaQueryWrapper<>();
        if (taskId != null) {
            wrapper.eq(SyncTaskLog::getTaskId, taskId);
        }
        wrapper.orderByDesc(SyncTaskLog::getCreateTime);
        return page(new Page<>(page, size), wrapper);
    }

    public SyncTaskLog startLog(Long taskId, Long dsInstanceId) {
        SyncTaskLog log = new SyncTaskLog();
        log.setTaskId(taskId);
        log.setDsInstanceId(dsInstanceId);
        log.setStartTime(LocalDateTime.now());
        log.setStatus("RUNNING");
        save(log);
        return log;
    }

    public void finishLog(Long logId, String status, String message, Long rowCount) {
        SyncTaskLog log = getById(logId);
        if (log == null) return;
        log.setEndTime(LocalDateTime.now());
        log.setStatus(status);
        log.setMessage(message);
        log.setRowCount(rowCount);
        updateById(log);
    }

    public LocalDateTime getLastSuccessTime(Long taskId) {
        SyncTaskLog last = lambdaQuery()
                .eq(SyncTaskLog::getTaskId, taskId)
                .eq(SyncTaskLog::getStatus, "SUCCESS")
                .orderByDesc(SyncTaskLog::getEndTime)
                .last("LIMIT 1")
                .one();
        return last != null ? last.getEndTime() : null;
    }

}
