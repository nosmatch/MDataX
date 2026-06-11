package com.mogu.data.schedulerx.engine;

import com.mogu.data.schedulerx.enums.EventType;
import com.mogu.data.schedulerx.enums.FailureStrategy;
import com.mogu.data.schedulerx.enums.TaskInstanceStatus;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 状态机
 *
 * @author fengzhu
 */
@Component
public class StateMachine {

    /**
     * 判断任务状态转换是否合法
     */
    public boolean canTransition(TaskInstanceStatus from, EventType event) {
        switch (from) {
            case PENDING:
            case WAITING_UPSTREAM:
                return event == EventType.START;
            case RUNNING:
                return event == EventType.SUCCESS || event == EventType.FAILURE || event == EventType.TIMEOUT;
            default:
                return false;
        }
    }

    /**
     * 执行状态转换
     */
    public TaskInstanceStatus transition(TaskInstanceStatus from, EventType event) {
        if (!canTransition(from, event)) {
            throw new IllegalStateException("非法状态转换: " + from + " -> " + event);
        }
        switch (event) {
            case START:
                return TaskInstanceStatus.RUNNING;
            case SUCCESS:
                return TaskInstanceStatus.SUCCESS;
            case FAILURE:
                return TaskInstanceStatus.FAILURE;
            case TIMEOUT:
                return TaskInstanceStatus.TIMEOUT;
            default:
                return from;
        }
    }

    /**
     * 聚合 DAG 实例状态
     *
     * @param taskStatuses 所有任务实例的状态
     * @param failureStrategy 失败策略
     * @return 聚合后的 DAG 状态
     */
    public String aggregateDagStatus(Map<String, String> taskStatuses, FailureStrategy failureStrategy) {
        if (taskStatuses.isEmpty()) {
            return "RUNNING";
        }

        boolean hasRunning = false;
        boolean hasFailure = false;
        boolean hasTimeout = false;
        boolean allSuccess = true;

        for (String status : taskStatuses.values()) {
            switch (status) {
                case "RUNNING":
                case "PENDING":
                case "WAITING_UPSTREAM":
                    hasRunning = true;
                    allSuccess = false;
                    break;
                case "FAILURE":
                    hasFailure = true;
                    allSuccess = false;
                    break;
                case "TIMEOUT":
                    hasTimeout = true;
                    allSuccess = false;
                    break;
                case "SUCCESS":
                    break;
                case "SKIPPED":
                    break;
                default:
                    allSuccess = false;
                    break;
            }
        }

        if (allSuccess) {
            return "SUCCESS";
        }
        if (hasFailure) {
            return "FAILURE";
        }
        if (hasTimeout) {
            return "TIMEOUT";
        }
        if (hasRunning) {
            return "RUNNING";
        }
        return "RUNNING";
    }

}
