package com.mogu.data.integration.dolphinscheduler;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * DolphinScheduler DAG 客户端验证测试
 *
 * @author fengzhu
 */
@SpringBootTest
public class DolphinSchedulerClientDagTest {

    @Autowired
    private DolphinSchedulerClient dsClient;

    @Autowired
    private DolphinSchedulerProperties props;

    // ==================== autoLayout 算法测试 ====================

    @Test
    public void testAutoLayoutSimpleChain() throws Exception {
        Method method = DolphinSchedulerClient.class.getDeclaredMethod(
                "autoLayout", List.class, List.class);
        method.setAccessible(true);

        List<DagNode> nodes = Arrays.asList(
                new DagNode(1L, "A", "scriptA"),
                new DagNode(2L, "B", "scriptB"),
                new DagNode(3L, "C", "scriptC")
        );
        List<DagRelation> relations = Arrays.asList(
                new DagRelation(1L, 2L),
                new DagRelation(2L, 3L)
        );

        @SuppressWarnings("unchecked")
        Map<Long, int[]> positions = (Map<Long, int[]>) method.invoke(dsClient, nodes, relations);

        // A 在 level 0, B 在 level 1, C 在 level 2
        assertTrue(positions.get(1L)[0] < positions.get(2L)[0]);
        assertTrue(positions.get(2L)[0] < positions.get(3L)[0]);
        // 同层 y 不重叠（这里只有一条链，同层只有一个节点）
    }

    @Test
    public void testAutoLayoutFork() throws Exception {
        Method method = DolphinSchedulerClient.class.getDeclaredMethod(
                "autoLayout", List.class, List.class);
        method.setAccessible(true);

        List<DagNode> nodes = Arrays.asList(
                new DagNode(1L, "A", "scriptA"),
                new DagNode(2L, "B", "scriptB"),
                new DagNode(3L, "C", "scriptC")
        );
        List<DagRelation> relations = Arrays.asList(
                new DagRelation(1L, 2L),
                new DagRelation(1L, 3L)
        );

        @SuppressWarnings("unchecked")
        Map<Long, int[]> positions = (Map<Long, int[]>) method.invoke(dsClient, nodes, relations);

        // A 在 level 0, B 和 C 在 level 1
        assertTrue(positions.get(1L)[0] < positions.get(2L)[0]);
        assertTrue(positions.get(1L)[0] < positions.get(3L)[0]);
        // B 和 C y 坐标不同（不重叠）
        assertNotEquals(positions.get(2L)[1], positions.get(3L)[1]);
    }

    // ==================== DS 集成测试 ====================

    @Test
    public void testCreateAndDeleteDagProcess() {
        if (!props.isEnabled()) {
            System.out.println("DS 未启用，跳过集成测试");
            return;
        }

        long taskCodeA = dsClient.generateTaskCode();
        long taskCodeB = dsClient.generateTaskCode();
        long taskCodeC = dsClient.generateTaskCode();

        List<DagNode> nodes = Arrays.asList(
                new DagNode(taskCodeA, "dag-A", buildScript("SQL", 1L)),
                new DagNode(taskCodeB, "dag-B", buildScript("SQL", 2L)),
                new DagNode(taskCodeC, "dag-C", buildScript("SQL", 3L))
        );
        List<DagRelation> relations = Arrays.asList(
                new DagRelation(taskCodeA, taskCodeB),
                new DagRelation(taskCodeB, taskCodeC)
        );

        // 1. 创建 DAG 工作流
        String processName = "mdatax-test-dag-" + System.currentTimeMillis();
        Long processCode = dsClient.createOrUpdateDagProcess(null, processName, nodes, relations);
        assertNotNull(processCode, "应返回 processCode");
        System.out.println("创建 DAG 工作流成功: processCode=" + processCode);

        // 2. 上线
        dsClient.releaseProcess(processCode, "ONLINE");
        System.out.println("工作流已上线");

        // 3. 更新（增加一个节点 D）
        long taskCodeD = dsClient.generateTaskCode();
        List<DagNode> updatedNodes = Arrays.asList(
                new DagNode(taskCodeA, "dag-A", buildScript("SQL", 1L)),
                new DagNode(taskCodeB, "dag-B", buildScript("SQL", 2L)),
                new DagNode(taskCodeC, "dag-C", buildScript("SQL", 3L)),
                new DagNode(taskCodeD, "dag-D", buildScript("SQL", 4L))
        );
        List<DagRelation> updatedRelations = Arrays.asList(
                new DagRelation(taskCodeA, taskCodeB),
                new DagRelation(taskCodeB, taskCodeC),
                new DagRelation(taskCodeC, taskCodeD)
        );
        Long updatedCode = dsClient.createOrUpdateDagProcess(processCode, processName, updatedNodes, updatedRelations);
        assertEquals(processCode, updatedCode, "更新应返回相同 processCode");
        System.out.println("更新 DAG 工作流成功");

        // 4. 手动触发实例
        Long instanceId = dsClient.startProcessInstance(processCode);
        assertNotNull(instanceId, "应返回 instanceId");
        System.out.println("手动触发实例成功: instanceId=" + instanceId);

        // 5. 停止实例（DS 3.2 不支持停止运行中实例，可能抛异常，捕获即可）
        try {
            dsClient.stopProcessInstance(instanceId);
            System.out.println("实例已停止");
        } catch (Exception e) {
            System.out.println("停止实例可能不支持或实例已完成: " + e.getMessage());
        }

        // 6. 下线并删除
        dsClient.releaseProcess(processCode, "OFFLINE");
        dsClient.deleteProcess(processCode);
        System.out.println("工作流已清理");
    }

    private String buildScript(String taskType, Long taskId) {
        return String.format(
                "curl -s -X POST %s/api/internal/task/execute " +
                        "-H \"Content-Type: application/json\" " +
                        "-d \"{\\\"taskType\\\":\\\"%s\\\",\\\"taskId\\\":%d,\\\"secret\\\":\\\"%s\\\"}\" " +
                        "--max-time 7200",
                props.getCallbackUrl(), taskType, taskId, props.getCallbackSecret()
        );
    }
}
