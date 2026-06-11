package com.mogu.data.schedulerx.executor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mogu.data.schedulerx.engine.TaskExecutor;
import com.mogu.data.schedulerx.entity.TaskInstance;
import com.mogu.data.schedulerx.enums.EventType;
import com.mogu.data.schedulerx.event.TaskEvent;
import com.mogu.data.schedulerx.event.TaskEventPublisher;
import com.mogu.data.schedulerx.util.IdGenerator;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * HTTP 回调执行器
 *
 * @author fengzhu
 */
@Slf4j
@Component
public class HttpCallbackExecutor implements TaskExecutor {

    @Autowired
    private OkHttpClient httpClient;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private TaskEventPublisher eventPublisher;

    @Value("${schedulerx.callback.base-url:http://localhost:8081}")
    private String callbackBaseUrl;

    @Override
    public void execute(TaskInstance taskInstance) {
        String taskInstanceId = taskInstance.getTaskInstanceId();
        String callbackUrl = buildCallbackUrl(taskInstanceId);

        try {
            // 构建请求体，注入 callback 信息
            Map<String, Object> body = buildRequestBody(taskInstance, callbackUrl);
            String jsonBody = objectMapper.writeValueAsString(body);

            RequestBody requestBody = RequestBody.create(
                    jsonBody,
                    MediaType.parse("application/json; charset=utf-8")
            );

            Request request = new Request.Builder()
                    .url(body.get("url").toString())
                    .post(requestBody)
                    .header("Content-Type", "application/json")
                    .build();

            log.info("[HttpCallbackExecutor] 发送任务: taskInstanceId={}, url={}",
                    taskInstanceId, body.get("url"));

            httpClient.newCall(request).enqueue(new Callback() {
                @Override
                public void onResponse(Call call, Response response) {
                    try (ResponseBody responseBody = response.body()) {
                        String responseStr = responseBody != null ? responseBody.string() : "";
                        log.info("[HttpCallbackExecutor] 任务响应: taskInstanceId={}, code={}, body={}",
                                taskInstanceId, response.code(), responseStr);
                        // 记录响应，不推进状态机（等回调）
                    } catch (IOException e) {
                        log.error("[HttpCallbackExecutor] 读取响应失败: taskInstanceId={}", taskInstanceId, e);
                    }
                }

                @Override
                public void onFailure(Call call, IOException e) {
                    log.error("[HttpCallbackExecutor] 网络异常: taskInstanceId={}, error={}",
                            taskInstanceId, e.getMessage());
                    // 发布 FAILURE 事件
                    TaskEvent event = new TaskEvent(taskInstanceId, taskInstance.getDagInstanceId(), EventType.FAILURE);
                    event.setErrorMsg(e.getMessage());
                    eventPublisher.publish(event);
                }
            });

        } catch (Exception e) {
            log.error("[HttpCallbackExecutor] 构建请求失败: taskInstanceId={}", taskInstanceId, e);
            TaskEvent event = new TaskEvent(taskInstanceId, taskInstance.getDagInstanceId(), EventType.FAILURE);
            event.setErrorMsg(e.getMessage());
            eventPublisher.publish(event);
        }
    }

    /**
     * 构建回调 URL
     */
    public String buildCallbackUrl(String taskInstanceId) {
        return callbackBaseUrl + "/api/v1/callback/" + taskInstanceId;
    }

    /**
     * 构建请求体
     */
    private Map<String, Object> buildRequestBody(TaskInstance taskInstance, String callbackUrl) throws Exception {
        Map<String, Object> body = new HashMap<>();

        // 解析 taskInstance 中的 requestBody（原始配置）
        if (taskInstance.getRequestBody() != null) {
            Map<String, Object> config = objectMapper.readValue(taskInstance.getRequestBody(), Map.class);
            body.putAll(config);
        }

        // 注入 callback 信息
        body.put("taskInstanceId", taskInstance.getTaskInstanceId());
        body.put("dagInstanceId", taskInstance.getDagInstanceId());
        body.put("callbackUrl", callbackUrl);

        return body;
    }

}
