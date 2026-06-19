package com.mogu.data.integration.scheduler;

import com.mogu.data.integration.entity.SqlTask;
import com.mogu.data.integration.entity.SqlTaskDependency;
import com.mogu.data.integration.entity.SqlTaskWorkflow;
import com.mogu.data.integration.entity.SyncTask;
import com.mogu.data.integration.mapper.SqlTaskDependencyMapper;
import com.mogu.data.integration.mapper.SqlTaskMapper;
import com.mogu.data.integration.mapper.SqlTaskWorkflowMapper;
import com.mogu.data.integration.mapper.SyncTaskMapper;
import com.mogu.data.schedulerx.client.SchedulerXClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * SchedulerX 调度管理器实现。
 *
 * <p>将 MDataX 的任务注册到 SchedulerX 服务，由 SchedulerX 负责定时触发和 DAG 编排。
 *
 * @author fengzhu
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "scheduler.type", havingValue = "schedulerx")
public class SchedulerXManager implements TaskSchedulerManager {

    private final SchedulerXClient schedulerXClient;
    private final SqlTaskMapper sqlTaskMapper;
    private final SyncTaskMapper syncTaskMapper;
    private final SqlTaskDependencyMapper sqlTaskDependencyMapper;
    private final SqlTaskWorkflowMapper sqlTaskWorkflowMapper;
    private final com.mogu.data.integration.mapper.TaskMapper taskMapper;
    private final com.mogu.data.integration.mapper.TaskSqlDetailMapper taskSqlDetailMapper;
    private final com.mogu.data.integration.mapper.TaskSyncDetailMapper taskSyncDetailMapper;
    private final com.mogu.data.integration.mapper.TaskQualityDetailMapper taskQualityDetailMapper;
    private final com.mogu.data.integration.service.TaskExecutionService taskExecutionService;
    private final com.mogu.data.integration.service.IdGeneratorService idGeneratorService;

    // ==================== 独立任务调度 ====================

    @Override
    public void scheduleSyncTask(SyncTask task) {
        String dagId = schedulerXClient.registerSyncTask(task);
        if (dagId != null) {
            task.setSchedulerxDagId(dagId);
            schedulerXClient.enableDag(dagId);
            log.info("[SchedulerXManager] 同步任务注册成功: taskId={}, dagId={}", task.getId(), dagId);
        }
    }

    @Override
    public void scheduleSqlTask(SqlTask task) {
        String dagId = schedulerXClient.registerSqlTask(task);
        if (dagId != null) {
            task.setSchedulerxDagId(dagId);
            schedulerXClient.enableDag(dagId);
            log.info("[SchedulerXManager] SQL 任务注册成功: taskId={}, dagId={}", task.getId(), dagId);
        }
    }

    @Override
    public void cancelSyncTask(Long taskId) {
        SyncTask task = syncTaskMapper.selectById(taskId);
        if (task != null && task.getSchedulerxDagId() != null) {
            schedulerXClient.disableDag(task.getSchedulerxDagId());
            log.info("[SchedulerXManager] 同步任务调度已禁用: taskId={}, dagId={}", taskId, task.getSchedulerxDagId());
        }
    }

    @Override
    public void cancelSqlTask(Long taskId) {
        SqlTask task = sqlTaskMapper.selectById(taskId);
        if (task != null && task.getSchedulerxDagId() != null) {
            schedulerXClient.disableDag(task.getSchedulerxDagId());
            log.info("[SchedulerXManager] SQL 任务调度已禁用: taskId={}, dagId={}", taskId, task.getSchedulerxDagId());
        }
    }

    @Override
    public void deleteSyncTask(Long taskId) {
        SyncTask task = syncTaskMapper.selectById(taskId);
        if (task != null && task.getSchedulerxDagId() != null) {
            schedulerXClient.deleteDag(task.getSchedulerxDagId());
            task.setSchedulerxDagId(null);
            log.info("[SchedulerXManager] 同步任务已删除: taskId={}", taskId);
        }
    }

    @Override
    public void deleteSqlTask(Long taskId) {
        SqlTask task = sqlTaskMapper.selectById(taskId);
        if (task != null && task.getSchedulerxDagId() != null) {
            schedulerXClient.deleteDag(task.getSchedulerxDagId());
            task.setSchedulerxDagId(null);
            log.info("[SchedulerXManager] SQL 任务已删除: taskId={}", taskId);
        }
    }

    @Override
    public void rescheduleSyncTask(SyncTask task) {
        deleteSyncTask(task.getId());
        scheduleSyncTask(task);
    }

