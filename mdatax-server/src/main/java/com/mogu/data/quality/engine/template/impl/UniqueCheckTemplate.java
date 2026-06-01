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
 * 唯一性检查模板
 * 检查字段值的唯一性，是否有重复值
 *
 * @author fengzhu
 * @since 2026-05-09
 */
public class UniqueCheckTemplate implements RuleTemplate {

    @Override
    public String getName() {
        return "UNIQUE_CHECK";
    }

    @Override
    public String getType() {
        return RuleType.COLUMN.getCode();
    }

    @Override
    public String getDescription() {
        return "检查字段值是否唯一，是否存在重复值";
    }

    @Override
    public List<ParamDefinition> getParamDefinitions() {
        return Collections.singletonList(
            ParamDefinition.builder()
                .name("maxDuplicateRatio")
                .type("decimal")
                .description("允许的最大重复比例（0-1之间，如0.01表示1%）")
                .required(true)
                .defaultValue(0.01)
                .example("0.01")
                .build()
        );
    }

    @Override
    public String generateSQL(TableMetadata table, ColumnMetadata column, Map<String, Object> params) {
        return String.format(
            "SELECT " +
                "  COUNT(*) AS total_rows, " +
                "  COUNT(DISTINCT %s) AS unique_count, " +
                "  COUNT(*) - COUNT(DISTINCT %s) AS duplicate_count " +
                "FROM %s",
            column.getName(),
            column.getName(),
            table.getFullName()
        );
    }

    @Override
    public CheckResult analyzeResult(ResultSet rs, Map<String, Object> params) throws Exception {
        Double maxDuplicateRatio = (Double) params.get("maxDuplicateRatio");

        long totalRows = 0;
        long uniqueCount = 0;
        long duplicateCount = 0;

        if (rs.next()) {
            totalRows = rs.getLong("total_rows");
            uniqueCount = rs.getLong("unique_count");
            duplicateCount = rs.getLong("duplicate_count");
        }

        if (totalRows == 0) {
            return CheckResult.builder()
                    .status("PASS")
                    .actualValue("0行")
                    .expectedValue("唯一")
                    .build();
        }

        double duplicateRatio = (double) duplicateCount / totalRows;

        if (duplicateRatio <= maxDuplicateRatio) {
            return CheckResult.builder()
                    .status("PASS")
                    .actualValue(String.format("唯一值: %,d, 重复: %,d (%.2f%%)", uniqueCount, duplicateCount, duplicateRatio * 100))
                    .expectedValue(String.format("重复比例≤%.2f%%", maxDuplicateRatio * 100))
                    .build();
        } else {
            return CheckResult.builder()
                    .status("FAIL")
                    .actualValue(String.format("唯一值: %,d, 重复: %,d (%.2f%%)", uniqueCount, duplicateCount, duplicateRatio * 100))
                    .expectedValue(String.format("重复比例≤%.2f%%", maxDuplicateRatio * 100))
                    .errorMessage(String.format(
                        "字段[%s]存在重复值，重复比例为%.2f%%，超过了阈值%.2f%%",
                        params.getOrDefault("columnName", "未知"),
                        duplicateRatio * 100,
                        maxDuplicateRatio * 100
                    ))
                    .build();
        }
    }
}
