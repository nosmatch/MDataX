package com.mogu.data.integration.dolphinscheduler;

import com.mogu.data.integration.entity.SqlTask;
import com.mogu.data.integration.entity.SyncTask;
import com.mogu.data.integration.mapper.SqlTaskMapper;
import com.mogu.data.integration.mapper.SyncTaskMapper;
import com.mogu.data.integration.scheduler.TaskSchedulerManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * DolphinScheduler 调度管理器实现。
 *
 * <p>将 MDataX 的任务注册到 DS，由 DS 负责定时触发。
 * 每个任务对应 DS 中的一个单节点工作流 + 一个定时调度。
 *
 * <p>工作流命名规则: {TASK_TYPE}-{TASK_NAME}，如 {@code SQL-ceshi1}、{@code SYNC-订单同步}。
 *
 * @author fengzhu
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "scheduler.type", havingValue = "dolphinscheduler")
public class DolphinSchedulerManager implements TaskSchedulerManager {

    private final DolphinSchedulerClient dsClient;
    private final DolphinSchedulerProperties props;
    private final SqlTaskMapper sqlTaskMapper;
    private final SyncTaskMapper syncTaskMapper;

    @Override
    public void scheduleSyncTask(SyncTask task) {
        doSchedule(task.getId(), task.getTaskName(), "SYNC",
                task.getCronExpression(), task.getDsProcessCode(), task.getDsScheduleId(),
                (code) -> task.setDsProcessCode(code),
                (id) -> task.setDsScheduleId(id));
    }

    @Override
    public void scheduleSqlTask(SqlTask task) {
        doSchedule(task.getId(), task.getTaskName(), "SQL",
                task.getCronExpression(), task.getDsProcessCode(), task.getDsScheduleId(),
                (code) -> task.setDsProcessCode(code),
                (id) -> task.setDsScheduleId(id));
    }

    @Override
    public void cancelSyncTask(Long taskId) {
        SyncTask task = syncTaskMapper.selectById(taskId);
        if (task != null && task.getDsProcessCode() != null) {
            cancelByTask(task.getDsProcessCode(), task.getDsScheduleId());
        }
    }

    @Override
    public void cancelSqlTask(Long taskId) {
        SqlTask task = sqlTaskMapper.selectById(taskId);
        if (task != null && task.getDsProcessCode() != null) {
            cancelByTask(task.getDsProcessCode(), task.getDsScheduleId());
        }
    }

    @Override
    public void deleteSyncTask(Long taskId) {
        SyncTask task = syncTaskMapper.selectById(taskId);
        if (task != null && task.getDsProcessCode() != null) {
            deleteByTask(taskId, task.getDsProcessCode(), task.getDsScheduleId());
        }
    }

    @Override
    public void deleteSqlTask(Long taskId) {
        SqlTask task = sqlTaskMapper.selectById(taskId);
        if (task != null && task.getDsProcessCode() != null) {
            deleteByTask(taskId, task.getDsProcessCode(), task.getDsScheduleId());
        }
    }

    @Override
    public void rescheduleSyncTask(SyncTask task) {
        cancelByTask(task.getDsProcessCode(), task.getDsScheduleId());
        scheduleSyncTask(task);
    }

    @Override
    public void rescheduleSqlTask(SqlTask task) {
        cancelByTask(task.getDsProcessCode(), task.getDsScheduleId());
        scheduleSqlTask(task);
    }

    // ==================== 通用调度逻辑 ====================

    private void doSchedule(Long taskId, String taskName, String taskType,
                            String cronExpression, Long existProcessCode, Integer existScheduleId,
                            java.util.function.Consumer<Long> processCodeSetter,
                            java.util.function.Consumer<Integer> scheduleIdSetter) {
        if (!props.isEnabled()) {
            log.warn("DolphinScheduler 未启用，跳过调度注册: taskId={}", taskId);
            return;
        }

        String processName = taskType + "-" + taskName;

        try {
            // 1. 创建或更新工作流
            Long processCode;
            if (existProcessCode == null) {
                processCode = dsClient.createSingleNodeProcess(processName, taskType, taskId);
                processCodeSetter.accept(processCode);
                log.info("DS 工作流创建成功: taskId={}, processCode={}", taskId, processCode);
            } else {
                dsClient.updateSingleNodeProcess(existProcessCode, processName, taskType, taskId);
                processCode = existProcessCode;
                log.info("DS 工作流更新成功: taskId={}, processCode={}", taskId, processCode);
            }

            // 2. 上线工作流（创建调度前必须先上线）
            dsClient.releaseProcess(processCode, "ONLINE");

            // 3. 处理定时调度
            if (cronExpression != null && !cronExpression.isEmpty()) {
                Integer scheduleId;
                if (existScheduleId == null) {
                    scheduleId = dsClient.createSchedule(processCode, cronExpression);
                    scheduleIdSetter.accept(scheduleId);
                    log.info("DS 定时调度创建成功: taskId={}, scheduleId={}", taskId, scheduleId);
                } else {
                    dsClient.updateSchedule(processCode, existScheduleId, cronExpression);
                    scheduleId = existScheduleId;
                    log.info("DS 定时调度更新成功: taskId={}, scheduleId={}", taskId, scheduleId);
                }

                // 4. 上线定时调度
                dsClient.onlineSchedule(processCode, scheduleId);
            }

        } catch (Exception e) {
            log.error("DS 调度注册失败: taskId={}, taskType={}", taskId, taskType, e);
            // 不抛异常，避免影响主流程；调用方应根据 ds_process_code 是否为 null 判断成功
        }
    }

    private void cancelByTask(Long processCode, Integer scheduleId) {
        if (processCode == null) {
            return;
        }
        try {
            if (scheduleId != null) {
                dsClient.offlineSchedule(processCode, scheduleId);
            }
            dsClient.releaseProcess(processCode, "OFFLINE");
            log.info("DS 调度已取消: processCode={}", processCode);
        } catch (Exception e) {
            log.error("DS 调度取消失败: processCode={}", processCode, e);
        }
    }

    private void deleteByTask(Long taskId, Long processCode, Integer scheduleId) {
        if (processCode == null) {
            return;
        }
        try {
            if (scheduleId != null) {
                dsClient.offlineSchedule(processCode, scheduleId);
            }
            dsClient.releaseProcess(processCode, "OFFLINE");
            dsClient.deleteProcess(processCode);
            log.info("DS 工作流已删除: taskId={}, processCode={}", taskId, processCode);
        } catch (Exception e) {
            log.error("DS 工作流删除失败: taskId={}, processCode={}", taskId, processCode, e);
        }
    }
}
