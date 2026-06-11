package com.mogu.data.schedulerx.engine;

import com.mogu.data.schedulerx.enums.EventType;
import com.mogu.data.schedulerx.enums.FailureStrategy;
import com.mogu.data.schedulerx.enums.TaskInstanceStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * StateMachine 单元测试
 *
 * @author fengzhu
 */
class StateMachineTest {

    private StateMachine stateMachine;

    @BeforeEach
    void setUp() {
        stateMachine = new StateMachine();
    }

    @Test
    @DisplayName("PENDING -> START -> RUNNING 合法")
    void testPendingToRunning() {
        assertTrue(stateMachine.canTransition(TaskInstanceStatus.PENDING, EventType.START));
        assertEquals(TaskInstanceStatus.RUNNING, stateMachine.transition(TaskInstanceStatus.PENDING, EventType.START));
    }

    @Test
    @DisplayName("WAITING_UPSTREAM -> START -> RUNNING 合法")
    void testWaitingToRunning() {
        assertTrue(stateMachine.canTransition(TaskInstanceStatus.WAITING_UPSTREAM, EventType.START));
        assertEquals(TaskInstanceStatus.RUNNING, stateMachine.transition(TaskInstanceStatus.WAITING_UPSTREAM, EventType.START));
    }

    @Test
    @DisplayName("RUNNING -> SUCCESS 合法")
    void testRunningToSuccess() {
        assertTrue(stateMachine.canTransition(TaskInstanceStatus.RUNNING, EventType.SUCCESS));
        assertEquals(TaskInstanceStatus.SUCCESS, stateMachine.transition(TaskInstanceStatus.RUNNING, EventType.SUCCESS));
    }

    @Test
    @DisplayName("RUNNING -> FAILURE 合法")
    void testRunningToFailure() {
        assertTrue(stateMachine.canTransition(TaskInstanceStatus.RUNNING, EventType.FAILURE));
        assertEquals(TaskInstanceStatus.FAILURE, stateMachine.transition(TaskInstanceStatus.RUNNING, EventType.FAILURE));
    }

    @Test
    @DisplayName("RUNNING -> TIMEOUT 合法")
    void testRunningToTimeout() {
        assertTrue(stateMachine.canTransition(TaskInstanceStatus.RUNNING, EventType.TIMEOUT));
        assertEquals(TaskInstanceStatus.TIMEOUT, stateMachine.transition(TaskInstanceStatus.RUNNING, EventType.TIMEOUT));
    }

    @Test
    @DisplayName("SUCCESS -> FAILURE 非法")
    void testSuccessToFailure_Illegal() {
        assertFalse(stateMachine.canTransition(TaskInstanceStatus.SUCCESS, EventType.FAILURE));
        assertThrows(IllegalStateException.class, () -> {
            stateMachine.transition(TaskInstanceStatus.SUCCESS, EventType.FAILURE);
        });
    }

    @Test
    @DisplayName("FAILURE -> SUCCESS 非法")
    void testFailureToSuccess_Illegal() {
        assertFalse(stateMachine.canTransition(TaskInstanceStatus.FAILURE, EventType.SUCCESS));
    }

    @Test
    @DisplayName("PENDING -> SUCCESS 非法（必须先START）")
    void testPendingToSuccess_Illegal() {
        assertFalse(stateMachine.canTransition(TaskInstanceStatus.PENDING, EventType.SUCCESS));
        assertThrows(IllegalStateException.class, () -> {
            stateMachine.transition(TaskInstanceStatus.PENDING, EventType.SUCCESS);
        });
    }

    @Test
    @DisplayName("所有任务SUCCESS，DAG聚合为SUCCESS")
    void testAggregate_AllSuccess() {
        Map<String, String> statuses = new HashMap<>();
        statuses.put("A", "SUCCESS");
        statuses.put("B", "SUCCESS");
        statuses.put("C", "SUCCESS");

        String result = stateMachine.aggregateDagStatus(statuses, FailureStrategy.STOP_ALL);
        assertEquals("SUCCESS", result);
    }

    @Test
    @DisplayName("有任务FAILURE，DAG聚合为FAILURE")
    void testAggregate_HasFailure() {
        Map<String, String> statuses = new HashMap<>();
        statuses.put("A", "SUCCESS");
        statuses.put("B", "FAILURE");
        statuses.put("C", "SUCCESS");

        String result = stateMachine.aggregateDagStatus(statuses, FailureStrategy.STOP_ALL);
        assertEquals("FAILURE", result);
    }

    @Test
    @DisplayName("有任务TIMEOUT，DAG聚合为TIMEOUT")
    void testAggregate_HasTimeout() {
        Map<String, String> statuses = new HashMap<>();
        statuses.put("A", "SUCCESS");
        statuses.put("B", "TIMEOUT");
        statuses.put("C", "SUCCESS");

        String result = stateMachine.aggregateDagStatus(statuses, FailureStrategy.STOP_ALL);
        assertEquals("TIMEOUT", result);
    }

    @Test
    @DisplayName("有任务RUNNING，DAG聚合为RUNNING")
    void testAggregate_HasRunning() {
        Map<String, String> statuses = new HashMap<>();
        statuses.put("A", "SUCCESS");
        statuses.put("B", "RUNNING");
        statuses.put("C", "SUCCESS");

        String result = stateMachine.aggregateDagStatus(statuses, FailureStrategy.STOP_ALL);
        assertEquals("RUNNING", result);
    }

    @Test
    @DisplayName("空状态聚合为RUNNING")
    void testAggregate_Empty() {
        Map<String, String> statuses = new HashMap<>();
        String result = stateMachine.aggregateDagStatus(statuses, FailureStrategy.STOP_ALL);
        assertEquals("RUNNING", result);
    }

    @Test
    @DisplayName("存在FAILURE和TIMEOUT时FAILURE优先")
    void testAggregate_FailureOverTimeout() {
        Map<String, String> statuses = new HashMap<>();
        statuses.put("A", "FAILURE");
        statuses.put("B", "TIMEOUT");

        String result = stateMachine.aggregateDagStatus(statuses, FailureStrategy.STOP_ALL);
        assertEquals("FAILURE", result);
    }

    @Test
    @DisplayName("SKIPPED任务不影响SUCCESS判定")
    void testAggregate_SkippedWithSuccess() {
        Map<String, String> statuses = new HashMap<>();
        statuses.put("A", "SUCCESS");
        statuses.put("B", "SKIPPED");
        statuses.put("C", "SUCCESS");

        String result = stateMachine.aggregateDagStatus(statuses, FailureStrategy.CONTINUE);
        assertEquals("SUCCESS", result);
    }
}
