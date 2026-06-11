package com.mogu.data.schedulerx.engine;

import com.mogu.data.schedulerx.entity.DagDef;
import com.mogu.data.schedulerx.entity.DagInstance;
import com.mogu.data.schedulerx.enums.DagInstanceStatus;
import com.mogu.data.schedulerx.service.DagDefService;
import com.mogu.data.schedulerx.service.DagInstanceService;
import com.mogu.data.schedulerx.util.IdGenerator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * DAG 触发器（手动触发入口）
 *
 * <p>定时调度已由 Quartz 接管，此类仅保留手动触发能力。
 *
 * @author fengzhu
 */
@Slf4j
@Component
public class TriggerScanner {

    @Autowired
    private DagDefService dagDefService;
    @Autowired
    private DagInstanceService dagInstanceService;
    @Autowired
    private DagEngine dagEngine;

    /**
     * 手动触发（外部调用）
     */
    public String manualTrigger(String dagId) {
        DagDef dagDef = dagDefService.lambdaQuery()
                .eq(DagDef::getDagId, dagId)
                .one();
        if (dagDef == null) {
            throw new IllegalArgumentException("DAG 不存在: " + dagId);
        }

        String instanceId = IdGenerator.generateDagInstanceId();

        DagInstance dagInstance = new DagInstance();
        dagInstance.setInstanceId(instanceId);
        dagInstance.setDagId(dagId);
        dagInstance.setTriggerType("MANUAL");
        dagInstance.setTriggerTime(LocalDateTime.now());
        dagInstance.setStatus(DagInstanceStatus.PENDING.name());
        dagInstance.setCreateTime(LocalDateTime.now());
        dagInstance.setUpdateTime(LocalDateTime.now());

        dagInstanceService.save(dagInstance);

        log.info("[TriggerScanner] 手动触发 DAG: dagId={}, instanceId={}", dagId, instanceId);

        dagEngine.start(instanceId);
        return instanceId;
    }

}
