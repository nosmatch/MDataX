package com.mogu.data.schedulerx.engine;

import com.mogu.data.schedulerx.engine.model.Dag;
import com.mogu.data.schedulerx.engine.model.DagTask;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * DAG 内存模型单元测试
 *
 * @author fengzhu
 */
class DagTest {

    @Test
    @DisplayName("buildDownstream: 线性DAG下游关系正确")
    void testBuildDownstream_Linear() {
        Dag dag = new Dag();
        dag.getTasks().add(buildTask("A", null));
        dag.getTasks().add(buildTask("B", Arrays.asList("A")));
        dag.getTasks().add(buildTask("C", Arrays.asList("B")));
        dag.buildDownstream();

        List<String> aDown = dag.getDownstream("A");
        assertEquals(1, aDown.size());
        assertEquals("B", aDown.get(0));

        List<String> bDown = dag.getDownstream("B");
        assertEquals(1, bDown.size());
        assertEquals("C", bDown.get(0));

        List<String> cDown = dag.getDownstream("C");
        assertTrue(cDown.isEmpty());
    }

    @Test
    @DisplayName("buildDownstream: 分支DAG下游关系正确")
    void testBuildDownstream_Branch() {
        Dag dag = new Dag();
        dag.getTasks().add(buildTask("A", null));
        dag.getTasks().add(buildTask("B", Arrays.asList("A")));
        dag.getTasks().add(buildTask("C", Arrays.asList("A")));
        dag.getTasks().add(buildTask("D", Arrays.asList("B", "C")));
        dag.buildDownstream();

        List<String> aDown = dag.getDownstream("A");
        assertEquals(2, aDown.size());
        assertTrue(aDown.contains("B"));
        assertTrue(aDown.contains("C"));

        List<String> dDown = dag.getDownstream("D");
        assertTrue(dDown.isEmpty());
    }

    @Test
    @DisplayName("getRootTasks: 正确获取入度为0的节点")
    void testGetRootTasks() {
        Dag dag = new Dag();
        dag.getTasks().add(buildTask("A", null));
        dag.getTasks().add(buildTask("B", null));
        dag.getTasks().add(buildTask("C", Arrays.asList("A", "B")));
        dag.buildDownstream();

        List<DagTask> roots = dag.getRootTasks();
        assertEquals(2, roots.size());
        List<String> ids = Arrays.asList(roots.get(0).getTaskId(), roots.get(1).getTaskId());
        assertTrue(ids.contains("A"));
        assertTrue(ids.contains("B"));
    }

    @Test
    @DisplayName("getRootTasks: 单根节点DAG")
    void testGetRootTasks_SingleRoot() {
        Dag dag = new Dag();
        dag.getTasks().add(buildTask("A", null));
        dag.getTasks().add(buildTask("B", Arrays.asList("A")));
        dag.buildDownstream();

        List<DagTask> roots = dag.getRootTasks();
        assertEquals(1, roots.size());
        assertEquals("A", roots.get(0).getTaskId());
    }

    @Test
    @DisplayName("getTask: 根据taskId获取DagTask")
    void testGetTask() {
        Dag dag = new Dag();
        DagTask taskA = buildTask("A", null);
        dag.getTasks().add(taskA);
        dag.getTaskMap().put("A", taskA);

        assertNotNull(dag.getTask("A"));
        assertEquals("A", dag.getTask("A").getTaskId());
        assertNull(dag.getTask("Z"));
    }

    @Test
    @DisplayName("getDownstream: 不存在的taskId返回空列表")
    void testGetDownstream_NotExist() {
        Dag dag = new Dag();
        dag.buildDownstream();
        List<String> down = dag.getDownstream("NOT_EXIST");
        assertNotNull(down);
        assertTrue(down.isEmpty());
    }

    @Test
    @DisplayName("buildDownstream: 重复调用不会出错")
    void testBuildDownstream_Idempotent() {
        Dag dag = new Dag();
        dag.getTasks().add(buildTask("A", null));
        dag.getTasks().add(buildTask("B", Arrays.asList("A")));
        dag.buildDownstream();
        dag.buildDownstream(); // 第二次调用

        List<String> aDown = dag.getDownstream("A");
        assertEquals(1, aDown.size());
        assertEquals("B", aDown.get(0));
    }

    private DagTask buildTask(String taskId, List<String> upstream) {
        DagTask task = new DagTask();
        task.setTaskId(taskId);
        task.setTaskName("Task_" + taskId);
        task.setUpstream(upstream);
        return task;
    }
}
