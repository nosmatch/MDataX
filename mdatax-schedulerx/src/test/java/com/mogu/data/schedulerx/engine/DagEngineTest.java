package com.mogu.data.schedulerx.engine;

import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mogu.data.schedulerx.engine.model.Dag;
import com.mogu.data.schedulerx.engine.model.DagTask;
import com.mogu.data.schedulerx.entity.DagDef;
import com.mogu.data.schedulerx.entity.DagInstance;
import com.mogu.data.schedulerx.entity.DagTaskEntity;
import com.mogu.data.schedulerx.entity.TaskInstance;
import com.mogu.data.schedulerx.enums.DagInstanceStatus;
import com.mogu.data.schedulerx.enums.EventType;
import com.mogu.data.schedulerx.enums.FailureStrategy;
import com.mogu.data.schedulerx.enums.TaskInstanceStatus;
import com.mogu.data.schedulerx.event.TaskEvent;
import com.mogu.data.schedulerx.event.TaskEventPublisher;
import com.mogu.data.schedulerx.service.DagDefService;
import com.mogu.data.schedulerx.service.DagInstanceService;
import com.mogu.data.schedulerx.service.DagTaskService;
import com.mogu.data.schedulerx.service.TaskInstanceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * DagEngine 单元测试
 *
 * @author fengzhu
 */
@ExtendWith(MockitoExtension.class)
class DagEngineTest {

    @InjectMocks
    private DagEngine dagEngine;

