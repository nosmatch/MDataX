package com.mogu.data.integration.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mogu.data.integration.entity.Datasource;
import com.mogu.data.integration.entity.Task;
import com.mogu.data.integration.entity.TaskExecution;
import com.mogu.data.integration.entity.TaskQualityDetail;
import com.mogu.data.integration.entity.TaskSqlDetail;
import com.mogu.data.integration.entity.TaskSyncDetail;
import com.mogu.data.integration.enums.DatasourceType;
import com.mogu.data.integration.mapper.DatasourceMapper;
import com.mogu.data.integration.mapper.TaskMapper;
import com.mogu.data.integration.mapper.TaskQualityDetailMapper;
import com.mogu.data.integration.mapper.TaskSqlDetailMapper;
import com.mogu.data.integration.mapper.TaskSyncDetailMapper;
import com.mogu.data.integration.scheduler.TaskSchedulerManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 统一任务服务
 *
 * @author fengzhu
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TaskService extends ServiceImpl<TaskMapper, Task> {

    private final TaskSchedulerManager schedulerManager;
    private final TaskSqlDetailMapper taskSqlDetailMapper;
    private final TaskSyncDetailMapper taskSyncDetailMapper;
    private final TaskQualityDetailMapper taskQualityDetailMapper;
    private final TaskExecutionService taskExecutionService;
    private final TaskDependencyService taskDependencyService;
    private final IdGeneratorService idGeneratorService;
    private final DatasourceMapper datasourceMapper;

    /**
     * 分页查询任务列表
     */
    public Page<Task> pageTasks(String keyword, String taskType, Integer status,
                                 Long ownerUserId, Integer priority, String tags,
                                 java.time.LocalDateTime startTime,
                                 java.time.LocalDateTime endTime,
                                 String lastExecutionStatus,
                                 long page, long size) {
        if (StringUtils.hasText(lastExecutionStatus)) {
            Page<Task> result = baseMapper.pageTasksByLastExecutionStatus(
                    keyword, taskType, status, ownerUserId, priority, tags,
                    startTime, endTime, lastExecutionStatus, new Page<>(page, size));
            fillLastExecutionInfo(result.getRecords());
            return result;
        }

        LambdaQueryWrapper<Task> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Task::getDeleted, 0);

        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(Task::getTaskName, keyword)
                    .or().like(Task::getTaskCode, keyword)
                    .or().like(Task::getTags, keyword));
        }
        if (StringUtils.hasText(taskType)) {
            wrapper.eq(Task::getTaskType, taskType);
        }
        if (status != null) {
            wrapper.eq(Task::getStatus, status);
        }
        if (ownerUserId != null) {
            wrapper.eq(Task::getOwnerUserId, ownerUserId);
        }
        if (priority != null) {
            wrapper.ge(Task::getPriority, priority);
        }
        if (StringUtils.hasText(tags)) {
            wrapper.like(Task::getTags, tags);
        }
        if (startTime != null) {
            wrapper.ge(Task::getCreateTime, startTime);
        }
        if (endTime != null) {
            wrapper.le(Task::getCreateTime, endTime);
        }

        wrapper.orderByDesc(Task::getCreateTime);
        Page<Task> result = page(new Page<>(page, size), wrapper);

        // 填充最近执行信息
        fillLastExecutionInfo(result.getRecords());

        return result;
    }

    /**
     * 批量填充任务的最近执行信息
     */
    private void fillLastExecutionInfo(List<Task> tasks) {
        if (tasks == null || tasks.isEmpty()) {
            return;
        }

        List<Long> taskIds = tasks.stream()
                .map(Task::getId)
                .distinct()
                .collect(java.util.stream.Collectors.toList());

        List<TaskMapper.TaskExecutionExt> lastExecutions = baseMapper.selectLastExecutionByTaskIds(taskIds);
        java.util.Map<Long, TaskMapper.TaskExecutionExt> executionMap = lastExecutions.stream()
                .collect(java.util.stream.Collectors.toMap(
                        TaskMapper.TaskExecutionExt::getTaskId,
                        e -> e,
                        (existing, replacement) -> existing
                ));

        for (Task task : tasks) {
            TaskMapper.TaskExecutionExt lastExecution = executionMap.get(task.getId());
            if (lastExecution != null) {
                task.setLastExecutionStatus(lastExecution.getStatus());
                task.setLastExecutionTime(lastExecution.getStartTime());
                task.setLastExecutionId(lastExecution.getExecutionId());
            }
        }
    }

    /**
     * 创建任务
     */
    @Transactional
    public Long createTask(Task task, Object detail) {
        // 生成任务编码
        if (task.getTaskCode() == null || task.getTaskCode().isEmpty()) {
            task.setTaskCode(generateTaskCode());
        }

        // 设置默认值
        if (task.getPriority() == null) {
            task.setPriority(5);
        }
        if (task.getStatus() == null) {
            task.setStatus(2);  // 草稿
        }
        if (task.getRetryTimes() == null) {
            task.setRetryTimes(0);
        }
        if (task.getRetryInterval() == null) {
            task.setRetryInterval(0);
        }
        if (task.getTimeoutSeconds() == null) {
            task.setTimeoutSeconds(0);
        }

        // 保存任务主表
        save(task);
        log.info("任务创建成功: taskId={}, taskCode={}, taskType={}",
                task.getId(), task.getTaskCode(), task.getTaskType());

        // 保存详情表
        if ("SQL".equals(task.getTaskType()) && detail instanceof TaskSqlDetail) {
            TaskSqlDetail sqlDetail = (TaskSqlDetail) detail;
            sqlDetail.setTaskId(task.getId());
            taskSqlDetailMapper.insert(sqlDetail);
        } else if ("SYNC".equals(task.getTaskType()) && detail instanceof TaskSyncDetail) {
            TaskSyncDetail syncDetail = (TaskSyncDetail) detail;
            syncDetail.setTaskId(task.getId());
            taskSyncDetailMapper.insert(syncDetail);
        } else if ("QUALITY".equals(task.getTaskType()) && detail instanceof TaskQualityDetail) {
            TaskQualityDetail qualityDetail = (TaskQualityDetail) detail;
            qualityDetail.setTaskId(task.getId());
            taskQualityDetailMapper.insert(qualityDetail);
        }

        return task.getId();
    }

    /**
     * 更新任务
     */
    @Transactional
    public void updateTask(Task task, Object detail) {
        Task existTask = getById(task.getId());
        if (existTask == null || existTask.getDeleted() != null && existTask.getDeleted() == 1) {
            throw new IllegalArgumentException("任务不存在");
        }

        // 检查任务编码唯一性
        if (task.getTaskCode() != null && !task.getTaskCode().equals(existTask.getTaskCode())) {
            if (lambdaQuery().eq(Task::getTaskCode, task.getTaskCode())
                    .eq(Task::getDeleted, 0).count() > 0) {
                throw new IllegalArgumentException("任务编码已存在");
            }
        }

        // 检查 Cron 表达式是否变化
        boolean cronChanged = task.getCronExpression() != null
                && !task.getCronExpression().equals(existTask.getCronExpression());

        // 更新主表
        updateById(task);

        // 更新详情表
        if ("SQL".equals(task.getTaskType()) && detail instanceof TaskSqlDetail) {
            TaskSqlDetail sqlDetail = (TaskSqlDetail) detail;
            sqlDetail.setTaskId(task.getId());
            if (taskSqlDetailMapper.selectByTaskId(task.getId()) != null) {
                taskSqlDetailMapper.updateByTaskId(sqlDetail);
            } else {
                taskSqlDetailMapper.insert(sqlDetail);
            }
        } else if ("SYNC".equals(task.getTaskType()) && detail instanceof TaskSyncDetail) {
            TaskSyncDetail syncDetail = (TaskSyncDetail) detail;
            syncDetail.setTaskId(task.getId());
            if (taskSyncDetailMapper.selectByTaskId(task.getId()) != null) {
                taskSyncDetailMapper.updateByTaskId(syncDetail);
            } else {
                taskSyncDetailMapper.insert(syncDetail);
            }
        } else if ("QUALITY".equals(task.getTaskType()) && detail instanceof TaskQualityDetail) {
            TaskQualityDetail qualityDetail = (TaskQualityDetail) detail;
            qualityDetail.setTaskId(task.getId());
            if (taskQualityDetailMapper.selectByTaskId(task.getId()) != null) {
                taskQualityDetailMapper.updateByTaskId(qualityDetail);
            } else {
                taskQualityDetailMapper.insert(qualityDetail);
            }
        }

        // 如果 Cron 变化且任务已启用，需要重新调度
        if (cronChanged && existTask.getStatus() != null && existTask.getStatus() == 1) {
            rescheduleTask(task.getId());
        }
    }

    /**
     * 删除任务
     */
    @Transactional
    public void deleteTask(Long taskId) {
        Task task = getById(taskId);
        if (task == null || task.getDeleted() != null && task.getDeleted() == 1) {
            throw new IllegalArgumentException("任务不存在");
        }

        // 检查是否有运行中的执行记录
        long runningCount = taskExecutionService.getRunningCountByTaskId(taskId);
        if (runningCount > 0) {
            throw new IllegalArgumentException("任务有运行中的执行记录，无法删除");
        }

        // 检查是否有依赖关系
        if (taskDependencyService.hasDependency(taskId)) {
            throw new IllegalArgumentException("任务存在依赖关系，请先删除依赖");
        }

        // 取消调度
        if (task.getStatus() != null && task.getStatus() == 1) {
            cancelTaskSchedule(taskId);
        }

        // 逻辑删除
        removeById(taskId);
        log.info("任务删除成功: taskId={}", taskId);
    }

    /**
     * 切换任务状态（启用/停用）
     */
    @Transactional
    public void toggleTaskStatus(Long taskId) {
        Task task = getById(taskId);
        if (task == null || task.getDeleted() != null && task.getDeleted() == 1) {
            throw new IllegalArgumentException("任务不存在");
        }

        int newStatus = task.getStatus() != null && task.getStatus() == 1 ? 0 : 1;
        task.setStatus(newStatus);
        updateById(task);

        if (newStatus == 1) {
            // 启用：注册调度
            if (task.getCronExpression() != null && !task.getCronExpression().isEmpty()) {
                scheduleTask(task);
            }
        } else {
            // 停用：取消调度
            cancelTaskSchedule(taskId);
        }

        log.info("任务状态切换成功: taskId={}, newStatus={}", taskId, newStatus);
    }

    /**
     * 手动触发任务执行
     */
    public Long executeTask(Long taskId, Long triggerUserId) {
        Task task = getById(taskId);
        if (task == null) {
            throw new IllegalArgumentException("任务不存在");
        }
        if (task.getStatus() == null || task.getStatus() != 1) {
            throw new IllegalArgumentException("任务未启用");
        }

        // 调用调度器触发，获取实例标识
        String instanceId = schedulerManager.triggerTask(task);
        if (instanceId == null) {
            throw new IllegalStateException("任务未同步到调度器，无法执行");
        }

        // 若调度器已记录执行（如本地调度器），直接复用其 executionId
        TaskExecution existing = taskExecutionService.lambdaQuery()
                .eq(TaskExecution::getSchedulerInstanceId, instanceId)
                .one();
        if (existing != null) {
            log.info("任务手动触发成功: taskId={}, executionId={}, instanceId={}",
                    taskId, existing.getExecutionId(), instanceId);
            return java.lang.Long.parseLong(existing.getExecutionId());
        }

        // 否则生成 executionId 并记录（如 DolphinScheduler 等外部调度器）
        String executionId = generateExecutionId();
        taskExecutionService.recordManualExecution(task.getId(), executionId, instanceId, triggerUserId);

        log.info("任务手动触发成功: taskId={}, executionId={}, instanceId={}",
                taskId, executionId, instanceId);
        return java.lang.Long.parseLong(executionId);
    }

    /**
     * 重新调度任务
     */
    private void rescheduleTask(Long taskId) {
        Task task = getById(taskId);
        if (task == null) {
            return;
        }
        schedulerManager.rescheduleTask(task);
        updateById(task);
        log.info("任务重新调度成功: taskId={}", taskId);
    }

    /**
     * 调度任务
     */
    private void scheduleTask(Task task) {
        schedulerManager.scheduleTask(task);
        updateById(task);
        log.info("任务调度成功: taskId={}", task.getId());
    }

    /**
     * 取消任务调度
     */
    private void cancelTaskSchedule(Long taskId) {
        Task task = getById(taskId);
        if (task == null) {
            return;
        }
        schedulerManager.cancelTask(taskId);
        log.info("任务调度已取消: taskId={}", taskId);
    }

    /**
     * 生成任务编码
     */
    private String generateTaskCode() {
        return "TASK-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    /**
     * 生成执行ID（8位数字）
     */
    private String generateExecutionId() {
        return idGeneratorService.generateExecutionId();
    }

    /**
     * 获取数据源的表列表（用于同步任务配置）
     */
    public List<String> getDatasourceTables(Long datasourceId) {
        Datasource ds = datasourceMapper.selectById(datasourceId);
        if (ds == null || ds.getDeleted() != null && ds.getDeleted() == 1) {
            throw new IllegalArgumentException("数据源不存在");
        }
        DatasourceType type = DatasourceType.of(ds.getType());
        if (type == null) {
            throw new IllegalArgumentException("未知的数据源类型");
        }
        switch (type) {
            case MYSQL:
                return listMySQLTables(ds);
            case CLICKHOUSE:
                return listClickHouseTables(ds);
            case ELASTICSEARCH:
                return listElasticsearchIndices(ds);
            case KAFKA:
                return listKafkaTopics(ds);
            case LOCAL_EXCEL:
                return listExcelSheets(ds);
            default:
                throw new IllegalArgumentException("该数据源类型不支持获取表列表");
        }
    }

    /**
     * 获取MySQL表列表
     */
    private List<String> listMySQLTables(Datasource ds) {
        List<String> tables = new ArrayList<>();
        try (Connection conn = DriverManager.getConnection(ds.getHost() + ":" + ds.getPort() + "/" + ds.getDatabaseName(),
                ds.getUsername(), ds.getPassword());
             ResultSet rs = conn.getMetaData().getTables(ds.getDatabaseName(), null, "%", new String[]{"TABLE"})) {
            while (rs.next()) {
                tables.add(rs.getString("TABLE_NAME"));
            }
        } catch (SQLException e) {
            log.error("获取MySQL表列表失败", e);
            throw new RuntimeException("获取MySQL表列表失败: " + e.getMessage());
        }
        return tables;
    }

    /**
     * 获取ClickHouse表列表
     */
    private List<String> listClickHouseTables(Datasource ds) {
        List<String> tables = new ArrayList<>();
        try (Connection conn = DriverManager.getConnection("jdbc:clickhouse://" + ds.getHost() + ":" + ds.getPort() + "/" + ds.getDatabaseName(),
                ds.getUsername(), ds.getPassword());
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SHOW TABLES")) {
            while (rs.next()) {
                tables.add(rs.getString(1));
            }
        } catch (SQLException e) {
            log.error("获取ClickHouse表列表失败", e);
            throw new RuntimeException("获取ClickHouse表列表失败: " + e.getMessage());
        }
        return tables;
    }

    /**
     * 获取Elasticsearch索引列表
     */
    private List<String> listElasticsearchIndices(Datasource ds) {
        // 简化实现，实际需要使用Elasticsearch客户端
        return new ArrayList<>();
    }

    /**
     * 获取Kafka主题列表
     */
    private List<String> listKafkaTopics(Datasource ds) {
        // 简化实现，实际需要使用Kafka客户端
        return new ArrayList<>();
    }

    /**
     * 获取Excel工作表列表
     */
    private List<String> listExcelSheets(Datasource ds) {
        // 简化实现，实际需要使用Excel处理库
        return new ArrayList<>();
    }
}
