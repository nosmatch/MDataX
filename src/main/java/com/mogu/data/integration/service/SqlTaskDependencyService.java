package com.mogu.data.integration.service;

import com.mogu.data.integration.entity.SqlTask;
import com.mogu.data.integration.entity.SqlTaskDependency;
import com.mogu.data.integration.mapper.SqlTaskDependencyMapper;
import com.mogu.data.integration.mapper.SqlTaskMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * SQL任务依赖关系服务
 *
 * @author fengzhu
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SqlTaskDependencyService {

    private final SqlTaskDependencyMapper dependencyMapper;
    private final SqlTaskMapper sqlTaskMapper;

    /**
     * 保存/更新任务的依赖关系
     *
     * @param taskId        下游任务ID
     * @param dependTaskIds 上游任务ID列表（必须在同一个Workflow内）
     */
    @Transactional
    public void saveDependencies(Long taskId, List<Long> dependTaskIds) {
        SqlTask task = sqlTaskMapper.selectById(taskId);
        if (task == null || task.getWorkflowId() == null) {
            throw new IllegalArgumentException("任务不存在或不属于任何工作流");
        }
        Long workflowId = task.getWorkflowId();

        // 1. 校验所有上游任务属于同一个 Workflow
        if (dependTaskIds != null && !dependTaskIds.isEmpty()) {
            for (Long dependId : dependTaskIds) {
                SqlTask dependTask = sqlTaskMapper.selectById(dependId);
                if (dependTask == null || !workflowId.equals(dependTask.getWorkflowId())) {
                    throw new IllegalArgumentException("上游任务不存在或不属于同一工作流: " + dependId);
                }
            }
            // 2. 循环依赖检测
            checkCircularDependency(taskId, dependTaskIds);
        }

        // 3. 先删后插
        dependencyMapper.deleteByTaskId(taskId);
        if (dependTaskIds != null && !dependTaskIds.isEmpty()) {
            for (Long dependId : dependTaskIds) {
                SqlTaskDependency dep = new SqlTaskDependency();
                dep.setWorkflowId(workflowId);
                dep.setTaskId(taskId);
                dep.setDependTaskId(dependId);
                dependencyMapper.insert(dep);
            }
        }
    }

    /**
     * 循环依赖检测（DFS）
     *
     * <p>即将给 taskId 添加依赖 newDependIds，检查是否会形成环。
     * 逻辑：构建包含现有依赖和新依赖的完整图，从 taskId 出发沿着上游方向 DFS，
     * 如果遇到一个已经在当前路径上的节点，说明存在环。
     */
    public void checkCircularDependency(Long taskId, List<Long> newDependIds) {
        if (newDependIds == null || newDependIds.isEmpty()) {
            return;
        }
        if (newDependIds.contains(taskId)) {
            throw new IllegalArgumentException("任务不能依赖自身");
        }

        SqlTask task = sqlTaskMapper.selectById(taskId);
        if (task == null || task.getWorkflowId() == null) {
            return;
        }

        // 构建内存依赖图：taskId -> 其上游依赖集合
        Map<Long, Set<Long>> graph = new HashMap<>();
        List<SqlTaskDependency> allDeps = dependencyMapper.selectByWorkflowId(task.getWorkflowId());
        for (SqlTaskDependency dep : allDeps) {
            graph.computeIfAbsent(dep.getTaskId(), k -> new HashSet<>()).add(dep.getDependTaskId());
        }
        // 叠加新依赖（此时还未入库）
        for (Long dependId : newDependIds) {
            graph.computeIfAbsent(taskId, k -> new HashSet<>()).add(dependId);
        }

        Set<Long> visited = new HashSet<>();
        Set<Long> path = new HashSet<>();
        if (hasCycleUpstream(taskId, graph, visited, path)) {
            throw new IllegalArgumentException("存在循环依赖");
        }
    }

    private boolean hasCycleUpstream(Long current, Map<Long, Set<Long>> graph,
                                     Set<Long> visited, Set<Long> path) {
        if (path.contains(current)) return true;
        if (visited.contains(current)) return false;

        visited.add(current);
        path.add(current);

        Set<Long> upstream = graph.getOrDefault(current, Collections.emptySet());
        for (Long next : upstream) {
            if (hasCycleUpstream(next, graph, visited, path)) {
                return true;
            }
        }
        path.remove(current);
        return false;
    }

    public List<Long> getDependTaskIds(Long taskId) {
        return dependencyMapper.selectDependTaskIds(taskId);
    }

    public List<Long> getDownstreamTaskIds(Long taskId) {
        return dependencyMapper.selectDownstreamTaskIds(taskId);
    }

    /**
     * 递归收集所有上游任务（包括间接上游）
     */
    public Set<Long> collectAllUpstream(Long taskId) {
        Set<Long> result = new HashSet<>();
        collectUpstreamRecursive(taskId, result);
        return result;
    }

    private void collectUpstreamRecursive(Long taskId, Set<Long> result) {
        List<Long> direct = dependencyMapper.selectDependTaskIds(taskId);
        for (Long upstream : direct) {
            if (result.add(upstream)) {
                collectUpstreamRecursive(upstream, result);
            }
        }
    }

    /**
     * 递归收集所有下游任务（包括间接下游）
     */
    public Set<Long> collectAllDownstream(Long taskId) {
        Set<Long> result = new HashSet<>();
        collectDownstreamRecursive(taskId, result);
        return result;
    }

    private void collectDownstreamRecursive(Long taskId, Set<Long> result) {
        List<Long> direct = dependencyMapper.selectDownstreamTaskIds(taskId);
        for (Long downstream : direct) {
            if (result.add(downstream)) {
                collectDownstreamRecursive(downstream, result);
            }
        }
    }

    @Transactional
    public void deleteByTaskId(Long taskId) {
        dependencyMapper.deleteByTaskId(taskId);
    }

    @Transactional
    public void deleteByWorkflowId(Long workflowId) {
        dependencyMapper.deleteByWorkflowId(workflowId);
    }
}
