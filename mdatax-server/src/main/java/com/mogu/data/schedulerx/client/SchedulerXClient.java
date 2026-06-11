package com.mogu.data.schedulerx.client;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mogu.data.integration.entity.SqlTask;
import com.mogu.data.integration.entity.SqlTaskWorkflow;
import com.mogu.data.integration.entity.SyncTask;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * SchedulerX 客户端
 *
 * <p>封装对 SchedulerX 调度服务的 REST API 调用。
 * 负责将 MDataX 的任务注册为 SchedulerX 的 DAG 定义。</p>
 *
 * @author fengzhu
 */
@Slf4j
@Component
public class SchedulerXClient {

    @Value("${schedulerx.enabled:false}")
    private boolean enabled;

    @Value("${schedulerx.base-url:http://localhost:8081}")
    private String baseUrl;

    @Value("${schedulerx.callback-url:http://localhost:8080}")
    private String callbackUrl;

    @Value("${schedulerx.callback-secret:mdatax-dev-secret-change-in-production}")
    private String callbackSecret;

    private OkHttpClient httpClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @PostConstruct
    public void init() {
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .build();
    }

    /**
     * 注册同步任务为单节点 DAG
     *
     * @param task 同步任务
     * @return DAG ID
     */
    public String registerSyncTask(SyncTask task) {
        if (!enabled) {
            log.debug("[SchedulerXClient] 已禁用，跳过注册同步任务: {}", task.getId());
            return null;
        }

        String dagId = "sync_" + task.getId();
        Map<String, Object> dagDef = buildSingleNodeDag(
                dagId,
                task.getTaskName(),
                task.getCronExpression(),
                "SYNC",
                task.getId(),
                buildSyncTaskConfig(task)
        );

        return registerOrUpdateDag(dagId, dagDef);
    }

    /**
     * 注册 SQL 任务为单节点 DAG
     *
     * @param task SQL 任务
     * @return DAG ID
     */
    public String registerSqlTask(SqlTask task) {
        if (!enabled) {
            log.debug("[SchedulerXClient] 已禁用，跳过注册 SQL 任务: {}", task.getId());
            return null;
        }

        String dagId = "sql_" + task.getId();
        Map<String, Object> dagDef = buildSingleNodeDag(
                dagId,
                task.getTaskName(),
                task.getCronExpression(),
                "SQL",
                task.getId(),
                buildSqlTaskConfig(task)
        );

        return registerOrUpdateDag(dagId, dagDef);
    }

    /**
     * 注册 Workflow 为多节点 DAG
     *
     * @param workflow 工作流
     * @param taskMap  任务 ID -> 任务对象
     * @param depsMap  任务 ID -> 上游任务 ID 列表
     * @return DAG ID
     */
    public String registerWorkflow(SqlTaskWorkflow workflow,
                                    Map<Long, SqlTask> taskMap,
                                    Map<Long, List<Long>> depsMap) {
        if (!enabled) {
            log.debug("[SchedulerXClient] 已禁用，跳过注册工作流: {}", workflow.getId());
            return null;
        }

        String dagId = "wf_" + workflow.getId();
        Map<String, Object> dagDef = buildWorkflowDag(dagId, workflow, taskMap, depsMap);
        return registerOrUpdateDag(dagId, dagDef);
    }

    /**
     * 更新 DAG（重调度）
     */
    public void updateDag(String dagId, Map<String, Object> dagDef) {
        if (!enabled || dagId == null) {
            return;
        }
        try {
            String json = objectMapper.writeValueAsString(dagDef);
            RequestBody body = RequestBody.create(json, MediaType.parse("application/json"));
            Request request = new Request.Builder()
                    .url(baseUrl + "/api/v1/dags/" + dagId)
                    .put(body)
                    .build();
            execute(request);
        } catch (Exception e) {
            log.error("[SchedulerXClient] 更新 DAG 失败: dagId={}", dagId, e);
        }
    }

    /**
     * 删除 DAG
     */
    public void deleteDag(String dagId) {
        if (!enabled || dagId == null) {
            return;
        }
        try {
            Request request = new Request.Builder()
                    .url(baseUrl + "/api/v1/dags/" + dagId)
                    .delete()
                    .build();
            execute(request);
            log.info("[SchedulerXClient] 删除 DAG: {}", dagId);
        } catch (Exception e) {
            log.error("[SchedulerXClient] 删除 DAG 失败: dagId={}", dagId, e);
        }
    }

    /**
     * 启用 DAG（恢复 Quartz 调度）
     */
    public void enableDag(String dagId) {
        if (!enabled || dagId == null) {
            return;
        }
        try {
            RequestBody emptyBody = RequestBody.create("", MediaType.parse("application/json"));
            Request request = new Request.Builder()
                    .url(baseUrl + "/api/v1/dags/" + dagId + "/enable")
                    .post(emptyBody)
                    .build();
            execute(request);
            log.info("[SchedulerXClient] 启用 DAG: {}", dagId);
        } catch (Exception e) {
            log.error("[SchedulerXClient] 启用 DAG 失败: dagId={}", dagId, e);
        }
    }

