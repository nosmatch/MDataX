# 规则模板引擎实现总结

## 概述
规则模板引擎是数据质量监控模块的核心组件，负责根据预定义的规则模板生成SQL并分析检查结果。

## 架构设计

### 1. 核心接口
**RuleTemplate** - 规则模板接口
- `getName()`: 获取模板名称
- `getType()`: 获取规则类型（TABLE/COLUMN）
- `getDescription()`: 获取模板描述
- `getParamDefinitions()`: 获取参数定义列表
- `generateSQL()`: 生成检查SQL
- `analyzeResult()`: 分析检查结果

### 2. 模型类（4个）
- **ParamDefinition**: 参数定义
  - name: 参数名称
  - type: 参数类型
  - description: 参数描述
  - required: 是否必填
  - defaultValue: 默认值
  - example: 示例值

- **TableMetadata**: 表元数据
  - tableId: 表ID
  - tableName: 表名
  - database: 数据库
  - fullName: 完整表名（database.tableName）
  - description: 表描述

- **ColumnMetadata**: 字段元数据
  - name: 字段名
  - type: 字段类型
  - description: 字段描述

- **CheckResult**: 检查结果
  - status: 检查状态（PASS/FAIL/WARN）
  - actualValue: 实际值
  - expectedValue: 期望值
  - errorMessage: 错误消息
  - checkDuration: 检查耗时

### 3. 规则模板实现（9个）

#### 表级规则模板（2个）

1. **RowCountCheckTemplate** - 行数检查
   - 检查表行数是否在[minRows, maxRows]范围内
   - 参数: minRows, maxRows
   - SQL: `SELECT COUNT(*) FROM table`

2. **RowCountFluctuationTemplate** - 行数波动检查
   - 检查表行数相对历史基线的波动比例
   - 参数: baselineRows（基线行数）, maxFluctuationRatio（最大波动比例）
   - SQL: `SELECT COUNT(*) FROM table`

#### 字段级规则模板（6个）

3. **NullCheckTemplate** - 空值检查
   - 检查字段空值比例是否超过阈值
   - 参数: maxNullRatio（最大空值比例）
   - SQL: 计算NULL值的比例

4. **UniqueCheckTemplate** - 唯一性检查
   - 检查字段值是否唯一
   - 参数: maxDuplicateRatio（允许的最大重复比例）
   - SQL: `COUNT(DISTINCT column)`

5. **EnumCheckTemplate** - 枚举值检查
   - 检查字段值是否在指定的枚举列表中
   - 参数: enumValues（逗号分隔的枚举值）
   - SQL: `column NOT IN (values)`

6. **RegexCheckTemplate** - 正则表达式检查
   - 检查字段值是否匹配正则表达式
   - 参数: regexPattern（正则表达式）
   - SQL: ClickHouse的match函数

7. **NumericRangeCheckTemplate** - 数值范围检查
   - 检查数值型字段的值是否在指定范围内
   - 参数: minValue, maxValue, maxOutOfRangeRatio
   - SQL: `column < minValue OR column > maxValue`

8. **DateRangeCheckTemplate** - 日期范围检查
   - 检查日期型字段的值是否在指定范围内
   - 参数: minDate, maxDate, maxOutOfRangeRatio
   - SQL: ClickHouse的toDate函数比较

#### 自定义规则模板（1个）

9. **BusinessRuleTemplate** - 自定义业务规则
   - 通过自定义SQL表达式进行业务规则检查
   - 参数: whereCondition（WHERE条件表达式）, maxViolationRatio
   - SQL: 用户自定义的WHERE条件

### 4. 工厂类

**RuleTemplateFactory** - 规则模板工厂
- 管理所有规则模板的注册和获取
- 提供按名称、按类型获取模板的方法
- 支持动态注册新的规则模板
- 启动时自动注册所有内置模板（@PostConstruct）

## 特性

### 1. 可扩展性
- 通过RuleTemplate接口可以轻松添加新的规则模板
- 工厂支持动态注册，可以在运行时添加新模板

### 2. 类型安全
- 每个模板都有明确的参数定义和类型
- 支持参数验证和默认值

### 3. 结果标准化
- 所有模板返回统一的CheckResult对象
- 包含状态、实际值、期望值、错误消息等标准字段

### 4. 灵活性
- 表级规则：对整个表进行检查
- 字段级规则：对特定字段进行检查
- 自定义规则：支持任意SQL表达式

## 使用示例

```java
@Autowired
private RuleTemplateFactory templateFactory;

// 获取规则模板
RuleTemplate template = templateFactory.getTemplate("NULL_CHECK");

// 准备元数据
TableMetadata table = TableMetadata.builder()
    .fullName("db.user")
    .build();

ColumnMetadata column = ColumnMetadata.builder()
    .name("email")
    .build();

// 准备参数
Map<String, Object> params = new HashMap<>();
params.put("maxNullRatio", 0.05);

// 生成SQL
String sql = template.generateSQL(table, column, params);

// 执行SQL后分析结果
ResultSet rs = statement.executeQuery(sql);
CheckResult result = template.analyzeResult(rs, params);

if ("PASS".equals(result.getStatus())) {
    System.out.println("检查通过");
} else {
    System.out.println("检查失败: " + result.getErrorMessage());
}
```

## 文件清单

总计15个文件：

**接口层（1个）**
- RuleTemplate.java

**模型层（4个）**
- model/ParamDefinition.java
- model/TableMetadata.java
- model/ColumnMetadata.java
- model/CheckResult.java

**实现层（9个）**
- impl/NullCheckTemplate.java
- impl/RowCountCheckTemplate.java
- impl/RowCountFluctuationTemplate.java
- impl/UniqueCheckTemplate.java
- impl/EnumCheckTemplate.java
- impl/RegexCheckTemplate.java
- impl/NumericRangeCheckTemplate.java
- impl/DateRangeCheckTemplate.java
- impl/BusinessRuleTemplate.java

**工厂层（1个）**
- RuleTemplateFactory.java

## 完成状态

✅ 规则模板引擎开发完成

已完成：
- ✅ RuleTemplate接口定义
- ✅ 4个模型类
- ✅ 9个规则模板实现（100%）
- ✅ RuleTemplateFactory工厂类

下一步：
- 质量检查执行引擎开发
- 告警系统开发
- 服务层（Mapper、Service、Controller）

---
@author fengzhu
@since 2026-05-09
