package com.mogu.data.quality.engine.executor;

import com.mogu.data.quality.engine.model.CheckExecutionResult;
import com.mogu.data.quality.engine.model.CheckTask;
import com.mogu.data.quality.engine.template.RuleTemplate;
import com.mogu.data.quality.engine.template.RuleTemplateFactory;
import com.mogu.data.quality.engine.template.model.CheckResult;
import com.mogu.data.quality.config.QualityMonitorConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Date;

/**
 * SQL执行器
 * 负责执行质量检查SQL，返回检查结果
 *
 * @author fengzhu
 * @since 2026-05-10
 */
@Slf4j
@Component
public class SqlExecutor {

    @Autowired
    private JdbcTemplate clickHouseJdbcTemplate;

    @Autowired
    private RuleTemplateFactory templateFactory;

    @Autowired
    private QualityMonitorConfig config;

    /**
     * 执行单个检查任务
     *
     * @param task 检查任务
     * @return 检查执行结果
     */
    public CheckExecutionResult execute(CheckTask task) {
        long startTime = System.currentTimeMillis();

        log.info("开始执行检查任务[ruleId={}, ruleName={}]",
            task.getRuleId(), task.getRuleName());

        CheckExecutionResult result = CheckExecutionResult.builder()
            .ruleId(task.getRuleId())
            .ruleName(task.getRuleName())
            .task(task)
            .executedSql(task.getCheckSql())
            .startTime(new Date(startTime))
            .build();

        try {
            // 1. 执行SQL查询
            ResultSet rs = executeQuery(task.getCheckSql());

            // 2. 获取规则模板并分析结果
            RuleTemplate template = templateFactory.getTemplate(task.getTemplateName());
            CheckResult checkResult = template.analyzeResult(rs, task.getParams());

            // 3. 设置检查结果
            long endTime = System.currentTimeMillis();
            result.setCheckResult(checkResult);
            result.setEndTime(new Date(endTime));
            result.setDuration(endTime - startTime);
            result.setSuccess(true);

            log.info("检查任务执行成功[ruleId={}, status={}, duration={}ms]",
                task.getRuleId(), checkResult.getStatus(), result.getDuration());

        } catch (Exception e) {
            // 执行失败
            long endTime = System.currentTimeMillis();
            result.setEndTime(new Date(endTime));
            result.setDuration(endTime - startTime);
            result.setSuccess(false);
            result.setErrorMessage(e.getMessage());
            result.setErrorStack(getStackTrace(e));

            log.error("检查任务执行失败[ruleId={}]", task.getRuleId(), e);
        }

        return result;
    }

    /**
     * 执行SQL查询
     *
     * @param sql SQL语句
     * @return ResultSet
     * @throws Exception 执行异常
     */
    private ResultSet executeQuery(String sql) throws Exception {
        // 使用JdbcTemplate执行原生SQL查询
        return clickHouseJdbcTemplate.execute((Connection conn) -> {
            PreparedStatement stmt = conn.prepareStatement(sql);
            stmt.setQueryTimeout(config.getQueryTimeout());
            return stmt.executeQuery();
        });
    }

    /**
     * 获取异常堆栈信息
     *
     * @param e 异常
     * @return 堆栈信息字符串
     */
    private String getStackTrace(Exception e) {
        java.io.StringWriter sw = new java.io.StringWriter();
        java.io.PrintWriter pw = new java.io.PrintWriter(sw);
        e.printStackTrace(pw);
        return sw.toString();
    }
}