    /**
     * 禁用 DAG（暂停 Quartz 调度）
     */
    public void disableDag(String dagId) {
        if (!enabled || dagId == null) {
            return;
        }
        try {
            RequestBody emptyBody = RequestBody.create("", MediaType.parse("application/json"));
            Request request = new Request.Builder()
                    .url(baseUrl + "/api/v1/dags/" + dagId + "/disable")
                    .post(emptyBody)
                    .build();
            execute(request);
            log.info("[SchedulerXClient] 禁用 DAG: {}", dagId);
        } catch (Exception e) {
            log.error("[SchedulerXClient] 禁用 DAG 失败: dagId={}", dagId, e);
        }
    }

    /**
     * 手动触发 DAG
     */
    public String triggerDag(String dagId) {
        if (!enabled || dagId == null) {
            return null;
        }
        try {
            RequestBody emptyBody = RequestBody.create("", MediaType.parse("application/json"));
            Request request = new Request.Builder()
                    .url(baseUrl + "/api/v1/dags/" + dagId + "/trigger")
                    .post(emptyBody)
                    .build();
            String response = execute(request);
            Map<String, Object> result = objectMapper.readValue(response, new TypeReference<Map<String, Object>>() {});
            Object instanceId = result.get("instanceId");
            return instanceId != null ? instanceId.toString() : null;
        } catch (Exception e) {
            log.error("[SchedulerXClient] 触发 DAG 失败: dagId={}", dagId, e);
            return null;
        }
    }

    /**
     * 杀除实例
     */
    public void killInstance(String instanceId) {
        if (!enabled || instanceId == null) {
            return;
        }
        try {
            RequestBody emptyBody = RequestBody.create("", MediaType.parse("application/json"));
            Request request = new Request.Builder()
                    .url(baseUrl + "/api/v1/instances/" + instanceId + "/kill")
                    .post(emptyBody)
                    .build();
            execute(request);
            log.info("[SchedulerXClient] 杀除实例: {}", instanceId);
        } catch (Exception e) {
            log.error("[SchedulerXClient] 杀除实例失败: instanceId={}", instanceId, e);
        }
    }

    /**
     * 重试实例失败任务
     */
    public void retryInstance(String instanceId) {
        if (!enabled || instanceId == null) {
            return;
        }
        try {
            RequestBody emptyBody = RequestBody.create("", MediaType.parse("application/json"));
            Request request = new Request.Builder()
                    .url(baseUrl + "/api/v1/instances/" + instanceId + "/retry")
                    .post(emptyBody)
                    .build();
            execute(request);
            log.info("[SchedulerXClient] 重试实例: {}", instanceId);
        } catch (Exception e) {
            log.error("[SchedulerXClient] 重试实例失败: instanceId={}", instanceId, e);
        }
    }

    /**
     * 查询 DAG 实例列表
     */
    public String listInstances(String dagId, int pageNum, int pageSize) {
        if (!enabled || dagId == null) {
            return null;
        }
        try {
            String url = baseUrl + "/api/v1/dags/" + dagId + "/instances?pageNum=" + pageNum + "&pageSize=" + pageSize;
            Request request = new Request.Builder()
                    .url(url)
                    .get()
                    .build();
            return execute(request);
        } catch (Exception e) {
            log.error("[SchedulerXClient] 查询实例列表失败: dagId={}", dagId, e);
            return null;
        }
    }

