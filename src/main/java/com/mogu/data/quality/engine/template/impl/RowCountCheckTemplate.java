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
 * 行数检查模板
 * 检查表的行数是否在指定范围内
 *
 * @author fengzhu
 * @since 2026-05-09
 */
public class RowCountCheckTemplate implements RuleTemplate {

    @Override
    public String getName() {
        return "ROW_COUNT_CHECK";
    }

    @Override
    public String getType() {
        return RuleType.TABLE.getCode();
    }

    @Override
    public String getDescription() {
        return "检查表行数是否在指定范围内";
    }

    @Override
    public List<ParamDefinition> getParamDefinitions() {
        return Arrays.asList(
            ParamDefinition.builder()
                .name("minRows")
                .type("long")
                .description("最小行数")
                .required(false)
                .defaultValue(0L)
                .example("1000")
                .build(),
            ParamDefinition.builder()
                .name("maxRows")
                .type("long")
                .description("最大行数")
                .required(false)
                .defaultValue(Long.MAX_VALUE)
                .example("1000000")
                .build()
        );
    }

    @Override
    public String generateSQL(TableMetadata table, ColumnMetadata column, Map<String, Object> params) {
        return String.format("SELECT COUNT(*) AS row_count FROM %s", table.getFullName());
    }

    @Override
    public CheckResult analyzeResult(ResultSet rs, Map<String, Object> params) throws Exception {
        Long minRows = ((Number) params.getOrDefault("minRows", 0L)).longValue();
        Long maxRows = ((Number) params.getOrDefault("maxRows", Long.MAX_VALUE)).longValue();

        long rowCount = 0;
        if (rs.next()) {
            rowCount = rs.getLong("row_count");
        }

        if (rowCount >= minRows && rowCount <= maxRows) {
            return CheckResult.builder()
                    .status("PASS")
                    .actualValue(String.format("%,d", rowCount))
                    .expectedValue(String.format("%,d ~ %,d", minRows, maxRows))
                    .build();
        } else {
            return CheckResult.builder()
                    .status("FAIL")
                    .actualValue(String.format("%,d", rowCount))
                    .expectedValue(String.format("%,d ~ %,d", minRows, maxRows))
                    .errorMessage(String.format(
                        "表[%s]的行数为%d，不在期望范围[%d, %d]内",
                        params.getOrDefault("tableName", "未知"),
                        rowCount,
                        minRows,
                        maxRows
                    ))
                    .build();
        }
    }
}
