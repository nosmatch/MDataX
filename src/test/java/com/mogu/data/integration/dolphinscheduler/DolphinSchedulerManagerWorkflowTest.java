package com.mogu.data.integration.dolphinscheduler;

import com.mogu.data.integration.entity.SqlTask;
import com.mogu.data.integration.entity.SqlTaskDependency;
import com.mogu.data.integration.entity.SqlTaskWorkflow;
import com.mogu.data.integration.mapper.SqlTaskDependencyMapper;
import com.mogu.data.integration.mapper.SqlTaskMapper;
import com.mogu.data.integration.mapper.SqlTaskWorkflowMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

/**
 * DolphinSchedulerManager Workflow 调度验证测试
 *
 * @author fengzhu
 */
@SpringBootTest
@Transactional
public class DolphinSchedulerManagerWorkflowTest {

    @Autowired
    private DolphinSchedulerManager manager;

    @Autowired
    private DolphinSchedulerClient dsClient;

    @Autowired
    private DolphinSchedulerProperties props;

    @Autowired
    private SqlTaskWorkflowMapper workflowMapper;

    @Autowired
    private SqlTaskMapper sqlTaskMapper;

    @Autowired
    private SqlTaskDependencyMapper dependencyMapper;

    private SqlTaskWorkflow workflow;
    private SqlTask taskA;
    private SqlTask taskB;

    @BeforeEach
    public void setup() {
        workflow = new SqlTaskWorkflow();
        workflow.setWorkflowName("test-wf-ds-" + System.currentTimeMillis());
        workflow.setCronExpression("0 2 * * *");
        workflow.setStatus(0);
        workflowMapper.insert(workflow);

        taskA = createTask("task-A");
        taskB = createTask("task-B");

        SqlTaskDependency dep = new SqlTaskDependency();
        dep.setWorkflowId(workflow.getId());
        dep.setTaskId(taskB.getId());
        dep.setDependTaskId(taskA.getId());
        dependencyMapper.insert(dep);
    }

    @Test
    public void testScheduleWorkflow() {
        if (!props.isEnabled()) {
            System.out.println("DS 未启用，跳过集成测试");
            return;
        }

        // 1. 调度 Workflow 到 DS
        manager.scheduleWorkflow(workflow);
        assertNotNull(workflow.getDsProcessCode(), "应返回 processCode");
        System.out.println("Workflow 调度成功: processCode=" + workflow.getDsProcessCode());

        // 2. 手动触发实例验证 DAG 可用
        Long instanceId = dsClient.startProcessInstance(workflow.getDsProcessCode());
        assertNotNull(instanceId, "应能手动触发实例");
        System.out.println("手动触发 Workflow 实例成功: instanceId=" + instanceId);

        // 3. 验证任务已分配 ds_task_code
        SqlTask updatedA = sqlTaskMapper.selectById(taskA.getId());
        assertNotNull(updatedA.getDsTaskCode(), "任务 A 应有 ds_task_code");
        System.out.println("任务 A ds_task_code=" + updatedA.getDsTaskCode());

        // 4. 增加任务 C，重新调度
        SqlTask taskC = createTask("task-C");
        SqlTaskDependency depBC = new SqlTaskDependency();
        depBC.setWorkflowId(workflow.getId());
        depBC.setTaskId(taskC.getId());
        depBC.setDependTaskId(taskB.getId());
        dependencyMapper.insert(depBC);

        Long oldProcessCode = workflow.getDsProcessCode();
        manager.rescheduleWorkflow(workflow);
        assertNotNull(workflow.getDsProcessCode(), "重新调度后仍有 processCode");
        System.out.println("Workflow 重新调度成功: processCode=" + workflow.getDsProcessCode());

        // 5. 再次手动触发
        Long instanceId2 = dsClient.startProcessInstance(workflow.getDsProcessCode());
        assertNotNull(instanceId2);
        System.out.println("重新调度后手动触发成功: instanceId=" + instanceId2);

        // 6. 清理
        dsClient.releaseProcess(workflow.getDsProcessCode(), "OFFLINE");
        dsClient.deleteProcess(workflow.getDsProcessCode());
        System.out.println("Workflow 已清理");
    }

    private SqlTask createTask(String name) {
        SqlTask task = new SqlTask();
        task.setTaskName(name);
        task.setSqlContent("SELECT 1");
        task.setWorkflowId(workflow.getId());
        sqlTaskMapper.insert(task);
        return task;
    }
}