    @Override
    public void rescheduleSqlTask(SqlTask task) {
        deleteSqlTask(task.getId());
        scheduleSqlTask(task);
    }

    // ==================== Workflow（DAG）调度 ====================

    @Override
    public void scheduleWorkflow(SqlTaskWorkflow workflow) {
        Map<Long, SqlTask> taskMap = loadWorkflowSqlTasks(workflow.getId());
        Map<Long, List<Long>> depsMap = loadWorkflowDependencies(workflow.getId());
        String dagId = schedulerXClient.registerWorkflow(workflow, taskMap, depsMap);
        if (dagId != null) {
            workflow.setSchedulerxDagId(dagId);
            schedulerXClient.enableDag(dagId);
            log.info("[SchedulerXManager] Workflow 注册成功: workflowId={}, dagId={}", workflow.getId(), dagId);
        }
    }

    @Override
    public void cancelWorkflow(Long workflowId) {
        SqlTaskWorkflow workflow = sqlTaskWorkflowMapper.selectById(workflowId);
        if (workflow != null && workflow.getSchedulerxDagId() != null) {
            schedulerXClient.disableDag(workflow.getSchedulerxDagId());
            log.info("[SchedulerXManager] Workflow 调度已禁用: workflowId={}", workflowId);
        }
    }

    @Override
    public void deleteWorkflow(Long workflowId) {
        SqlTaskWorkflow workflow = sqlTaskWorkflowMapper.selectById(workflowId);
        if (workflow != null && workflow.getSchedulerxDagId() != null) {
            schedulerXClient.deleteDag(workflow.getSchedulerxDagId());
            workflow.setSchedulerxDagId(null);
            log.info("[SchedulerXManager] Workflow 已删除: workflowId={}", workflowId);
        }
    }

    @Override
    public void rescheduleWorkflow(SqlTaskWorkflow workflow) {
        deleteWorkflow(workflow.getId());
        scheduleWorkflow(workflow);
    }

    // ==================== 手动触发 ====================

    @Override
    public Long triggerSqlTask(SqlTask task) {
        if (task == null || task.getSchedulerxDagId() == null) {
            return null;
        }
        String result = schedulerXClient.triggerDag(task.getSchedulerxDagId());
        return result != null ? java.lang.Long.parseLong(result) : null;
    }

    @Override
    public Long triggerSyncTask(SyncTask task) {
        if (task == null || task.getSchedulerxDagId() == null) {
            return null;
        }
        String result = schedulerXClient.triggerDag(task.getSchedulerxDagId());
        return result != null ? java.lang.Long.parseLong(result) : null;
    }

    @Override
    public String triggerWorkflow(SqlTaskWorkflow workflow) {
        if (workflow == null || workflow.getSchedulerxDagId() == null) {
            return null;
        }
        return schedulerXClient.triggerDag(workflow.getSchedulerxDagId());
    }

    // ==================== 实例操作 ====================

    @Override
    public void stopWorkflowInstance(String instanceId) {
        if (instanceId == null) {
            return;
        }
        schedulerXClient.killInstance(instanceId);
    }

    @Override
    public void pauseWorkflowInstance(String instanceId) {
        if (instanceId == null) {
            return;
        }
        // SchedulerX 暂无实例级暂停，以 kill 替代
        schedulerXClient.killInstance(instanceId);
    }

    @Override
    public void retryWorkflowInstance(String instanceId) {
        if (instanceId == null) {
            return;
        }
        schedulerXClient.retryInstance(instanceId);
    }

    @Override
    public String listWorkflowInstances(SqlTaskWorkflow workflow, int pageNum, int pageSize) {
        if (workflow == null || workflow.getSchedulerxDagId() == null) {
            return null;
        }
        return schedulerXClient.listInstances(workflow.getSchedulerxDagId(), pageNum, pageSize);
    }

    @Override
    public String getInstanceDetail(String instanceId) {
        return schedulerXClient.getInstanceDetail(instanceId);
    }

    @Override
    public String getInstanceTasks(String instanceId) {
        return schedulerXClient.getInstanceTasks(instanceId);
    }

    @Override
    public String getSqlTaskInstances(Long taskId, int pageNum, int pageSize) {
        SqlTask task = sqlTaskMapper.selectById(taskId);
        if (task == null || task.getSchedulerxDagId() == null) {
            return null;
        }
        return schedulerXClient.listInstances(task.getSchedulerxDagId(), pageNum, pageSize);
    }

    @Override
    public String getSyncTaskInstances(Long taskId, int pageNum, int pageSize) {
        SyncTask task = syncTaskMapper.selectById(taskId);
        if (task == null || task.getSchedulerxDagId() == null) {
            return null;
        }
        return schedulerXClient.listInstances(task.getSchedulerxDagId(), pageNum, pageSize);
    }

