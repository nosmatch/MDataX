package com.mogu.data.schedulerx.engine;

import com.mogu.data.schedulerx.engine.model.Dag;
import com.mogu.data.schedulerx.engine.model.DagTask;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TopologyResolver 单元测试
 *
 * @author fengzhu
 */
class TopologyResolverTest {

    private TopologyResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new TopologyResolver();
    }

    @Test
    @DisplayName("线性DAG: A -> B -> C，无任务状态时A就绪")
    void testLinearDag() {
        Dag dag = buildLinearDag();
        Map<String, String> statusMap = new HashMap<>();

        List<DagTask> ready = resolver.resolveReadyTasks(dag, statusMap);
        assertEquals(1, ready.size());
        assertEquals("A", ready.get(0).getTaskId());
    }

    @Test
    @DisplayName("线性DAG: A成功后B就绪")
    void testLinearDag_AfterASuccess() {
        Dag dag = buildLinearDag();
        Map<String, String> statusMap = new HashMap<>();
        statusMap.put("A", "SUCCESS");

        List<DagTask> ready = resolver.resolveReadyTasks(dag, statusMap);
        assertEquals(1, ready.size());
        assertEquals("B", ready.get(0).getTaskId());
    }

    @Test
    @DisplayName("线性DAG: A和B都成功后C就绪")
    void testLinearDag_AfterABSuccess() {
        Dag dag = buildLinearDag();
        Map<String, String> statusMap = new HashMap<>();
        statusMap.put("A", "SUCCESS");
        statusMap.put("B", "SUCCESS");

        List<DagTask> ready = resolver.resolveReadyTasks(dag, statusMap);
        assertEquals(1, ready.size());
        assertEquals("C", ready.get(0).getTaskId());
    }

    @Test
    @DisplayName("分支DAG: A -> (B, C) -> D，初始只有A就绪")
    void testBranchDag_Initial() {
        Dag dag = buildBranchDag();
        Map<String, String> statusMap = new HashMap<>();

        List<DagTask> ready = resolver.resolveReadyTasks(dag, statusMap);
        assertEquals(1, ready.size());
        assertEquals("A", ready.get(0).getTaskId());
    }

    @Test
    @DisplayName("分支DAG: A成功后B和C同时就绪")
    void testBranchDag_AfterASuccess() {
        Dag dag = buildBranchDag();
        Map<String, String> statusMap = new HashMap<>();
        statusMap.put("A", "SUCCESS");

        List<DagTask> ready = resolver.resolveReadyTasks(dag, statusMap);
        assertEquals(2, ready.size());
        List<String> ids = Arrays.asList(ready.get(0).getTaskId(), ready.get(1).getTaskId());
        assertTrue(ids.contains("B"));
        assertTrue(ids.contains("C"));
    }

    @Test
    @DisplayName("分支DAG: A/B/C成功后D就绪")
    void testBranchDag_AfterABCSuccess() {
        Dag dag = buildBranchDag();
        Map<String, String> statusMap = new HashMap<>();
        statusMap.put("A", "SUCCESS");
        statusMap.put("B", "SUCCESS");
        statusMap.put("C", "SUCCESS");

        List<DagTask> ready = resolver.resolveReadyTasks(dag, statusMap);
        assertEquals(1, ready.size());
        assertEquals("D", ready.get(0).getTaskId());
    }

    @Test
    @DisplayName("分支DAG: A/B成功但C失败，D不应就绪")
    void testBranchDag_CFailure() {
        Dag dag = buildBranchDag();
        Map<String, String> statusMap = new HashMap<>();
        statusMap.put("A", "SUCCESS");
        statusMap.put("B", "SUCCESS");
        statusMap.put("C", "FAILURE");

        List<DagTask> ready = resolver.resolveReadyTasks(dag, statusMap);
        assertTrue(ready.isEmpty(), "上游有失败任务，D不应就绪");
    }

    @Test
    @DisplayName("复杂DAG无环检测通过")
    void testHasCycle_NoCycle() {
        Dag dag = buildBranchDag();
        assertFalse(resolver.hasCycle(dag), "无环DAG应返回false");
    }

    @Test
    @DisplayName("简单环检测: A -> B -> A")
    void testHasCycle_SimpleCycle() {
        Dag dag = new Dag();
        dag.getTasks().add(buildTask("A", Arrays.asList("B")));
        dag.getTasks().add(buildTask("B", Arrays.asList("A")));
        dag.buildDownstream();

        assertTrue(resolver.hasCycle(dag), "有环DAG应返回true");
    }

    @Test
    @DisplayName("复杂环检测: A -> B -> C -> A")
    void testHasCycle_ComplexCycle() {
        Dag dag = new Dag();
        dag.getTasks().add(buildTask("A", Arrays.asList("B")));
        dag.getTasks().add(buildTask("B", Arrays.asList("C")));
        dag.getTasks().add(buildTask("C", Arrays.asList("A")));
        dag.buildDownstream();

        assertTrue(resolver.hasCycle(dag), "有环DAG应返回true");
    }

    @Test
    @DisplayName("多根节点DAG: A和B都是根")
    void testMultipleRoots() {
        Dag dag = new Dag();
        dag.getTasks().add(buildTask("A", null));
        dag.getTasks().add(buildTask("B", null));
        dag.getTasks().add(buildTask("C", Arrays.asList("A", "B")));
        dag.buildDownstream();

        Map<String, String> statusMap = new HashMap<>();
        List<DagTask> ready = resolver.resolveReadyTasks(dag, statusMap);
        assertEquals(2, ready.size());
    }

    @Test
    @DisplayName("已执行过的任务不会重复就绪")
    void testAlreadyExecutedNotReady() {
        Dag dag = buildLinearDag();
        Map<String, String> statusMap = new HashMap<>();
        statusMap.put("A", "RUNNING");

        List<DagTask> ready = resolver.resolveReadyTasks(dag, statusMap);
        assertTrue(ready.isEmpty(), "RUNNING状态的任务不应重复就绪");
    }

    // ============ helper methods ============

    private Dag buildLinearDag() {
        Dag dag = new Dag();
        dag.getTasks().add(buildTask("A", null));
        dag.getTasks().add(buildTask("B", Arrays.asList("A")));
        dag.getTasks().add(buildTask("C", Arrays.asList("B")));
        dag.buildDownstream();
        return dag;
    }

    private Dag buildBranchDag() {
        Dag dag = new Dag();
        dag.getTasks().add(buildTask("A", null));
        dag.getTasks().add(buildTask("B", Arrays.asList("A")));
        dag.getTasks().add(buildTask("C", Arrays.asList("A")));
        dag.getTasks().add(buildTask("D", Arrays.asList("B", "C")));
        dag.buildDownstream();
        return dag;
    }

    private DagTask buildTask(String taskId, List<String> upstream) {
        DagTask task = new DagTask();
        task.setTaskId(taskId);
        task.setTaskName("Task_" + taskId);
        task.setUpstream(upstream);
        return task;
    }
}
