package com.mogu.data.integration.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mogu.data.integration.dolphinscheduler.DolphinSchedulerClient;
import com.mogu.data.integration.entity.SqlTask;
import com.mogu.data.integration.entity.SqlTaskWorkflow;
import com.mogu.data.integration.mapper.SqlTaskMapper;
import com.mogu.data.integration.scheduler.TaskSchedulerManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * SQL任务服务
 *
 * <p>支持独立任务和 Workflow（DAG）内任务两种模式。</p>
 *
 * @author fengzhu
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SqlTaskService extends ServiceImpl<SqlTaskMapper, SqlTask> {

    private final TaskSchedulerManager schedulerManager;
    private final SqlTaskDependencyService dependencyService;
    private final SqlTaskWorkflowService workflowService;
    private final DolphinSchedulerClient dsClient;

    public Page<SqlTask> pageTasks(String keyword, long page, long size) {
        LambdaQueryWrapper<SqlTask> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SqlTask::getDeleted, 0);
        if (StringUtils.hasText(keyword)) {
            wrapper.like(SqlTask::getTaskName, keyword);
        }
        wrapper.orderByDesc(SqlTask::getCreateTime);
        return page(new Page<>(page, size), wrapper);
    }

    /**
     * 创建任务
     *
     * @param task          任务实体
     * @param dependTaskIds 依赖的上游任务ID列表（仅 Workflow 内任务有效）
     */
    @Transactional
    public void createTask(SqlTask task, List<Long> dependTaskIds) {
        if (lambdaQuery().eq(SqlTask::getTaskName, task.getTaskName()).eq(SqlTask::getDeleted, 0).count() > 0) {
            throw new IllegalArgumentException("任务名称已存在");
        }

        if (task.getWorkflowId() != null) {
            // ========== Workflow 内任务 ==========
            task.setCronExpression(null);
            task.setDsProcessCode(null);
            task.setDsScheduleId(null);
            task.setStatus(0);
            save(task);

            // 生成稳定的 ds_task_code
            if (task.getDsTaskCode() == null) {
                task.setDsTaskCode(dsClient.generateTaskCode());
                updateById(task);
            }

            // 保存依赖关系
            if (dependTaskIds != null && !dependTaskIds.isEmpty()) {
                dependencyService.saveDependencies(task.getId(), dependTaskIds);
            }

            // 触发 Workflow 同步到 DS
            workflowService.syncToDs(task.getWorkflowId());
        } else {
            // ========== 独立任务 ==========
            task.setStatus(0);
            save(task);

            if (task.getStatus() != null && task.getStatus() == 1
                    && task.getCronExpression() != null && !task.getCronExpression().isEmpty()) {
                schedulerManager.scheduleSqlTask(task);
                updateById(task);
            }
        }
    }

    /**
     * 更新任务
     *
     * @param task          任务实体
     * @param dependTaskIds 新的依赖列表（仅 Workflow 内任务有效，null 表示不修改依赖）
     */
    @Transactional
    public void updateTask(SqlTask task, List<Long> dependTaskIds) {
        SqlTask exist = getById(task.getId());
        if (exist == null || exist.getDeleted() != null && exist.getDeleted() == 1) {
            throw new IllegalArgumentException("任务不存在");
        }

        // 不允许变更所属 Workflow
        if (task.getWorkflowId() != null && !task.getWorkflowId().equals(exist.getWorkflowId())) {
            throw new IllegalArgumentException("不允许变更任务所属工作流");
        }

        if (StringUtils.hasText(task.getTaskName()) && !exist.getTaskName().equals(task.getTaskName())) {
            if (lambdaQuery().eq(SqlTask::getTaskName, task.getTaskName()).eq(SqlTask::getDeleted, 0).count() > 0) {
                throw new IllegalArgumentException("任务名称已存在");
            }
        }

        if (exist.getWorkflowId() != null) {
            // ========== Workflow 内任务 ==========
            // 只在传入的 task 有非 null 字段时才更新数据库
            boolean hasUpdate = task.getTaskName() != null || task.getSqlContent() != null
                    || task.getTargetTable() != null;
            if (hasUpdate) {
                task.setCronExpression(null);
                task.setDsProcessCode(null);
                task.setDsScheduleId(null);
                updateById(task);
            }

            // 依赖关系变化时重新保存并同步 Workflow
            if (dependTaskIds != null) {
                dependencyService.saveDependencies(task.getId(), dependTaskIds);
                workflowService.syncToDs(exist.getWorkflowId());
            }
        } else {
            // ========== 独立任务 ==========
            boolean cronChanged = task.getCronExpression() != null
                    && !task.getCronExpression().equals(exist.getCronExpression());
            updateById(task);

            if (cronChanged) {
                SqlTask updated = getById(task.getId());
                schedulerManager.rescheduleSqlTask(updated);
                updateById(updated);
            }
        }
    }

    @Transactional
    public void deleteTask(Long taskId) {
        SqlTask task = getById(taskId);
        if (task == null || task.getDeleted() != null && task.getDeleted() == 1) {
            throw new IllegalArgumentException("任务不存在");
        }

        if (task.getWorkflowId() != null) {
            // ========== Workflow 内任务 ==========
            List<Long> downstream = dependencyService.getDownstreamTaskIds(taskId);
            if (!downstream.isEmpty()) {
                throw new IllegalArgumentException("该任务被工作流内其他任务依赖，无法删除");
            }
            dependencyService.deleteByTaskId(taskId);
            removeById(taskId);
            workflowService.syncToDs(task.getWorkflowId());
        } else {
            // ========== 独立任务 ==========
            if (task.getStatus() != null && task.getStatus() == 1) {
                throw new IllegalArgumentException("启用状态的任务不能删除，请先停用");
            }
            schedulerManager.deleteSqlTask(taskId);
            removeById(taskId);
        }
    }

    // ==================== 流级操作 ====================

    /**
     * 递归触发任务及其所有上游依赖的手动执行
     */
    public void runWithDependencies(Long taskId) {
        SqlTask task = getById(taskId);
        if (task == null) {
            throw new IllegalArgumentException("任务不存在");
        }

        if (task.getWorkflowId() != null) {
            // Workflow 内任务：触发整个 Workflow
            SqlTaskWorkflow workflow = workflowService.getById(task.getWorkflowId());
            if (workflow != null && workflow.getDsProcessCode() != null) {
                dsClient.startProcessInstance(workflow.getDsProcessCode());
            }
        } else {
            // 独立任务：直接触发
            if (task.getDsProcessCode() != null) {
                dsClient.startProcessInstance(task.getDsProcessCode());
            }
        }
    }

    /**
     * 暂停当前任务及其所有下游任务的调度
     */
    @Transactional
    public void pauseChain(Long taskId) {
        SqlTask task = getById(taskId);
        if (task == null) {
            throw new IllegalArgumentException("任务不存在");
        }

        Set<Long> chain = dependencyService.collectAllDownstream(taskId);
        chain.add(taskId);

        for (Long id : chain) {
            SqlTask t = getById(id);
            if (t == null) continue;

            if (t.getWorkflowId() != null) {
                // Workflow 内任务通过停用 Workflow 实现
                SqlTaskWorkflow wf = workflowService.getById(t.getWorkflowId());
                if (wf != null && wf.getStatus() != null && wf.getStatus() == 1) {
                    workflowService.toggleStatus(wf.getId());
                }
            } else {
                // 独立任务
                if (t.getStatus() != null && t.getStatus() == 1) {
                    toggleStatus(id);
                }
            }
        }
    }

    /**
     * 恢复当前任务及其所有下游任务的调度
     */
    @Transactional
    public void resumeChain(Long taskId) {
        SqlTask task = getById(taskId);
        if (task == null) {
            throw new IllegalArgumentException("任务不存在");
        }

        Set<Long> chain = dependencyService.collectAllDownstream(taskId);
        chain.add(taskId);

        for (Long id : chain) {
            SqlTask t = getById(id);
            if (t == null) continue;

            if (t.getWorkflowId() != null) {
                SqlTaskWorkflow wf = workflowService.getById(t.getWorkflowId());
                if (wf != null && wf.getStatus() != null && wf.getStatus() == 0) {
                    workflowService.toggleStatus(wf.getId());
                }
            } else {
                if (t.getStatus() != null && t.getStatus() == 0) {
                    toggleStatus(id);
                }
            }
        }
    }

    // ==================== 兼容旧调用（无依赖参数） ====================

    public void createTask(SqlTask task) {
        createTask(task, null);
    }

    public void updateTask(SqlTask task) {
        updateTask(task, null);
    }

    // ==================== 原有方法 ====================

    public SqlTask toggleStatus(Long taskId) {
        SqlTask task = getById(taskId);
        if (task == null || task.getDeleted() != null && task.getDeleted() == 1) {
            throw new IllegalArgumentException("任务不存在");
        }
        int newStatus = task.getStatus() != null && task.getStatus() == 1 ? 0 : 1;
        task.setStatus(newStatus);
        updateById(task);

        if (newStatus == 1 && task.getCronExpression() != null && !task.getCronExpression().isEmpty()) {
            schedulerManager.scheduleSqlTask(task);
            updateById(task);
        } else {
            schedulerManager.cancelSqlTask(taskId);
        }
        return task;
    }
}
