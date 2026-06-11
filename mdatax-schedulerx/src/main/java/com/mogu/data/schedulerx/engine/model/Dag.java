package com.mogu.data.schedulerx.engine.model;

import com.mogu.data.schedulerx.enums.FailureStrategy;
import lombok.Data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * DAG 内存模型
 *
 * @author fengzhu
 */
@Data
public class Dag {

    private String dagId;

    private String dagName;

    private String cronExpression;

    private FailureStrategy failureStrategy;

    private Integer timeoutSeconds;

    private String timezone;

    private Integer retryIntervalSeconds;

    private List<DagTask> tasks = new ArrayList<>();

    private Map<String, DagTask> taskMap = new HashMap<>();

    /**
     * taskId -> downstream taskIds
     */
    private Map<String, List<String>> downstream = new HashMap<>();

    /**
     * 构建下游依赖关系图
     */
    public void buildDownstream() {
        downstream.clear();
        for (DagTask task : tasks) {
            downstream.putIfAbsent(task.getTaskId(), new ArrayList<>());
            if (task.getUpstream() != null) {
                for (String upstreamId : task.getUpstream()) {
                    downstream.putIfAbsent(upstreamId, new ArrayList<>());
                    downstream.get(upstreamId).add(task.getTaskId());
                }
            }
        }
    }

    /**
     * 获取入度为 0 的任务（无上游依赖）
     */
    public List<DagTask> getRootTasks() {
        List<DagTask> roots = new ArrayList<>();
        for (DagTask task : tasks) {
            if (task.getUpstream() == null || task.getUpstream().isEmpty()) {
                roots.add(task);
            }
        }
        return roots;
    }

    /**
     * 根据 taskId 获取 DagTask
     */
    public DagTask getTask(String taskId) {
        return taskMap.get(taskId);
    }

    /**
     * 获取指定任务的所有下游任务
     */
    public List<String> getDownstream(String taskId) {
        return downstream.getOrDefault(taskId, new ArrayList<>());
    }

}
