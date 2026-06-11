package com.mogu.data.schedulerx.quartz;

import com.mogu.data.schedulerx.engine.DagEngine;
import com.mogu.data.schedulerx.entity.DagDef;
import com.mogu.data.schedulerx.entity.DagInstance;
import com.mogu.data.schedulerx.enums.DagInstanceStatus;
import com.mogu.data.schedulerx.service.DagDefService;
import com.mogu.data.schedulerx.service.DagInstanceService;
import com.mogu.data.schedulerx.util.IdGenerator;
import lombok.extern.slf4j.Slf4j;
import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.quartz.QuartzJobBean;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;

/**
 * Quartz DAG 触发 Job
 *
 * @author fengzhu
 */
@Slf4j
public class DagQuartzJob extends QuartzJobBean {

    public static final String DAG_ID_KEY = "dagId";

    @Autowired
    private DagDefService dagDefService;
    @Autowired
    private DagInstanceService dagInstanceService;
    @Autowired
    private DagEngine dagEngine;

    @Override
    protected void executeInternal(JobExecutionContext context) {
        JobDataMap dataMap = context.getMergedJobDataMap();
        String dagId = dataMap.getString(DAG_ID_KEY);

        log.info("[DagQuartzJob] Quartz 触发 DAG: dagId={}", dagId);

        DagDef dagDef = dagDefService.lambdaQuery()
                .eq(DagDef::getDagId, dagId)
                .one();

        if (dagDef == null) {
            log.error("[DagQuartzJob] DAG 不存在: {}", dagId);
            return;
        }

        if (dagDef.getStatus() == null || dagDef.getStatus() != 1) {
            log.warn("[DagQuartzJob] DAG 已禁用，跳过触发: {}", dagId);
            return;
        }

        Date triggerTime = context.getScheduledFireTime();
        if (triggerTime == null) {
            triggerTime = new Date();
        }

        String instanceId = IdGenerator.generateDagInstanceId();

        DagInstance dagInstance = new DagInstance();
        dagInstance.setInstanceId(instanceId);
        dagInstance.setDagId(dagId);
        dagInstance.setTriggerType("SCHEDULED");
        dagInstance.setTriggerTime(LocalDateTime.ofInstant(triggerTime.toInstant(), ZoneId.systemDefault()));
        dagInstance.setStatus(DagInstanceStatus.PENDING.name());
        dagInstance.setCreateTime(LocalDateTime.now());
        dagInstance.setUpdateTime(LocalDateTime.now());

        dagInstanceService.save(dagInstance);

        log.info("[DagQuartzJob] 创建 DAG 实例: dagId={}, instanceId={}", dagId, instanceId);

        dagEngine.start(instanceId);
    }
}