    @Mock
    private DagDefService dagDefService;
    @Mock
    private DagTaskService dagTaskService;
    @Mock
    private DagInstanceService dagInstanceService;
    @Mock
    private TaskInstanceService taskInstanceService;
    @Mock
    private TopologyResolver topologyResolver;
    @Mock
    private StateMachine stateMachine;
    @Mock
    private TaskEventPublisher eventPublisher;
    @Mock
    private TaskExecutor taskExecutor;
    @Mock
    private ScheduledExecutorService retryExecutor;

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        ReflectionTestUtils.setField(dagEngine, "objectMapper", objectMapper);
    }

    // ============ start() 测试 ============

    @Test
    @DisplayName("start: 正常启动 DAG 实例，提交就绪任务")
    void testStart_Normal() {
        String instanceId = "DI123";
        String dagId = "dag_001";

        DagInstance dagInstance = buildDagInstance(instanceId, dagId, "PENDING");
        when(dagInstanceService.lambdaQuery()).thenReturn(mockChain(dagInstance));

        DagDef dagDef = buildDagDef(dagId, "STOP_ALL");
        when(dagDefService.lambdaQuery()).thenReturn(mockChain(dagDef));

        DagTaskEntity taskEntity = buildTaskEntity(dagId, "task_A", null);
        when(dagTaskService.lambdaQuery()).thenReturn(mockChain(Collections.singletonList(taskEntity)));

        DagTask dagTask = buildDagTask("task_A", null);
        when(topologyResolver.resolveReadyTasks(any(Dag.class), any())).thenReturn(Collections.singletonList(dagTask));

        dagEngine.start(instanceId);

        verify(dagInstanceService).updateById(argThat(i ->
                i.getStatus().equals("RUNNING") && i.getStartTime() != null));
        verify(taskExecutor).execute(any(TaskInstance.class));
    }

    @Test
    @DisplayName("start: DAG 实例不存在时直接返回")
    void testStart_InstanceNotFound() {
        String instanceId = "DI_NOT_FOUND";
        when(dagInstanceService.lambdaQuery()).thenReturn(mockChain((DagInstance) null));

        dagEngine.start(instanceId);

        verifyNoInteractions(dagDefService);
    }

    @Test
    @DisplayName("start: DAG 定义不存在时标记实例为 FAILURE")
    void testStart_DagDefNotFound() {
        String instanceId = "DI123";
        String dagId = "dag_missing";

        DagInstance dagInstance = buildDagInstance(instanceId, dagId, "PENDING");
        when(dagInstanceService.lambdaQuery()).thenReturn(mockChain(dagInstance));
        when(dagDefService.lambdaQuery()).thenReturn(mockChain((DagDef) null));

        dagEngine.start(instanceId);

        verify(dagInstanceService).updateById(argThat(i ->
                i.getStatus().equals("FAILURE")));
    }

    @Test
    @DisplayName("start: 无就绪任务时直接标记 SUCCESS")
    void testStart_NoReadyTasks() {
        String instanceId = "DI123";
        String dagId = "dag_001";

        DagInstance dagInstance = buildDagInstance(instanceId, dagId, "PENDING");
        when(dagInstanceService.lambdaQuery()).thenReturn(mockChain(dagInstance));

        DagDef dagDef = buildDagDef(dagId, "STOP_ALL");
        when(dagDefService.lambdaQuery()).thenReturn(mockChain(dagDef));

        DagTaskEntity taskEntity = buildTaskEntity(dagId, "task_A", null);
        when(dagTaskService.lambdaQuery()).thenReturn(mockChain(Collections.singletonList(taskEntity)));

        when(topologyResolver.resolveReadyTasks(any(Dag.class), any())).thenReturn(Collections.emptyList());

        dagEngine.start(instanceId);

        verify(dagInstanceService, times(2)).updateById(any());
    }

    // ============ onTaskComplete - SUCCESS 测试 ============

    @Test
    @DisplayName("onTaskComplete SUCCESS: 触发下游任务并检查 DAG 完成")
    void testOnTaskComplete_Success() {
        String instanceId = "DI123";
        String dagId = "dag_001";
        String taskInstanceId = "TI456";

        TaskInstance taskInstance = buildTaskInstance(taskInstanceId, instanceId, dagId, "task_A", "RUNNING", 1);
        when(taskInstanceService.lambdaQuery()).thenReturn(mockChain(taskInstance));

        DagInstance dagInstance = buildDagInstance(instanceId, dagId, "RUNNING");
        when(dagInstanceService.lambdaQuery()).thenReturn(mockChain(dagInstance));

        DagDef dagDef = buildDagDef(dagId, "STOP_ALL");
        when(dagDefService.lambdaQuery()).thenReturn(mockChain(dagDef));

        DagTaskEntity taskEntity = buildTaskEntity(dagId, "task_A", null);
        when(dagTaskService.lambdaQuery()).thenReturn(mockChain(Collections.singletonList(taskEntity)));

        when(stateMachine.transition(TaskInstanceStatus.RUNNING, EventType.SUCCESS)).thenReturn(TaskInstanceStatus.SUCCESS);

        when(taskInstanceService.lambdaQuery()).thenReturn(mockChain(taskInstance));
        DagTask downstreamTask = buildDagTask("task_B", Collections.singletonList("task_A"));
        when(topologyResolver.resolveReadyTasks(any(Dag.class), any())).thenReturn(Collections.singletonList(downstreamTask));

        TaskEvent event = new TaskEvent(taskInstanceId, instanceId, EventType.SUCCESS);
        dagEngine.onTaskComplete(event);

        verify(taskInstanceService).updateById(argThat(i ->
                i.getStatus().equals("SUCCESS")));
        verify(taskExecutor).execute(any(TaskInstance.class));
    }

    // ============ onTaskComplete - FAILURE 测试 ============

    @Test
    @DisplayName("onTaskComplete FAILURE: 有重试次数时立即重试")
    void testOnTaskComplete_Failure_WithRetry() {
        String instanceId = "DI123";
        String dagId = "dag_001";
        String taskInstanceId = "TI456";

        TaskInstance taskInstance = buildTaskInstance(taskInstanceId, instanceId, dagId, "task_A", "RUNNING", 1);
        when(taskInstanceService.lambdaQuery()).thenReturn(mockChain(taskInstance));

        DagInstance dagInstance = buildDagInstance(instanceId, dagId, "RUNNING");
        when(dagInstanceService.lambdaQuery()).thenReturn(mockChain(dagInstance));

        DagDef dagDef = buildDagDef(dagId, "STOP_ALL");
        dagDef.setMaxRetryTimes(2);  // 允许重试 2 次
        dagDef.setRetryIntervalSeconds(0);  // 立即重试
        when(dagDefService.lambdaQuery()).thenReturn(mockChain(dagDef));

        DagTaskEntity taskEntity = buildTaskEntity(dagId, "task_A", null);
        when(dagTaskService.lambdaQuery()).thenReturn(mockChain(Collections.singletonList(taskEntity)));

        when(stateMachine.transition(TaskInstanceStatus.RUNNING, EventType.FAILURE)).thenReturn(TaskInstanceStatus.FAILURE);

        TaskEvent event = new TaskEvent(taskInstanceId, instanceId, EventType.FAILURE);
        dagEngine.onTaskComplete(event);

        verify(taskInstanceService).updateById(argThat(i ->
                i.getStatus().equals("FAILURE")));
        verify(taskExecutor).execute(argThat(i ->
                i.getAttemptNumber() == 2));
    }

    @Test
    @DisplayName("onTaskComplete FAILURE: 无重试次数时 STOP_ALL 策略")
    void testOnTaskComplete_Failure_StopAll() {
        String instanceId = "DI123";
        String dagId = "dag_001";
        String taskInstanceId = "TI456";

        TaskInstance taskInstance = buildTaskInstance(taskInstanceId, instanceId, dagId, "task_A", "RUNNING", 3);
        when(taskInstanceService.lambdaQuery()).thenReturn(mockChain(taskInstance));

        DagInstance dagInstance = buildDagInstance(instanceId, dagId, "RUNNING");
        when(dagInstanceService.lambdaQuery()).thenReturn(mockChain(dagInstance));

        DagDef dagDef = buildDagDef(dagId, "STOP_ALL");
        dagDef.setMaxRetryTimes(0);  // 不允许重试
        when(dagDefService.lambdaQuery()).thenReturn(mockChain(dagDef));

        DagTaskEntity taskEntity = buildTaskEntity(dagId, "task_A", null);
        when(dagTaskService.lambdaQuery()).thenReturn(mockChain(Collections.singletonList(taskEntity)));

        when(stateMachine.transition(TaskInstanceStatus.RUNNING, EventType.FAILURE)).thenReturn(TaskInstanceStatus.FAILURE);

        TaskInstance runningTask = buildTaskInstance("TI789", instanceId, dagId, "task_B", "RUNNING", 1);
        when(taskInstanceService.lambdaQuery()).thenReturn(mockChain(runningTask));

        TaskEvent event = new TaskEvent(taskInstanceId, instanceId, EventType.FAILURE);
        dagEngine.onTaskComplete(event);

        verify(dagInstanceService).updateById(argThat(i ->
                i.getStatus().equals("FAILURE")));
    }

    // ============ kill 测试 ============

    @Test
    @DisplayName("kill: 正常杀除实例，标记所有 RUNNING 任务为 FAILURE")
    void testKill() {
        String instanceId = "DI123";

        DagInstance dagInstance = buildDagInstance(instanceId, "dag_001", "RUNNING");
        when(dagInstanceService.lambdaQuery()).thenReturn(mockChain(dagInstance));

        TaskInstance runningTask1 = buildTaskInstance("TI001", instanceId, "dag_001", "task_A", "RUNNING", 1);
        TaskInstance runningTask2 = buildTaskInstance("TI002", instanceId, "dag_001", "task_B", "RUNNING", 1);
        when(taskInstanceService.lambdaQuery()).thenReturn(mockChain(Arrays.asList(runningTask1, runningTask2)));

        dagEngine.kill(instanceId);

        verify(taskInstanceService, times(2)).updateById(argThat(i ->
                i.getStatus().equals("FAILURE")));
        verify(dagInstanceService).updateById(argThat(i ->
                i.getStatus().equals("STOPPED")));
    }

    @Test
    @DisplayName("kill: 实例不存在时静默返回")
    void testKill_InstanceNotFound() {
        String instanceId = "DI_NOT_FOUND";
        when(dagInstanceService.lambdaQuery()).thenReturn(mockChain((DagInstance) null));

        dagEngine.kill(instanceId);

        verifyNoInteractions(taskInstanceService);
    }

    // ============ retryFailedTasks 测试 ============

    @Test
    @DisplayName("retryFailedTasks: 重新提交失败任务")
    void testRetryFailedTasks() {
        String instanceId = "DI123";
        String dagId = "dag_001";

        DagInstance dagInstance = buildDagInstance(instanceId, dagId, "FAILURE");
        when(dagInstanceService.lambdaQuery()).thenReturn(mockChain(dagInstance));

        DagDef dagDef = buildDagDef(dagId, "STOP_ALL");
        when(dagDefService.lambdaQuery()).thenReturn(mockChain(dagDef));

        DagTaskEntity taskEntity = buildTaskEntity(dagId, "task_A", null);
        when(dagTaskService.lambdaQuery()).thenReturn(mockChain(Collections.singletonList(taskEntity)));

        TaskInstance failedTask = buildTaskInstance("TI001", instanceId, dagId, "task_A", "FAILURE", 1);
        when(taskInstanceService.lambdaQuery()).thenReturn(mockChain(Collections.singletonList(failedTask)));

        dagEngine.retryFailedTasks(instanceId);

        verify(dagInstanceService).updateById(argThat(i ->
                i.getStatus().equals("RUNNING")));
        verify(taskExecutor).execute(argThat(i ->
                i.getAttemptNumber() == 2));
    }

    @Test
    @DisplayName("retryFailedTasks: 无失败任务时静默返回")
    void testRetryFailedTasks_NoFailedTasks() {
        String instanceId = "DI123";
        String dagId = "dag_001";

        DagInstance dagInstance = buildDagInstance(instanceId, dagId, "FAILURE");
        when(dagInstanceService.lambdaQuery()).thenReturn(mockChain(dagInstance));

        DagDef dagDef = buildDagDef(dagId, "STOP_ALL");
        when(dagDefService.lambdaQuery()).thenReturn(mockChain(dagDef));

        DagTaskEntity taskEntity = buildTaskEntity(dagId, "task_A", null);
        when(dagTaskService.lambdaQuery()).thenReturn(mockChain(Collections.singletonList(taskEntity)));

        when(taskInstanceService.lambdaQuery()).thenReturn(mockChain(Collections.emptyList()));

        dagEngine.retryFailedTasks(instanceId);

        verifyNoInteractions(taskExecutor);
    }

    // ============ helper methods ============

    private DagInstance buildDagInstance(String instanceId, String dagId, String status) {
        DagInstance instance = new DagInstance();
        instance.setInstanceId(instanceId);
        instance.setDagId(dagId);
        instance.setStatus(status);
        instance.setTriggerType("SCHEDULED");
        instance.setStartTime(LocalDateTime.now().minusMinutes(1));
        instance.setCreateTime(LocalDateTime.now());
        return instance;
    }

    private DagDef buildDagDef(String dagId, String failureStrategy) {
        DagDef def = new DagDef();
        def.setDagId(dagId);
        def.setDagName("Test DAG");
        def.setCronExpression("0 0 * * * ?");
        def.setTimezone("Asia/Shanghai");
        def.setFailureStrategy(failureStrategy);
        def.setMaxRetryTimes(0);
        def.setRetryIntervalSeconds(0);
        def.setStatus(1);
        return def;
    }

    private DagTaskEntity buildTaskEntity(String dagId, String taskId, List<String> upstream) {
        DagTaskEntity entity = new DagTaskEntity();
        entity.setDagId(dagId);
        entity.setTaskId(taskId);
        entity.setTaskName("Task " + taskId);
        entity.setTaskType("HTTP_CALLBACK");
        entity.setRetryTimes(0);
        entity.setTimeoutSeconds(600);
        try {
            if (upstream != null) {
                entity.setUpstreamTasks(objectMapper.writeValueAsString(upstream));
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return entity;
    }

    private DagTask buildDagTask(String taskId, List<String> upstream) {
        DagTask task = new DagTask();
        task.setTaskId(taskId);
        task.setTaskName("Task " + taskId);
        task.setUpstream(upstream);
        task.setTaskType("HTTP_CALLBACK");
        return task;
    }

    private TaskInstance buildTaskInstance(String taskInstanceId, String dagInstanceId, String dagId,
                                           String taskId, String status, int attemptNumber) {
        TaskInstance instance = new TaskInstance();
        instance.setTaskInstanceId(taskInstanceId);
        instance.setDagInstanceId(dagInstanceId);
        instance.setDagId(dagId);
        instance.setTaskId(taskId);
        instance.setTaskName("Task " + taskId);
        instance.setStatus(status);
        instance.setAttemptNumber(attemptNumber);
        instance.setStartTime(LocalDateTime.now());
        instance.setCreateTime(LocalDateTime.now());
        return instance;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private <T> LambdaQueryChainWrapper<T> mockChain(T result) {
        LambdaQueryChainWrapper[] ref = new LambdaQueryChainWrapper[1];
        ref[0] = mock(LambdaQueryChainWrapper.class, invocation -> {
            String name = invocation.getMethod().getName();
            if ("one".equals(name)) {
                return result;
            }
            if ("list".equals(name)) {
                return java.util.Collections.emptyList();
            }
            return ref[0];
        });
        return ref[0];
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private <T> LambdaQueryChainWrapper<T> mockChain(List<T> result) {
        LambdaQueryChainWrapper[] ref = new LambdaQueryChainWrapper[1];
        ref[0] = mock(LambdaQueryChainWrapper.class, invocation -> {
            if ("list".equals(invocation.getMethod().getName())) {
                return result;
            }
            return ref[0];
        });
        return ref[0];
    }
}
