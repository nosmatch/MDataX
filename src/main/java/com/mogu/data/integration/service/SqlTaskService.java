package com.mogu.data.integration.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mogu.data.integration.entity.SqlTask;
import com.mogu.data.integration.mapper.SqlTaskMapper;
import com.mogu.data.integration.scheduler.TaskSchedulerManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * SQL任务服务
 *
 * @author fengzhu
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SqlTaskService extends ServiceImpl<SqlTaskMapper, SqlTask> {

    private final TaskSchedulerManager schedulerManager;

    public Page<SqlTask> pageTasks(String keyword, long page, long size) {
        LambdaQueryWrapper<SqlTask> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SqlTask::getDeleted, 0);
        if (StringUtils.hasText(keyword)) {
            wrapper.like(SqlTask::getTaskName, keyword);
        }
        wrapper.orderByDesc(SqlTask::getCreateTime);
        return page(new Page<>(page, size), wrapper);
    }

    public void createTask(SqlTask task) {
        if (lambdaQuery().eq(SqlTask::getTaskName, task.getTaskName()).eq(SqlTask::getDeleted, 0).count() > 0) {
            throw new IllegalArgumentException("任务名称已存在");
        }
        task.setStatus(0);
        save(task);

        // 只有启用状态且配置了 Cron，才注册到调度器
        if (task.getStatus() != null && task.getStatus() == 1
                && task.getCronExpression() != null && !task.getCronExpression().isEmpty()) {
            schedulerManager.scheduleSqlTask(task);
            updateById(task); // 保存 ds_process_code / ds_schedule_id
        }
    }

    public void updateTask(SqlTask task) {
        SqlTask exist = getById(task.getId());
        if (exist == null) {
            throw new IllegalArgumentException("任务不存在");
        }
        if (StringUtils.hasText(task.getTaskName()) && !exist.getTaskName().equals(task.getTaskName())) {
            if (lambdaQuery().eq(SqlTask::getTaskName, task.getTaskName()).eq(SqlTask::getDeleted, 0).count() > 0) {
                throw new IllegalArgumentException("任务名称已存在");
            }
        }

        // 判断 Cron 是否变化
        boolean cronChanged = task.getCronExpression() != null
                && !task.getCronExpression().equals(exist.getCronExpression());

        updateById(task);

        // 如果 Cron 变化，重新调度
        if (cronChanged) {
            SqlTask updated = getById(task.getId());
            schedulerManager.rescheduleSqlTask(updated);
            updateById(updated); // 保存新的 ds_process_code / ds_schedule_id
        }
    }

    public SqlTask toggleStatus(Long taskId) {
        SqlTask task = getById(taskId);
        if (task == null || task.getDeleted() != null && task.getDeleted() == 1) {
            throw new IllegalArgumentException("任务不存在");
        }
        int newStatus = task.getStatus() != null && task.getStatus() == 1 ? 0 : 1;
        task.setStatus(newStatus);
        updateById(task);

        // 同步调度状态
        if (newStatus == 1 && task.getCronExpression() != null && !task.getCronExpression().isEmpty()) {
            schedulerManager.scheduleSqlTask(task);
            updateById(task); // 保存 ds_process_code / ds_schedule_id
        } else {
            schedulerManager.cancelSqlTask(taskId);
        }
        return task;
    }

    public void deleteTask(Long taskId) {
        SqlTask task = getById(taskId);
        if (task == null || task.getDeleted() != null && task.getDeleted() == 1) {
            throw new IllegalArgumentException("任务不存在");
        }
        if (task.getStatus() != null && task.getStatus() == 1) {
            throw new IllegalArgumentException("启用状态的任务不能删除，请先停用");
        }
        schedulerManager.deleteSqlTask(taskId);
        removeById(taskId);
    }

}
