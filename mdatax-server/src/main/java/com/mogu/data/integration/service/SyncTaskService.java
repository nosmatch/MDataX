package com.mogu.data.integration.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mogu.data.integration.entity.Datasource;
import com.mogu.data.integration.entity.SyncTask;
import com.mogu.data.integration.enums.DatasourceType;
import com.mogu.data.integration.mapper.DatasourceMapper;
import com.mogu.data.integration.mapper.SqlTaskDependencyMapper;
import com.mogu.data.integration.mapper.SyncTaskMapper;
import com.mogu.data.integration.scheduler.TaskSchedulerManager;
import com.mogu.data.common.LoginUser;
import com.mogu.data.integration.vo.SyncTaskVO;
import com.mogu.data.system.entity.User;
import com.mogu.data.system.mapper.UserMapper;
import com.mogu.data.system.service.TaskCollaboratorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.TimeUnit;

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
    private final SqlTaskDependencyMapper sqlTaskDependencyMapper;
    private final SqlTaskWorkflowService workflowService;
    private final TaskCollaboratorService collaboratorService;
    private final UserMapper userMapper;

    @Qualifier("clickHouseJdbcTemplate")
    private final JdbcTemplate clickHouseJdbcTemplate;

    public Page<SyncTaskVO> pageTasks(String keyword, long page, long size) {
        Page<SyncTask> taskPage = page(new Page<>(page, size), new LambdaQueryWrapper<SyncTask>()
                .eq(SyncTask::getDeleted, 0)
                .like(StringUtils.hasText(keyword), SyncTask::getTaskName, keyword)
                .orderByDesc(SyncTask::getCreateTime));

        // 批量查询工作流名称，避免 N+1
        java.util.Map<Long, String> workflowNameMap = new java.util.HashMap<>();
        for (SyncTask task : taskPage.getRecords()) {
            if (task.getWorkflowId() != null && !workflowNameMap.containsKey(task.getWorkflowId())) {
                com.mogu.data.integration.entity.SqlTaskWorkflow wf = workflowService.getById(task.getWorkflowId());
                workflowNameMap.put(task.getWorkflowId(), wf != null ? wf.getWorkflowName() : null);
            }
        }

        Long currentUserId = LoginUser.currentUserId();

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
            vo.setWorkflowId(task.getWorkflowId());
            vo.setWorkflowName(workflowNameMap.get(task.getWorkflowId()));
            vo.setLastSyncTime(task.getLastSyncTime());
            vo.setCreateTime(task.getCreateTime());
            vo.setUpdateTime(task.getUpdateTime());
            vo.setCreateUserId(task.getCreateUserId());

            // 创建人名称
            if (task.getCreateUserId() != null) {
                User creator = userMapper.selectById(task.getCreateUserId());
                if (creator != null) {
                    vo.setCreateUserName(creator.getNickname() != null ? creator.getNickname() : creator.getUsername());
                }
            }

            // 操作权限
            vo.setCanOperate(collaboratorService.canOperate(task.getId(), "SYNC", currentUserId, task.getCreateUserId()));

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
        validateSyncTaskDatasource(task.getDatasourceId());
        if (lambdaQuery().eq(SyncTask::getTaskName, task.getTaskName()).eq(SyncTask::getDeleted, 0).count() > 0) {
            throw new IllegalArgumentException("任务名称已存在");
        }
        task.setCreateUserId(LoginUser.currentUserId());
        if (task.getWorkflowId() != null) {
            // ========== Workflow 内任务 ==========
            task.setCronExpression(null);
            task.setDsProcessCode(null);
            task.setDsScheduleId(null);
            task.setStatus(0);
            save(task);
            // 触发 Workflow 同步到 DS
            workflowService.syncToDs(task.getWorkflowId());
        } else {
            // ========== 独立任务 ==========
            task.setStatus(0);
            save(task);
            // 调度由 toggleStatus 统一控制，创建时不自动注册
        }
    }

    public void updateTask(SyncTask task) {
        SyncTask exist = getById(task.getId());
        if (exist == null) {
            throw new IllegalArgumentException("任务不存在");
        }
        checkPermission(exist);
        // 不允许变更所属 Workflow
        if (task.getWorkflowId() != null && !task.getWorkflowId().equals(exist.getWorkflowId())) {
            throw new IllegalArgumentException("不允许变更任务所属工作流");
        }
        if (task.getDatasourceId() != null) {
            validateSyncTaskDatasource(task.getDatasourceId());
        }
        if (StringUtils.hasText(task.getTaskName()) && !exist.getTaskName().equals(task.getTaskName())) {
            if (lambdaQuery().eq(SyncTask::getTaskName, task.getTaskName()).eq(SyncTask::getDeleted, 0).count() > 0) {
                throw new IllegalArgumentException("任务名称已存在");
            }
        }

        if (exist.getWorkflowId() != null) {
            // ========== Workflow 内任务 ==========
            updateById(task);
            // 触发 Workflow 同步（节点名称等可能变化）
            workflowService.syncToDs(exist.getWorkflowId());
        } else {
            // ========== 独立任务 ==========
            String newCron = task.getCronExpression();
            String oldCron = exist.getCronExpression();
            boolean cronChanged = (newCron == null && oldCron != null)
                    || (newCron != null && !newCron.equals(oldCron));
            updateById(task);

            // 如果 Cron 变化且任务处于启用状态，重新调度
            if (cronChanged && exist.getStatus() != null && exist.getStatus() == 1) {
                SyncTask updated = getById(task.getId());
                schedulerManager.rescheduleSyncTask(updated);
                updateById(updated); // 保存新的 ds_process_code / ds_schedule_id
            }
        }
    }

    public SyncTask toggleStatus(Long taskId) {
        SyncTask task = getById(taskId);
        if (task == null || task.getDeleted() != null && task.getDeleted() == 1) {
            throw new IllegalArgumentException("任务不存在");
        }
        checkPermission(task);
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
        checkPermission(task);
        if (task.getWorkflowId() != null) {
            // ========== Workflow 内任务 ==========
            List<Long> downstream = sqlTaskDependencyMapper.selectDownstreamTaskIds(taskId);
            if (!downstream.isEmpty()) {
                throw new IllegalArgumentException("该任务被工作流内其他任务依赖，无法删除");
            }
            sqlTaskDependencyMapper.deleteByTaskId(taskId);
            removeById(taskId);
            workflowService.syncToDs(task.getWorkflowId());
        } else {
            // ========== 独立任务 ==========
            if (task.getStatus() != null && task.getStatus() == 1) {
                throw new IllegalArgumentException("启用状态的任务不能删除，请先停用");
            }
            schedulerManager.deleteSyncTask(taskId);
            removeById(taskId);
        }
    }

    public List<String> listTables(Long datasourceId) {
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

    private List<String> listMySQLTables(Datasource ds) {
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
            log.warn("获取MySQL表列表失败: datasourceId={}, error={}", ds.getId(), e.getMessage());
            throw new IllegalArgumentException("连接数据源失败: " + e.getMessage());
        }
        return tables;
    }

    private List<String> listClickHouseTables(Datasource ds) {
        List<String> tables = new ArrayList<>();
        try {
            List<String> rows = clickHouseJdbcTemplate.queryForList(
                    "SHOW TABLES FROM " + ds.getDatabaseName(), String.class);
            tables.addAll(rows);
        } catch (Exception e) {
            log.warn("获取ClickHouse表列表失败: datasourceId={}, error={}", ds.getId(), e.getMessage());
            throw new IllegalArgumentException("连接ClickHouse失败: " + e.getMessage());
        }
        return tables;
    }

    private List<String> listElasticsearchIndices(Datasource ds) {
        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .build();
        Request.Builder builder = new Request.Builder()
                .url("http://" + ds.getHost() + ":" + ds.getPort() + "/_cat/indices?format=json");
        boolean hasAuth = StringUtils.hasText(ds.getUsername()) && StringUtils.hasText(ds.getPassword());
        if (hasAuth) {
            builder.header("Authorization", okhttp3.Credentials.basic(ds.getUsername(), ds.getPassword()));
        }
        try (Response response = client.newCall(builder.build()).execute()) {
            if (response.code() == 401) {
                if (!hasAuth) {
                    throw new IllegalArgumentException("ES需要认证，请填写用户名和密码");
                } else {
                    throw new IllegalArgumentException("ES认证失败，请检查用户名和密码");
                }
            }
            if (!response.isSuccessful()) {
                throw new IllegalArgumentException("ES请求失败: HTTP " + response.code());
            }
            String body = response.body().string();
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            java.util.List<java.util.Map<String, Object>> list = mapper.readValue(body,
                    new com.fasterxml.jackson.core.type.TypeReference<java.util.List<java.util.Map<String, Object>>>() {});
            List<String> indices = new ArrayList<>();
            for (java.util.Map<String, Object> item : list) {
                Object index = item.get("index");
                if (index != null && !index.toString().startsWith(".")) {
                    indices.add(index.toString());
                }
            }
            return indices;
        } catch (Exception e) {
            log.warn("获取ES索引列表失败: datasourceId={}, error={}", ds.getId(), e.getMessage());
            throw new IllegalArgumentException("连接ES失败: " + e.getMessage());
        }
    }

    private List<String> listKafkaTopics(Datasource ds) {
        Properties props = new Properties();
        props.put("bootstrap.servers", ds.getHost() + ":" + ds.getPort());
        props.put("connections.max.idle.ms", 5000);
        props.put("request.timeout.ms", 5000);
        try (org.apache.kafka.clients.admin.AdminClient admin = org.apache.kafka.clients.admin.AdminClient.create(props)) {
            Set<String> topics = admin.listTopics().names().get(5, TimeUnit.SECONDS);
            return new ArrayList<>(topics);
        } catch (Exception e) {
            log.warn("获取Kafka Topic列表失败: datasourceId={}, error={}", ds.getId(), e.getMessage());
            throw new IllegalArgumentException("连接Kafka失败: " + e.getMessage());
        }
    }

    private List<String> listExcelSheets(Datasource ds) {
        String filePath = ds.getExtraConfig();
        if (!StringUtils.hasText(filePath)) {
            throw new IllegalArgumentException("Excel数据源未配置文件路径");
        }
        File file = new File(filePath);
        if (!file.exists() || !file.isFile()) {
            throw new IllegalArgumentException("Excel文件不存在: " + filePath);
        }
        List<String> sheets = new ArrayList<>();
        try (Workbook workbook = WorkbookFactory.create(file)) {
            for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
                sheets.add(workbook.getSheetName(i));
            }
        } catch (Exception e) {
            log.warn("读取Excel文件失败: filePath={}, error={}", filePath, e.getMessage());
            throw new IllegalArgumentException("读取Excel文件失败: " + e.getMessage());
        }
        return sheets;
    }

    private void validateSyncTaskDatasource(Long datasourceId) {
        Datasource ds = datasourceMapper.selectById(datasourceId);
        if (ds == null || ds.getDeleted() != null && ds.getDeleted() == 1) {
            throw new IllegalArgumentException("数据源不存在");
        }
    }

    private void checkPermission(SyncTask task) {
        Long userId = LoginUser.currentUserId();
        if (!collaboratorService.canOperate(task.getId(), "SYNC", userId, task.getCreateUserId())) {
            throw new IllegalArgumentException("无权操作该任务，仅创建人、协作者或管理员可操作");
        }
    }

}
