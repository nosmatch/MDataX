package com.mogu.data.integration;

import com.mogu.data.integration.entity.SqlTask;
import com.mogu.data.integration.entity.SqlTaskWorkflow;
import com.mogu.data.integration.mapper.SqlTaskMapper;
import com.mogu.data.integration.mapper.SqlTaskWorkflowMapper;
import com.mogu.data.integration.service.SqlTaskDependencyService;
import com.mogu.data.integration.service.SqlTaskWorkflowService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SQL任务工作流与依赖服务验证测试
 *
 * @author fengzhu
 */
@SpringBootTest
@Transactional
public class ServiceWorkflowTest {

    @Autowired
    private SqlTaskWorkflowService workflowService;

    @Autowired
    private SqlTaskDependencyService dependencyService;

    @Autowired
    private SqlTaskWorkflowMapper workflowMapper;

    @Autowired
    private SqlTaskMapper sqlTaskMapper;

    private Long wfId;

    @BeforeEach
    public void setup() {
        SqlTaskWorkflow wf = new SqlTaskWorkflow();
        wf.setWorkflowName("test-wf-" + System.currentTimeMillis());
        wf.setCronExpression("0 2 * * *");
        wf.setStatus(0);
        workflowMapper.insert(wf);
        wfId = wf.getId();
    }

    // ==================== SqlTaskWorkflowService ====================

    @Test
    public void testCreateWorkflowSuccess() {
        SqlTaskWorkflow wf = new SqlTaskWorkflow();
        wf.setWorkflowName("new-wf-" + System.currentTimeMillis());
        wf.setCronExpression("0 3 * * *");
        workflowService.createWorkflow(wf);
        assertNotNull(wf.getId());
        assertEquals(0, wf.getStatus());
    }

    @Test
    public void testCreateWorkflowDuplicateName() {
        SqlTaskWorkflow wf = new SqlTaskWorkflow();
        wf.setWorkflowName("dup-wf");
        workflowService.createWorkflow(wf);

        SqlTaskWorkflow wf2 = new SqlTaskWorkflow();
        wf2.setWorkflowName("dup-wf");
        assertThrows(IllegalArgumentException.class, () -> workflowService.createWorkflow(wf2));
    }

    @Test
    public void testUpdateWorkflowSuccess() {
        SqlTaskWorkflow wf = workflowService.getById(wfId);
        wf.setDescription("updated desc");
        workflowService.updateWorkflow(wf);

        SqlTaskWorkflow updated = workflowService.getById(wfId);
        assertEquals("updated desc", updated.getDescription());
    }

    @Test
    public void testDeleteWorkflowWithTasksBlocked() {
        // 在工作流内创建一个任务
        SqlTask task = new SqlTask();
        task.setTaskName("task-in-wf");
        task.setSqlContent("SELECT 1");
        task.setWorkflowId(wfId);
        sqlTaskMapper.insert(task);

        assertThrows(IllegalArgumentException.class, () -> workflowService.deleteWorkflow(wfId));
    }

    @Test
    public void testDeleteWorkflowEmpty() {
        workflowService.deleteWorkflow(wfId);
        assertNull(workflowService.getById(wfId));
    }

    // ==================== SqlTaskDependencyService ====================

    @Test
    public void testSaveDependenciesSuccess() {
        // 创建 Workflow 内的 3 个任务: A(id=10), B(id=20), C(id=30)
        Long taskA = insertTask("task-A", wfId);
        Long taskB = insertTask("task-B", wfId);
        Long taskC = insertTask("task-C", wfId);

        // B 依赖 A, C 依赖 A
        dependencyService.saveDependencies(taskB, Collections.singletonList(taskA));
        dependencyService.saveDependencies(taskC, Collections.singletonList(taskA));

        List<Long> upstreamOfB = dependencyService.getDependTaskIds(taskB);
        assertEquals(1, upstreamOfB.size());
        assertEquals(taskA, upstreamOfB.get(0));

        List<Long> downstreamOfA = dependencyService.getDownstreamTaskIds(taskA);
        assertEquals(2, downstreamOfA.size());
    }

    @Test
    public void testCircularDependencyBlocked() {
        Long taskA = insertTask("c-A", wfId);
        Long taskB = insertTask("c-B", wfId);
        Long taskC = insertTask("c-C", wfId);

        // A -> B -> C
        dependencyService.saveDependencies(taskB, Collections.singletonList(taskA));
        dependencyService.saveDependencies(taskC, Collections.singletonList(taskB));

        // 尝试 C -> A，形成环
        assertThrows(IllegalArgumentException.class, () ->
                dependencyService.saveDependencies(taskA, Collections.singletonList(taskC)));
    }

    @Test
    public void testCrossWorkflowDependencyBlocked() {
        // 另一个工作流
        SqlTaskWorkflow wf2 = new SqlTaskWorkflow();
        wf2.setWorkflowName("wf2-" + System.currentTimeMillis());
        workflowMapper.insert(wf2);

        Long taskA = insertTask("w1-task", wfId);
        Long taskB = insertTask("w2-task", wf2.getId());

        assertThrows(IllegalArgumentException.class, () ->
                dependencyService.saveDependencies(taskA, Collections.singletonList(taskB)));
    }

    @Test
    public void testCollectAllUpstream() {
        Long taskA = insertTask("u-A", wfId);
        Long taskB = insertTask("u-B", wfId);
        Long taskC = insertTask("u-C", wfId);

        // A -> B -> C
        dependencyService.saveDependencies(taskB, Collections.singletonList(taskA));
        dependencyService.saveDependencies(taskC, Arrays.asList(taskA, taskB));

        Set<Long> upstreamOfC = dependencyService.collectAllUpstream(taskC);
        assertEquals(2, upstreamOfC.size());
        assertTrue(upstreamOfC.contains(taskA));
        assertTrue(upstreamOfC.contains(taskB));
    }

    @Test
    public void testCollectAllDownstream() {
        Long taskA = insertTask("d-A", wfId);
        Long taskB = insertTask("d-B", wfId);
        Long taskC = insertTask("d-C", wfId);

        // A -> B -> C
        dependencyService.saveDependencies(taskB, Collections.singletonList(taskA));
        dependencyService.saveDependencies(taskC, Collections.singletonList(taskB));

        Set<Long> downstreamOfA = dependencyService.collectAllDownstream(taskA);
        assertEquals(2, downstreamOfA.size());
        assertTrue(downstreamOfA.contains(taskB));
        assertTrue(downstreamOfA.contains(taskC));
    }

    @Test
    public void testDeleteByTaskId() {
        Long taskA = insertTask("del-A", wfId);
        Long taskB = insertTask("del-B", wfId);
        dependencyService.saveDependencies(taskB, Collections.singletonList(taskA));

        dependencyService.deleteByTaskId(taskB);
        assertTrue(dependencyService.getDependTaskIds(taskB).isEmpty());
    }

    @Test
    public void testDeleteByWorkflowId() {
        Long taskA = insertTask("delw-A", wfId);
        Long taskB = insertTask("delw-B", wfId);
        dependencyService.saveDependencies(taskB, Collections.singletonList(taskA));

        dependencyService.deleteByWorkflowId(wfId);
        assertTrue(dependencyService.getDependTaskIds(taskB).isEmpty());
    }

    // ==================== helper ====================

    private Long insertTask(String name, Long workflowId) {
        SqlTask task = new SqlTask();
        task.setTaskName(name);
        task.setSqlContent("SELECT 1");
        task.setWorkflowId(workflowId);
        sqlTaskMapper.insert(task);
        return task.getId();
    }
}
