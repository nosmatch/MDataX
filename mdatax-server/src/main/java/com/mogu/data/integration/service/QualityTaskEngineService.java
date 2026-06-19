package com.mogu.data.integration.service;

import com.mogu.data.integration.entity.Task;
import com.mogu.data.integration.entity.TaskQualityDetail;
import com.mogu.data.integration.mapper.TaskMapper;
import com.mogu.data.integration.mapper.TaskQualityDetailMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 质量监控任务执行引擎。
 *
 * <p>接收统一任务 ID，读取 {@link TaskQualityDetail} 中的规则配置，
 * 调用底层质量检查能力完成校验。当前质量规则模块独立存储在
 * {@code quality_rule} 表，此处作为统一任务调度的执行入口，
 * 实际检查逻辑可委托给 {@code QualityCheckEngineService}。
 *
 * @author fengzhu
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QualityTaskEngineService {

    private final TaskMapper taskMapper;
    private final TaskQualityDetailMapper taskQualityDetailMapper;

    /**
     * 执行质量监控任务。
     *
     * @param taskId 统一任务 ID
     */
    public void execute(Long taskId) {
        Task task = taskMapper.selectById(taskId);
        if (task == null) {
            throw new IllegalArgumentException("任务不存在: " + taskId);
        }

        TaskQualityDetail detail = taskQualityDetailMapper.selectByTaskId(taskId);
        if (detail == null) {
            throw new IllegalArgumentException("质量任务详情不存在: " + taskId);
        }

        log.info("[QualityTaskEngine] 开始执行质量监控任务: taskId={}, taskName={}, ruleTemplate={}, databaseName={}, tableName={}, tableId={}, columnName={}",
                taskId, task.getTaskName(), detail.getRuleTemplate(),
                detail.getDatabaseName(), detail.getTableName(),
                detail.getTableId(), detail.getColumnName());

        if (detail.getTableId() == null) {
            throw new IllegalStateException("质量任务未关联数据表: " + taskId);
        }

        // TODO: 接入实际质量检查引擎 QualityCheckEngineService.check(tableId, "SCHEDULED", "system")
        // 当前仅记录执行日志，质量报告结果待质量模块后端实现后补全。
        log.info("[QualityTaskEngine] 质量监控任务执行完成: taskId={}, ruleTemplate={}",
                taskId, detail.getRuleTemplate());
    }
}
