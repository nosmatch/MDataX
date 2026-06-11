package com.mogu.data.schedulerx.engine;

import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import com.mogu.data.schedulerx.entity.DagDef;
import com.mogu.data.schedulerx.entity.DagInstance;
import com.mogu.data.schedulerx.service.DagDefService;
import com.mogu.data.schedulerx.service.DagInstanceService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * TriggerScanner 单元测试
 *
 * @author fengzhu
 */
@ExtendWith(MockitoExtension.class)
class TriggerScannerTest {

    @InjectMocks
    private TriggerScanner triggerScanner;

    @Mock
    private DagDefService dagDefService;
    @Mock
    private DagInstanceService dagInstanceService;
    @Mock
    private DagEngine dagEngine;

    @Test
    @DisplayName("manualTrigger: 正常手动触发，创建 MANUAL 类型实例")
    void testManualTrigger_Normal() {
        String dagId = "dag_001";
        DagDef dagDef = new DagDef();
        dagDef.setDagId(dagId);
        dagDef.setDagName("Test DAG");
        dagDef.setStatus(1);

        when(dagDefService.lambdaQuery()).thenReturn(mockChain(dagDef));

        String instanceId = triggerScanner.manualTrigger(dagId);

        assertNotNull(instanceId);
        assertTrue(instanceId.startsWith("DI"));
        verify(dagInstanceService).save(argThat(i ->
                i.getDagId().equals(dagId) && i.getTriggerType().equals("MANUAL")));
        verify(dagEngine).start(instanceId);
    }

    @Test
    @DisplayName("manualTrigger: DAG 不存在时抛出异常")
    void testManualTrigger_DagNotFound() {
        String dagId = "dag_missing";
        when(dagDefService.lambdaQuery()).thenReturn(mockChain((DagDef) null));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> {
            triggerScanner.manualTrigger(dagId);
        });
        assertTrue(ex.getMessage().contains("DAG 不存在"));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private <T> LambdaQueryChainWrapper<T> mockChain(T result) {
        LambdaQueryChainWrapper[] ref = new LambdaQueryChainWrapper[1];
        ref[0] = mock(LambdaQueryChainWrapper.class, invocation -> {
            if ("one".equals(invocation.getMethod().getName())) {
                return result;
            }
            return ref[0];
        });
        return ref[0];
    }
}
