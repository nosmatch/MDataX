package com.mogu.data.schedulerx.executor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mogu.data.schedulerx.entity.TaskInstance;
import com.mogu.data.schedulerx.event.TaskEventPublisher;
import okhttp3.OkHttpClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Method;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * HttpCallbackExecutor 单元测试
 *
 * @author fengzhu
 */
class HttpCallbackExecutorTest {

    private HttpCallbackExecutor executor;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        executor = new HttpCallbackExecutor();
        objectMapper = new ObjectMapper();

        ReflectionTestUtils.setField(executor, "httpClient", new OkHttpClient());
        ReflectionTestUtils.setField(executor, "objectMapper", objectMapper);
        ReflectionTestUtils.setField(executor, "eventPublisher", mock(TaskEventPublisher.class));
        ReflectionTestUtils.setField(executor, "callbackBaseUrl", "http://localhost:8081");
    }

    @Test
    @DisplayName("buildCallbackUrl: 正确拼接回调地址")
    void testBuildCallbackUrl() {
        String url = executor.buildCallbackUrl("TI123456789");
        assertEquals("http://localhost:8081/api/v1/callback/TI123456789", url);
    }

    @Test
    @DisplayName("buildCallbackUrl: 使用自定义 baseUrl")
    void testBuildCallbackUrl_CustomBaseUrl() {
        ReflectionTestUtils.setField(executor, "callbackBaseUrl", "http://schedulerx:8081");
        String url = executor.buildCallbackUrl("TI987654321");
        assertEquals("http://schedulerx:8081/api/v1/callback/TI987654321", url);
    }

    @Test
    @DisplayName("buildRequestBody: 正确注入 callback 信息")
    void testBuildRequestBody() throws Exception {
        TaskInstance taskInstance = new TaskInstance();
        taskInstance.setTaskInstanceId("TI123");
        taskInstance.setDagInstanceId("DI456");
        taskInstance.setRequestBody("{\"url\":\"http://mdatax:8080/api/task\",\"method\":\"POST\"}");

        Method method = HttpCallbackExecutor.class.getDeclaredMethod(
                "buildRequestBody", TaskInstance.class, String.class);
        method.setAccessible(true);

        Map<String, Object> body = (Map<String, Object>) method.invoke(executor, taskInstance, "http://localhost:8081/api/v1/callback/TI123");

        assertEquals("http://mdatax:8080/api/task", body.get("url"));
        assertEquals("POST", body.get("method"));
        assertEquals("TI123", body.get("taskInstanceId"));
        assertEquals("DI456", body.get("dagInstanceId"));
        assertEquals("http://localhost:8081/api/v1/callback/TI123", body.get("callbackUrl"));
    }

    @Test
    @DisplayName("buildRequestBody: requestBody 为空时仍能注入基础信息")
    void testBuildRequestBody_EmptyConfig() throws Exception {
        TaskInstance taskInstance = new TaskInstance();
        taskInstance.setTaskInstanceId("TI789");
        taskInstance.setDagInstanceId("DI000");
        taskInstance.setRequestBody(null);

        Method method = HttpCallbackExecutor.class.getDeclaredMethod(
                "buildRequestBody", TaskInstance.class, String.class);
        method.setAccessible(true);

        Map<String, Object> body = (Map<String, Object>) method.invoke(executor, taskInstance, "http://localhost:8081/callback");

        assertEquals("TI789", body.get("taskInstanceId"));
        assertEquals("DI000", body.get("dagInstanceId"));
        assertEquals("http://localhost:8081/callback", body.get("callbackUrl"));
    }

    @Test
    @DisplayName("buildRequestBody: 保留原始配置中的所有字段")
    void testBuildRequestBody_PreserveOriginalFields() throws Exception {
        TaskInstance taskInstance = new TaskInstance();
        taskInstance.setTaskInstanceId("TI111");
        taskInstance.setDagInstanceId("DI222");
        taskInstance.setRequestBody("{\"url\":\"http://test.com\",\"headers\":{\"X-Token\":\"abc123\"},\"body\":{\"taskId\":1001}}");

        Method method = HttpCallbackExecutor.class.getDeclaredMethod(
                "buildRequestBody", TaskInstance.class, String.class);
        method.setAccessible(true);

        Map<String, Object> result = (Map<String, Object>) method.invoke(executor, taskInstance, "http://callback.url");

        assertEquals("http://test.com", result.get("url"));
        @SuppressWarnings("unchecked")
        Map<String, Object> headers = (Map<String, Object>) result.get("headers");
        assertNotNull(headers);
        assertEquals("abc123", headers.get("X-Token"));
        @SuppressWarnings("unchecked")
        Map<String, Object> requestBody = (Map<String, Object>) result.get("body");
        assertNotNull(requestBody);
        assertEquals(1001, requestBody.get("taskId"));
    }
}
