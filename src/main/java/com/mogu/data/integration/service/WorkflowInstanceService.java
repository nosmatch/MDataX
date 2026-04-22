package com.mogu.data.integration.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mogu.data.integration.entity.WorkflowInstance;
import com.mogu.data.integration.mapper.WorkflowInstanceMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 工作流执行实例服务
 *
 * @author fengzhu
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WorkflowInstanceService extends ServiceImpl<WorkflowInstanceMapper, WorkflowInstance> {

    /**
     * 记录工作流实例启动（手动触发）
     */
    public WorkflowInstance recordManualStart(Long workflowId, Long dsInstanceId) {
        WorkflowInstance instance = new WorkflowInstance();
        instance.setWorkflowId(workflowId);
        instance.setDsInstanceId(dsInstanceId);
        instance.setStatus("RUNNING");
        instance.setStartTime(LocalDateTime.now());
        instance.setTriggerType("MANUAL");
        save(instance);
        log.info("工作流实例记录(手动): workflowId={}, dsInstanceId={}", workflowId, dsInstanceId);
        return instance;
    }

    /**
     * 记录或查找工作流实例（定时调度回调时）
     */
    public WorkflowInstance recordOrFindScheduled(Long workflowId, Long dsInstanceId) {
        WorkflowInstance exist = baseMapper.selectByDsInstanceId(dsInstanceId);
        if (exist != null) {
            return exist;
        }
        WorkflowInstance instance = new WorkflowInstance();
        instance.setWorkflowId(workflowId);
        instance.setDsInstanceId(dsInstanceId);
        instance.setStatus("RUNNING");
        instance.setStartTime(LocalDateTime.now());
        instance.setTriggerType("SCHEDULE");
        save(instance);
        log.info("工作流实例记录(定时): workflowId={}, dsInstanceId={}", workflowId, dsInstanceId);
        return instance;
    }

    /**
     * 完成工作流实例
     */
    public void finishInstance(Long dsInstanceId, String status, String errorMsg) {
        WorkflowInstance instance = baseMapper.selectByDsInstanceId(dsInstanceId);
        if (instance == null) {
            return;
        }
        instance.setStatus(status);
        instance.setEndTime(LocalDateTime.now());
        instance.setErrorMsg(errorMsg);
        updateById(instance);
        log.info("工作流实例完成: dsInstanceId={}, status={}", dsInstanceId, status);
    }

    /**
     * 查询工作流的最近一次执行
     */
    public WorkflowInstance getLatestByWorkflowId(Long workflowId) {
        return baseMapper.selectLatestByWorkflowId(workflowId);
    }

    /**
     * 查询工作流最新的 RUNNING 实例
     */
    public WorkflowInstance getLatestRunningByWorkflowId(Long workflowId) {
        return baseMapper.selectLatestRunningByWorkflowId(workflowId);
    }

    /**
     * 根据 DS 实例ID查询
     */
    public WorkflowInstance getByDsInstanceId(Long dsInstanceId) {
        return baseMapper.selectByDsInstanceId(dsInstanceId);
    }

    /**
     * 分页查询执行历史
     */
    public Page<WorkflowInstance> pageHistory(Long workflowId, long page, long size) {
        LambdaQueryWrapper<WorkflowInstance> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(WorkflowInstance::getWorkflowId, workflowId);
        wrapper.orderByDesc(WorkflowInstance::getCreateTime);
        return page(new Page<>(page, size), wrapper);
    }
}
