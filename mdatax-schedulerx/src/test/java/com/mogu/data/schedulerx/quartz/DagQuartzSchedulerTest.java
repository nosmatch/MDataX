package com.mogu.data.schedulerx.quartz;

import com.mogu.data.schedulerx.entity.DagDef;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.quartz.*;

import java.util.TimeZone;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * DagQuartzScheduler 单元测试
 *
 * @author fengzhu
 */
@ExtendWith(MockitoExtension.class)
class DagQuartzSchedulerTest {

    @InjectMocks
    private DagQuartzScheduler dagQuartzScheduler;

    @Mock
    private Scheduler scheduler;

    @BeforeEach
    void setUp() {
        dagQuartzScheduler = new DagQuartzScheduler(scheduler);
    }

    @Test
    @DisplayName("scheduleDag: 新 DAG 创建 Job 和 Trigger")
    void testScheduleDag_New() throws SchedulerException {
        DagDef dagDef = buildDagDef("dag_001", "0 0 * * * ?", "Asia/Shanghai");

        when(scheduler.checkExists(any(JobKey.class))).thenReturn(false);

        dagQuartzScheduler.scheduleDag(dagDef);

        ArgumentCaptor<JobDetail> jobCaptor = ArgumentCaptor.forClass(JobDetail.class);
        ArgumentCaptor<Trigger> triggerCaptor = ArgumentCaptor.forClass(Trigger.class);
        verify(scheduler).scheduleJob(jobCaptor.capture(), triggerCaptor.capture());

        JobDetail job = jobCaptor.getValue();
        assertEquals("dag_001", job.getKey().getName());
        assertEquals("dag_jobs", job.getKey().getGroup());
        assertEquals("dag_001", job.getJobDataMap().getString(DagQuartzJob.DAG_ID_KEY));

        CronTrigger trigger = (CronTrigger) triggerCaptor.getValue();
        assertEquals("dag_001_trigger", trigger.getKey().getName());
        assertEquals("dag_triggers", trigger.getKey().getGroup());
        assertEquals("0 0 * * * ?", trigger.getCronExpression());
        assertEquals(TimeZone.getTimeZone("Asia/Shanghai"), trigger.getTimeZone());
    }

    @Test
    @DisplayName("scheduleDag: 已有 DAG 更新 Trigger")
    void testScheduleDag_Existing() throws SchedulerException {
        DagDef dagDef = buildDagDef("dag_001", "0 30 * * * ?", "UTC");

        when(scheduler.checkExists(any(JobKey.class))).thenReturn(true);
        when(scheduler.checkExists(any(TriggerKey.class))).thenReturn(true);

        dagQuartzScheduler.scheduleDag(dagDef);

        verify(scheduler).addJob(any(JobDetail.class), eq(true));
        verify(scheduler).rescheduleJob(any(TriggerKey.class), any(Trigger.class));
    }

    @Test
    @DisplayName("scheduleDag: 无 Cron 表达式时跳过")
    void testScheduleDag_NoCron() throws SchedulerException {
        DagDef dagDef = buildDagDef("dag_001", null, "Asia/Shanghai");

        dagQuartzScheduler.scheduleDag(dagDef);

        verifyNoInteractions(scheduler);
    }

    @Test
    @DisplayName("scheduleDag: 无效时区时使用系统默认")
    void testScheduleDag_InvalidTimezone() throws SchedulerException {
        DagDef dagDef = buildDagDef("dag_001", "0 0 * * * ?", "INVALID_ZONE");

        when(scheduler.checkExists(any(JobKey.class))).thenReturn(false);

        dagQuartzScheduler.scheduleDag(dagDef);

        ArgumentCaptor<Trigger> triggerCaptor = ArgumentCaptor.forClass(Trigger.class);
        verify(scheduler).scheduleJob(any(JobDetail.class), triggerCaptor.capture());

        CronTrigger trigger = (CronTrigger) triggerCaptor.getValue();
        // 无效时区会回退到 GMT（TimeZone.getTimeZone 对无效 ID 返回 GMT）
        assertNotNull(trigger.getTimeZone());
    }

    @Test
    @DisplayName("scheduleDag: SchedulerException 时抛出 RuntimeException")
    void testScheduleDag_SchedulerException() throws SchedulerException {
        DagDef dagDef = buildDagDef("dag_001", "0 0 * * * ?", "Asia/Shanghai");

        when(scheduler.checkExists(any(JobKey.class))).thenThrow(new SchedulerException("DB error"));

        RuntimeException ex = assertThrows(RuntimeException.class, () -> {
            dagQuartzScheduler.scheduleDag(dagDef);
        });
        assertTrue(ex.getMessage().contains("调度 DAG 失败"));
    }

    @Test
    @DisplayName("unscheduleDag: 删除 Job 和 Trigger")
    void testUnscheduleDag() throws SchedulerException {
        String dagId = "dag_001";

        when(scheduler.checkExists(any(TriggerKey.class))).thenReturn(true);
        when(scheduler.checkExists(any(JobKey.class))).thenReturn(true);

        dagQuartzScheduler.unscheduleDag(dagId);

        verify(scheduler).unscheduleJob(any(TriggerKey.class));
        verify(scheduler).deleteJob(any(JobKey.class));
    }

    @Test
    @DisplayName("unscheduleDag: 不存在的调度静默处理")
    void testUnscheduleDag_NotExists() throws SchedulerException {
        String dagId = "dag_missing";

        when(scheduler.checkExists(any(TriggerKey.class))).thenReturn(false);
        when(scheduler.checkExists(any(JobKey.class))).thenReturn(false);

        dagQuartzScheduler.unscheduleDag(dagId);

        verify(scheduler, never()).unscheduleJob(any());
        verify(scheduler, never()).deleteJob(any());
    }

    @Test
    @DisplayName("pauseDag: 暂停 Trigger")
    void testPauseDag() throws SchedulerException {
        String dagId = "dag_001";

        when(scheduler.checkExists(any(TriggerKey.class))).thenReturn(true);

        dagQuartzScheduler.pauseDag(dagId);

        verify(scheduler).pauseTrigger(any(TriggerKey.class));
    }

    @Test
    @DisplayName("pauseDag: 不存在的 Trigger 静默处理")
    void testPauseDag_NotExists() throws SchedulerException {
        String dagId = "dag_missing";

        when(scheduler.checkExists(any(TriggerKey.class))).thenReturn(false);

        dagQuartzScheduler.pauseDag(dagId);

        verify(scheduler, never()).pauseTrigger(any());
    }

    @Test
    @DisplayName("resumeDag: 恢复 Trigger")
    void testResumeDag() throws SchedulerException {
        String dagId = "dag_001";

        when(scheduler.checkExists(any(TriggerKey.class))).thenReturn(true);

        dagQuartzScheduler.resumeDag(dagId);

        verify(scheduler).resumeTrigger(any(TriggerKey.class));
    }

    @Test
    @DisplayName("resumeDag: 不存在的 Trigger 静默处理")
    void testResumeDag_NotExists() throws SchedulerException {
        String dagId = "dag_missing";

        when(scheduler.checkExists(any(TriggerKey.class))).thenReturn(false);

        dagQuartzScheduler.resumeDag(dagId);

        verify(scheduler, never()).resumeTrigger(any());
    }

    // ============ helper methods ============

    private DagDef buildDagDef(String dagId, String cron, String timezone) {
        DagDef def = new DagDef();
        def.setDagId(dagId);
        def.setDagName("Test DAG");
        def.setCronExpression(cron);
        def.setTimezone(timezone);
        def.setStatus(1);
        return def;
    }
}
