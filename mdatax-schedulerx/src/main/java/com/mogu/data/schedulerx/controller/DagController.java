package com.mogu.data.schedulerx.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mogu.data.schedulerx.dto.*;
import com.mogu.data.schedulerx.engine.TopologyResolver;
import com.mogu.data.schedulerx.engine.model.Dag;
import com.mogu.data.schedulerx.engine.model.DagTask;
import com.mogu.data.schedulerx.entity.DagDef;
import com.mogu.data.schedulerx.entity.DagInstance;
import com.mogu.data.schedulerx.entity.DagTaskEntity;
import com.mogu.data.schedulerx.enums.DagInstanceStatus;
import com.mogu.data.schedulerx.enums.FailureStrategy;
import com.mogu.data.schedulerx.service.DagDefService;
import com.mogu.data.schedulerx.service.DagInstanceService;
import com.mogu.data.schedulerx.service.DagTaskService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * DAG 管理 Controller
 *
 * @author fengzhu
 */
@Slf4j
@Validated
@RestController
@RequestMapping("/api/v1/dags")
public class DagController {

    @Autowired
    private DagDefService dagDefService;
    @Autowired
    private DagTaskService dagTaskService;
    @Autowired
    private DagInstanceService dagInstanceService;
    @Autowired
    private TopologyResolver topologyResolver;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private com.mogu.data.schedulerx.engine.TriggerScanner triggerScanner;
    @Autowired
    private com.mogu.data.schedulerx.quartz.DagQuartzScheduler dagQuartzScheduler;

    /**
     * 注册 DAG
     */
    @PostMapping
    public Object create(@Valid @RequestBody DagCreateRequest request) {
        // 检查 dagId 是否已存在
        DagDef existing = dagDefService.lambdaQuery()
                .eq(DagDef::getDagId, request.getDagId())
                .one();
        if (existing != null) {
            throw new IllegalArgumentException("DAG ID 已存在: " + request.getDagId());
        }

        // 校验 cron
        if (request.getCronExpression() != null && !request.getCronExpression().trim().isEmpty()) {
            try {
                new org.springframework.scheduling.support.CronSequenceGenerator(request.getCronExpression());
            } catch (Exception e) {
                throw new IllegalArgumentException("Cron 表达式不合法: " + request.getCronExpression());
            }
        }

        // 校验依赖关系
        validateTasks(request.getTasks(), request.getDagId());

        // 保存 DAG 定义
        DagDef dagDef = new DagDef();
        dagDef.setDagId(request.getDagId());
        dagDef.setDagName(request.getDagName());
        dagDef.setOwner(request.getOwner());
        dagDef.setOwnerRefId(request.getOwnerRefId());
        dagDef.setCronExpression(request.getCronExpression());
        dagDef.setTimezone(request.getTimezone());
        dagDef.setTimeoutSeconds(request.getTimeoutSeconds());
        dagDef.setFailureStrategy(request.getFailureStrategy());
        dagDef.setMaxRetryTimes(request.getMaxRetryTimes());
        dagDef.setRetryIntervalSeconds(request.getRetryIntervalSeconds());
        dagDef.setStatus(1);
        dagDef.setDescription(request.getDescription());
        dagDef.setCreateTime(LocalDateTime.now());
        dagDef.setUpdateTime(LocalDateTime.now());
        dagDefService.save(dagDef);

        // 保存任务节点
        saveTasks(request.getTasks(), request.getDagId());

        // 创建 Quartz 定时调度（如果有 cron 且已启用）
        dagQuartzScheduler.scheduleDag(dagDef);

        return java.util.Collections.singletonMap("dagId", request.getDagId());
    }

