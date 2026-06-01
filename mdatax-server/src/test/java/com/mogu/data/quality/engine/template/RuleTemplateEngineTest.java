package com.mogu.data.quality.engine.template;

import com.mogu.data.quality.engine.template.impl.*;
import com.mogu.data.quality.engine.template.model.*;
import com.mogu.data.quality.enums.RuleType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 规则模板引擎测试类
 * 验证所有规则模板的功能是否符合预期
 *
 * @author fengzhu
 * @since 2026-05-09
 */
@DisplayName("规则模板引擎测试")
public class RuleTemplateEngineTest {

    private RuleTemplateFactory factory;

    @BeforeEach
    public void setUp() {
        factory = new RuleTemplateFactory();
        factory.initialize();
    }

    @Test
    @DisplayName("1. 验证工厂初始化 - 所有9个模板应该被正确注册")
    public void testFactoryInitialization() {
        assertEquals(9, factory.getTemplateCount(),
            "应该注册9个规则模板");

        List<String> expectedTemplates = Arrays.asList(
            "NULL_CHECK",
            "ROW_COUNT_CHECK",
            "ROW_COUNT_FLUCTUATION",
            "UNIQUE_CHECK",
            "ENUM_CHECK",
            "REGEX_CHECK",
            "NUMERIC_RANGE_CHECK",
            "DATE_RANGE_CHECK",
            "BUSINESS_RULE"
        );

        for (String templateName : expectedTemplates) {
            assertTrue(factory.hasTemplate(templateName),
                "应该包含模板: " + templateName);
        }

        System.out.println("✅ 工厂初始化测试通过 - 所有9个模板已正确注册");
    }

    @Test
    @DisplayName("2. 验证按类型查询模板")
    public void testGetTemplatesByType() {
        // 获取表级规则
        List<RuleTemplate> tableTemplates = factory.getTemplatesByType(RuleType.TABLE.getCode());
        assertEquals(3, tableTemplates.size(),
            "应该有3个表级规则模板（ROW_COUNT_CHECK, ROW_COUNT_FLUCTUATION, BUSINESS_RULE）");

        // 获取字段级规则
        List<RuleTemplate> columnTemplates = factory.getTemplatesByType(RuleType.COLUMN.getCode());
        assertEquals(6, columnTemplates.size(),
            "应该有6个字段级规则模板");

        System.out.println("✅ 按类型查询测试通过");
        System.out.println("   - 表级规则: " + tableTemplates.size() + "个");
        System.out.println("   - 字段级规则: " + columnTemplates.size() + "个");
    }

    @Test
    @DisplayName("3. 验证空值检查模板 (NULL_CHECK)")
    public void testNullCheckTemplate() {
        RuleTemplate template = factory.getTemplate("NULL_CHECK");

        // 验证基本信息
        assertEquals("NULL_CHECK", template.getName());
        assertEquals(RuleType.COLUMN.getCode(), template.getType());
        assertEquals("检查字段空值比例是否超过阈值", template.getDescription());

        // 验证参数定义
        List<ParamDefinition> params = template.getParamDefinitions();
        assertEquals(1, params.size());
        assertEquals("maxNullRatio", params.get(0).getName());
        assertEquals(0.05, params.get(0).getDefaultValue());

        // 验证SQL生成
        TableMetadata table = TableMetadata.builder()
            .database("test_db")
            .tableName("users")
            .fullName("test_db.users")
            .build();

        ColumnMetadata column = ColumnMetadata.builder()
            .name("email")
            .type("String")
            .build();

        Map<String, Object> paramMap = new HashMap<>();
        paramMap.put("maxNullRatio", 0.05);

        String sql = template.generateSQL(table, column, paramMap);
        assertTrue(sql.contains("COUNT(*)"));
        assertTrue(sql.contains("SUM(CASE WHEN email IS NULL"));
        assertTrue(sql.contains("test_db.users"));

        System.out.println("✅ 空值检查模板测试通过");
        System.out.println("   生成的SQL: " + sql);
    }

