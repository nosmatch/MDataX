package com.mogu.data.quality.engine.template.impl;

import com.mogu.data.quality.engine.template.RuleTemplate;
import com.mogu.data.quality.engine.template.model.CheckResult;
import com.mogu.data.quality.engine.template.model.ColumnMetadata;
import com.mogu.data.quality.engine.template.model.ParamDefinition;
import com.mogu.data.quality.engine.template.model.TableMetadata;
import com.mogu.data.quality.enums.RuleType;

import java.sql.ResultSet;
import java.util.*;

/**
 * 自定义业务规则模板
 * 通过自定义SQL表达式进行业务规则检查
 *
 * @author fengzhu
 * @since 2026-05-09
 */
public class BusinessRuleTemplate implements RuleTemplate {

    @Override
    public String getName() {
        return "BUSINESS_RULE";
    }

    @Override
    public String getType() {
        return RuleType.TABLE.getCode();
    }

    @Override
    public String getDescription() {
        return "通过自定义SQL表达式进行业务规则检查";
    }

    @Override
    public List<ParamDefinition> getParamDefinitions() {
        return Arrays.asList(
            ParamDefinition.builder()
                .name("whereCondition")
                .type("string")
                .description("WHERE条件表达式，描述违规数据（如：amount < 0 或 age < 18）")
                .required(true)
                .example("amount < 0")
                .build(),
            ParamDefinition.builder()
                .name("maxViolationRatio")
                .type("decimal")
                .description("允许的最大违规比例（0-1之间）")
                .required(true)
                .defaultValue(0.0)
                .example("0.0")
                .build()
        );
    }

    @Override
    public String generateSQL(TableMetadata table, ColumnMetadata column, Map<String, Object> params) {
        String whereCondition = (String) params.get("whereCondition");

        return String.format(
            "SELECT " +
                "  COUNT(*) AS total_rows, " +
                "  COUNT(*) AS violation_count " +
                "FROM %s " +
                "WHERE %s",
            table.getFullName(),
            whereCondition
        );
    }

    @Override
    public CheckResult analyzeResult(ResultSet rs, Map<String, Object> params) throws Exception {
        Double maxViolationRatio = (Double) params.get("maxViolationRatio");

        // 对于业务规则，我们需要先获取总数和违规数
        // 但由于SQL中已经包含了WHERE条件，我们需要重新查询总数
        // 这里简化处理，假设调用方会提供totalRows参数

        long totalRows = ((Number) params.getOrDefault("totalRows", 0L)).longValue();
        long violationCount = 0;

        if (rs.next()) {
            violationCount = rs.getLong("violation_count");
        }

        double violationRatio = totalRows == 0 ? 0.0 : (double) violationCount / totalRows;

        String ruleDescription = (String) params.getOrDefault("ruleDescription", "业务规则");

        if (violationRatio <= maxViolationRatio) {
            return CheckResult.builder()
                    .status("PASS")
                    .actualValue(String.format("违规: %,d/%,d (%.2f%%)", violationCount, totalRows, violationRatio * 100))
                    .expectedValue(String.format("违规比例≤%.2f%%", maxViolationRatio * 100))
                    .build();
        } else {
            return CheckResult.builder()
                    .status("FAIL")
                    .actualValue(String.format("违规: %,d/%,d (%.2f%%)", violationCount, totalRows, violationRatio * 100))
                    .expectedValue(String.format("违规比例≤%.2f%%", maxViolationRatio * 100))
                    .errorMessage(String.format(
                        "表[%s]违反业务规则[%s]，违规数据%d条（%.2f%%），超过了阈值%.2f%%",
                        params.getOrDefault("tableName", "未知"),
                        ruleDescription,
                        violationCount,
                        violationRatio * 100,
                        maxViolationRatio * 100
                    ))
                    .build();
        }
    }
}
