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
 * 数值范围检查模板
 * 检查数值型字段的值是否在指定范围内
 *
 * @author fengzhu
 * @since 2026-05-09
 */
public class NumericRangeCheckTemplate implements RuleTemplate {

    @Override
    public String getName() {
        return "NUMERIC_RANGE_CHECK";
    }

    @Override
    public String getType() {
        return RuleType.COLUMN.getCode();
    }

    @Override
    public String getDescription() {
        return "检查数值型字段的值是否在指定范围内";
    }

    @Override
    public List<ParamDefinition> getParamDefinitions() {
        return Arrays.asList(
            ParamDefinition.builder()
                .name("minValue")
                .type("decimal")
                .description("最小值")
                .required(false)
                .example("0")
                .build(),
            ParamDefinition.builder()
                .name("maxValue")
                .type("decimal")
                .description("最大值")
                .required(false)
                .example("100")
                .build(),
            ParamDefinition.builder()
                .name("maxOutOfRangeRatio")
                .type("decimal")
                .description("允许的超范围最大比例")
                .required(true)
                .defaultValue(0.0)
                .example("0.01")
                .build()
        );
    }

    @Override
    public String generateSQL(TableMetadata table, ColumnMetadata column, Map<String, Object> params) {
        Double minValue = params.get("minValue") != null ? ((Number) params.get("minValue")).doubleValue() : null;
        Double maxValue = params.get("maxValue") != null ? ((Number) params.get("maxValue")).doubleValue() : null;

        StringBuilder condition = new StringBuilder();
        List<String> conditions = new ArrayList<>();

        if (minValue != null) {
            conditions.add(String.format("CAST(%s AS DOUBLE) < %s", column.getName(), minValue));
        }
        if (maxValue != null) {
            conditions.add(String.format("CAST(%s AS DOUBLE) > %s", column.getName(), maxValue));
        }

        if (conditions.isEmpty()) {
            // 如果没有指定范围，则检查所有值
            return String.format(
                "SELECT COUNT(*) AS total_rows, 0 AS out_of_range_count FROM %s",
                table.getFullName()
            );
        }

        String combinedCondition = String.join(" OR ", conditions);

        return String.format(
            "SELECT " +
                "  COUNT(*) AS total_rows, " +
                "  SUM(CASE WHEN %s IS NULL THEN 1 ELSE 0 END) AS null_count, " +
                "  SUM(CASE WHEN %s THEN 1 ELSE 0 END) AS out_of_range_count " +
                "FROM %s",
            column.getName(),
            combinedCondition,
            table.getFullName()
        );
    }

    @Override
    public CheckResult analyzeResult(ResultSet rs, Map<String, Object> params) throws Exception {
        Double maxOutOfRangeRatio = (Double) params.get("maxOutOfRangeRatio");

        long totalRows = 0;
        long nullCount = 0;
        long outOfRangeCount = 0;

        if (rs.next()) {
            totalRows = rs.getLong("total_rows");
            nullCount = rs.getLong("null_count");
            outOfRangeCount = rs.getLong("out_of_range_count");
        }

        long validCount = totalRows - nullCount - outOfRangeCount;
        double outOfRangeRatio = totalRows == 0 ? 0.0 : (double) outOfRangeCount / totalRows;

        Double minValue = params.get("minValue") != null ? ((Number) params.get("minValue")).doubleValue() : null;
        Double maxValue = params.get("maxValue") != null ? ((Number) params.get("maxValue")).doubleValue() : null;

        String rangeStr;
        if (minValue != null && maxValue != null) {
            rangeStr = String.format("[%s, %s]", minValue, maxValue);
        } else if (minValue != null) {
            rangeStr = String.format("≥%s", minValue);
        } else if (maxValue != null) {
            rangeStr = String.format("≤%s", maxValue);
        } else {
            rangeStr = "任意值";
        }

        if (outOfRangeRatio <= maxOutOfRangeRatio) {
            return CheckResult.builder()
                    .status("PASS")
                    .actualValue(String.format("范围内: %,d/%,d (%.2f%%)", validCount, totalRows, (1 - outOfRangeRatio) * 100))
                    .expectedValue(String.format("值在范围%s内", rangeStr))
                    .build();
        } else {
            return CheckResult.builder()
                    .status("FAIL")
                    .actualValue(String.format("范围内: %,d/%,d (%.2f%%)", validCount, totalRows, (1 - outOfRangeRatio) * 100))
                    .expectedValue(String.format("值在范围%s内", rangeStr))
                    .errorMessage(String.format(
                        "字段[%s]有%d个值（%.2f%%）超出范围%s",
                        params.getOrDefault("columnName", "未知"),
                        outOfRangeCount,
                        outOfRangeRatio * 100,
                        rangeStr
                    ))
                    .build();
        }
    }
}
