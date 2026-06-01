package com.mogu.data.integration.engine;

import com.mogu.data.integration.service.SqlTaskEngineService;
import com.mogu.data.integration.service.SyncEngineService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 本地任务执行引擎实现。
 *
 * <p>在本地 JVM 中直接调用底层同步引擎和 SQL 引擎执行任务。
 * 所有执行逻辑（重试、依赖触发、日志记录）均在此层统一编排。
 *
 * @author fengzhu
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LocalTaskEngine implements TaskEngine {

    private final SyncEngineService syncEngineService;
    private final SqlTaskEngineService sqlTaskEngineService;

    @Override
    public void executeSyncTask(Long taskId, Long dsInstanceId) {
        log.info("[LocalTaskEngine] 开始执行同步任务: taskId={}, dsInstanceId={}", taskId, dsInstanceId);
        syncEngineService.execute(taskId, dsInstanceId);
        log.info("[LocalTaskEngine] 同步任务执行完成: taskId={}", taskId);
    }

    @Override
    public void executeSqlTask(Long taskId, Long dsInstanceId) {
        log.info("[LocalTaskEngine] 开始执行 SQL 任务: taskId={}, dsInstanceId={}", taskId, dsInstanceId);
        sqlTaskEngineService.execute(taskId, dsInstanceId);
        log.info("[LocalTaskEngine] SQL 任务执行完成: taskId={}", taskId);
    }
}