    /**
     * 更新 DAG
     */
    @PutMapping("/{dagId}")
    public Object update(@PathVariable String dagId, @Valid @RequestBody DagUpdateRequest request) {
        DagDef dagDef = dagDefService.lambdaQuery()
                .eq(DagDef::getDagId, dagId)
                .one();
        if (dagDef == null) {
            throw new IllegalArgumentException("DAG 不存在: " + dagId);
        }

        // 校验 cron
        if (request.getCronExpression() != null && !request.getCronExpression().trim().isEmpty()) {
            try {
                new org.springframework.scheduling.support.CronSequenceGenerator(request.getCronExpression());
            } catch (Exception e) {
                throw new IllegalArgumentException("Cron 表达式不合法: " + request.getCronExpression());
            }
        }

        // 校验依赖关系
        validateTasks(request.getTasks(), dagId);

        // 更新 DAG 定义
        dagDef.setDagName(request.getDagName());
        dagDef.setCronExpression(request.getCronExpression());
        dagDef.setTimezone(request.getTimezone());
        dagDef.setTimeoutSeconds(request.getTimeoutSeconds());
        dagDef.setFailureStrategy(request.getFailureStrategy());
        dagDef.setMaxRetryTimes(request.getMaxRetryTimes());
        dagDef.setRetryIntervalSeconds(request.getRetryIntervalSeconds());
        dagDef.setDescription(request.getDescription());
        dagDef.setUpdateTime(LocalDateTime.now());
        dagDefService.updateById(dagDef);

        // 删除旧的任务节点
        dagTaskService.lambdaUpdate()
                .eq(DagTaskEntity::getDagId, dagId)
                .remove();

        // 保存新的任务节点
        saveTasks(request.getTasks(), dagId);

        // 同步 Quartz 调度：先清除旧的，再根据新配置重建
        dagQuartzScheduler.unscheduleDag(dagId);
        if (request.getCronExpression() != null && !request.getCronExpression().trim().isEmpty()) {
            dagQuartzScheduler.scheduleDag(dagDef);
        }

        return java.util.Collections.singletonMap("dagId", dagId);
    }

    /**
     * 删除 DAG
     */
    @DeleteMapping("/{dagId}")
    public Object delete(@PathVariable String dagId) {
        DagDef dagDef = dagDefService.lambdaQuery()
                .eq(DagDef::getDagId, dagId)
                .one();
        if (dagDef == null) {
            throw new IllegalArgumentException("DAG 不存在: " + dagId);
        }

        // 检查是否有运行中的实例
        long runningCount = dagInstanceService.lambdaQuery()
                .eq(com.mogu.data.schedulerx.entity.DagInstance::getDagId, dagId)
                .eq(com.mogu.data.schedulerx.entity.DagInstance::getStatus, DagInstanceStatus.RUNNING.name())
                .count();
        if (runningCount > 0) {
            throw new IllegalStateException("DAG 有运行中的实例，不能删除");
        }

        dagTaskService.lambdaUpdate()
                .eq(DagTaskEntity::getDagId, dagId)
                .remove();
        dagDefService.removeById(dagDef.getId());

        // 删除 Quartz 调度
        dagQuartzScheduler.unscheduleDag(dagId);

        return java.util.Collections.singletonMap("success", true);
    }

