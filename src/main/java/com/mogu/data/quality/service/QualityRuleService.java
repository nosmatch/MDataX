package com.mogu.data.quality.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mogu.data.quality.dto.RuleCreateRequest;
import com.mogu.data.quality.dto.RuleUpdateRequest;
import com.mogu.data.quality.engine.template.RuleTemplate;
import com.mogu.data.quality.engine.template.RuleTemplateFactory;
import com.mogu.data.quality.engine.template.model.ColumnMetadata;
import com.mogu.data.quality.engine.template.model.TableMetadata;
import com.mogu.data.quality.entity.QualityRule;
import com.mogu.data.quality.mapper.QualityRuleMapper;
import com.mogu.data.metadata.entity.MetadataColumn;
import com.mogu.data.metadata.entity.MetadataTable;
import com.mogu.data.metadata.mapper.MetadataColumnMapper;
import com.mogu.data.metadata.mapper.MetadataTableMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 质量规则服务
 *
 * @author fengzhu
 * @since 2026-05-10
 */
@Slf4j
@Service
public class QualityRuleService {

    @Autowired
    private QualityRuleMapper qualityRuleMapper;

    @Autowired
    private RuleTemplateFactory templateFactory;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private MetadataTableMapper metadataTableMapper;

    @Autowired
    private MetadataColumnMapper metadataColumnMapper;

    /**
     * 创建质量规则
     *
     * @param request 创建请求
     * @param createdBy 创建人
     * @return 规则ID
     */
    @Transactional(rollbackFor = Exception.class)
    public Long createRule(RuleCreateRequest request, String createdBy) {
        log.info("创建质量规则[tableId={}, ruleName={}, template={}]",
            request.getTableId(), request.getRuleName(), request.getRuleTemplate());

        // 1. 验证规则模板是否存在
        if (!templateFactory.hasTemplate(request.getRuleTemplate())) {
            throw new IllegalArgumentException("规则模板不存在: " + request.getRuleTemplate());
        }

        // 2. 构建规则实体
        QualityRule rule = new QualityRule();
        rule.setRuleName(request.getRuleName());
        rule.setRuleType(request.getRuleType());
        rule.setRuleTemplate(request.getRuleTemplate());
        rule.setTableId(request.getTableId());
        rule.setTableName(request.getTableName());
        rule.setDatabase(request.getDatabase());
        rule.setColumnName(request.getColumnName());

        // 3. 转换参数Map为JSON
        try {
            Map<String, Object> params = request.getRuleParams();
            log.info("创建规则 - 接收到的ruleParams: {}", params);
            log.info("创建规则 - ruleParams类型: {}", params != null ? params.getClass().getName() : "null");
            if (params != null && !params.isEmpty()) {
                String paramsJson = objectMapper.writeValueAsString(params);
                log.info("创建规则 - 序列化后的JSON: {}", paramsJson);
                rule.setCheckParams(paramsJson);
            } else {
                log.info("创建规则 - ruleParams为空，使用空对象");
                rule.setCheckParams("{}");
            }
        } catch (Exception e) {
            log.error("转换规则参数JSON失败", e);
            throw new IllegalArgumentException("规则参数格式错误: " + e.getMessage());
        }

        // 4. 设置其他属性
        rule.setPriority(request.getPriority() != null ? request.getPriority() : 2);
        rule.setWeight(request.getWeight() != null ? request.getWeight() : java.math.BigDecimal.ONE);
        rule.setEnabled(request.getEnabled() != null ? request.getEnabled() : true);
        rule.setCheckMode(request.getCheckModes() != null && !request.getCheckModes().isEmpty()
            ? String.join(",", request.getCheckModes())
            : "MANUAL");
        rule.setScheduleCron(request.getScheduleCron());
        rule.setAlertOnFailure(request.getAlertOnFailure() != null ? request.getAlertOnFailure() : true);
        rule.setDescription(request.getDescription());
        rule.setCreatedBy(createdBy);
        rule.setCreatedAt(LocalDateTime.now());
        rule.setUpdatedAt(LocalDateTime.now());

        // 5. 保存到数据库
        qualityRuleMapper.insert(rule);

        log.info("质量规则创建成功[id={}]", rule.getId());

        return rule.getId();
    }

