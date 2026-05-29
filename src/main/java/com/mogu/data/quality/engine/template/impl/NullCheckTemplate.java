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
 * 空值检查模板
 * 检查字段的空值比例是否超过阈值
 *
 * @author fengzhu
 * @since 2026-05-09
 */
public class NullCheckTemplate implements RuleTemplate {

    @Override
    public String getName() {
        return "NULL_CHECK";
    }

    @Override
    public String getType() {
        return RuleType.COLUMN.getCode();
    }

    @Override
    public String getDescription() {
        return "检查字段空值比例是否超过阈值";
    }

    @Override
    public List<ParamDefinition> getParamDefinitions() {
        return Collections.singletonList(
            ParamDefinition.builder()
                .name("maxNullRatio")
                .type("decimal")
                .description("最大空值比例（0-1之间的小数，如0.05表示5%）")
                .required(true)
                .defaultValue(0.05)
                .example("0.05")
                .build()
        );
    }

    @Override
    public String generateSQL(TableMetadata table, ColumnMetadata column, Map<String, Object> params) {
        Double maxNullRatio = (Double) params.get("maxNullRatio");

        return String.format(
            "SELECT " +
                "  COUNT(*) AS total_rows, " +
                "  SUM(CASE WHEN %s IS NULL THEN 1 ELSE 0 END) AS null_rows, " +
                "  CAST(SUM(CASE WHEN %s IS NULL THEN 1 ELSE 0 END) AS DOUBLE) / COUNT(*) AS null_ratio " +
                "FROM %s",
            column.getName(),
            column.getName(),
            table.getFullName()
        );
    }

    @Override
    public CheckResult analyzeResult(ResultSet rs, Map<String, Object> params) throws Exception {
        Double maxNullRatio = (Double) params.get("maxNullRatio");

        long totalRows = 0;
        long nullRows = 0;

        if (rs.next()) {
            totalRows = rs.getLong("total_rows");
            nullRows = rs.getLong("null_rows");
        }

        double actualNullRatio = totalRows == 0 ? 0.0 : (double) nullRows / totalRows;

        if (actualNullRatio <= maxNullRatio) {
            return CheckResult.builder()
                    .status("PASS")
                    .actualValue(String.format("%.2f%%", actualNullRatio * 100))
                    .expectedValue(String.format("≤ %.2f%%", maxNullRatio * 100))
                    .build();
        } else {
            return CheckResult.builder()
                    .status("FAIL")
                    .actualValue(String.format("%.2f%%", actualNullRatio * 100))
                    .expectedValue(String.format("≤ %.2f%%", maxNullRatio * 100))
                    .errorMessage(String.format(
                        "字段[%s]的空值比例为%.2f%%，超过了阈值%.2f%%",
                        params.getOrDefault("columnName", "未知"),
                        actualNullRatio * 100,
                        maxNullRatio * 100
                    ))
                    .build();
        }
    }
}