    // ==================== 内部方法 ====================

    private Map<Long, SqlTask> loadWorkflowSqlTasks(Long workflowId) {
        List<SqlTask> sqlTasks = sqlTaskMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<SqlTask>()
                        .eq(SqlTask::getWorkflowId, workflowId)
                        .eq(SqlTask::getDeleted, 0));
        Map<Long, SqlTask> map = new HashMap<>();
        for (SqlTask task : sqlTasks) {
            map.put(task.getId(), task);
        }
        return map;
    }

    private Map<Long, List<Long>> loadWorkflowDependencies(Long workflowId) {
        List<SqlTaskDependency> deps = sqlTaskDependencyMapper.selectByWorkflowId(workflowId);
        Map<Long, List<Long>> map = new HashMap<>();
        for (SqlTaskDependency dep : deps) {
            map.computeIfAbsent(dep.getTaskId(), k -> new ArrayList<>())
                    .add(dep.getDependTaskId());
        }
        return map;
    }

    // ==================== 统一任务调度实现 ====================

    @Override
    public void scheduleTask(com.mogu.data.integration.entity.Task task) {
        if ("SQL".equals(task.getTaskType())) {
            // 优先从新表读取
            com.mogu.data.integration.entity.TaskSqlDetail detail = taskSqlDetailMapper.selectByTaskId(task.getId());
            if (detail != null) {
                scheduleNewSqlTask(task, detail);
            } else {
                // 向后兼容：从旧表读取
                SqlTask sqlTask = sqlTaskMapper.selectById(task.getId());
                if (sqlTask != null) {
                    scheduleSqlTask(sqlTask);
                } else {
                    log.warn("[SchedulerXManager] SQL任务未找到详情: taskId={}", task.getId());
                }
            }
        } else if ("SYNC".equals(task.getTaskType())) {
            // 优先从新表读取
            com.mogu.data.integration.entity.TaskSyncDetail detail = taskSyncDetailMapper.selectByTaskId(task.getId());
            if (detail != null) {
                scheduleNewSyncTask(task, detail);
            } else {
                // 向后兼容：从旧表读取
                SyncTask syncTask = syncTaskMapper.selectById(task.getId());
                if (syncTask != null) {
                    scheduleSyncTask(syncTask);
                } else {
                    log.warn("[SchedulerXManager] 同步任务未找到详情: taskId={}", task.getId());
                }
            }
        } else if ("QUALITY".equals(task.getTaskType())) {
            com.mogu.data.integration.entity.TaskQualityDetail detail = taskQualityDetailMapper.selectByTaskId(task.getId());
            if (detail != null) {
                scheduleNewQualityTask(task, detail);
            } else {
                log.warn("[SchedulerXManager] 质量监控任务未找到详情: taskId={}", task.getId());
            }
        }
    }

    @Override
    public void cancelTask(Long taskId) {
        // 先尝试作为SQL任务取消
        SqlTask sqlTask = sqlTaskMapper.selectById(taskId);
        if (sqlTask != null) {
            cancelSqlTask(taskId);
            return;
        }
        // 再尝试作为Sync任务取消
        SyncTask syncTask = syncTaskMapper.selectById(taskId);
        if (syncTask != null) {
            cancelSyncTask(taskId);
        }
    }

    @Override
    public void rescheduleTask(com.mogu.data.integration.entity.Task task) {
        cancelTask(task.getId());
        scheduleTask(task);
    }

    @Override
    public String triggerTask(com.mogu.data.integration.entity.Task task) {
        if (task == null) {
            return null;
        }

        // 生成MDataX执行ID
        String executionId = idGeneratorService.generateExecutionId();
        String schedulerInstanceId = null;

        // 如果已经有调度器ID，直接触发
        if (task.getSchedulerxDagId() != null) {
            schedulerInstanceId = schedulerXClient.triggerDag(task.getSchedulerxDagId());
        } else {
            // 如果没有调度器ID，尝试先调度再触发
            log.info("[SchedulerXManager] 任务未同步到调度器，尝试自动同步: taskId={}", task.getId());
            try {
                // 先同步任务到调度器
                scheduleTask(task);

                // 重新加载任务获取调度器ID
                com.mogu.data.integration.entity.Task updatedTask = taskMapper.selectById(task.getId());
                if (updatedTask != null && updatedTask.getSchedulerxDagId() != null) {
                    log.info("[SchedulerXManager] 自动同步成功，触发任务: taskId={}, dagId={}",
                            task.getId(), updatedTask.getSchedulerxDagId());
                    schedulerInstanceId = schedulerXClient.triggerDag(updatedTask.getSchedulerxDagId());
                }
            } catch (Exception e) {
                log.error("[SchedulerXManager] 自动同步失败: taskId={}", task.getId(), e);
            }
        }

        if (schedulerInstanceId != null) {
            // 记录执行记录
            taskExecutionService.recordScheduleExecution(task.getId(), executionId, schedulerInstanceId);
            log.info("[SchedulerXManager] 任务触发成功: taskId={}, executionId={}, schedulerInstanceId={}",
                    task.getId(), executionId, schedulerInstanceId);
            return executionId;
        }

        log.warn("[SchedulerXManager] 任务无法同步到调度器: taskId={}", task.getId());
        return null;
    }

    // ==================== 新表结构适配方法 ====================

    /**
     * 调度新表结构的SQL任务
     */
    private void scheduleNewSqlTask(com.mogu.data.integration.entity.Task task,
                                   com.mogu.data.integration.entity.TaskSqlDetail detail) {
        try {
            // 构造兼容的SqlTask对象用于调度
            SqlTask compatTask = new SqlTask();
            compatTask.setId(task.getId());
            compatTask.setTaskName(task.getTaskName());
            compatTask.setSqlContent(detail.getSqlContent());
            compatTask.setDescription(task.getDescription());
            compatTask.setCronExpression(task.getCronExpression());
            compatTask.setStatus(task.getStatus());
            compatTask.setRetryTimes(task.getRetryTimes());
            compatTask.setRetryInterval(task.getRetryInterval());
            compatTask.setCreateUserId(task.getCreateUserId());

            // 调用原有的调度逻辑
            scheduleSqlTask(compatTask);

            // 更新新表的调度器ID
            if (compatTask.getSchedulerxDagId() != null) {
                task.setSchedulerxDagId(compatTask.getSchedulerxDagId());
                taskMapper.updateById(task);
                log.info("[SchedulerXManager] 新表SQL任务调度成功: taskId={}, dagId={}",
                        task.getId(), compatTask.getSchedulerxDagId());
            }
        } catch (Exception e) {
            log.error("[SchedulerXManager] 新表SQL任务调度失败: taskId={}", task.getId(), e);
        }
    }

    /**
     * 调度新表结构的同步任务
     */
    private void scheduleNewSyncTask(com.mogu.data.integration.entity.Task task,
                                    com.mogu.data.integration.entity.TaskSyncDetail detail) {
        try {
            // 构造兼容的SyncTask对象用于调度
            SyncTask compatTask = new SyncTask();
            compatTask.setId(task.getId());
            compatTask.setTaskName(task.getTaskName());
            compatTask.setDatasourceId(detail.getSourceDatasourceId()); // 使用源数据源ID
            compatTask.setSourceTable(detail.getSourceTable());
            compatTask.setTargetTable(detail.getTargetTable());
            compatTask.setSyncType(detail.getSyncType());
            compatTask.setTimeField(detail.getTimeField());
            compatTask.setCronExpression(task.getCronExpression());
            compatTask.setStatus(task.getStatus());
            compatTask.setRetryTimes(task.getRetryTimes());
            compatTask.setRetryInterval(task.getRetryInterval());
            compatTask.setCreateUserId(task.getCreateUserId());

            // 调用原有的调度逻辑
            scheduleSyncTask(compatTask);

            // 更新新表的调度器ID
            if (compatTask.getSchedulerxDagId() != null) {
                task.setSchedulerxDagId(compatTask.getSchedulerxDagId());
                taskMapper.updateById(task);
                log.info("[SchedulerXManager] 新表同步任务调度成功: taskId={}, dagId={}",
                        task.getId(), compatTask.getSchedulerxDagId());
            }
        } catch (Exception e) {
            log.error("[SchedulerXManager] 新表同步任务调度失败: taskId={}", task.getId(), e);
        }
    }

    /**
     * 调度新表结构的质量监控任务
     */
    private void scheduleNewQualityTask(com.mogu.data.integration.entity.Task task,
                                        com.mogu.data.integration.entity.TaskQualityDetail detail) {
        try {
            String dagId = schedulerXClient.registerQualityTask(task, detail);
            if (dagId != null) {
                task.setSchedulerxDagId(dagId);
                taskMapper.updateById(task);
                log.info("[SchedulerXManager] 新表质量监控任务调度成功: taskId={}, dagId={}",
                        task.getId(), dagId);
            }
        } catch (Exception e) {
            log.error("[SchedulerXManager] 新表质量监控任务调度失败: taskId={}", task.getId(), e);
        }
    }
}
