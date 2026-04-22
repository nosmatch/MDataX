package com.mogu.data.integration;

import com.mogu.data.integration.entity.SqlTask;
import com.mogu.data.integration.entity.SqlTaskWorkflow;
import com.mogu.data.integration.mapper.SqlTaskMapper;
import com.mogu.data.integration.mapper.SqlTaskWorkflowMapper;
import com.mogu.data.integration.service.SqlTaskDependencyService;
import com.mogu.data.integration.service.SqlTaskService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SQL任务服务改造验证测试
 *
 * @author fengzhu
 */
@SpringBootTest
@Transactional
public class ServiceSqlTaskTest {

    @Autowired
    private SqlTaskService sqlTaskService;

    @Autowired
    private SqlTaskMapper sqlTaskMapper;

    @Autowired
    private SqlTaskWorkflowMapper workflowMapper;

    @Autowired
    private SqlTaskDependencyService dependencyService;

    // ==================== 独立任务（兼容旧逻辑） ====================

    @Test
    public void testCreateStandaloneTask() {
        SqlTask task = new SqlTask();
        task.setTaskName("standalone-task");
        task.setSqlContent("SELECT 1");
        task.setCronExpression("0 2 * * *");
        sqlTaskService.createTask(task);

        assertNotNull(task.getId());
        assertNull(task.getWorkflowId());
        assertEquals(0, task.getStatus());
    }

    @Test
    public void testUpdateStandaloneTaskCron() {
        SqlTask task = new SqlTask();
        task.setTaskName("standalone-update");
        task.setSqlContent("SELECT 1");
        task.setCronExpression("0 2 * * *");
        sqlTaskService.createTask(task);

        SqlTask update = new SqlTask();
        update.setId(task.getId());
        update.setCronExpression("0 3 * * *");
        sqlTaskService.updateTask(update);

        SqlTask result = sqlTaskService.getById(task.getId());
        assertEquals("0 3 * * *", result.getCronExpression());
    }

    @Test
    public void testDeleteStandaloneTask() {
        SqlTask task = new SqlTask();
        task.setTaskName("standalone-del");
        task.setSqlContent("SELECT 1");
        sqlTaskService.createTask(task);

        sqlTaskService.deleteTask(task.getId());
        assertNull(sqlTaskService.getById(task.getId()));
    }

    // ==================== Workflow 内任务 ====================

    @Test
    public void testCreateWorkflowTask() {
        SqlTaskWorkflow wf = createWorkflow();

        SqlTask task = new SqlTask();
        task.setTaskName("wf-task");
        task.setSqlContent("SELECT 1");
        task.setWorkflowId(wf.getId());
        sqlTaskService.createTask(task, null);

        SqlTask result = sqlTaskService.getById(task.getId());
        assertEquals(wf.getId(), result.getWorkflowId());
        assertNull(result.getCronExpression());
        assertNotNull(result.getDsTaskCode());
    }

    @Test
    public void testCreateWorkflowTaskWithDependency() {
        SqlTaskWorkflow wf = createWorkflow();

        SqlTask taskA = new SqlTask();
        taskA.setTaskName("wf-dep-A");
        taskA.setSqlContent("SELECT 1");
        taskA.setWorkflowId(wf.getId());
        sqlTaskService.createTask(taskA, null);

        SqlTask taskB = new SqlTask();
        taskB.setTaskName("wf-dep-B");
        taskB.setSqlContent("SELECT 1");
        taskB.setWorkflowId(wf.getId());
        sqlTaskService.createTask(taskB, Collections.singletonList(taskA.getId()));

        List<Long> upstream = dependencyService.getDependTaskIds(taskB.getId());
        assertEquals(1, upstream.size());
        assertEquals(taskA.getId(), upstream.get(0));
    }

    @Test
    public void testDeleteWorkflowTaskWithDownstreamBlocked() {
        SqlTaskWorkflow wf = createWorkflow();

        SqlTask taskA = new SqlTask();
        taskA.setTaskName("wf-del-A");
        taskA.setSqlContent("SELECT 1");
        taskA.setWorkflowId(wf.getId());
        sqlTaskService.createTask(taskA, null);

        SqlTask taskB = new SqlTask();
        taskB.setTaskName("wf-del-B");
        taskB.setSqlContent("SELECT 1");
        taskB.setWorkflowId(wf.getId());
        sqlTaskService.createTask(taskB, Collections.singletonList(taskA.getId()));

        assertThrows(IllegalArgumentException.class, () ->
                sqlTaskService.deleteTask(taskA.getId()));
    }

    @Test
    public void testDeleteWorkflowTaskSuccess() {
        SqlTaskWorkflow wf = createWorkflow();

        SqlTask taskA = new SqlTask();
        taskA.setTaskName("wf-del-ok");
        taskA.setSqlContent("SELECT 1");
        taskA.setWorkflowId(wf.getId());
        sqlTaskService.createTask(taskA, null);

        SqlTask taskB = new SqlTask();
        taskB.setTaskName("wf-del-ok2");
        taskB.setSqlContent("SELECT 1");
        taskB.setWorkflowId(wf.getId());
        sqlTaskService.createTask(taskB, Collections.singletonList(taskA.getId()));

        sqlTaskService.deleteTask(taskB.getId());

        assertNull(sqlTaskService.getById(taskB.getId()));
    }

    @Test
    public void testChangeWorkflowBlocked() {
        SqlTaskWorkflow wf1 = createWorkflow();
        SqlTaskWorkflow wf2 = createWorkflow();

        SqlTask task = new SqlTask();
        task.setTaskName("wf-change");
        task.setSqlContent("SELECT 1");
        task.setWorkflowId(wf1.getId());
        sqlTaskService.createTask(task, null);

        SqlTask update = new SqlTask();
        update.setId(task.getId());
        update.setWorkflowId(wf2.getId());

        assertThrows(IllegalArgumentException.class, () ->
                sqlTaskService.updateTask(update, null));
    }

    // ==================== helper ====================

    private SqlTaskWorkflow createWorkflow() {
        SqlTaskWorkflow wf = new SqlTaskWorkflow();
        wf.setWorkflowName("wf-" + System.currentTimeMillis());
        wf.setStatus(0);
        workflowMapper.insert(wf);
        return wf;
    }

    private SqlTask createWfTask(String name, Long workflowId) {
        SqlTask task = new SqlTask();
        task.setTaskName(name);
        task.setSqlContent("SELECT 1");
        task.setWorkflowId(workflowId);
        sqlTaskMapper.insert(task);
        return task;
    }
}
