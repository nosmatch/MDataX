package com.mogu.data.schedulerx.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mogu.data.schedulerx.dto.InstanceQueryRequest;
import com.mogu.data.schedulerx.dto.InstanceResponse;
import com.mogu.data.schedulerx.dto.TaskInstanceResponse;
import com.mogu.data.schedulerx.engine.DagEngine;
import com.mogu.data.schedulerx.entity.DagDef;
import com.mogu.data.schedulerx.entity.DagInstance;
import com.mogu.data.schedulerx.entity.TaskInstance;
import com.mogu.data.schedulerx.service.DagDefService;
import com.mogu.data.schedulerx.service.DagInstanceService;
import com.mogu.data.schedulerx.service.TaskInstanceService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

/**
 * DAG 实例管理 Controller
 *
 * @author fengzhu
 */
@Slf4j
@Validated
@RestController
@RequestMapping("/api/v1/instances")
public class InstanceController {

    @Autowired
    private DagInstanceService dagInstanceService;
    @Autowired
    private TaskInstanceService taskInstanceService;
    @Autowired
    private DagDefService dagDefService;
    @Autowired
    private DagEngine dagEngine;
    @Autowired
    private ObjectMapper objectMapper;

    /**
     * 实例列表
     */
    @GetMapping
    public Object list(InstanceQueryRequest request) {
        LambdaQueryWrapper<DagInstance> wrapper = new LambdaQueryWrapper<>();

        if (request.getDagId() != null && !request.getDagId().trim().isEmpty()) {
            wrapper.eq(DagInstance::getDagId, request.getDagId());
        }
        if (request.getStatus() != null && !request.getStatus().trim().isEmpty()) {
            wrapper.eq(DagInstance::getStatus, request.getStatus());
        }
        if (request.getTriggerType() != null && !request.getTriggerType().trim().isEmpty()) {
            wrapper.eq(DagInstance::getTriggerType, request.getTriggerType());
        }
        if (request.getStartTime() != null && !request.getStartTime().trim().isEmpty()) {
            wrapper.ge(DagInstance::getCreateTime, request.getStartTime());
        }
        if (request.getEndTime() != null && !request.getEndTime().trim().isEmpty()) {
            wrapper.le(DagInstance::getCreateTime, request.getEndTime());
        }

        wrapper.orderByDesc(DagInstance::getCreateTime);

        Page<DagInstance> page = dagInstanceService.page(
                new Page<>(request.getPageNum(), request.getPageSize()), wrapper);

        List<InstanceResponse> list = page.getRecords().stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());

        java.util.Map<String, Object> result = new java.util.HashMap<>();
        result.put("records", list);
        result.put("total", page.getTotal());
        result.put("current", page.getCurrent());
        result.put("size", page.getSize());
        return result;
    }

    /**
     * 实例详情
     */
    @GetMapping("/{instanceId}")
    public Object detail(@PathVariable String instanceId) {
        DagInstance dagInstance = dagInstanceService.lambdaQuery()
                .eq(DagInstance::getInstanceId, instanceId)
                .one();
        if (dagInstance == null) {
            throw new IllegalArgumentException("实例不存在: " + instanceId);
        }
        return convertToResponse(dagInstance);
    }

    /**
     * 实例的任务列表
     */
    @GetMapping("/{instanceId}/tasks")
    public Object tasks(@PathVariable String instanceId) {
        DagInstance dagInstance = dagInstanceService.lambdaQuery()
                .eq(DagInstance::getInstanceId, instanceId)
                .one();
        if (dagInstance == null) {
            throw new IllegalArgumentException("实例不存在: " + instanceId);
        }

        List<TaskInstance> taskInstances = taskInstanceService.lambdaQuery()
                .eq(TaskInstance::getDagInstanceId, instanceId)
                .orderByAsc(TaskInstance::getCreateTime)
                .list();

        return taskInstances.stream()
                .map(this::convertToTaskResponse)
                .collect(Collectors.toList());
    }

    /**
     * 单个任务实例详情
     */
    @GetMapping("/{instanceId}/tasks/{taskInstanceId}")
    public Object taskDetail(@PathVariable String instanceId, @PathVariable String taskInstanceId) {
        DagInstance dagInstance = dagInstanceService.lambdaQuery()
                .eq(DagInstance::getInstanceId, instanceId)
                .one();
        if (dagInstance == null) {
            throw new IllegalArgumentException("实例不存在: " + instanceId);
        }

        TaskInstance task = taskInstanceService.lambdaQuery()
                .eq(TaskInstance::getTaskInstanceId, taskInstanceId)
                .eq(TaskInstance::getDagInstanceId, instanceId)
                .one();
        if (task == null) {
            throw new IllegalArgumentException("任务实例不存在: " + taskInstanceId);
        }

        return convertToTaskResponse(task);
    }

    /**
     * 杀除实例
     */
    @PostMapping("/{instanceId}/kill")
    public Object kill(@PathVariable String instanceId) {
        DagInstance dagInstance = dagInstanceService.lambdaQuery()
                .eq(DagInstance::getInstanceId, instanceId)
                .one();
        if (dagInstance == null) {
            throw new IllegalArgumentException("实例不存在: " + instanceId);
        }

        dagEngine.kill(instanceId);
        return java.util.Collections.singletonMap("success", true);
    }

    /**
     * 重试失败任务
     */
    @PostMapping("/{instanceId}/retry")
    public Object retry(@PathVariable String instanceId) {
        DagInstance dagInstance = dagInstanceService.lambdaQuery()
                .eq(DagInstance::getInstanceId, instanceId)
                .one();
        if (dagInstance == null) {
            throw new IllegalArgumentException("实例不存在: " + instanceId);
        }

        dagEngine.retryFailedTasks(instanceId);
        return java.util.Collections.singletonMap("success", true);
    }

    // ============ helper methods ============

    private InstanceResponse convertToResponse(DagInstance instance) {
        InstanceResponse response = new InstanceResponse();
        response.setInstanceId(instance.getInstanceId());
        response.setDagId(instance.getDagId());
        response.setStatus(instance.getStatus());
        response.setTriggerType(instance.getTriggerType());
        response.setTriggerTime(instance.getTriggerTime());
        response.setStartTime(instance.getStartTime());
        response.setEndTime(instance.getEndTime());
        response.setDurationMs(instance.getDurationMs());
        response.setFailureTaskId(instance.getFailureTaskId());
        response.setRetryCount(instance.getRetryCount());
        response.setCreateTime(instance.getCreateTime());

        // 查询 DAG 名称
        DagDef dagDef = dagDefService.lambdaQuery()
                .eq(DagDef::getDagId, instance.getDagId())
                .one();
        if (dagDef != null) {
            response.setDagName(dagDef.getDagName());
        }

        return response;
    }

    private TaskInstanceResponse convertToTaskResponse(TaskInstance task) {
        TaskInstanceResponse response = new TaskInstanceResponse();
        response.setTaskInstanceId(task.getTaskInstanceId());
        response.setDagInstanceId(task.getDagInstanceId());
        response.setDagId(task.getDagId());
        response.setTaskId(task.getTaskId());
        response.setTaskName(task.getTaskName());
        response.setStatus(task.getStatus());
        response.setAttemptNumber(task.getAttemptNumber());
        response.setStartTime(task.getStartTime());
        response.setEndTime(task.getEndTime());
        response.setDurationMs(task.getDurationMs());
        response.setResponseBody(task.getResponseBody());
        response.setOutput(task.getOutput());
        response.setErrorMsg(task.getErrorMsg());
        response.setCreateTime(task.getCreateTime());
        return response;
    }

}
