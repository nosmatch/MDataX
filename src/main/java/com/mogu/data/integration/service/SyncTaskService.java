package com.mogu.data.integration.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mogu.data.integration.entity.Datasource;
import com.mogu.data.integration.entity.SyncTask;
import com.mogu.data.integration.mapper.DatasourceMapper;
import com.mogu.data.integration.mapper.SyncTaskMapper;
import com.mogu.data.integration.scheduler.TaskSchedulerManager;
import com.mogu.data.integration.vo.SyncTaskVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * 同步任务服务
 *
 * @author fengzhu
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SyncTaskService extends ServiceImpl<SyncTaskMapper, SyncTask> {

    private final DatasourceMapper datasourceMapper;
    private final TaskSchedulerManager schedulerManager;

    public Page<SyncTaskVO> pageTasks(String keyword, long page, long size) {
        Page<SyncTask> taskPage = page(new Page<>(page, size), new LambdaQueryWrapper<SyncTask>()
                .eq(SyncTask::getDeleted, 0)
                .like(StringUtils.hasText(keyword), SyncTask::getTaskName, keyword)
                .orderByDesc(SyncTask::getCreateTime));

        List<SyncTaskVO> voList = new ArrayList<>();
        for (SyncTask task : taskPage.getRecords()) {
            SyncTaskVO vo = new SyncTaskVO();
            vo.setId(task.getId());
            vo.setTaskName(task.getTaskName());
            vo.setDatasourceId(task.getDatasourceId());
            vo.setSourceTable(task.getSourceTable());
            vo.setTargetTable(task.getTargetTable());
            vo.setSyncType(task.getSyncType());
            vo.setTimeField(task.getTimeField());
            vo.setCronExpression(task.getCronExpression());
            vo.setStatus(task.getStatus());
            vo.setLastSyncTime(task.getLastSyncTime());
            vo.setCreateTime(task.getCreateTime());
            vo.setUpdateTime(task.getUpdateTime());

            Datasource ds = datasourceMapper.selectById(task.getDatasourceId());
            if (ds != null) {
                vo.setDatasourceName(ds.getName());
            }
            voList.add(vo);
        }

        Page<SyncTaskVO> result = new Page<>();
        result.setCurrent(taskPage.getCurrent());
        result.setSize(taskPage.getSize());
        result.setTotal(taskPage.getTotal());
        result.setPages(taskPage.getPages());
        result.setRecords(voList);
        return result;
    }

    public void createTask(SyncTask task) {
        if (lambdaQuery().eq(SyncTask::getTaskName, task.getTaskName()).eq(SyncTask::getDeleted, 0).count() > 0) {
            throw new IllegalArgumentException("任务名称已存在");
        }
        task.setStatus(0);
        save(task);

        // 只有启用状态且配置了 Cron，才注册到调度器
        if (task.getStatus() != null && task.getStatus() == 1
                && task.getCronExpression() != null && !task.getCronExpression().isEmpty()) {
            schedulerManager.scheduleSyncTask(task);
            updateById(task); // 保存 ds_process_code / ds_schedule_id
        }
    }

    public void updateTask(SyncTask task) {
        SyncTask exist = getById(task.getId());
        if (exist == null) {
            throw new IllegalArgumentException("任务不存在");
        }
        if (StringUtils.hasText(task.getTaskName()) && !exist.getTaskName().equals(task.getTaskName())) {
            if (lambdaQuery().eq(SyncTask::getTaskName, task.getTaskName()).eq(SyncTask::getDeleted, 0).count() > 0) {
                throw new IllegalArgumentException("任务名称已存在");
            }
        }

        // 判断 Cron 是否变化
        boolean cronChanged = task.getCronExpression() != null
                && !task.getCronExpression().equals(exist.getCronExpression());

        updateById(task);

        // 如果 Cron 变化，重新调度
        if (cronChanged) {
            SyncTask updated = getById(task.getId());
            schedulerManager.rescheduleSyncTask(updated);
            updateById(updated); // 保存新的 ds_process_code / ds_schedule_id
        }
    }

    public SyncTask toggleStatus(Long taskId) {
        SyncTask task = getById(taskId);
        if (task == null || task.getDeleted() != null && task.getDeleted() == 1) {
            throw new IllegalArgumentException("任务不存在");
        }
        int newStatus = task.getStatus() != null && task.getStatus() == 1 ? 0 : 1;
        task.setStatus(newStatus);
        updateById(task);

        // 同步调度状态
        if (newStatus == 1 && task.getCronExpression() != null && !task.getCronExpression().isEmpty()) {
            schedulerManager.scheduleSyncTask(task);
            updateById(task); // 保存 ds_process_code / ds_schedule_id
        } else {
            schedulerManager.cancelSyncTask(taskId);
        }
        return task;
    }

    public void deleteTask(Long taskId) {
        SyncTask task = getById(taskId);
        if (task == null || task.getDeleted() != null && task.getDeleted() == 1) {
            throw new IllegalArgumentException("任务不存在");
        }
        if (task.getStatus() != null && task.getStatus() == 1) {
            throw new IllegalArgumentException("启用状态的任务不能删除，请先停用");
        }
        schedulerManager.deleteSyncTask(taskId);
        removeById(taskId);
    }

    public List<String> listTables(Long datasourceId) {
        Datasource ds = datasourceMapper.selectById(datasourceId);
        if (ds == null || ds.getDeleted() != null && ds.getDeleted() == 1) {
            throw new IllegalArgumentException("数据源不存在");
        }
        String url = String.format("jdbc:mysql://%s:%d/%s?useSSL=false&serverTimezone=Asia/Shanghai&connectTimeout=5000&socketTimeout=5000",
                ds.getHost(), ds.getPort(), ds.getDatabaseName());
        List<String> tables = new ArrayList<>();
        try (Connection conn = DriverManager.getConnection(url, ds.getUsername(), ds.getPassword())) {
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SHOW TABLES")) {
                while (rs.next()) {
                    tables.add(rs.getString(1));
                }
            }
        } catch (Exception e) {
            log.warn("获取数据源表列表失败: datasourceId={}, error={}", datasourceId, e.getMessage());
            throw new IllegalArgumentException("连接数据源失败: " + e.getMessage());
        }
        return tables;
    }

}