    @Test
    @DisplayName("4. 验证行数检查模板 (ROW_COUNT_CHECK)")
    public void testRowCountCheckTemplate() {
        RuleTemplate template = factory.getTemplate("ROW_COUNT_CHECK");

        assertEquals(RuleType.TABLE.getCode(), template.getType());

        List<ParamDefinition> params = template.getParamDefinitions();
        assertEquals(2, params.size());

        // 验证SQL生成
        TableMetadata table = TableMetadata.builder()
            .fullName("db.orders")
            .build();

        Map<String, Object> paramMap = new HashMap<>();
        paramMap.put("minRows", 1000L);
        paramMap.put("maxRows", 100000L);

        String sql = template.generateSQL(table, null, paramMap);
        assertTrue(sql.contains("COUNT(*)"));
        assertTrue(sql.contains("db.orders"));

        System.out.println("✅ 行数检查模板测试通过");
        System.out.println("   生成的SQL: " + sql);
    }

    @Test
    @DisplayName("5. 验证唯一性检查模板 (UNIQUE_CHECK)")
    public void testUniqueCheckTemplate() {
        RuleTemplate template = factory.getTemplate("UNIQUE_CHECK");

        List<ParamDefinition> params = template.getParamDefinitions();
        assertEquals(1, params.size());
        assertEquals("maxDuplicateRatio", params.get(0).getName());

        // 验证SQL生成
        TableMetadata table = TableMetadata.builder()
            .fullName("db.users")
            .build();

        ColumnMetadata column = ColumnMetadata.builder()
            .name("user_id")
            .build();

        Map<String, Object> paramMap = new HashMap<>();
        paramMap.put("maxDuplicateRatio", 0.01);

        String sql = template.generateSQL(table, column, paramMap);
        assertTrue(sql.contains("COUNT(DISTINCT user_id)"));
        assertTrue(sql.contains("COUNT(*) - COUNT(DISTINCT"));

        System.out.println("✅ 唯一性检查模板测试通过");
        System.out.println("   生成的SQL: " + sql);
    }

    @Test
    @DisplayName("6. 验证枚举值检查模板 (ENUM_CHECK)")
    public void testEnumCheckTemplate() {
        RuleTemplate template = factory.getTemplate("ENUM_CHECK");

        TableMetadata table = TableMetadata.builder()
            .fullName("db.users")
            .build();

        ColumnMetadata column = ColumnMetadata.builder()
            .name("status")
            .build();

        Map<String, Object> paramMap = new HashMap<>();
        paramMap.put("enumValues", "0,1,2");

        String sql = template.generateSQL(table, column, paramMap);
        assertTrue(sql.contains("NOT IN"));
        assertTrue(sql.contains("0, 1, 2"));

        System.out.println("✅ 枚举值检查模板测试通过");
        System.out.println("   生成的SQL: " + sql);
    }

    @Test
    @DisplayName("7. 验证正则表达式检查模板 (REGEX_CHECK)")
    public void testRegexCheckTemplate() {
        RuleTemplate template = factory.getTemplate("REGEX_CHECK");

        TableMetadata table = TableMetadata.builder()
            .fullName("db.users")
            .build();

        ColumnMetadata column = ColumnMetadata.builder()
            .name("phone")
            .build();

        Map<String, Object> paramMap = new HashMap<>();
        paramMap.put("regexPattern", "^1[3-9]\\\\d{9}$");

        String sql = template.generateSQL(table, column, paramMap);
        assertTrue(sql.contains("match"));
        assertTrue(sql.contains("^1[3-9]"));

        System.out.println("✅ 正则表达式检查模板测试通过");
        System.out.println("   生成的SQL: " + sql);
    }

