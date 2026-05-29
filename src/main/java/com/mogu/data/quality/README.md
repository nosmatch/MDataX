# 质量监控模块包结构说明

> 模块名称：quality（数据质量监控）
> 创建日期：2026-05-09
> 作者：fengzhu

---

## 📦 包结构概览

```
com.mogu.data.quality
├── alert/                    # 告警系统
│   ├── channel/             # 告警渠道（钉钉/邮件/企微）
│   └── notifier/            # 告警通知器
├── config/                   # 配置类
├── constants/                # 常量类
├── controller/               # 控制器层
├── dto/                      # 数据传输对象
├── entity/                   # 实体类
├── engine/                   # 规则引擎
│   ├── analyzer/            # 结果分析器
│   ├── executor/            # SQL执行器
│   ├── parser/              # 规则解析器
│   └── template/            # 规则模板
├── enums/                    # 枚举类
├── mapper/                   # 数据访问层
├── service/                  # 服务层
│   └── impl/               # 服务实现
└── vo/                       # 视图对象
```

---

## 📁 各包说明

### 1. controller/ - 控制器层
**说明**：处理HTTP请求，调用Service层

**包含的类**：
- `QualityRuleController` - 质量规则管理
- `QualityReportController` - 质量报告查询
- `QualityDashboardController` - 质量大盘
- `QualityAlertController` - 质量告警管理
- `QualityAnomalyController` - 异常管理
- `QualityCheckController` - 质量检查

**数量**：6个Controller

---

### 2. service/ & service/impl/ - 服务层
**说明**：业务逻辑层，处理核心业务

**包含的接口**：
- `QualityRuleService` - 质量规则服务
- `QualityReportService` - 质量报告服务
- `QualityDashboardService` - 质量大盘服务
- `QualityAlertService` - 质量告警服务
- `QualityAnomalyService` - 异常管理服务
- `QualityCheckService` - 质量检查服务（核心）

**数量**：6个Service接口 + 6个Service实现

---

### 3. mapper/ - 数据访问层
**说明**：MyBatis Mapper接口，访问数据库

**包含的类**：
- `QualityRuleMapper` - 质量规则Mapper
- `QualityCheckResultMapper` - 检查结果Mapper
- `QualityReportMapper` - 质量报告Mapper
- `QualityAlertRuleMapper` - 告警规则Mapper
- `QualityAlertHistoryMapper` - 告警历史Mapper

**数量**：5个Mapper

---

### 4. entity/ - 实体类
**说明**：数据库表对应的实体类

**包含的类**：
- `QualityRule` - 质量规则实体
- `QualityCheckResult` - 质量检查结果实体
- `QualityReport` - 质量报告实体
- `QualityAlertRule` - 质量告警规则实体
- `QualityAlertHistory` - 质量告警历史实体

**数量**：5个Entity

**注解规范**：
- `@TableName` - 指定表名
- `@TableId` - 主键
- `@author fengzhu` - 作者标注

---

### 5. vo/ - 视图对象
**说明**：查询结果封装对象，返回给前端

**包含的类**：
- `QualityRuleVO` - 质量规则视图对象
- `CheckResultVO` - 检查结果视图对象
- `QualityReportVO` - 质量报告视图对象
- `QualityDashboardVO` - 质量大盘视图对象
- `AnomalyVO` - 异常视图对象

**数量**：5个VO

---

### 6. dto/ - 数据传输对象
**说明**：请求参数封装对象

**包含的类**：
- `RuleCreateRequest` - 创建规则请求
- `RuleUpdateRequest` - 更新规则请求
- `ManualCheckRequest` - 手动检查请求
- `AlertRuleCreateRequest` - 创建告警规则请求
- `AlertRuleUpdateRequest` - 更新告警规则请求
- `QualityReportQueryRequest` - 质量报告查询请求
- `AnomalyHandleRequest` - 异常处理请求
- `TestAlertRequest` - 测试告警请求

**数量**：8个DTO

---

### 7. enums/ - 枚举类
**说明**：枚举类型定义

**包含的类**：
- `RuleType` - 规则类型（TABLE/COLUMN）
- `RuleTemplate` - 规则模板（9种模板）
- `CheckType` - 检查类型（REALTIME/SCHEDULED/MANUAL）
- `CheckStatus` - 检查状态（PASS/FAIL/WARN）
- `QualityLevel` - 质量等级（EXCELLENT/GOOD/PASS/FAIL）
- `AlertChannel` - 告警渠道（DINGTALK/EMAIL/WEWORK）

**数量**：6个Enum

---

### 8. constants/ - 常量类
**说明**：常量定义

**包含的类**：
- `QualityConstants` - 质量监控常量
- `RuleTemplateConstants` - 规则模板常量

**数量**：2个Constant

---

### 9. engine/ - 规则引擎
**说明**：质量检查的核心引擎

#### 9.1 engine/template/ - 规则模板
**说明**：9种内置规则模板实现