    /**
     * 回调状态
     */
    public void callback(String taskInstanceId, boolean success, Object output) {
        if (!enabled || taskInstanceId == null) {
            return;
        }
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("status", success ? "SUCCESS" : "FAILURE");
            if (output != null) {
                body.put("output", objectMapper.writeValueAsString(output));
            }

            String json = objectMapper.writeValueAsString(body);
            RequestBody requestBody = RequestBody.create(json, MediaType.parse("application/json"));
            Request request = new Request.Builder()
                    .url(baseUrl + "/api/v1/callback/" + taskInstanceId)
                    .post(requestBody)
                    .build();
            execute(request);
            log.debug("[SchedulerXClient] 回调成功: taskInstanceId={}, success={}", taskInstanceId, success);
        } catch (Exception e) {
            log.error("[SchedulerXClient] 回调失败: taskInstanceId={}", taskInstanceId, e);
        }
    }

    // ============ private methods ============

    private String registerOrUpdateDag(String dagId, Map<String, Object> dagDef) {
        try {
            // 先尝试注册（POST）
            String json = objectMapper.writeValueAsString(dagDef);
            RequestBody body = RequestBody.create(json, MediaType.parse("application/json"));
            Request createRequest = new Request.Builder()
                    .url(baseUrl + "/api/v1/dags")
                    .post(body)
                    .build();

            try (Response response = httpClient.newCall(createRequest).execute()) {
                if (response.isSuccessful()) {
                    log.info("[SchedulerXClient] 注册 DAG 成功: {}", dagId);
                    return dagId;
                }
                // 如果 400 且 DAG 已存在，则更新
                if (response.code() == 400) {
                    String respBody = response.body() != null ? response.body().string() : "";
                    if (respBody.contains("已存在")) {
                        return updateExistingDag(dagId, dagDef);
                    }
                }
            }

            // 如果 POST 失败（可能是已存在），尝试更新
            return updateExistingDag(dagId, dagDef);

        } catch (Exception e) {
            log.error("[SchedulerXClient] 注册 DAG 失败: dagId={}", dagId, e);
            return null;
        }
    }

    private String updateExistingDag(String dagId, Map<String, Object> dagDef) {
        try {
            String json = objectMapper.writeValueAsString(dagDef);
            RequestBody body = RequestBody.create(json, MediaType.parse("application/json"));
            Request request = new Request.Builder()
                    .url(baseUrl + "/api/v1/dags/" + dagId)
                    .put(body)
                    .build();
            execute(request);
            log.info("[SchedulerXClient] 更新 DAG 成功: {}", dagId);
            return dagId;
        } catch (Exception e) {
            log.error("[SchedulerXClient] 更新 DAG 失败: dagId={}", dagId, e);
            return null;
        }
    }

    private Map<String, Object> buildSingleNodeDag(String dagId, String dagName,
                                                    String cron, String taskType,
                                                    Long taskId, Map<String, Object> taskConfig) {
        Map<String, Object> dag = new HashMap<>();
        dag.put("dagId", dagId);
        dag.put("dagName", dagName);
        dag.put("owner", "mdatax");
        dag.put("cronExpression", cron);
        dag.put("failureStrategy", "STOP_ALL");
        dag.put("timeoutSeconds", 3600);

        List<Map<String, Object>> tasks = new ArrayList<>();
        Map<String, Object> task = new HashMap<>();
        task.put("taskId", "task_" + taskId);
        task.put("taskName", dagName);
        task.put("taskType", "HTTP_CALLBACK");
        task.put("taskConfig", taskConfig);
        tasks.add(task);
        dag.put("tasks", tasks);

        return dag;
    }

    private Map<String, Object> buildWorkflowDag(String dagId, SqlTaskWorkflow workflow,
                                                  Map<Long, SqlTask> taskMap,
                                                  Map<Long, List<Long>> depsMap) {
        Map<String, Object> dag = new HashMap<>();
        dag.put("dagId", dagId);
        dag.put("dagName", workflow.getWorkflowName());
        dag.put("owner", "mdatax");
        dag.put("cronExpression", workflow.getCronExpression());
        dag.put("failureStrategy", "STOP_ALL");
        dag.put("timeoutSeconds", 3600);

        List<Map<String, Object>> tasks = new ArrayList<>();
        for (SqlTask sqlTask : taskMap.values()) {
            Map<String, Object> task = new HashMap<>();
            task.put("taskId", "task_" + sqlTask.getId());
            task.put("taskName", sqlTask.getTaskName());
            task.put("taskType", "HTTP_CALLBACK");
            task.put("taskConfig", buildSqlTaskConfig(sqlTask));

            List<Long> upstreamIds = depsMap.get(sqlTask.getId());
            if (upstreamIds != null && !upstreamIds.isEmpty()) {
                List<String> upstream = new ArrayList<>();
                for (Long upId : upstreamIds) {
                    upstream.add("task_" + upId);
                }
                task.put("upstream", upstream);
            }
            tasks.add(task);
        }
        dag.put("tasks", tasks);

        return dag;
    }

    private Map<String, Object> buildSyncTaskConfig(SyncTask task) {
        Map<String, Object> config = new HashMap<>();
        config.put("url", callbackUrl + "/api/internal/task/execute");
        config.put("method", "POST");
        Map<String, Object> body = new HashMap<>();
        body.put("taskType", "SYNC");
        body.put("taskId", task.getId());
        body.put("secret", callbackSecret);
        config.put("body", body);
        config.put("timeout", 1800);
        return config;
    }

    private Map<String, Object> buildSqlTaskConfig(SqlTask task) {
        Map<String, Object> config = new HashMap<>();
        config.put("url", callbackUrl + "/api/internal/task/execute");
        config.put("method", "POST");
        Map<String, Object> body = new HashMap<>();
        body.put("taskType", "SQL");
        body.put("taskId", task.getId());
        body.put("secret", callbackSecret);
        config.put("body", body);
        config.put("timeout", 600);
        return config;
    }

    private String execute(Request request) throws IOException {
        try (Response response = httpClient.newCall(request).execute()) {
            String body = response.body() != null ? response.body().string() : "";
            if (!response.isSuccessful()) {
                throw new IOException("HTTP " + response.code() + ": " + body);
            }
            return body;
        }
    }

}