    @Test
    @DisplayName("8. 验证数值范围检查模板 (NUMERIC_RANGE_CHECK)")
    public void testNumericRangeCheckTemplate() {
        RuleTemplate template = factory.getTemplate("NUMERIC_RANGE_CHECK");

        List<ParamDefinition> params = template.getParamDefinitions();
        assertEquals(3, params.size());

        TableMetadata table = TableMetadata.builder()
            .fullName("db.orders")
            .build();

        ColumnMetadata column = ColumnMetadata.builder()
            .name("amount")
            .build();

        Map<String, Object> paramMap = new HashMap<>();
        paramMap.put("minValue", 0.0);
        paramMap.put("maxValue", 10000.0);
        paramMap.put("maxOutOfRangeRatio", 0.01);

        String sql = template.generateSQL(table, column, paramMap);
        assertTrue(sql.contains("CAST(amount AS DOUBLE)"));

        System.out.println("✅ 数值范围检查模板测试通过");
        System.out.println("   生成的SQL: " + sql);
    }

    @Test
    @DisplayName("9. 验证日期范围检查模板 (DATE_RANGE_CHECK)")
    public void testDateRangeCheckTemplate() {
        RuleTemplate template = factory.getTemplate("DATE_RANGE_CHECK");

        TableMetadata table = TableMetadata.builder()
            .fullName("db.orders")
            .build();

        ColumnMetadata column = ColumnMetadata.builder()
            .name("order_date")
            .build();

        Map<String, Object> paramMap = new HashMap<>();
        paramMap.put("minDate", "2020-01-01");
        paramMap.put("maxDate", "2025-12-31");
        paramMap.put("maxOutOfRangeRatio", 0.01);

        String sql = template.generateSQL(table, column, paramMap);
        assertTrue(sql.contains("toDate"));
        assertTrue(sql.contains("2020-01-01"));
        assertTrue(sql.contains("2025-12-31"));

        System.out.println("✅ 日期范围检查模板测试通过");
        System.out.println("   生成的SQL: " + sql);
    }

    @Test
    @DisplayName("10. 验证自定义业务规则模板 (BUSINESS_RULE)")
    public void testBusinessRuleTemplate() {
        RuleTemplate template = factory.getTemplate("BUSINESS_RULE");

        assertEquals(RuleType.TABLE.getCode(), template.getType());

        TableMetadata table = TableMetadata.builder()
            .fullName("db.orders")
            .build();

        Map<String, Object> paramMap = new HashMap<>();
        paramMap.put("whereCondition", "amount < 0");
        paramMap.put("maxViolationRatio", 0.0);
        paramMap.put("totalRows", 1000L);

        String sql = template.generateSQL(table, null, paramMap);
        assertTrue(sql.contains("WHERE amount < 0"));
        assertTrue(sql.contains("violation_count"));

        System.out.println("✅ 自定义业务规则模板测试通过");
        System.out.println("   生成的SQL: " + sql);
    }

    @Test
    @DisplayName("11. 验证模板信息查询")
    public void testTemplateInfo() {
        List<RuleTemplateFactory.TemplateInfo> infos = factory.getTemplateInfos();

        assertEquals(9, infos.size());

        // 验证每个模板都有完整的信息
        for (RuleTemplateFactory.TemplateInfo info : infos) {
            assertNotNull(info.getName());
            assertNotNull(info.getType());
            assertNotNull(info.getDescription());
            assertNotNull(info.getParamDefinitions());
            assertFalse(info.getParamDefinitions().isEmpty());
        }

        System.out.println("✅ 模板信息查询测试通过");
        System.out.println("\n📋 所有规则模板列表:");
        System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");

        for (RuleTemplateFactory.TemplateInfo info : infos) {
            System.out.printf("%-25s | %-6s | %s%n",
                info.getName(),
                info.getType(),
                info.getDescription());
        }
        System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
    }

