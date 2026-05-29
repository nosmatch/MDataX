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
 * 行数波动检查模板
 * 检查表行数相对历史基线的波动比例
 *
 * @author fengzhu
 * @since 2026-05-09
 */
public class RowCountFluctuationTemplate implements RuleTemplate {

    @Override
    public String getName() {
        return "ROW_COUNT_FLUCTUATION";
    }

    @Override
    public String getType() {
        return RuleType.TABLE.getCode();
    }

    @Override
    public String getDescription() {
        return "检查表行数相对历史基线的波动比例";
    }

    @Override
    public List<ParamDefinition> getParamDefinitions() {
        return Arrays.asList(
            ParamDefinition.builder()
                .name("baselineRows")
                .type("long")
                .description("基线行数（历史正常值）")
                .required(true)
                .example("100000")
                .build(),
            ParamDefinition.builder()
                .name("maxFluctuationRatio")
                .type("decimal")
                .description("最大波动比例（0-1之间，如0.2表示20%）")
                .required(true)
                .defaultValue(0.2)
                .example("0.2")
                .build()
        );
    }

    @Override
    public String generateSQL(TableMetadata table, ColumnMetadata column, Map<String, Object> params) {
        return String.format("SELECT COUNT(*) AS row_count FROM %s", table.getFullName());
    }

    @Override
    public CheckResult analyzeResult(ResultSet rs, Map<String, Object> params) throws Exception {
        Long baselineRows = ((Number) params.get("baselineRows")).longValue();
        Double maxFluctuationRatio = (Double) params.get("maxFluctuationRatio");

        long currentRows = 0;
        if (rs.next()) {
            currentRows = rs.getLong("row_count");
        }

        if (baselineRows == 0) {
            return CheckResult.builder()
                    .status("WARN")
                    .actualValue(String.format("%,d", currentRows))
                    .expectedValue("基线值 > 0")
                    .errorMessage("基线行数为0，无法计算波动比例")
                    .build();
        }

        double fluctuationRatio = Math.abs((double)(currentRows - baselineRows) / baselineRows);

        if (fluctuationRatio <= maxFluctuationRatio) {
            return CheckResult.builder()
                    .status("PASS")
                    .actualValue(String.format("%,d (%.2f%%)", currentRows, fluctuationRatio * 100))
                    .expectedValue(String.format("基线: %,d, 波动≤%.2f%%", baselineRows, maxFluctuationRatio * 100))
                    .build();
        } else {
            return CheckResult.builder()
                    .status("FAIL")
                    .actualValue(String.format("%,d (%.2f%%)", currentRows, fluctuationRatio * 100))
                    .expectedValue(String.format("基线: %,d, 波动≤%.2f%%", baselineRows, maxFluctuationRatio * 100))
                    .errorMessage(String.format(
                        "表[%s]的行数为%d，相对基线%d的波动比例为%.2f%%，超过了阈值%.2f%%",
                        params.getOrDefault("tableName", "未知"),
                        currentRows,
                        baselineRows,
                        fluctuationRatio * 100,
                        maxFluctuationRatio * 100
                    ))
                    .build();
        }
    }
}
