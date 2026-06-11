package com.mogu.data.schedulerx.engine;

import com.mogu.data.schedulerx.entity.TaskInstance;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TimeoutChecker 单元测试
 *
 * @author fengzhu
 */
class TimeoutCheckerTest {

    private TimeoutChecker checker;

    @BeforeEach
    void setUp() {
        checker = new TimeoutChecker();
    }

    @Test
    @DisplayName("isTimeout: 未超时应返回false")
    void testNotTimeout() throws Exception {
        TaskInstance task = new TaskInstance();
        task.setStartTime(LocalDateTime.now().minusSeconds(300)); // 5分钟前开始

        java.lang.reflect.Method method = TimeoutChecker.class.getDeclaredMethod(
                "isTimeout", TaskInstance.class, LocalDateTime.class);
        method.setAccessible(true);

        boolean result = (boolean) method.invoke(checker, task, LocalDateTime.now());
        assertFalse(result, "5分钟不应超过默认600秒超时");
    }

    @Test
    @DisplayName("isTimeout: 已超时应返回true")
    void testIsTimeout() throws Exception {
        TaskInstance task = new TaskInstance();
        task.setStartTime(LocalDateTime.now().minusSeconds(800)); // 13分钟前开始

        java.lang.reflect.Method method = TimeoutChecker.class.getDeclaredMethod(
                "isTimeout", TaskInstance.class, LocalDateTime.class);
        method.setAccessible(true);

        boolean result = (boolean) method.invoke(checker, task, LocalDateTime.now());
        assertTrue(result, "13分钟应超过默认600秒超时");
    }

    @Test
    @DisplayName("isTimeout: 刚好600秒边界，不应超时")
    void testTimeoutBoundary() throws Exception {
        TaskInstance task = new TaskInstance();
        task.setStartTime(LocalDateTime.now().minusSeconds(600)); // 刚好600秒

        java.lang.reflect.Method method = TimeoutChecker.class.getDeclaredMethod(
                "isTimeout", TaskInstance.class, LocalDateTime.class);
        method.setAccessible(true);

        boolean result = (boolean) method.invoke(checker, task, LocalDateTime.now());
        assertFalse(result, "刚好600秒不应判定为超时");
    }

    @Test
    @DisplayName("isTimeout: startTime为null时不超时")
    void testNullStartTime() throws Exception {
        TaskInstance task = new TaskInstance();
        task.setStartTime(null);

        java.lang.reflect.Method method = TimeoutChecker.class.getDeclaredMethod(
                "isTimeout", TaskInstance.class, LocalDateTime.class);
        method.setAccessible(true);

        boolean result = (boolean) method.invoke(checker, task, LocalDateTime.now());
        assertFalse(result, "startTime为null不应判定为超时");
    }

    @Test
    @DisplayName("isTimeout: 601秒应判定超时")
    void testJustOverTimeout() throws Exception {
        TaskInstance task = new TaskInstance();
        task.setStartTime(LocalDateTime.now().minusSeconds(601)); // 601秒

        java.lang.reflect.Method method = TimeoutChecker.class.getDeclaredMethod(
                "isTimeout", TaskInstance.class, LocalDateTime.class);
        method.setAccessible(true);

        boolean result = (boolean) method.invoke(checker, task, LocalDateTime.now());
        assertTrue(result, "601秒应判定为超时");
    }
}
