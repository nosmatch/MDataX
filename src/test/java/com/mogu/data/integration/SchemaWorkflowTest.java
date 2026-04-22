package com.mogu.data.integration;

import com.mogu.data.integration.entity.SqlTaskDependency;
import com.mogu.data.integration.entity.SqlTaskWorkflow;
import com.mogu.data.integration.mapper.SqlTaskDependencyMapper;
import com.mogu.data.integration.mapper.SqlTaskWorkflowMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SQL任务工作流 Schema 验证测试
 *
 * @author fengzhu
 */
@SpringBootTest
@Transactional
public class SchemaWorkflowTest {

    @Autowired
    private SqlTaskWorkflowMapper workflowMapper;

    @Autowired
    private SqlTaskDependencyMapper dependencyMapper;

    @Test
    public void testWorkflowCrud() {
        // 插入
        SqlTaskWorkflow wf = new SqlTaskWorkflow();
        wf.setWorkflowName("test-wf-crud");
        wf.setCronExpression("0 2 * * *");
        wf.setStatus(0);
        workflowMapper.insert(wf);
        assertNotNull(wf.getId());

        // 查询
        SqlTaskWorkflow found = workflowMapper.selectById(wf.getId());
        assertEquals("test-wf-crud", found.getWorkflowName());

        // 更新
        found.setStatus(1);
        workflowMapper.updateById(found);
        SqlTaskWorkflow updated = workflowMapper.selectById(wf.getId());
        assertEquals(1, updated.getStatus());

        // 删除
        workflowMapper.deleteById(wf.getId());
        assertNull(workflowMapper.selectById(wf.getId()));
    }

    @Test
    public void testDependencyMapper() {
        Long wfId = 999L;

        // 插入 A->B, A->C
        SqlTaskDependency dep1 = new SqlTaskDependency();
        dep1.setWorkflowId(wfId);
        dep1.setTaskId(20L);
        dep1.setDependTaskId(10L);
        dependencyMapper.insert(dep1);

        SqlTaskDependency dep2 = new SqlTaskDependency();
        dep2.setWorkflowId(wfId);
        dep2.setTaskId(30L);
        dep2.setDependTaskId(10L);
        dependencyMapper.insert(dep2);

        // selectDependTaskIds: B 的上游
        List<Long> upstreamOfB = dependencyMapper.selectDependTaskIds(20L);
        assertEquals(1, upstreamOfB.size());
        assertEquals(10L, upstreamOfB.get(0));

        // selectDownstreamTaskIds: A 的下游
        List<Long> downstreamOfA = dependencyMapper.selectDownstreamTaskIds(10L);
        assertEquals(2, downstreamOfA.size());

        // selectByWorkflowId
        List<SqlTaskDependency> all = dependencyMapper.selectByWorkflowId(wfId);
        assertEquals(2, all.size());

        // deleteByTaskId
        dependencyMapper.deleteByTaskId(20L);
        assertEquals(0, dependencyMapper.selectDependTaskIds(20L).size());

        // deleteByWorkflowId
        dependencyMapper.deleteByWorkflowId(wfId);
        assertEquals(0, dependencyMapper.selectByWorkflowId(wfId).size());
    }
}