    /**
     * 更新质量规则
     *
     * @param ruleId 规则ID
     * @param request 更新请求
     * @return 是否成功
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean updateRule(Long ruleId, RuleUpdateRequest request) {
        log.info("更新质量规则[ruleId={}]", ruleId);

        // 1. 查询原规则
        QualityRule rule = qualityRuleMapper.selectById(ruleId);
        if (rule == null) {
            throw new IllegalArgumentException("规则不存在: " + ruleId);
        }

        // 2. 验证规则模板
        if (request.getRuleTemplate() != null && !templateFactory.hasTemplate(request.getRuleTemplate())) {
            throw new IllegalArgumentException("规则模板不存在: " + request.getRuleTemplate());
        }

        // 3. 更新字段
        log.info("更新前 - database={}, tableName={}, columnName={}",
            rule.getDatabase(), rule.getTableName(), rule.getColumnName());
        log.info("请求数据 - request.database={}, request.tableName={}, request.columnName={}",
            request.getDatabase(), request.getTableName(), request.getColumnName());

        if (request.getRuleName() != null) {
            rule.setRuleName(request.getRuleName());
        }
        if (request.getRuleType() != null) {
            rule.setRuleType(request.getRuleType());
        }
        if (request.getRuleTemplate() != null) {
            rule.setRuleTemplate(request.getRuleTemplate());
        }
        if (request.getDatabase() != null) {
            rule.setDatabase(request.getDatabase());
            log.info("已设置 database={}", rule.getDatabase());
        }
        if (request.getTableName() != null) {
            rule.setTableName(request.getTableName());
            log.info("已设置 tableName={}", rule.getTableName());
        }
        if (request.getColumnName() != null) {
            rule.setColumnName(request.getColumnName());
            log.info("已设置 columnName={}", rule.getColumnName());
        }

        log.info("更新后 - database={}, tableName={}, columnName={}",
            rule.getDatabase(), rule.getTableName(), rule.getColumnName());
        if (request.getRuleParams() != null) {
            try {
                String paramsJson = objectMapper.writeValueAsString(request.getRuleParams());
                rule.setCheckParams(paramsJson);
            } catch (Exception e) {
                log.error("转换规则参数JSON失败", e);
                throw new IllegalArgumentException("规则参数格式错误");
            }
        }
        if (request.getPriority() != null) {
            rule.setPriority(request.getPriority());
        }
        if (request.getWeight() != null) {
            rule.setWeight(request.getWeight());
        }
        if (request.getEnabled() != null) {
            rule.setEnabled(request.getEnabled());
        }
        if (request.getCheckModes() != null && !request.getCheckModes().isEmpty()) {
            rule.setCheckMode(String.join(",", request.getCheckModes()));
        }
        if (request.getScheduleCron() != null) {
            rule.setScheduleCron(request.getScheduleCron());
        }
        if (request.getAlertOnFailure() != null) {
            rule.setAlertOnFailure(request.getAlertOnFailure());
        }
        if (request.getDescription() != null) {
            rule.setDescription(request.getDescription());
        }

        rule.setUpdatedAt(LocalDateTime.now());

        // 4. 保存更新
        int rows = qualityRuleMapper.updateById(rule);

        log.info("质量规则更新成功[ruleId={}, rows={}]", ruleId, rows);

        return rows > 0;
    }

    /**
     * 删除质量规则
     *
     * @param ruleId 规则ID
     * @return 是否成功
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteRule(Long ruleId) {
        log.info("删除质量规则[ruleId={}]", ruleId);

        int rows = qualityRuleMapper.deleteById(ruleId);

        log.info("质量规则删除成功[ruleId={}, rows={}]", ruleId, rows);

        return rows > 0;
    }

    /**
     * 获取规则详情
     *
     * @param ruleId 规则ID
     * @return 规则详情
     */
    public QualityRule getRule(Long ruleId) {
        log.info("获取规则详情[ruleId={}]", ruleId);

        QualityRule rule = qualityRuleMapper.selectById(ruleId);
        if (rule == null) {
            throw new IllegalArgumentException("规则不存在: " + ruleId);
        }

        log.info("规则数据[id={}, database={}, tableName={}, columnName={}]",
            rule.getId(), rule.getDatabase(), rule.getTableName(), rule.getColumnName());

        // 解析规则参数JSON到ruleParamsMap字段，供前端使用
        if (rule.getCheckParams() != null && !rule.getCheckParams().isEmpty()) {
            try {
                Map<String, Object> params = objectMapper.readValue(rule.getCheckParams(),
                    new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
                rule.setRuleParamsMap(params);
            } catch (Exception e) {
                log.error("解析规则参数失败[ruleId={}]", rule.getId(), e);
                rule.setRuleParamsMap(new java.util.HashMap<>());
            }
        } else {
            rule.setRuleParamsMap(new java.util.HashMap<>());
        }

        log.info("返回规则[id={}, database={}, tableName={}, columnName={}]",
            rule.getId(), rule.getDatabase(), rule.getTableName(), rule.getColumnName());

        return rule;
    }

    /**
     * 查询规则列表
     *
     * @param tableId 表ID（可选）
     * @param ruleName 规则名称（可选，模糊查询）
     * @param ruleType 规则类型（可选）
     * @param ruleTemplate 规则模板（可选）
     * @param enabled 是否启用（可选）
     * @param pageNum 页码
     * @param pageSize 页大小
     * @return 分页结果
     */
    public IPage<QualityRule> listRules(Long tableId, String ruleName, String ruleType,
                                       String ruleTemplate, Boolean enabled,
                                       Integer pageNum, Integer pageSize) {
        log.info("查询规则列表[tableId={}, ruleName={}, ruleType={}, template={}, enabled={}, page={}, size={}]",
            tableId, ruleName, ruleType, ruleTemplate, enabled, pageNum, pageSize);

        // 构建查询条件
        LambdaQueryWrapper<QualityRule> wrapper = new LambdaQueryWrapper<>();

        if (tableId != null) {
            wrapper.eq(QualityRule::getTableId, tableId);
        }
        if (ruleName != null && !ruleName.isEmpty()) {
            wrapper.like(QualityRule::getRuleName, ruleName);
        }
        if (ruleType != null && !ruleType.isEmpty()) {
            wrapper.eq(QualityRule::getRuleType, ruleType);
        }
        if (ruleTemplate != null && !ruleTemplate.isEmpty()) {
            wrapper.eq(QualityRule::getRuleTemplate, ruleTemplate);
        }
        if (enabled != null) {
            wrapper.eq(QualityRule::getEnabled, enabled);
        }

        wrapper.orderByDesc(QualityRule::getCreatedAt);

        // 分页查询
        Page<QualityRule> page = new Page<>(pageNum, pageSize);
        IPage<QualityRule> result = qualityRuleMapper.selectPage(page, wrapper);

        // 解析规则参数JSON到ruleParamsMap字段，供前端使用
        for (QualityRule rule : result.getRecords()) {
            log.info("=== 规则[ruleId={}] ===", rule.getId());
            log.info("  database={}, tableName={}, columnName={}",
                rule.getDatabase(), rule.getTableName(), rule.getColumnName());

            if (rule.getCheckParams() != null && !rule.getCheckParams().isEmpty()) {
                try {
                    Map<String, Object> params = objectMapper.readValue(rule.getCheckParams(),
                        new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
                    rule.setRuleParamsMap(params);
                } catch (Exception e) {
                    log.error("解析规则参数失败[ruleId={}]", rule.getId(), e);
                    rule.setRuleParamsMap(new java.util.HashMap<>());
                }
            } else {
                rule.setRuleParamsMap(new java.util.HashMap<>());
            }
        }

        log.info("查询到{}条规则，返回给前端", result.getRecords().size());

        return result;
    }

    /**
     * 启用规则
     *
     * @param ruleId 规则ID
     * @return 是否成功
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean enableRule(Long ruleId) {
        log.info("启用质量规则[ruleId={}]", ruleId);

        QualityRule rule = qualityRuleMapper.selectById(ruleId);
        if (rule == null) {
            throw new IllegalArgumentException("规则不存在: " + ruleId);
        }

        rule.setEnabled(true);
        rule.setUpdatedAt(LocalDateTime.now());

        int rows = qualityRuleMapper.updateById(rule);

        log.info("质量规则启用成功[ruleId={}]", ruleId);

        return rows > 0;
    }

    /**
     * 禁用规则
     *
     * @param ruleId 规则ID
     * @return 是否成功
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean disableRule(Long ruleId) {
        log.info("禁用质量规则[ruleId={}]", ruleId);

        QualityRule rule = qualityRuleMapper.selectById(ruleId);
        if (rule == null) {
            throw new IllegalArgumentException("规则不存在: " + ruleId);
        }

        rule.setEnabled(false);
        rule.setUpdatedAt(LocalDateTime.now());

        int rows = qualityRuleMapper.updateById(rule);

        log.info("质量规则禁用成功[ruleId={}]", ruleId);

        return rows > 0;
    }

    /**
     * 测试规则（生成检查SQL，不实际执行）
     *
     * @param ruleId 规则ID
     * @return 测试结果（包含生成的SQL）
     */
    public Map<String, Object> testRule(Long ruleId) {
        log.info("测试质量规则[ruleId={}]", ruleId);

        // 1. 查询规则
        QualityRule rule = getRule(ruleId);

        // 2. 解析参数
        Map<String, Object> params = new HashMap<>();
        if (rule.getCheckParams() != null) {
            try {
                params = objectMapper.readValue(rule.getCheckParams(),
                    new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
            } catch (Exception e) {
                log.error("解析规则参数失败[ruleId={}]", rule.getId(), e);
            }
        }

        // 3. 构建表元数据
        TableMetadata table = TableMetadata.builder()
            .tableId(rule.getTableId())
            .tableName(rule.getTableName())
            .database(rule.getDatabase())
            .fullName(rule.getDatabase() + "." + rule.getTableName())
            .build();

        // 4. 构建字段元数据（字段级规则）
        ColumnMetadata column = null;
        if (rule.getColumnName() != null && !rule.getColumnName().isEmpty()) {
            column = ColumnMetadata.builder()
                .name(rule.getColumnName())
                .type("VARCHAR") // 默认类型
                .description("")
                .build();
        }

        // 5. 生成检查SQL
        RuleTemplate template = templateFactory.getTemplate(rule.getRuleTemplate());
        String checkSql = template.generateSQL(table, column, params);

        // 6. 返回测试结果
        Map<String, Object> result = new HashMap<>();
        result.put("ruleId", ruleId);
        result.put("ruleName", rule.getRuleName());
        result.put("templateName", rule.getRuleTemplate());
        result.put("checkSql", checkSql);
        result.put("params", params);
        result.put("success", true);

        log.info("规则测试成功[ruleId={}]", ruleId);

        return result;
    }

    /**
     * 复制规则
     *
     * @param ruleId 原规则ID
     * @param newRuleName 新规则名称
     * @param createdBy 创建人
     * @return 新规则ID
     */
    @Transactional(rollbackFor = Exception.class)
    public Long copyRule(Long ruleId, String newRuleName, String createdBy) {
        log.info("复制质量规则[ruleId={}, newRuleName={}]", ruleId, newRuleName);

        // 1. 查询原规则
        QualityRule originalRule = getRule(ruleId);

        // 2. 创建新规则
        QualityRule newRule = new QualityRule();
        newRule.setRuleName(newRuleName);
        newRule.setRuleType(originalRule.getRuleType());
        newRule.setRuleTemplate(originalRule.getRuleTemplate());
        newRule.setTableId(originalRule.getTableId());
        newRule.setTableName(originalRule.getTableName());
        newRule.setDatabase(originalRule.getDatabase());
        newRule.setColumnName(originalRule.getColumnName());
        newRule.setCheckParams(originalRule.getCheckParams());
        newRule.setPriority(originalRule.getPriority());
        newRule.setWeight(originalRule.getWeight());
        newRule.setEnabled(false); // 复制的规则默认禁用
        newRule.setCheckMode(originalRule.getCheckMode());
        newRule.setScheduleCron(originalRule.getScheduleCron());
        newRule.setAlertOnFailure(originalRule.getAlertOnFailure());
        newRule.setDescription("复制自: " + originalRule.getRuleName());
        newRule.setCreatedBy(createdBy);
        newRule.setCreatedAt(LocalDateTime.now());
        newRule.setUpdatedAt(LocalDateTime.now());

        // 3. 保存新规则
        qualityRuleMapper.insert(newRule);

        log.info("质量规则复制成功[originalId={}, newId={}]", ruleId, newRule.getId());

        return newRule.getId();
    }

    /**
     * 获取表的所有启用规则
     *
     * @param tableId 表ID
     * @return 规则列表
     */
    public List<QualityRule> getEnabledRulesByTableId(Long tableId) {
        return qualityRuleMapper.selectList(
            new LambdaQueryWrapper<QualityRule>()
                .eq(QualityRule::getTableId, tableId)
                .eq(QualityRule::getEnabled, true)
                .orderByAsc(QualityRule::getPriority)
        );
    }

    /**
     * 获取所有数据库列表
     *
     * @return 数据库列表
     */
    public List<String> getAllDatabases() {
        log.info("获取所有数据库列表");

        // 查询所有不重复的数据库名
        List<MetadataTable> tables = metadataTableMapper.selectList(
            new LambdaQueryWrapper<MetadataTable>()
                .select(MetadataTable::getDatabaseName)
                .isNotNull(MetadataTable::getDatabaseName)
                .ne(MetadataTable::getDeleted, 1)
        );

        // 去重并排序
        List<String> databases = tables.stream()
            .map(MetadataTable::getDatabaseName)
            .distinct()
            .sorted()
            .collect(Collectors.toList());

        log.info("获取到{}个数据库", databases.size());

        return databases;
    }

    /**
     * 根据数据库名获取表列表
     *
     * @param database 数据库名
     * @return 表列表（包含表名和表ID）
     */
    public List<Map<String, String>> getTablesByDatabase(String database) {
        log.info("获取数据库[{}]的表列表", database);

        // 查询指定数据库下的所有表
        List<MetadataTable> tables = metadataTableMapper.selectList(
            new LambdaQueryWrapper<MetadataTable>()
                .eq(MetadataTable::getDatabaseName, database)
                .ne(MetadataTable::getDeleted, 1)
                .orderByAsc(MetadataTable::getTableName)
        );

        // 转换为前端需要的格式
        List<Map<String, String>> result = tables.stream()
            .map(table -> {
                Map<String, String> map = new HashMap<>();
                map.put("id", String.valueOf(table.getId()));
                map.put("name", table.getTableName());
                map.put("fullName", table.getDatabaseName() + "." + table.getTableName());
                return map;
            })
            .collect(Collectors.toList());

        log.info("数据库[{}]共有{}张表", database, result.size());

        return result;
    }

    /**
     * 根据表名获取字段列表
     *
     * @param database 数据库名
     * @param table 表名
     * @return 字段列表
     */
    public List<String> getColumnsByTable(String database, String table) {
        log.info("获取表[{}.{}]的字段列表", database, table);

        // 1. 查询表信息
        MetadataTable metadataTable = metadataTableMapper.selectOne(
            new LambdaQueryWrapper<MetadataTable>()
                .eq(MetadataTable::getDatabaseName, database)
                .eq(MetadataTable::getTableName, table)
                .ne(MetadataTable::getDeleted, 1)
        );

        if (metadataTable == null) {
            log.warn("表[{}.{}]不存在", database, table);
            return Collections.emptyList();
        }

        // 2. 查询字段信息
        List<MetadataColumn> columns = metadataColumnMapper.selectList(
            new LambdaQueryWrapper<MetadataColumn>()
                .eq(MetadataColumn::getTableId, metadataTable.getId())
                .orderByAsc(MetadataColumn::getOrdinalPosition)
        );

        // 3. 提取字段名
        List<String> columnNames = columns.stream()
            .map(MetadataColumn::getColumnName)
            .collect(Collectors.toList());

        log.info("表[{}.{}]共有{}个字段", database, table, columnNames.size());

        return columnNames;
    }
}
