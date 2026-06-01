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
 * 枚举值检查模板
 * 检查字段值是否在指定的枚举列表中
 *
 * @author fengzhu
 * @since 2026-05-09
 */
public class EnumCheckTemplate implements RuleTemplate {

    @Override
    public String getName() {
        return "ENUM_CHECK";
    }

    @Override
    public String getType() {
        return RuleType.COLUMN.getCode();
    }

    @Override
    public String getDescription() {
        return "检查字段值是否在指定的枚举列表中";
    }

    @Override
    public List<ParamDefinition> getParamDefinitions() {
        return Collections.singletonList(
            ParamDefinition.builder()
                .name("enumValues")
                .type("string")
                .description("允许的枚举值列表，逗号分隔")
                .required(true)
                .example("0,1,2")
                .build()
        );
    }

    @Override
    @SuppressWarnings("unchecked")
    public String generateSQL(TableMetadata table, ColumnMetadata column, Map<String, Object> params) {
        Object enumValuesObj = params.get("enumValues");
        String[] enumValues;

        if (enumValuesObj instanceof String) {
            enumValues = ((String) enumValuesObj).split(",");
        } else if (enumValuesObj instanceof List) {
            List<String> list = (List<String>) enumValuesObj;
            enumValues = list.toArray(new String[0]);
        } else {
            enumValues = new String[0];
        }

        StringBuilder inClause = new StringBuilder();
        for (int i = 0; i < enumValues.length; i++) {
            if (i > 0) {
                inClause.append(", ");
            }
            String value = enumValues[i].trim();
            // 简单判断是否为数字或字符串
            if (value.matches("-?\\d+")) {
                inClause.append(value);
            } else {
                inClause.append("'").append(value.replace("'", "''")).append("'");
            }
        }

        return String.format(
            "SELECT " +
                "  COUNT(*) AS total_rows, " +
                "  SUM(CASE WHEN %s IS NULL THEN 1 ELSE 0 END) AS null_count, " +
                "  SUM(CASE WHEN %s NOT IN (%s) THEN 1 ELSE 0 END) AS invalid_count " +
                "FROM %s",
            column.getName(),
            column.getName(),
            inClause.toString(),
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

        if (invalidCount == 0) {
            return CheckResult.builder()
                    .status("PASS")
                    .actualValue(String.format("有效: %,d, 无效: %,d", validCount, invalidCount))
                    .expectedValue("所有值在枚举列表中")
                    .build();
        } else {
            return CheckResult.builder()
                    .status("FAIL")
                    .actualValue(String.format("有效: %,d, 无效: %,d", validCount, invalidCount))
                    .expectedValue("所有值在枚举列表中")
                    .errorMessage(String.format(
                        "字段[%s]存在%d个无效值，不在允许的枚举列表中",
                        params.getOrDefault("columnName", "未知"),
                        invalidCount
                    ))
                    .build();
        }
    }
}