    @Test
    @DisplayName("12. 验证参数定义的完整性")
    public void testParamDefinitions() {
        List<String> templateNames = factory.getAvailableTemplateNames();

        for (String templateName : templateNames) {
            RuleTemplate template = factory.getTemplate(templateName);
            List<ParamDefinition> params = template.getParamDefinitions();

            assertFalse(params.isEmpty(),
                "模板 " + templateName + " 应该有参数定义");

            // 验证每个参数都有必要的字段
            for (ParamDefinition param : params) {
                assertNotNull(param.getName(),
                    "参数name不能为空: " + templateName);
                assertNotNull(param.getType(),
                    "参数type不能为空: " + templateName);
                assertNotNull(param.getDescription(),
                    "参数description不能为空: " + templateName);
            }
        }

        System.out.println("✅ 参数定义完整性测试通过");
        System.out.println("\n📝 各模板参数明细:");
        System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");

        for (String templateName : templateNames) {
            RuleTemplate template = factory.getTemplate(templateName);
            List<ParamDefinition> params = template.getParamDefinitions();

            System.out.println("\n" + templateName + ":");
            for (ParamDefinition param : params) {
                System.out.printf("  - %-20s | %-8s | 必填:%-5b | 默认值: %s%n",
                    param.getName(),
                    param.getType(),
                    param.isRequired(),
                    param.getDefaultValue());
            }
        }
        System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
    }

    @Test
    @DisplayName("13. 验证异常处理")
    public void testExceptionHandling() {
        // 测试获取不存在的模板
        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            factory.getTemplate("NON_EXISTENT_TEMPLATE");
        });

        assertTrue(exception.getMessage().contains("规则模板不存在"));

        System.out.println("✅ 异常处理测试通过");
    }

    @Test
    @DisplayName("14. 验证行数波动检查模板")
    public void testRowCountFluctuationTemplate() {
        RuleTemplate template = factory.getTemplate("ROW_COUNT_FLUCTUATION");

        List<ParamDefinition> params = template.getParamDefinitions();
        assertEquals(2, params.size());

        TableMetadata table = TableMetadata.builder()
            .fullName("db.daily_orders")
            .build();

        Map<String, Object> paramMap = new HashMap<>();
        paramMap.put("baselineRows", 100000L);
        paramMap.put("maxFluctuationRatio", 0.2);

        String sql = template.generateSQL(table, null, paramMap);
        assertTrue(sql.contains("COUNT(*)"));
        assertTrue(sql.contains("db.daily_orders"));

        System.out.println("✅ 行数波动检查模板测试通过");
        System.out.println("   生成的SQL: " + sql);
    }

    /**
     * 主测试入口 - 运行所有测试
     */
    public static void main(String[] args) {
        String line = repeat("=", 60);
        System.out.println("\n" + line);
        System.out.println("🧪 规则模板引擎功能验证测试");
        System.out.println(line + "\n");

        RuleTemplateEngineTest test = new RuleTemplateEngineTest();
        long startTime = System.currentTimeMillis();

        try {
            test.setUp();
            test.testFactoryInitialization();
            test.testGetTemplatesByType();
            test.testNullCheckTemplate();
            test.testRowCountCheckTemplate();
            test.testUniqueCheckTemplate();
            test.testEnumCheckTemplate();
            test.testRegexCheckTemplate();
            test.testNumericRangeCheckTemplate();
            test.testDateRangeCheckTemplate();
            test.testBusinessRuleTemplate();
            test.testTemplateInfo();
            test.testParamDefinitions();
            test.testExceptionHandling();
            test.testRowCountFluctuationTemplate();

            long duration = System.currentTimeMillis() - startTime;

            System.out.println("\n" + line);
            System.out.println("✅ 所有测试通过！规则模板引擎功能验证完成");
            System.out.println("⏱️  总耗时: " + duration + "ms");
            System.out.println("📊 测试统计: 14个测试用例全部通过");
            System.out.println(line + "\n");

        } catch (Exception e) {
            System.err.println("\n❌ 测试失败: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Java 8兼容的字符串重复方法
     */
    private static String repeat(String str, int count) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < count; i++) {
            sb.append(str);
        }
        return sb.toString();
    }
}
