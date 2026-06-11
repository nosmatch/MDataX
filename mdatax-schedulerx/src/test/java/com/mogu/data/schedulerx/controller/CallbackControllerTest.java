package com.mogu.data.schedulerx.controller;

import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import com.mogu.data.schedulerx.dto.CallbackRequest;
import com.mogu.data.schedulerx.entity.TaskInstance;
import com.mogu.data.schedulerx.enums.TaskInstanceStatus;
import com.mogu.data.schedulerx.event.TaskEventPublisher;
import com.mogu.data.schedulerx.service.TaskInstanceService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * CallbackController 单元测试
 *
 * @author fengzhu
 */
@ExtendWith(MockitoExtension.class)
class CallbackControllerTest {

    @InjectMocks
    private CallbackController callbackController;

    @Mock
    private TaskInstanceService taskInstanceService;
    @Mock
    private TaskEventPublisher eventPublisher;

    @Test
    @DisplayName("callback SUCCESS: RUNNING 状态任务接收成功回调")
    void testCallback_Success() {
        String taskInstanceId = "TI123";
        String dagInstanceId = "DI456";

        TaskInstance taskInstance = buildTaskInstance(taskInstanceId, dagInstanceId, "RUNNING");
        when(taskInstanceService.lambdaQuery()).thenReturn(mockChain(taskInstance));

        CallbackRequest request = new CallbackRequest();
        request.setStatus("SUCCESS");
        request.setOutput("{\"rows\":100}");
        request.setDurationMs(5000);

        Object result = callbackController.callback(taskInstanceId, request);

        assertNotNull(result);
        verify(eventPublisher).publish(argThat(e ->
                e.getTaskInstanceId().equals(taskInstanceId)
                        && e.getDagInstanceId().equals(dagInstanceId)
                        && e.getType().name().equals("SUCCESS")
                        && e.getOutput().equals("{\"rows\":100}")
                        && e.getDurationMs() == 5000));
    }

    @Test
    @DisplayName("callback FAILURE: RUNNING 状态任务接收失败回调")
    void testCallback_Failure() {
        String taskInstanceId = "TI123";
        String dagInstanceId = "DI456";

        TaskInstance taskInstance = buildTaskInstance(taskInstanceId, dagInstanceId, "RUNNING");
        when(taskInstanceService.lambdaQuery()).thenReturn(mockChain(taskInstance));

        CallbackRequest request = new CallbackRequest();
        request.setStatus("FAILURE");
        request.setLogs("连接超时");
        request.setDurationMs(30000);

        Object result = callbackController.callback(taskInstanceId, request);

        assertNotNull(result);
        verify(eventPublisher).publish(argThat(e ->
                e.getType().name().equals("FAILURE")
                        && e.getErrorMsg().equals("连接超时")));
    }

    @Test
    @DisplayName("callback TIMEOUT: RUNNING 状态任务接收超时回调")
    void testCallback_Timeout() {
        String taskInstanceId = "TI123";
        String dagInstanceId = "DI456";

        TaskInstance taskInstance = buildTaskInstance(taskInstanceId, dagInstanceId, "RUNNING");
        when(taskInstanceService.lambdaQuery()).thenReturn(mockChain(taskInstance));

        CallbackRequest request = new CallbackRequest();
        request.setStatus("TIMEOUT");
        request.setLogs("执行超过600秒");

        Object result = callbackController.callback(taskInstanceId, request);

        assertNotNull(result);
        verify(eventPublisher).publish(argThat(e ->
                e.getType().name().equals("TIMEOUT")
                        && e.getErrorMsg().equals("执行超过600秒")));
    }

    @Test
    @DisplayName("callback: FAILED 别名也映射为 FAILURE")
    void testCallback_FailedAlias() {
        String taskInstanceId = "TI123";
        String dagInstanceId = "DI456";

        TaskInstance taskInstance = buildTaskInstance(taskInstanceId, dagInstanceId, "RUNNING");
        when(taskInstanceService.lambdaQuery()).thenReturn(mockChain(taskInstance));

        CallbackRequest request = new CallbackRequest();
        request.setStatus("FAILED");

        Object result = callbackController.callback(taskInstanceId, request);

        assertNotNull(result);
        verify(eventPublisher).publish(argThat(e ->
                e.getType().name().equals("FAILURE")));
    }

    @Test
    @DisplayName("callback: 非 RUNNING 状态任务忽略回调")
    void testCallback_NotRunning() {
        String taskInstanceId = "TI123";
        String dagInstanceId = "DI456";

        TaskInstance taskInstance = buildTaskInstance(taskInstanceId, dagInstanceId, "SUCCESS");
        when(taskInstanceService.lambdaQuery()).thenReturn(mockChain(taskInstance));

        CallbackRequest request = new CallbackRequest();
        request.setStatus("SUCCESS");

        Object result = callbackController.callback(taskInstanceId, request);

        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> map = (java.util.Map<String, Object>) result;
        assertEquals("任务已完成，忽略回调", map.get("message"));
        verifyNoInteractions(eventPublisher);
    }

    @Test
    @DisplayName("callback: 任务实例不存在时抛出异常")
    void testCallback_InstanceNotFound() {
        String taskInstanceId = "TI_NOT_FOUND";
        when(taskInstanceService.lambdaQuery()).thenReturn(mockChain((TaskInstance) null));

        CallbackRequest request = new CallbackRequest();
        request.setStatus("SUCCESS");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> {
            callbackController.callback(taskInstanceId, request);
        });
        assertTrue(ex.getMessage().contains("任务实例不存在"));
    }

    @Test
    @DisplayName("callback: 不支持的状态抛出异常")
    void testCallback_UnsupportedStatus() {
        String taskInstanceId = "TI123";
        String dagInstanceId = "DI456";

        TaskInstance taskInstance = buildTaskInstance(taskInstanceId, dagInstanceId, "RUNNING");
        when(taskInstanceService.lambdaQuery()).thenReturn(mockChain(taskInstance));

        CallbackRequest request = new CallbackRequest();
        request.setStatus("UNKNOWN");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> {
            callbackController.callback(taskInstanceId, request);
        });
        assertTrue(ex.getMessage().contains("不支持的状态"));
    }

    // ============ helper methods ============

    private TaskInstance buildTaskInstance(String taskInstanceId, String dagInstanceId, String status) {
        TaskInstance instance = new TaskInstance();
        instance.setTaskInstanceId(taskInstanceId);
        instance.setDagInstanceId(dagInstanceId);
        instance.setStatus(status);
        return instance;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private <T> LambdaQueryChainWrapper<T> mockChain(T result) {
        LambdaQueryChainWrapper[] ref = new LambdaQueryChainWrapper[1];
        ref[0] = mock(LambdaQueryChainWrapper.class, invocation -> {
            if ("one".equals(invocation.getMethod().getName())) {
                return result;
            }
            return ref[0];
        });
        return ref[0];
    }
}
