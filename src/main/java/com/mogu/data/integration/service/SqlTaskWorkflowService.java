package com.mogu.data.integration.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mogu.data.integration.entity.SqlTask;
import com.mogu.data.integration.entity.SqlTaskDependency;
import com.mogu.data.integration.entity.SqlTaskWorkflow;
import com.mogu.data.integration.util.CronUtils;
import com.mogu.data.integration.entity.WorkflowInstance;
import com.mogu.data.integration.mapper.SqlTaskDependencyMapper;
import com.mogu.data.integration.mapper.SqlTaskMapper;
import com.mogu.data.integration.mapper.SqlTaskWorkflowMapper;
import com.mogu.data.integration.scheduler.TaskSchedulerManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * SQL任务工作流服务
 *
 * @author fengzhu
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SqlTaskWorkflowService extends ServiceImpl<SqlTaskWorkflowMapper, SqlTaskWorkflow> {

    private final TaskSchedulerManager schedulerManager;
    private final SqlTaskMapper sqlTaskMapper;
    private final SqlTaskDependencyMapper dependencyMapper;
    private final WorkflowInstanceService instanceService;

    public Page<SqlTaskWorkflow> pageWorkflows(String keyword, long page, long size) {
        LambdaQueryWrapper<SqlTaskWorkflow> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SqlTaskWorkflow::getDeleted, 0);
        if (StringUtils.hasText(keyword)) {
            wrapper.like(SqlTaskWorkflow::getWorkflowName, keyword);
        }
        wrapper.orderByDesc(SqlTaskWorkflow::getCreateTime);
        return page(new Page<>(page, size), wrapper);
    }

    @Transactional
    public void createWorkflow(SqlTaskWorkflow workflow) {
        if (lambdaQuery().eq(SqlTaskWorkflow::getWorkflowName, workflow.getWorkflowName())
                .eq(SqlTaskWorkflow::getDeleted, 0).count() > 0) {
            throw new IllegalArgumentException("工作流名称已存在");
        }
        workflow.setStatus(0);
        save(workflow);
        log.info("SQL工作流创建成功: workflowId={}, name={}", workflow.getId(), workflow.getWorkflowName());
    }

    @Transactional
    public void updateWorkflow(SqlTaskWorkflow workflow) {
        SqlTaskWorkflow exist = getById(workflow.getId());
        if (exist == null || exist.getDeleted() != null && exist.getDeleted() == 1) {
            throw new IllegalArgumentException("工作流不存在");
        }
        if (StringUtils.hasText(workflow.getWorkflowName())
                && !exist.getWorkflowName().equals(workflow.getWorkflowName())) {
            if (lambdaQuery().eq(SqlTaskWorkflow::getWorkflowName, workflow.getWorkflowName())
                    .eq(SqlTaskWorkflow::getDeleted, 0).count() > 0) {
                throw new IllegalArgumentException("工作流名称已存在");
            }
        }

        boolean cronChanged = workflow.getCronExpression() != null
                && !workflow.getCronExpression().equals(exist.getCronExpression());

        updateById(workflow);

        if (cronChanged && exist.getStatus() != null && exist.getStatus() == 1) {
            SqlTaskWorkflow updated = getById(workflow.getId());
            schedulerManager.rescheduleWorkflow(updated);
            updateById(updated);
        }
    }

    @Transactional
    public SqlTaskWorkflow toggleStatus(Long workflowId) {
        SqlTaskWorkflow workflow = getById(workflowId);
        if (workflow == null || workflow.getDeleted() != null && workflow.getDeleted() == 1) {
            throw new IllegalArgumentException("工作流不存在");
        }
        int newStatus = workflow.getStatus() != null && workflow.getStatus() == 1 ? 0 : 1;
        workflow.setStatus(newStatus);
        updateById(workflow);

        if (newStatus == 1 && workflow.getCronExpression() != null
                && !workflow.getCronExpression().isEmpty()) {
            schedulerManager.scheduleWorkflow(workflow);
            updateById(workflow);
        } else {
            schedulerManager.cancelWorkflow(workflowId);
        }
        return workflow;
    }

    @Transactional
    public void deleteWorkflow(Long workflowId) {
        SqlTaskWorkflow workflow = getById(workflowId);
        if (workflow == null || workflow.getDeleted() != null && workflow.getDeleted() == 1) {
            throw new IllegalArgumentException("工作流不存在");
        }
        LambdaQueryWrapper<SqlTask> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SqlTask::getWorkflowId, workflowId).eq(SqlTask::getDeleted, 0);
        long taskCount = sqlTaskMapper.selectCount(wrapper);
        if (taskCount > 0) {
            throw new IllegalArgumentException("工作流内存在任务，无法删除");
        }
        if (workflow.getDsProcessCode() != null) {
            schedulerManager.deleteWorkflow(workflowId);
        }
        removeById(workflowId);
    }

    /**
     * 同步 Workflow 到 DolphinScheduler（由 DS Manager 实现）
     */
    public void syncToDs(Long workflowId) {
        SqlTaskWorkflow workflow = getById(workflowId);
        if (workflow == null) {
            return;
        }
        schedulerManager.rescheduleWorkflow(workflow);
        updateById(workflow);
    }

    /**
     * 获取工作流的 DAG 数据（节点 + 边）
     */
    public Map<String, Object> getDag(Long workflowId) {
        List<SqlTask> tasks = sqlTaskMapper.selectList(
                new LambdaQueryWrapper<SqlTask>()
                        .eq(SqlTask::getWorkflowId, workflowId)
                        .eq(SqlTask::getDeleted, 0));

        List<SqlTaskDependency> deps = dependencyMapper.selectByWorkflowId(workflowId);

        List<Map<String, Object>> nodes = tasks.stream().map(t -> {
            Map<String, Object> node = new HashMap<>();
            node.put("id", t.getId());
            node.put("name", t.getTaskName());
            node.put("dsTaskCode", t.getDsTaskCode());
            node.put("status", t.getStatus());
            return node;
        }).collect(Collectors.toList());

        List<Map<String, Object>> edges = deps.stream().map(d -> {
            Map<String, Object> edge = new HashMap<>();
            edge.put("from", d.getDependTaskId());
            edge.put("to", d.getTaskId());
            return edge;
        }).collect(Collectors.toList());

        Map<String, Object> result = new HashMap<>();
        result.put("nodes", nodes);
        result.put("edges", edges);
        return result;
    }

    /**
     * 获取工作流最近一次执行信息
     */
    public WorkflowInstance getLastExecution(Long workflowId) {
        return instanceService.getLatestByWorkflowId(workflowId);
    }

    /**
     * 计算工作流下次执行时间
     */
    public LocalDateTime getNextExecutionTime(Long workflowId) {
        SqlTaskWorkflow workflow = getById(workflowId);
        if (workflow == null || workflow.getStatus() == null || workflow.getStatus() != 1) {
            return null;
        }
        String cron = workflow.getCronExpression();
        if (!StringUtils.hasText(cron)) {
            return null;
        }
        try {
            String springCron = CronUtils.convertQuartzToSpringCron(cron);
            CronExpression expr = CronExpression.parse(springCron);
            ZonedDateTime now = ZonedDateTime.now(ZoneId.systemDefault());
            ZonedDateTime next = expr.next(now);
            if (next == null) {
                return null;
            }
            return next.toLocalDateTime();
        } catch (Exception e) {
            log.warn("Cron表达式解析失败: workflowId={}, cron={}", workflowId, cron);
            return null;
        }
    }
}
