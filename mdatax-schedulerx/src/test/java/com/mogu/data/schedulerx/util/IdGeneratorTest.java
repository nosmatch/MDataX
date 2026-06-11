package com.mogu.data.schedulerx.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * IdGenerator 单元测试
 *
 * @author fengzhu
 */
class IdGeneratorTest {

    @Test
    @DisplayName("DAG实例ID格式正确: DI前缀+时间戳+4位随机数")
    void testDagInstanceIdFormat() {
        String id = IdGenerator.generateDagInstanceId();
        assertNotNull(id);
        assertTrue(id.startsWith("DI"), "应以DI开头");
        assertTrue(id.length() >= 16, "长度应至少16位");
        assertTrue(id.matches("DI\\d+"), "应只包含DI和数字");
    }

    @Test
    @DisplayName("任务实例ID格式正确: TI前缀+时间戳+4位随机数")
    void testTaskInstanceIdFormat() {
        String id = IdGenerator.generateTaskInstanceId();
        assertNotNull(id);
        assertTrue(id.startsWith("TI"), "应以TI开头");
        assertTrue(id.length() >= 16, "长度应至少16位");
        assertTrue(id.matches("TI\\d+"), "应只包含TI和数字");
    }

    @Test
    @DisplayName("生成的ID具有唯一性")
    void testUniqueness() {
        Set<String> dagIds = new HashSet<>();
        Set<String> taskIds = new HashSet<>();

        for (int i = 0; i < 1000; i++) {
            String dagId = IdGenerator.generateDagInstanceId();
            String taskId = IdGenerator.generateTaskInstanceId();
            assertFalse(dagIds.contains(dagId), "DAG实例ID应唯一: " + dagId);
            assertFalse(taskIds.contains(taskId), "任务实例ID应唯一: " + taskId);
            dagIds.add(dagId);
            taskIds.add(taskId);
        }

        assertEquals(1000, dagIds.size());
        assertEquals(1000, taskIds.size());
    }

    @Test
    @DisplayName("连续生成的ID不相同")
    void testConsecutiveIdsDifferent() {
        String id1 = IdGenerator.generateDagInstanceId();
        String id2 = IdGenerator.generateDagInstanceId();
        assertNotEquals(id1, id2);
    }

    @Test
    @DisplayName("DAG和任务实例ID不会冲突")
    void testNoCollisionBetweenTypes() {
        Set<String> allIds = new HashSet<>();
        for (int i = 0; i < 100; i++) {
            allIds.add(IdGenerator.generateDagInstanceId());
            allIds.add(IdGenerator.generateTaskInstanceId());
        }
        assertEquals(200, allIds.size(), "两种ID之间不应冲突");
    }
}
