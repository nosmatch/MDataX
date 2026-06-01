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
 * 日期范围检查模板
 * 检查日期型字段的值是否在指定范围内
 *
 * @author fengzhu
 * @since 2026-05-09
 */
public class DateRangeCheckTemplate implements RuleTemplate {

    @Override
    public String getName() {
        return "DATE_RANGE_CHECK";
    }

    @Override
    public String getType() {
        return RuleType.COLUMN.getCode();
    }

    @Override
    public String getDescription() {
        return "检查日期型字段的值是否在指定范围内";
    }

    @Override
    public List<ParamDefinition> getParamDefinitions() {
        return Arrays.asList(
            ParamDefinition.builder()
                .name("minDate")
                .type("string")
                .description("最小日期（格式：yyyy-MM-dd）")
                .required(false)
                .example("2020-01-01")
                .build(),
            ParamDefinition.builder()
                .name("maxDate")
                .type("string")
                .description("最大日期（格式：yyyy-MM-dd）")
                .required(false)
                .example("2025-12-31")
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
        String minDate = (String) params.get("minDate");
        String maxDate = (String) params.get("maxDate");

        List<String> conditions = new ArrayList<>();

        if (minDate != null && !minDate.isEmpty()) {
            // ClickHouse日期比较
            conditions.add(String.format("toDate(%s) < toDate('%s')", column.getName(), minDate));
        }
        if (maxDate != null && !maxDate.isEmpty()) {
            conditions.add(String.format("toDate(%s) > toDate('%s')", column.getName(), maxDate));
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

        String minDate = (String) params.get("minDate");
        String maxDate = (String) params.get("maxDate");

        String rangeStr;
        if (minDate != null && !minDate.isEmpty() && maxDate != null && !maxDate.isEmpty()) {
            rangeStr = String.format("[%s, %s]", minDate, maxDate);
        } else if (minDate != null && !minDate.isEmpty()) {
            rangeStr = String.format("≥%s", minDate);
        } else if (maxDate != null && !maxDate.isEmpty()) {
            rangeStr = String.format("≤%s", maxDate);
        } else {
            rangeStr = "任意日期";
        }

        if (outOfRangeRatio <= maxOutOfRangeRatio) {
            return CheckResult.builder()
                    .status("PASS")
                    .actualValue(String.format("范围内: %,d/%,d (%.2f%%)", validCount, totalRows, (1 - outOfRangeRatio) * 100))
                    .expectedValue(String.format("日期在范围%s内", rangeStr))
                    .build();
        } else {
            return CheckResult.builder()
                    .status("FAIL")
                    .actualValue(String.format("范围内: %,d/%,d (%.2f%%)", validCount, totalRows, (1 - outOfRangeRatio) * 100))
                    .expectedValue(String.format("日期在范围%s内", rangeStr))
                    .errorMessage(String.format(
                        "字段[%s]有%d个值（%.2f%%）超出日期范围%s",
                        params.getOrDefault("columnName", "未知"),
                        outOfRangeCount,
                        outOfRangeRatio * 100,
                        rangeStr
                    ))
                    .build();
        }
    }
}
