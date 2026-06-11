package com.mogu.data.schedulerx.engine;

import com.mogu.data.schedulerx.engine.model.Dag;
import com.mogu.data.schedulerx.engine.model.DagTask;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * DAG 拓扑解析器
 *
 * @author fengzhu
 */
@Component
public class TopologyResolver {

    /**
     * 基于当前各任务状态，解析出所有就绪（入度为 0）的任务
     *
     * @param dag          DAG 定义
     * @param taskStatusMap 各任务当前状态
     * @return 就绪任务列表
     */
    public List<DagTask> resolveReadyTasks(Dag dag, Map<String, String> taskStatusMap) {
        List<DagTask> readyTasks = new ArrayList<>();
        for (DagTask task : dag.getTasks()) {
            List<String> upstream = task.getUpstream();
            if (upstream == null || upstream.isEmpty()) {
                // 根节点，若还未执行则可就绪
                if (!taskStatusMap.containsKey(task.getTaskId())) {
                    readyTasks.add(task);
                }
                continue;
            }
            // 检查所有上游是否都已成功
            boolean allUpstreamSuccess = true;
            for (String upId : upstream) {
                String status = taskStatusMap.get(upId);
                if (!"SUCCESS".equals(status)) {
                    allUpstreamSuccess = false;
                    break;
                }
            }
            // 所有上游成功且当前任务还未执行
            if (allUpstreamSuccess && !taskStatusMap.containsKey(task.getTaskId())) {
                readyTasks.add(task);
            }
        }
        return readyTasks;
    }

    /**
     * 检测 DAG 中是否存在环（Kahn 算法）
     *
     * @param dag DAG 定义
     * @return true 表示存在环
     */
    public boolean hasCycle(Dag dag) {
        // 计算入度
        Map<String, Integer> inDegree = new HashMap<>();
        for (DagTask task : dag.getTasks()) {
            inDegree.putIfAbsent(task.getTaskId(), 0);
            List<String> upstream = task.getUpstream();
            if (upstream != null) {
                inDegree.put(task.getTaskId(), upstream.size());
            }
        }

        // 入度为 0 的节点入队
        Queue<String> queue = new LinkedList<>();
        for (Map.Entry<String, Integer> entry : inDegree.entrySet()) {
            if (entry.getValue() == 0) {
                queue.offer(entry.getKey());
            }
        }

        int visited = 0;
        while (!queue.isEmpty()) {
            String taskId = queue.poll();
            visited++;
            List<String> downstream = dag.getDownstream(taskId);
            if (downstream != null) {
                for (String downId : downstream) {
                    int degree = inDegree.getOrDefault(downId, 0) - 1;
                    inDegree.put(downId, degree);
                    if (degree == 0) {
                        queue.offer(downId);
                    }
                }
            }
        }

        // 如果访问的节点数不等于总节点数，说明有环
        return visited != dag.getTasks().size();
    }

}