**包含的类**：
- `NullCheckTemplate` - 空值检查
- `RowCountCheckTemplate` - 行数检查
- `RowCountFluctuationTemplate` - 行数波动检查
- `UniqueCheckTemplate` - 唯一值检查
- `EnumCheckTemplate` - 枚举值检查
- `RegexCheckTemplate` - 正则表达式检查
- `NumericRangeCheckTemplate` - 数值范围检查
- `DateRangeCheckTemplate` - 日期范围检查
- `BusinessRuleTemplate` - 业务规则检查

**数量**：9个Template

#### 9.2 engine/parser/ - 规则解析器
**说明**：解析规则配置，生成检查SQL

**包含的类**：
- `RuleParser` - 规则解析器

**数量**：1个Parser

#### 9.3 engine/executor/ - SQL执行器
**说明**：执行检查SQL，收集结果

**包含的类**：
- `SqlExecutor` - SQL执行器

**数量**：1个Executor

#### 9.4 engine/analyzer/ - 结果分析器
**说明**：分析检查结果，计算质量分数

**包含的类**：
- `ResultAnalyzer` - 结果分析器

**数量**：1个Analyzer

---

### 10. alert/ - 告警系统
**说明**：质量告警发送和管理

#### 10.1 alert/channel/ - 告警渠道
**说明**：告警渠道实现

**包含的类**：
- `DingTalkAlertChannel` - 钉钉告警渠道
- `EmailAlertChannel` - 邮件告警渠道
- `WeWorkAlertChannel` - 企业微信告警渠道

**数量**：3个Channel

#### 10.2 alert/notifier/ - 告警通知器
**说明**：告警通知和抑制

**包含的类**：
- `AlertNotifier` - 告警通知器
- `AlertSuppressor` - 告警抑制器

**数量**：2个Notifier

---

### 11. config/ - 配置类
**说明**：Spring配置类

**包含的类**：
- `QualityMonitorConfig` - 质量监控配置
- `QualityCheckExecutorConfig` - 质量检查线程池配置
- `AsyncExecutorConfig` - 异步执行器配置

**数量**：3个Config

---

## 📊 包结构统计

| 层级 | 包数量 | 说明 |
|------|--------|------|
| 一级包 | 11个 | 主要功能模块 |
| 二级包 | 7个 | 子模块 |
| 总计 | 18个 | 所有包目录 |

---

## 🔧 代码规范

### 1. 类命名规范
- **Entity**：使用表名，如 `QualityRule`
- **VO**：使用 `VO` 后缀，如 `QualityRuleVO`
- **DTO**：使用 `Request` 后缀，如 `RuleCreateRequest`
- **Service**：使用 `Service` 后缀，如 `QualityRuleService`
- **Mapper**：使用 `Mapper` 后缀，如 `QualityRuleMapper`
- **Controller**：使用 `Controller` 后缀，如 `QualityRuleController`
- **Enum**：使用描述性名称，如 `RuleType`
- **Template**：使用 `Template` 后缀，如 `NullCheckTemplate`

### 2. 注解规范
- **所有类必须添加**：`@author fengzhu`
- **Entity类**：添加 `@TableName`、`@TableId`
- **Controller类**：添加 `@RestController`、`@RequestMapping`
- **Service类**：添加 `@Service`
- **Mapper类**：添加 `@Mapper`
- **Config类**：添加 `@Configuration`

### 3. 包依赖规范
```
Controller → Service → Mapper → Entity
     ↓         ↓
    VO      DTO
```

- Controller 只能调用 Service，不能直接调用 Mapper
- Service 可以调用 Mapper 和其他 Service
- 所有层都可以使用 Entity、VO、DTO、Enum

---

## 📝 开发计划

### 第一阶段：基础类开发（当前）
- [x] 创建包结构
- [ ] 创建Entity类（5个）
- [ ] 创建Enum类（6个）
- [ ] 创建Constant类（2个）
- [ ] 创建VO类（5个）
- [ ] 创建DTO类（8个）

### 第二阶段：规则引擎开发
- [ ] 创建规则模板（9个）
- [ ] 创建规则解析器
- [ ] 创建SQL执行器
- [ ] 创建结果分析器

### 第三阶段：业务功能开发
- [ ] 创建Mapper接口和XML
- [ ] 创建Service接口和实现
- [ ] 创建Controller

### 第四阶段：告警系统开发
- [ ] 创建告警渠道（3个）
- [ ] 创建告警通知器

### 第五阶段：配置类开发
- [ ] 创建配置类（3个）

---

## 🎯 下一步

**步骤2：创建基础类**
- 2.1 创建Entity类（5个）
- 2.2 创建Enum类（6个）
- 2.3 创建Constant类（2个）
- 2.4 创建VO类（5个）
- 2.5 创建DTO类（8个）
- 2.6 创建Exception类（1个）

---

**文档结束**
