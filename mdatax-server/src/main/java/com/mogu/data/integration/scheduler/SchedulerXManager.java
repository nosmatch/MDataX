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
    public String triggerSqlTask(SqlTask task) {
        if (task == null || task.getSchedulerxDagId() == null) {
            return null;
        }
        return schedulerXClient.triggerDag(task.getSchedulerxDagId());
    }

    @Override
    public String triggerSyncTask(SyncTask task) {
        if (task == null || task.getSchedulerxDagId() == null) {
            return null;
        }
        return schedulerXClient.triggerDag(task.getSchedulerxDagId());
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
    public String listWorkflowInstances(SqlTaskWorkflow workflow) {
        if (workflow == null || workflow.getSchedulerxDagId() == null) {
            return null;
        }
        return schedulerXClient.listInstances(workflow.getSchedulerxDagId(), 1, 100);
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
}