    /**
     * DAG 列表
     */
    @GetMapping
    public Object list(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "20") Integer pageSize,
            @RequestParam(required = false) String keyword) {
        LambdaQueryWrapper<DagDef> wrapper = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.trim().isEmpty()) {
            wrapper.like(DagDef::getDagName, keyword)
                    .or()
                    .like(DagDef::getDagId, keyword);
        }
        wrapper.orderByDesc(DagDef::getCreateTime);

        Page<DagDef> page = dagDefService.page(new Page<>(pageNum, pageSize), wrapper);
        List<DagResponse> list = page.getRecords().stream()
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
     * DAG 详情
     */
    @GetMapping("/{dagId}")
    public Object detail(@PathVariable String dagId) {
        DagDef dagDef = dagDefService.lambdaQuery()
                .eq(DagDef::getDagId, dagId)
                .one();
        if (dagDef == null) {
            throw new IllegalArgumentException("DAG 不存在: " + dagId);
        }

        DagResponse response = convertToResponse(dagDef);

        // 查询任务节点
        List<DagTaskEntity> taskEntities = dagTaskService.lambdaQuery()
                .eq(DagTaskEntity::getDagId, dagId)
                .list();
        List<TaskNodeResponse> tasks = taskEntities.stream()
                .map(this::convertToTaskNodeResponse)
                .collect(Collectors.toList());
        response.setTasks(tasks);

        return response;
    }

    /**
     * 查询 DAG 的所有实例
     */
    @GetMapping("/{dagId}/instances")
    public Object dagInstances(
            @PathVariable String dagId,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "20") Integer pageSize) {
        DagDef dagDef = dagDefService.lambdaQuery()
                .eq(DagDef::getDagId, dagId)
                .one();
        if (dagDef == null) {
            throw new IllegalArgumentException("DAG 不存在: " + dagId);
        }

        LambdaQueryWrapper<DagInstance> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DagInstance::getDagId, dagId);
        wrapper.orderByDesc(DagInstance::getCreateTime);

        Page<DagInstance> page = dagInstanceService.page(new Page<>(pageNum, pageSize), wrapper);

        List<InstanceResponse> list = page.getRecords().stream()
                .map(this::convertToInstanceResponse)
                .collect(Collectors.toList());

        java.util.Map<String, Object> result = new java.util.HashMap<>();
        result.put("records", list);
        result.put("total", page.getTotal());
        result.put("current", page.getCurrent());
        result.put("size", page.getSize());
        return result;
    }

    /**
     * 手动触发
     */
    @PostMapping("/{dagId}/trigger")
    public Object trigger(@PathVariable String dagId) {
        DagDef dagDef = dagDefService.lambdaQuery()
                .eq(DagDef::getDagId, dagId)
                .one();
        if (dagDef == null) {
            throw new IllegalArgumentException("DAG 不存在: " + dagId);
        }
        if (dagDef.getStatus() == null || dagDef.getStatus() != 1) {
            throw new IllegalStateException("DAG 已禁用，不能触发");
        }

        // 手动触发
        String instanceId = triggerScanner.manualTrigger(dagId);

        return java.util.Collections.singletonMap("instanceId", instanceId);
    }

    /**
     * 禁用 DAG
     */
    @PostMapping("/{dagId}/disable")
    public Object disable(@PathVariable String dagId) {
        DagDef dagDef = dagDefService.lambdaQuery()
                .eq(DagDef::getDagId, dagId)
                .one();
        if (dagDef == null) {
            throw new IllegalArgumentException("DAG 不存在: " + dagId);
        }
        dagDef.setStatus(0);
        dagDef.setUpdateTime(LocalDateTime.now());
        dagDefService.updateById(dagDef);

        // 暂停 Quartz 调度
        dagQuartzScheduler.pauseDag(dagId);

        return java.util.Collections.singletonMap("success", true);
    }

    /**
     * 启用 DAG
     */
    @PostMapping("/{dagId}/enable")
    public Object enable(@PathVariable String dagId) {
        DagDef dagDef = dagDefService.lambdaQuery()
                .eq(DagDef::getDagId, dagId)
                .one();
        if (dagDef == null) {
            throw new IllegalArgumentException("DAG 不存在: " + dagId);
        }
        dagDef.setStatus(1);
        dagDef.setUpdateTime(LocalDateTime.now());
        dagDefService.updateById(dagDef);

        // 恢复或创建 Quartz 调度
        dagQuartzScheduler.scheduleDag(dagDef);

        return java.util.Collections.singletonMap("success", true);
    }

    // ============ helper methods ============

    private void validateTasks(List<TaskNodeRequest> tasks, String dagId) {
        if (tasks == null || tasks.isEmpty()) {
            throw new IllegalArgumentException("任务节点列表不能为空");
        }

        // 检查 taskId 唯一性
        java.util.Set<String> taskIds = new java.util.HashSet<>();
        for (TaskNodeRequest task : tasks) {
            if (!taskIds.add(task.getTaskId())) {
                throw new IllegalArgumentException("任务 ID 重复: " + task.getTaskId());
            }
        }

        // 构建内存 DAG 检查环
        Dag dag = new Dag();
        dag.setDagId(dagId);
        for (TaskNodeRequest taskReq : tasks) {
            DagTask task = new DagTask();
            task.setTaskId(taskReq.getTaskId());
            task.setUpstream(taskReq.getUpstream());
            dag.getTasks().add(task);
            dag.getTaskMap().put(task.getTaskId(), task);
        }
        dag.buildDownstream();

        if (topologyResolver.hasCycle(dag)) {
            throw new IllegalArgumentException("DAG 存在环，请检查依赖关系");
        }

        // 检查 upstream 引用的 taskId 是否存在
        for (TaskNodeRequest task : tasks) {
            if (task.getUpstream() != null) {
                for (String upId : task.getUpstream()) {
                    if (!taskIds.contains(upId)) {
                        throw new IllegalArgumentException("上游任务不存在: " + upId);
                    }
                }
            }
        }
    }

    private void saveTasks(List<TaskNodeRequest> tasks, String dagId) {
        for (TaskNodeRequest taskReq : tasks) {
            DagTaskEntity entity = new DagTaskEntity();
            entity.setDagId(dagId);
            entity.setTaskId(taskReq.getTaskId());
            entity.setTaskName(taskReq.getTaskName());
            entity.setTaskType(taskReq.getTaskType());
            entity.setRetryTimes(taskReq.getRetryTimes());
            entity.setTimeoutSeconds(taskReq.getTimeoutSeconds());
            entity.setPriority(taskReq.getPriority());

            try {
                if (taskReq.getTaskConfig() != null) {
                    entity.setTaskConfig(objectMapper.writeValueAsString(taskReq.getTaskConfig()));
                }
                if (taskReq.getUpstream() != null) {
                    entity.setUpstreamTasks(objectMapper.writeValueAsString(taskReq.getUpstream()));
                }
            } catch (Exception e) {
                throw new RuntimeException("序列化任务配置失败", e);
            }

            entity.setCreateTime(LocalDateTime.now());
            entity.setUpdateTime(LocalDateTime.now());
            dagTaskService.save(entity);
        }
    }

    private DagResponse convertToResponse(DagDef dagDef) {
        DagResponse response = new DagResponse();
        response.setDagId(dagDef.getDagId());
        response.setDagName(dagDef.getDagName());
        response.setOwner(dagDef.getOwner());
        response.setOwnerRefId(dagDef.getOwnerRefId());
        response.setCronExpression(dagDef.getCronExpression());
        response.setTimezone(dagDef.getTimezone());
        response.setTimeoutSeconds(dagDef.getTimeoutSeconds());
        response.setFailureStrategy(dagDef.getFailureStrategy());
        response.setMaxRetryTimes(dagDef.getMaxRetryTimes());
        response.setRetryIntervalSeconds(dagDef.getRetryIntervalSeconds());
        response.setStatus(dagDef.getStatus());
        response.setDescription(dagDef.getDescription());
        response.setCreateTime(dagDef.getCreateTime());
        response.setUpdateTime(dagDef.getUpdateTime());
        return response;
    }

    private TaskNodeResponse convertToTaskNodeResponse(DagTaskEntity entity) {
        TaskNodeResponse response = new TaskNodeResponse();
        response.setTaskId(entity.getTaskId());
        response.setTaskName(entity.getTaskName());
        response.setTaskType(entity.getTaskType());
        response.setRetryTimes(entity.getRetryTimes());
        response.setTimeoutSeconds(entity.getTimeoutSeconds());
        response.setPriority(entity.getPriority());

        try {
            if (entity.getTaskConfig() != null) {
                response.setTaskConfig(objectMapper.readValue(entity.getTaskConfig(), new TypeReference<java.util.Map<String, Object>>() {}));
            }
            if (entity.getUpstreamTasks() != null) {
                response.setUpstream(objectMapper.readValue(entity.getUpstreamTasks(), new TypeReference<java.util.List<String>>() {}));
            }
        } catch (Exception e) {
            log.error("解析任务配置失败: {}", entity.getTaskId(), e);
        }

        return response;
    }

    private InstanceResponse convertToInstanceResponse(DagInstance instance) {
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

        DagDef dagDef = dagDefService.lambdaQuery()
                .eq(DagDef::getDagId, instance.getDagId())
                .one();
        if (dagDef != null) {
            response.setDagName(dagDef.getDagName());
        }

        return response;
    }

}
