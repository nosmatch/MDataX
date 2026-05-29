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
 * 正则表达式检查模板
 * 检查字段值是否匹配指定的正则表达式
 *
 * @author fengzhu
 * @since 2026-05-09
 */
public class RegexCheckTemplate implements RuleTemplate {

    @Override
    public String getName() {
        return "REGEX_CHECK";
    }

    @Override
    public String getType() {
        return RuleType.COLUMN.getCode();
    }

    @Override
    public String getDescription() {
        return "检查字段值是否匹配指定的正则表达式";
    }

    @Override
    public List<ParamDefinition> getParamDefinitions() {
        return Collections.singletonList(
            ParamDefinition.builder()
                .name("regexPattern")
                .type("string")
                .description("正则表达式模式")
                .required(true)
                .example("^1[3-9]\\d{9}$")
                .build()
        );
    }

    @Override
    public String generateSQL(TableMetadata table, ColumnMetadata column, Map<String, Object> params) {
        String regexPattern = (String) params.get("regexPattern");

        // 注意：ClickHouse的正则函数是match(expression, pattern)
        // MySQL使用REGEXP或RLIKE
        // 这里使用ClickHouse语法
        return String.format(
            "SELECT " +
                "  COUNT(*) AS total_rows, " +
                "  SUM(CASE WHEN %s IS NULL THEN 1 ELSE 0 END) AS null_count, " +
                "  SUM(CASE WHEN NOT match(toString(%s), '%s') THEN 1 ELSE 0 END) AS invalid_count " +
                "FROM %s",
            column.getName(),
            column.getName(),
            regexPattern.replace("'", "\\'"),
            table.getFullName()
        );
    }

    @Override
    public CheckResult analyzeResult(ResultSet rs, Map<String, Object> params) throws Exception {
        long totalRows = 0;
        long nullCount = 0;
        long invalidCount = 0;

        if (rs.next()) {
            totalRows = rs.getLong("total_rows");
            nullCount = rs.getLong("null_count");
            invalidCount = rs.getLong("invalid_count");
        }

        long validCount = totalRows - nullCount - invalidCount;
        double validRatio = totalRows == 0 ? 1.0 : (double) validCount / totalRows;

        if (invalidCount == 0) {
            return CheckResult.builder()
                    .status("PASS")
                    .actualValue(String.format("匹配: %,d/%,d (100%%)", validCount, totalRows))
                    .expectedValue("所有值匹配正则表达式")
                    .build();
        } else if (validRatio >= 0.95) {
            // 95%以上匹配，给出警告但通过
            return CheckResult.builder()
                    .status("WARN")
                    .actualValue(String.format("匹配: %,d/%,d (%.2f%%)", validCount, totalRows, validRatio * 100))
                    .expectedValue("所有值匹配正则表达式")
                    .errorMessage(String.format(
                        "字段[%s]有%d个值（%.2f%%）不匹配正则表达式",
                        params.getOrDefault("columnName", "未知"),
                        invalidCount,
                        (1 - validRatio) * 100
                    ))
                    .build();
        } else {
            return CheckResult.builder()
                    .status("FAIL")
                    .actualValue(String.format("匹配: %,d/%,d (%.2f%%)", validCount, totalRows, validRatio * 100))
                    .expectedValue("所有值匹配正则表达式")
                    .errorMessage(String.format(
                        "字段[%s]有%d个值（%.2f%%）不匹配正则表达式",
                        params.getOrDefault("columnName", "未知"),
                        invalidCount,
                        (1 - validRatio) * 100
                    ))
                    .build();
        }
    }
}
