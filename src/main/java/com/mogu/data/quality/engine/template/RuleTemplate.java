package com.mogu.data.quality.engine.template;

import com.mogu.data.quality.engine.template.model.ParamDefinition;
import com.mogu.data.quality.engine.template.model.TableMetadata;
import com.mogu.data.quality.engine.template.model.ColumnMetadata;
import com.mogu.data.quality.engine.template.model.CheckResult;

import java.sql.ResultSet;
import java.util.List;
import java.util.Map;

/**
 * 规则模板接口
 * 所有规则模板都必须实现此接口
 *
 * @author fengzhu
 * @since 2026-05-09
 */
public interface RuleTemplate {

    /**
     * 获取模板名称
     *
     * @return 模板名称（如：NULL_CHECK）
     */
    String getName();

    /**
     * 获取模板类型
     *
     * @return 模板类型（TABLE/COLUMN）
     */
    String getType();

    /**
     * 获取模板描述
     *
     * @return 模板描述
     */
    String getDescription();

    /**
     * 获取参数定义
     *
     * @return 参数定义列表
     */
    List<ParamDefinition> getParamDefinitions();

    /**
     * 生成检查SQL
     *
     * @param table 表元数据
     * @param column 字段元数据（表级规则为null）
     * @param params 检查参数
     * @return 检查SQL
     */
    String generateSQL(TableMetadata table, ColumnMetadata column, Map<String, Object> params);

    /**
     * 分析检查结果
     *
     * @param rs 查询结果集
     * @param params 检查参数
     * @return 检查结果
     * @throws Exception 查询异常
     */
    CheckResult analyzeResult(ResultSet rs, Map<String, Object> params) throws Exception;

    /**
     * 验证参数
     *
     * @param params 检查参数
     * @throws IllegalArgumentException 参数不合法
     */
    default void validateParams(Map<String, Object> params) {
        List<ParamDefinition> paramDefinitions = getParamDefinitions();
        for (ParamDefinition paramDefinition : paramDefinitions) {
            String paramName = paramDefinition.getName();
            Object value = params.get(paramName);
            if (value == null && paramDefinition.isRequired()) {
                throw new IllegalArgumentException(
                    String.format("参数[%s]不能为空", paramName)
                );
            }
        }
    }
}
