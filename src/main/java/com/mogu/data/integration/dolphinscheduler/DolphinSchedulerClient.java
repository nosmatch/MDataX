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
    public Long createSingleNodeProcess(String name, String taskType, Long taskId, Long taskCode) {
        long tc = taskCode != null ? taskCode : generateTaskCode();
        String rawScript = buildCallbackScript(taskType, taskId);

        // 构建 TaskNode
        Map<String, Object> taskNode = buildShellTaskNode(tc, name, rawScript);

        // 构建 TaskRelation
        List<Map<String, Object>> taskRelations = new ArrayList<>();
        Map<String, Object> relation = new HashMap<>();
        relation.put("name", "");
        relation.put("preTaskCode", 0);
        relation.put("postTaskCode", tc);
        relation.put("preTaskVersion", 0);
        relation.put("postTaskVersion", 1);
        relation.put("conditionType", "NONE");
        relation.put("conditionParams", new HashMap<>());
        taskRelations.add(relation);

        // 构建 locations（DS 3.2 要求为数组字符串）
        List<Map<String, Object>> locationList = new ArrayList<>();
        Map<String, Object> location = new HashMap<>();
        location.put("taskCode", tc);
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
        return buildShellTaskNode(taskCode, name, rawScript, null);
    }

    private Map<String, Object> buildShellTaskNode(long taskCode, String name, String rawScript, Long taskDefId) {
        Map<String, Object> taskNode = new HashMap<>();
        if (taskDefId != null) {
            taskNode.put("id", taskDefId);
        }
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
    public void updateSingleNodeProcess(Long processCode, String name, String taskType, Long taskId, Long taskCode) {
        long tc = taskCode != null ? taskCode : generateTaskCode();
        String rawScript = buildCallbackScript(taskType, taskId);

        Map<String, Object> taskNode = buildShellTaskNode(tc, name, rawScript);

        List<Map<String, Object>> taskRelations = new ArrayList<>();
        Map<String, Object> relation = new HashMap<>();
        relation.put("name", "");
        relation.put("preTaskCode", 0);
        relation.put("postTaskCode", tc);
        relation.put("preTaskVersion", 0);
        relation.put("postTaskVersion", 1);
        relation.put("conditionType", "NONE");
        relation.put("conditionParams", new HashMap<>());
        taskRelations.add(relation);

        List<Map<String, Object>> locationList = new ArrayList<>();
        Map<String, Object> location = new HashMap<>();
        location.put("taskCode", tc);
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
        try {
            postForm(url, request);
            log.info("DS 工作流已{}: processCode={}", "ONLINE".equals(releaseState) ? "上线" : "下线", processCode);
        } catch (RuntimeException e) {
            if (isNotFound(e)) {
                log.warn("DS 工作流已不存在，忽略上线/下线: processCode={}", processCode);
                return;
            }
            throw e;
        }
    }

    /**
     * 检查工作流定义是否存在
     */
    public boolean processExists(Long processCode) {
        try {
            String url = buildUrl(String.format("/projects/%d/process-definition/%d",
                    props.getProjectCode(), processCode));
            get(url);
            return true;
        } catch (RuntimeException e) {
            if (isNotFound(e)) {
                return false;
            }
            log.warn("DS 查询工作流存在性失败: processCode={}", processCode, e);
            return false;
        }
    }

    /**
     * 删除工作流定义
     */
    public void deleteProcess(Long processCode) {
        String url = buildUrl(String.format("/projects/%d/process-definition/%d",
                props.getProjectCode(), processCode));
        try {
            delete(url);
            log.info("DS 工作流已删除: processCode={}", processCode);
        } catch (RuntimeException e) {
            if (isNotFound(e)) {
                log.warn("DS 工作流已不存在，忽略删除: processCode={}", processCode);
                return;
            }
            throw e;
        }
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
        String cron = fixQuartzCron(cronExpression);
        Map<String, Object> schedule = new HashMap<>();
        schedule.put("crontab", cron);
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
        String cron = fixQuartzCron(cronExpression);
        Map<String, Object> schedule = new HashMap<>();
        schedule.put("crontab", cron);
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
        try {
            postForm(url, new HashMap<>());
            log.info("DS 定时调度已下线: processCode={}, scheduleId={}", processCode, scheduleId);
        } catch (RuntimeException e) {
            if (isNotFound(e)) {
                log.warn("DS 定时调度已不存在，忽略下线: processCode={}, scheduleId={}", processCode, scheduleId);
                return;
            }
            throw e;
        }
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

    // ==================== 多节点 DAG 工作流 ====================

    /**
     * 创建/更新多节点 DAG 工作流
     *
     * @param processCode 已存在的工作流编码（更新时用，null 则创建）
     * @param name        工作流名称
     * @param nodes       节点列表
     * @param relations   节点依赖关系
     * @return 工作流定义编码
     */
    public Long createOrUpdateDagProcess(Long processCode, String name,
                                         List<DagNode> nodes, List<DagRelation> relations) {
        // 0. 更新时先查询现有任务定义 ID（DS 更新需要 id 字段才能匹配）
        Map<Long, Long> codeToId = new HashMap<>();
        if (processCode != null) {
            codeToId = getTaskDefinitionIds(processCode);
        }

        // 1. 构建 TaskDefinitionJson
        List<Map<String, Object>> taskDefinitions = new ArrayList<>();
        List<Map<String, Object>> locationList = new ArrayList<>();

        Map<Long, int[]> positions = autoLayout(nodes, relations);
        for (DagNode node : nodes) {
            Map<String, Object> taskDef = buildShellTaskNode(
                    node.getTaskCode(), node.getTaskName(), node.getCallbackScript(),
                    codeToId.get(node.getTaskCode()));
            taskDefinitions.add(taskDef);

            int[] pos = positions.get(node.getTaskCode());
            Map<String, Object> location = new HashMap<>();
            location.put("taskCode", node.getTaskCode());
            location.put("x", pos[0]);
            location.put("y", pos[1]);
            locationList.add(location);
        }

        // 2. 构建 TaskRelationJson
        List<Map<String, Object>> taskRelations = new ArrayList<>();
        // 没有前置依赖的节点需要 preTaskCode=0
        Set<Long> hasPre = new HashSet<>();
        for (DagRelation rel : relations) {
            hasPre.add(rel.getPostTaskCode());
            Map<String, Object> relation = new HashMap<>();
            relation.put("name", "");
            relation.put("preTaskCode", rel.getPreTaskCode());
            relation.put("postTaskCode", rel.getPostTaskCode());
            relation.put("preTaskVersion", 0);
            relation.put("postTaskVersion", 1);
            relation.put("conditionType", "NONE");
            relation.put("conditionParams", new HashMap<>());
            taskRelations.add(relation);
        }
        // 根节点：preTaskCode=0
        for (DagNode node : nodes) {
            if (!hasPre.contains(node.getTaskCode())) {
                Map<String, Object> relation = new HashMap<>();
                relation.put("name", "");
                relation.put("preTaskCode", 0);
                relation.put("postTaskCode", node.getTaskCode());
                relation.put("preTaskVersion", 0);
                relation.put("postTaskVersion", 1);
                relation.put("conditionType", "NONE");
                relation.put("conditionParams", new HashMap<>());
                taskRelations.add(relation);
            }
        }

        // 3. 构建请求
        Map<String, Object> request = new HashMap<>();
        request.put("name", name);
        request.put("description", "Created by MDataX");
        request.put("globalParams", "[]");
        request.put("taskDefinitionJson", toJson(taskDefinitions));
        request.put("taskRelationJson", toJson(taskRelations));
        request.put("tenantCode", props.getTenantCode());
        request.put("timeout", 0);
        request.put("locations", toJson(locationList));
        request.put("executionType", "SERIAL_WAIT");

        String url;
        log.info("DS DAG 工作流请求参数: {}", toJson(request));
        if (processCode == null) {
            // DS 3.2.1 创建工作流定义使用 POST + form-data（@RequestParam）
            url = buildUrl(String.format("/projects/%d/process-definition",
                    props.getProjectCode()));
            String response = postForm(url, request);
            log.info("DS DAG 创建工作流响应: {}", response);
            return parseProcessCode(response);
        } else {
            // DS 3.2.1 更新工作流定义使用 PUT + form-data（@RequestParam）
            url = buildUrl(String.format("/projects/%d/process-definition/%d",
                    props.getProjectCode(), processCode));
            String response = putForm(url, request);
            log.info("DS DAG 更新工作流响应: {}", response);
            return processCode;
        }
    }

    /**
     * 简单自动布局：按拓扑层级网格排列
     */
    private Map<Long, int[]> autoLayout(List<DagNode> nodes, List<DagRelation> relations) {
        // 构建下游图
        Map<Long, Set<Long>> graph = new HashMap<>();
        for (DagNode node : nodes) {
            graph.put(node.getTaskCode(), new HashSet<>());
        }
        for (DagRelation rel : relations) {
            graph.get(rel.getPreTaskCode()).add(rel.getPostTaskCode());
        }

        // Kahn 算法计算层级（从根开始，根=0）
        Map<Long, Integer> levels = new HashMap<>();
        Map<Long, Integer> inDegree = new HashMap<>();
        for (DagNode node : nodes) {
            inDegree.put(node.getTaskCode(), 0);
        }
        for (DagRelation rel : relations) {
            inDegree.put(rel.getPostTaskCode(), inDegree.get(rel.getPostTaskCode()) + 1);
        }

        Queue<Long> queue = new LinkedList<>();
        for (Map.Entry<Long, Integer> entry : inDegree.entrySet()) {
            if (entry.getValue() == 0) {
                queue.offer(entry.getKey());
                levels.put(entry.getKey(), 0);
            }
        }

        while (!queue.isEmpty()) {
            Long current = queue.poll();
            for (Long next : graph.get(current)) {
                levels.put(next, Math.max(levels.getOrDefault(next, 0), levels.get(current) + 1));
                inDegree.put(next, inDegree.get(next) - 1);
                if (inDegree.get(next) == 0) {
                    queue.offer(next);
                }
            }
        }

        // 按层级排列
        Map<Integer, Integer> levelCount = new HashMap<>();
        Map<Long, int[]> positions = new HashMap<>();
        int xSpacing = 200;
        int ySpacing = 120;

        for (DagNode node : nodes) {
            int level = levels.getOrDefault(node.getTaskCode(), 0);
            int count = levelCount.getOrDefault(level, 0);
            positions.put(node.getTaskCode(), new int[]{
                    level * xSpacing + 100,
                    count * ySpacing + 100
            });
            levelCount.put(level, count + 1);
        }
        return positions;
    }

    // ==================== 实例管理 ====================

    /**
     * 停止工作流实例
     */
    public void stopProcessInstance(Long processInstanceId) {
        String url = buildUrl(String.format("/projects/%d/executors/execute",
                props.getProjectCode()));
        Map<String, Object> request = new HashMap<>();
        request.put("processInstanceId", processInstanceId);
        request.put("executeType", "STOP");
        postForm(url, request);
        log.info("DS 实例已停止: instanceId={}", processInstanceId);
    }

    /**
     * 暂停工作流实例
     */
    public void pauseProcessInstance(Long processInstanceId) {
        String url = buildUrl(String.format("/projects/%d/executors/execute",
                props.getProjectCode()));
        Map<String, Object> request = new HashMap<>();
        request.put("processInstanceId", processInstanceId);
        request.put("executeType", "PAUSE");
        postForm(url, request);
        log.info("DS 实例已暂停: instanceId={}", processInstanceId);
    }

    /**
     * 从失败节点重试工作流实例
     */
    public void retryFailureTask(Long processInstanceId) {
        String url = buildUrl(String.format("/projects/%d/executors/execute",
                props.getProjectCode()));
        Map<String, Object> request = new HashMap<>();
        request.put("processInstanceId", processInstanceId);
        request.put("executeType", "RETRY_FAILURE");
        postForm(url, request);
        log.info("DS 实例失败节点已重试: instanceId={}", processInstanceId);
    }

    /**
     * 查询工作流实例列表
     *
     * @param processCode 工作流定义编码
     * @return 实例列表 JSON 字符串
     */
    public String listProcessInstances(Long processCode) {
        String url = buildUrl(String.format(
                "/projects/%d/process-instances?pageNo=1&pageSize=100&processDefinitionCode=%d",
                props.getProjectCode(), processCode));
        String response = get(url);
        log.info("DS 实例列表查询: processCode={}, response={}", processCode, response);
        return response;
    }

    /**
     * 查询工作流定义中各任务定义的 id 映射（code -> id）
     */
    public Map<Long, Long> getTaskDefinitionIds(Long processCode) {
        Map<Long, Long> result = new HashMap<>();
        try {
            String url = buildUrl(String.format(
                    "/projects/%d/process-definition/%d",
                    props.getProjectCode(), processCode));
            String response = get(url);
            if (response == null || response.isEmpty()) {
                return result;
            }
            JsonNode node = objectMapper.readTree(response);
            if (!node.has("data")) {
                return result;
            }
            JsonNode data = node.get("data");
            if (data.has("taskDefinitionList")) {
                for (JsonNode td : data.get("taskDefinitionList")) {
                    if (td.has("code") && td.has("id")) {
                        result.put(td.get("code").asLong(), td.get("id").asLong());
                    }
                }
            }
            log.info("DS 任务定义ID查询: processCode={}, 映射数={}", processCode, result.size());
        } catch (Exception e) {
            log.warn("DS 任务定义ID查询失败: processCode={}", processCode, e);
        }
        return result;
    }

    /**
     * 查询工作流最新实例状态（通过实例列表 API，取第一条记录）
     *
     * <p>DS 3.2.1 的实例列表中 code 字段为 null，无法按 code 匹配。
     * 工作流设置了 SERIAL_WAIT，同一时刻只有一个实例运行，直接取最新实例即可。
     *
     * @param processCode 工作流定义编码
     * @return 状态字符串（如 SUCCESS / FAILURE / RUNNING_EXECUTION），查询失败返回 null
     */
    public String getProcessInstanceStatus(Long processCode) {
        try {
            String response = listProcessInstances(processCode);
            log.info("DS 实例状态查询解析: processCode={}", processCode);
            if (response == null || response.isEmpty()) {
                log.warn("DS 实例列表响应为空");
                return null;
            }
            JsonNode root = objectMapper.readTree(response);
            if (!root.has("data")) {
                log.warn("DS 实例列表响应无 data 字段: {}", response);
                return null;
            }
            JsonNode data = root.get("data");
            JsonNode totalList = data.has("totalList") ? data.get("totalList") : data;
            if (totalList == null || !totalList.isArray()) {
                log.warn("DS 实例列表 totalList 非数组: data={}", data);
                return null;
            }
            if (totalList.size() == 0) {
                log.warn("DS 实例列表为空: processCode={}", processCode);
                return null;
            }
            JsonNode first = totalList.get(0);
            String state = first.has("state") ? first.get("state").asText() : null;
            log.info("DS 最新实例状态: processCode={}, state={}", processCode, state);
            return state;
        } catch (Exception e) {
            log.warn("DS 实例状态查询失败: processCode={}", processCode, e);
            return null;
        }
    }

    // ==================== 工具方法 ====================

    private String buildCallbackScript(String taskType, Long taskId) {
        return String.format(
                "curl -s -X POST %s/api/internal/task/execute " +
                        "-H \"Content-Type: application/json\" " +
                        "-d \"{\\\"taskType\\\":\\\"%s\\\",\\\"taskId\\\":%d,\\\"secret\\\":\\\"%s\\\"}\" " +
                        "--max-time 7200",
                props.getCallbackUrl(), taskType, taskId, props.getCallbackSecret()
        );
    }

    /**
     * 修复 Quartz Cron 表达式：日字段和周字段不能同时为 *，将后者改为 ?
     */
    private String fixQuartzCron(String cron) {
        if (cron == null || cron.isEmpty()) {
            return cron;
        }
        String[] parts = cron.trim().split("\\s+");
        if (parts.length == 6 && "*".equals(parts[3]) && "*".equals(parts[5])) {
            parts[5] = "?";
            return String.join(" ", parts);
        }
        return cron;
    }

    private String buildUrl(String path) {
        String base = props.getBaseUrl();
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base + path;
    }

    private static final java.util.concurrent.atomic.AtomicLong CODE_SEQ =
            new java.util.concurrent.atomic.AtomicLong(System.currentTimeMillis());

    public long generateTaskCode() {
        // DS 要求 taskCode 为正 long 且在合理范围内
        return CODE_SEQ.incrementAndGet();
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

    private String get(String url) {
        Request request = new Request.Builder()
                .url(url)
                .get()
                .addHeader("token", props.getToken())
                .build();
        return execute(request);
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
            // 检查业务响应码（DS 常返回 HTTP 200 但 code != 0）
            try {
                JsonNode node = objectMapper.readTree(body);
                if (node.has("code")) {
                    int code = node.get("code").asInt();
                    if (code != 0) {
                        String msg = node.has("msg") ? node.get("msg").asText()
                                : (node.has("message") ? node.get("message").asText() : "未知错误");
                        log.error("DS API 业务错误: {} {} → code={}, msg={}",
                                request.method(), request.url(), code, msg);
                        throw new RuntimeException("DS API 业务错误: code=" + code + ", msg=" + msg);
                    }
                }
            } catch (IOException e) {
                // 忽略解析错误（非 JSON 响应）
            }
            log.info("DS API 请求成功: {} {} → {}", request.method(), request.url(), response.code());
            return body;
        } catch (IOException e) {
            throw new RuntimeException("DS API 请求异常: " + request.url(), e);
        }
    }

    private boolean isNotFound(RuntimeException e) {
        String msg = e.getMessage();
        return msg != null && (msg.contains("404") || msg.contains("不存在") || msg.contains("not found") || msg.contains("NOT_FOUND"));
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
