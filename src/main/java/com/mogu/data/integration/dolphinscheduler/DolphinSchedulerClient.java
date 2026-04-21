package com.mogu.data.integration.dolphinscheduler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.*;

/**
 * DolphinScheduler REST API 客户端
 *
 * <p>封装 DS 3.x 的核心 API，包括工作流定义、定时调度、实例管理等。
 *
 * @author fengzhu
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DolphinSchedulerClient {

    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private final DolphinSchedulerProperties props;
    private final ObjectMapper objectMapper;

    private final OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
            .build();

    // ==================== 工作流定义 ====================

    /**
     * 创建单节点工作流（用于普通任务调度）
     *
     * @param name     工作流名称
     * @param taskType 任务类型: SYNC / SQL
     * @param taskId   任务ID
     * @return 工作流定义编码
     */
    public Long createSingleNodeProcess(String name, String taskType, Long taskId) {
        long taskCode = generateTaskCode();
        String rawScript = buildCallbackScript(taskType, taskId);

        // 构建 TaskNode
        Map<String, Object> taskNode = buildShellTaskNode(taskCode, name, rawScript);

        // 构建 TaskRelation
        List<Map<String, Object>> taskRelations = new ArrayList<>();
        Map<String, Object> relation = new HashMap<>();
        relation.put("name", "");
        relation.put("preTaskCode", 0);
        relation.put("postTaskCode", taskCode);
        relation.put("preTaskVersion", 0);
        relation.put("postTaskVersion", 1);
        relation.put("conditionType", "NONE");
        relation.put("conditionParams", new HashMap<>());
        taskRelations.add(relation);

        // 构建 locations（DS 3.2 要求为数组字符串）
        List<Map<String, Object>> locationList = new ArrayList<>();
        Map<String, Object> location = new HashMap<>();
        location.put("taskCode", taskCode);
        location.put("x", 100);
        location.put("y", 100);
        locationList.add(location);

        Map<String, Object> request = new HashMap<>();
        request.put("name", name);
        request.put("description", "Created by MDataX");
        request.put("globalParams", "[]");
        request.put("taskDefinitionJson", toJson(Collections.singletonList(taskNode)));
        request.put("taskRelationJson", toJson(taskRelations));
        request.put("tenantCode", props.getTenantCode());
        request.put("timeout", 0);
        request.put("locations", toJson(locationList));
        request.put("executionType", "SERIAL_WAIT");

        String url = buildUrl(String.format("/projects/%d/process-definition", props.getProjectCode()));
        log.info("DS 创建工作流请求: url={}, params={}", url, request);
        String response = postForm(url, request);
        log.info("DS 创建工作流响应: {}", response);
        return parseProcessCode(response);
    }

    private Map<String, Object> buildShellTaskNode(long taskCode, String name, String rawScript) {
        Map<String, Object> taskNode = new HashMap<>();
        taskNode.put("code", taskCode);
        taskNode.put("name", name);
        taskNode.put("version", 1);
        taskNode.put("description", "");
        taskNode.put("delayTime", 0);
        taskNode.put("taskType", "SHELL");
        taskNode.put("flag", "YES");
        taskNode.put("taskPriority", "MEDIUM");
        taskNode.put("workerGroup", props.getWorkerGroup());
        taskNode.put("environmentCode", -1);
        taskNode.put("failRetryTimes", 0);
        taskNode.put("failRetryInterval", 1);
        taskNode.put("timeoutFlag", "CLOSE");
        taskNode.put("timeoutNotifyStrategy", "");
        taskNode.put("timeout", 0);
        taskNode.put("isCache", "NO");
        taskNode.put("taskGroupId", 0);
        taskNode.put("taskGroupPriority", 0);
        taskNode.put("cpuQuota", -1);
        taskNode.put("memoryMax", -1);
        taskNode.put("taskExecuteType", "BATCH");

        Map<String, Object> taskParams = new HashMap<>();
        taskParams.put("resourceList", new ArrayList<>());
        taskParams.put("localParams", new ArrayList<>());
        taskParams.put("rawScript", rawScript);
        taskParams.put("dependence", "");
        taskParams.put("programType", "SHELL");

        taskNode.put("taskParams", taskParams);
        return taskNode;
    }

    /**
     * 更新单节点工作流
     */
    public void updateSingleNodeProcess(Long processCode, String name, String taskType, Long taskId) {
        long taskCode = generateTaskCode();
        String rawScript = buildCallbackScript(taskType, taskId);

        Map<String, Object> taskNode = buildShellTaskNode(taskCode, name, rawScript);

        List<Map<String, Object>> taskRelations = new ArrayList<>();
        Map<String, Object> relation = new HashMap<>();
        relation.put("name", "");
        relation.put("preTaskCode", 0);
        relation.put("postTaskCode", taskCode);
        relation.put("preTaskVersion", 0);
        relation.put("postTaskVersion", 1);
        relation.put("conditionType", "NONE");
        relation.put("conditionParams", new HashMap<>());
        taskRelations.add(relation);

        List<Map<String, Object>> locationList = new ArrayList<>();
        Map<String, Object> location = new HashMap<>();
        location.put("taskCode", taskCode);
        location.put("x", 100);
        location.put("y", 100);
        locationList.add(location);

        Map<String, Object> request = new HashMap<>();
        request.put("name", name);
        request.put("description", "Updated by MDataX");
        request.put("globalParams", "[]");
        request.put("taskDefinitionJson", toJson(Collections.singletonList(taskNode)));
        request.put("taskRelationJson", toJson(taskRelations));
        request.put("tenantCode", props.getTenantCode());
        request.put("timeout", 0);
        request.put("locations", toJson(locationList));
        request.put("executionType", "SERIAL_WAIT");

        String url = buildUrl(String.format("/projects/%d/process-definition/%d",
                props.getProjectCode(), processCode));
        log.info("DS 更新工作流请求: url={}, params={}", url, request);
        putForm(url, request);
    }

    /**
     * 发布或下线工作流
     *
     * @param processCode  工作流编码
     * @param releaseState ONLINE / OFFLINE
     */
    public void releaseProcess(Long processCode, String releaseState) {
        Map<String, Object> request = new HashMap<>();
        request.put("releaseState", releaseState);

        String url = buildUrl(String.format("/projects/%d/process-definition/%d/release",
                props.getProjectCode(), processCode));
        postForm(url, request);
        log.info("DS 工作流已{}: processCode={}", "ONLINE".equals(releaseState) ? "上线" : "下线", processCode);
    }

    /**
     * 删除工作流定义
     */
    public void deleteProcess(Long processCode) {
        String url = buildUrl(String.format("/projects/%d/process-definition/%d",
                props.getProjectCode(), processCode));
        delete(url);
        log.info("DS 工作流已删除: processCode={}", processCode);
    }

    // ==================== 定时调度 ====================

    /**
     * 创建定时调度
     *
     * @param processCode    工作流编码
     * @param cronExpression Quartz Cron 表达式（6位: 秒 分 时 日 月 周）
     * @return 调度ID
     */
    public Integer createSchedule(Long processCode, String cronExpression) {
        Map<String, Object> schedule = new HashMap<>();
        schedule.put("crontab", cronExpression);
        schedule.put("startTime", "2024-01-01 00:00:00");
        schedule.put("endTime", "2099-12-31 23:59:59");
        schedule.put("timezoneId", "Asia/Shanghai");

        Map<String, Object> request = new HashMap<>();
        request.put("processDefinitionCode", processCode);
        request.put("schedule", toJson(schedule));

        String url = buildUrl(String.format("/projects/%d/schedules",
                props.getProjectCode()));
        String response = postForm(url, request);
        return parseScheduleId(response);
    }

    /**
     * 更新定时调度
     */
    public void updateSchedule(Long processCode, Integer scheduleId, String cronExpression) {
        Map<String, Object> schedule = new HashMap<>();
        schedule.put("crontab", cronExpression);
        schedule.put("startTime", "2024-01-01 00:00:00");
        schedule.put("endTime", "2099-12-31 23:59:59");
        schedule.put("timezoneId", "Asia/Shanghai");

        Map<String, Object> request = new HashMap<>();
        request.put("schedule", toJson(schedule));

        String url = buildUrl(String.format("/projects/%d/schedules/%d",
                props.getProjectCode(), scheduleId));
        putForm(url, request);
    }

    /**
     * 上线定时调度
     */
    public void onlineSchedule(Long processCode, Integer scheduleId) {
        String url = buildUrl(String.format("/projects/%d/schedules/%d/online",
                props.getProjectCode(), scheduleId));
        postForm(url, new HashMap<>());
        log.info("DS 定时调度已上线: processCode={}, scheduleId={}", processCode, scheduleId);
    }

    /**
     * 下线定时调度
     */
    public void offlineSchedule(Long processCode, Integer scheduleId) {
        String url = buildUrl(String.format("/projects/%d/schedules/%d/offline",
                props.getProjectCode(), scheduleId));
        postForm(url, new HashMap<>());
        log.info("DS 定时调度已下线: processCode={}, scheduleId={}", processCode, scheduleId);
    }

    // ==================== 实例管理 ====================

    /**
     * 手动启动工作流实例（立即执行）
     *
     * @param processCode 工作流编码
     * @return 实例ID
     */
    public Long startProcessInstance(Long processCode) {
        Map<String, Object> request = new HashMap<>();
        request.put("processDefinitionCode", processCode);
        request.put("scheduleTime", "");
        request.put("runMode", "RUN_MODE_SERIAL");
        request.put("warningType", "NONE");
        request.put("warningGroupId", 0);
        request.put("execType", null);
        request.put("startNodeList", null);
        request.put("taskDependType", "TASK_POST");
        request.put("complementDependentMode", "OFF_MODE");
        request.put("failureStrategy", "END");
        request.put("processInstancePriority", "MEDIUM");
        request.put("workerGroup", props.getWorkerGroup());
        request.put("environmentCode", -1);
        request.put("startParams", null);
        request.put("expectedParallelismNumber", "");
        request.put("dryRun", 0);
        request.put("tenantCode", props.getTenantCode());

        String url = buildUrl(String.format("/projects/%d/executors/start-process-instance",
                props.getProjectCode()));
        String response = postForm(url, request);
        return parseInstanceId(response);
    }

    // ==================== 工具方法 ====================

    private String buildCallbackScript(String taskType, Long taskId) {
        return String.format(
                "curl -s -X POST %s/api/internal/task/execute " +
                        "-H \"Content-Type: application/json\" " +
                        "-d \"{\\\"taskType\\\":\\\"%s\\\",\\\"taskId\\\":%d,\\\"secret\\\":\\\"%s\\\",\\\"instanceId\\\":\\\"$processInstanceId\\\"}\" " +
                        "--max-time 7200",
                props.getCallbackUrl(), taskType, taskId, props.getCallbackSecret()
        );
    }

    private String buildUrl(String path) {
        String base = props.getBaseUrl();
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base + path;
    }

    private long generateTaskCode() {
        // DS 要求 taskCode 为正 long 且在合理范围内
        long code = System.currentTimeMillis();
        if (code < 0) {
            code = Math.abs(code);
        }
        return code;
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return obj.toString();
        }
    }

    // ==================== HTTP 请求封装 ====================

    private String postJson(String url, Map<String, Object> body) {
        return executeRequest("POST", url, body);
    }

    private String putJson(String url, Map<String, Object> body) {
        return executeRequest("PUT", url, body);
    }

    private void delete(String url) {
        Request request = new Request.Builder()
                .url(url)
                .delete()
                .addHeader("token", props.getToken())
                .build();
        execute(request);
    }

    private String postForm(String url, Map<String, Object> params) {
        FormBody.Builder builder = new FormBody.Builder();
        params.forEach((k, v) -> {
            if (v != null) {
                builder.add(k, v.toString());
            }
        });
        Request request = new Request.Builder()
                .url(url)
                .post(builder.build())
                .addHeader("token", props.getToken())
                .build();
        return execute(request);
    }

    private String putForm(String url, Map<String, Object> params) {
        FormBody.Builder builder = new FormBody.Builder();
        params.forEach((k, v) -> {
            if (v != null) {
                builder.add(k, v.toString());
            }
        });
        Request request = new Request.Builder()
                .url(url)
                .put(builder.build())
                .addHeader("token", props.getToken())
                .build();
        return execute(request);
    }

    private String executeRequest(String method, String url, Map<String, Object> body) {
        try {
            String json = objectMapper.writeValueAsString(body);
            RequestBody requestBody = RequestBody.create(json, JSON);
            Request request = new Request.Builder()
                    .url(url)
                    .method(method, requestBody)
                    .addHeader("Content-Type", "application/json")
                    .addHeader("token", props.getToken())
                    .build();
            return execute(request);
        } catch (Exception e) {
            throw new RuntimeException("DS API 请求序列化失败: " + url, e);
        }
    }

    private String execute(Request request) {
        try (Response response = httpClient.newCall(request).execute()) {
            String body = response.body() != null ? response.body().string() : "";
            if (!response.isSuccessful()) {
                log.error("DS API 请求失败: {} {} → {}\n{}",
                        request.method(), request.url(), response.code(), body);
                throw new RuntimeException("DS API 请求失败: HTTP " + response.code() + ", " + body);
            }
            log.debug("DS API 请求成功: {} {} → {}", request.method(), request.url(), response.code());
            return body;
        } catch (IOException e) {
            throw new RuntimeException("DS API 请求异常: " + request.url(), e);
        }
    }

    // ==================== 响应解析 ====================

    private Long parseProcessCode(String response) {
        try {
            JsonNode node = objectMapper.readTree(response);
            if (node.has("data") && node.get("data").has("code")) {
                return node.get("data").get("code").asLong();
            }
            throw new RuntimeException("无法从响应中解析 processCode: " + response);
        } catch (IOException e) {
            throw new RuntimeException("解析 DS 响应失败", e);
        }
    }

    private Integer parseScheduleId(String response) {
        try {
            JsonNode node = objectMapper.readTree(response);
            if (node.has("data") && node.get("data").has("id")) {
                return node.get("data").get("id").asInt();
            }
            throw new RuntimeException("无法从响应中解析 scheduleId: " + response);
        } catch (IOException e) {
            throw new RuntimeException("解析 DS 响应失败", e);
        }
    }

    private Long parseInstanceId(String response) {
        try {
            JsonNode node = objectMapper.readTree(response);
            if (node.has("data")) {
                return node.get("data").asLong();
            }
            throw new RuntimeException("无法从响应中解析 instanceId: " + response);
        } catch (IOException e) {
            throw new RuntimeException("解析 DS 响应失败", e);
        }
    }
}
