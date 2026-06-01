package com.mogu.data.quality.engine.parser;

import com.mogu.data.quality.engine.model.CheckTask;
import com.mogu.data.quality.engine.template.RuleTemplate;
import com.mogu.data.quality.engine.template.RuleTemplateFactory;
import com.mogu.data.quality.engine.template.model.ColumnMetadata;
import com.mogu.data.quality.engine.template.model.TableMetadata;
import com.mogu.data.quality.entity.QualityRule;
import com.mogu.data.quality.service.QualityRuleService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 规则解析器
 * 负责解析规则配置，生成检查任务
 *
 * @author fengzhu
 * @since 2026-05-10
 */
@Slf4j
@Component
public class RuleParser {

    @Autowired
    private QualityRuleService qualityRuleService;

    @Autowired
    private RuleTemplateFactory templateFactory;

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * 解析表的所有启用规则，生成检查任务列表
     *
     * @param tableId 表ID
     * @param checkType 检查类型
     * @param triggeredBy 触发者
     * @return 检查任务列表
     */
    public List<CheckTask> parseRules(Long tableId, String checkType, String triggeredBy) {
        log.info("开始解析表[{}]的质量规则", tableId);

        // 1. 查询表的所有启用规则
        List<QualityRule> rules = qualityRuleService.getEnabledRulesByTableId(tableId);

        if (rules == null || rules.isEmpty()) {
            log.warn("表[{}]没有启用的质量规则", tableId);
            return new ArrayList<>();
        }

        log.info("表[{}]共有{}条启用的质量规则", tableId, rules.size());

        // 2. 为每个规则生成检查任务
        List<CheckTask> tasks = new ArrayList<>();
        for (QualityRule rule : rules) {
            try {
                CheckTask task = parseRule(rule, checkType, triggeredBy);
                if (task != null) {
                    tasks.add(task);
                }
            } catch (Exception e) {
                log.error("解析规则[{}]失败", rule.getId(), e);
                // 继续解析下一个规则，不中断整个流程
            }
        }

        log.info("成功解析{}条规则，生成{}个检查任务", rules.size(), tasks.size());

        return tasks;
    }

    /**
     * 解析单个规则，生成检查任务
     *
     * @param rule 规则配置
     * @param checkType 检查类型
     * @param triggeredBy 触发者
     * @return 检查任务
     */
    private CheckTask parseRule(QualityRule rule, String checkType, String triggeredBy) {
        // 1. 获取规则模板
        String templateName = rule.getRuleTemplate();
        RuleTemplate template = templateFactory.getTemplate(templateName);

        // 2. 解析规则参数
        Map<String, Object> params = parseRuleParams(rule.getCheckParams());

        // 3. 构建表元数据
        TableMetadata table = TableMetadata.builder()
            .tableId(rule.getTableId())
            .tableName(rule.getTableName())
            .database(rule.getDatabase())
            .fullName(rule.getDatabase() + "." + rule.getTableName())
            .build();

        // 4. 构建字段元数据（字段级规则）
        ColumnMetadata column = null;
        if (rule.getColumnName() != null && !rule.getColumnName().isEmpty()) {
            column = ColumnMetadata.builder()
                .name(rule.getColumnName())
                .type("VARCHAR") // 默认类型
                .description("")
                .build();
        }

        // 5. 生成检查SQL
        String checkSql = template.generateSQL(table, column, params);

        // 6. 构建检查任务
        return CheckTask.builder()
            .ruleId(rule.getId())
            .ruleName(rule.getRuleName())
            .templateName(templateName)
            .table(table)
            .column(column)
            .params(params)
            .checkType(checkType)
            .triggeredBy(triggeredBy)
            .checkSql(checkSql)
            .priority(rule.getPriority())
            .build();
    }

    /**
     * 解析规则参数JSON
     *
     * @param ruleParamsJson 规则参数JSON字符串
     * @return 参数Map
     */
    private Map<String, Object> parseRuleParams(String ruleParamsJson) {
        if (ruleParamsJson == null || ruleParamsJson.isEmpty()) {
            return new java.util.HashMap<>();
        }

        try {
            return objectMapper.readValue(ruleParamsJson, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            log.error("解析规则参数JSON失败: {}", ruleParamsJson, e);
            return new java.util.HashMap<>();
        }
    }
}
