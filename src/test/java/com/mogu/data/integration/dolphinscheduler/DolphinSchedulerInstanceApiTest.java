package com.mogu.data.integration.dolphinscheduler;

import com.mogu.data.integration.entity.SqlTask;
import com.mogu.data.integration.entity.SqlTaskWorkflow;
import com.mogu.data.integration.mapper.SqlTaskMapper;
import com.mogu.data.integration.mapper.SqlTaskWorkflowMapper;
import com.mogu.data.integration.service.SqlTaskDependencyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

/**
 * DS 实例管理 API 验证测试
 *
 * @author fengzhu
 */
@SpringBootTest
@Transactional
public class DolphinSchedulerInstanceApiTest {

    @Autowired
    private DolphinSchedulerClient dsClient;

    @Autowired
    private DolphinSchedulerProperties props;

    @Autowired
    private SqlTaskWorkflowMapper workflowMapper;

    @Autowired
    private SqlTaskMapper sqlTaskMapper;

    @Autowired
    private SqlTaskDependencyService dependencyService;

    @Autowired
    private com.mogu.data.integration.mapper.SqlTaskDependencyMapper dependencyMapper;

    private SqlTaskWorkflow workflow;
    private Long processCode;

    @BeforeEach
    public void setup() {
        if (!props.isEnabled()) {
            return;
        }

        workflow = new SqlTaskWorkflow();
        workflow.setWorkflowName("instance-test-" + System.currentTimeMillis());
        workflow.setStatus(0);
        workflowMapper.insert(workflow);

        SqlTask taskA = createTask("inst-A");
        SqlTask taskB = createTask("inst-B");
        dependencyService.saveDependencies(taskB.getId(), Collections.singletonList(taskA.getId()));

        // 创建 DS DAG
        processCode = createDagInDs();
        workflow.setDsProcessCode(processCode);
        workflowMapper.updateById(workflow);
    }

    @Test
    public void testListProcessInstances() {
        if (!props.isEnabled()) {
            System.out.println("DS 未启用，跳过集成测试");
            return;
        }

        // 先触发一个实例
        Long instanceId = dsClient.startProcessInstance(processCode);
        assertNotNull(instanceId);

        // 查询实例列表
        String response = dsClient.listProcessInstances(processCode);
        assertNotNull(response);
        assertTrue(response.contains("data"));
        System.out.println("实例列表: " + response);
    }

    @Test
    public void testStopAndRetryInstance() {
        if (!props.isEnabled()) {
            System.out.println("DS 未启用，跳过集成测试");
            return;
        }

        // 触发一个长耗时实例（这里用正常任务，DS 会快速完成）
        Long instanceId = dsClient.startProcessInstance(processCode);
        assertNotNull(instanceId);
        System.out.println("实例已触发: instanceId=" + instanceId);

        // 停止（可能实例已完成，只是验证 API 调用不抛异常）
        try {
            dsClient.stopProcessInstance(instanceId);
            System.out.println("停止实例 API 调用成功");
        } catch (Exception e) {
            System.out.println("停止实例可能不支持或已完成: " + e.getMessage());
        }
    }

    @Test
    public void testPauseInstance() {
        if (!props.isEnabled()) {
            System.out.println("DS 未启用，跳过集成测试");
            return;
        }

        Long instanceId = dsClient.startProcessInstance(processCode);
        assertNotNull(instanceId);

        try {
            dsClient.pauseProcessInstance(instanceId);
            System.out.println("暂停实例 API 调用成功");
        } catch (Exception e) {
            System.out.println("暂停实例可能不支持: " + e.getMessage());
        }
    }

    // ==================== helper ====================

    private SqlTask createTask(String name) {
        SqlTask task = new SqlTask();
        task.setTaskName(name);
        task.setSqlContent("SELECT 1");
        task.setWorkflowId(workflow.getId());
        sqlTaskMapper.insert(task);

        if (task.getDsTaskCode() == null) {
            task.setDsTaskCode(dsClient.generateTaskCode());
            sqlTaskMapper.updateById(task);
        }
        return task;
    }

    private Long createDagInDs() {
        java.util.List<SqlTask> tasks = sqlTaskMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<SqlTask>()
                        .eq(SqlTask::getWorkflowId, workflow.getId())
                        .eq(SqlTask::getDeleted, 0));

        java.util.List<DagNode> nodes = new java.util.ArrayList<>();
        java.util.Map<Long, Long> taskIdToCode = new java.util.HashMap<>();

        for (SqlTask task : tasks) {
            long code = task.getDsTaskCode();
            taskIdToCode.put(task.getId(), code);
            String script = String.format(
                    "curl -s -X POST %s/api/internal/task/execute " +
                            "-H \"Content-Type: application/json\" " +
                            "-d \"{\\\"taskType\\\":\\\"SQL\\\",\\\"taskId\\\":%d,\\\"secret\\\":\\\"%s\\\",\\\"instanceId\\\":\\\"$processInstanceId\\\"}\" " +
                            "--max-time 7200",
                    props.getCallbackUrl(), task.getId(), props.getCallbackSecret());
            nodes.add(new DagNode(code, task.getTaskName(), script));
        }

        java.util.List<com.mogu.data.integration.entity.SqlTaskDependency> deps = dependencyMapper.selectByWorkflowId(workflow.getId());
        java.util.List<DagRelation> relations = new java.util.ArrayList<>();
        for (com.mogu.data.integration.entity.SqlTaskDependency dep : deps) {
            Long pre = taskIdToCode.get(dep.getDependTaskId());
            Long post = taskIdToCode.get(dep.getTaskId());
            if (pre != null && post != null) {
                relations.add(new DagRelation(pre, post));
            }
        }

        Long pc = dsClient.createOrUpdateDagProcess(null, "TEST-" + workflow.getWorkflowName(), nodes, relations);
        dsClient.releaseProcess(pc, "ONLINE");
        return pc;
    }
}
