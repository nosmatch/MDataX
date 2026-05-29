# 质量规则管理开发总结

## 概述
质量规则管理是数据质量监控模块的业务功能核心，为用户提供规则的完整生命周期管理能力。

## 完成情况

### Service层（QualityRuleService）✅

实现了9个核心方法：

#### 1. createRule - 创建规则
- 验证规则模板是否存在
- 转换参数Map为JSON
- 设置默认值（优先级、权重、启用状态等）
- 保存到数据库
- 支持事务

**请求参数**：RuleCreateRequest
- 必填：ruleName, ruleType, ruleTemplate, tableId, tableName, database
- 可选：columnName, ruleParams, priority, weight, enabled, checkModes, scheduleCron, alertOnFailure, description

**返回**：规则ID

#### 2. updateRule - 更新规则
- 查询原规则是否存在
- 验证规则模板
- 更新指定字段（支持部分更新）
- 更新修改时间

**请求参数**：RuleUpdateRequest
- 所有字段都是可选的
- 支持部分更新

**返回**：是否成功

#### 3. deleteRule - 删除规则
- 根据规则ID删除
- 支持事务

**返回**：是否成功

#### 4. getRule - 获取规则详情
- 根据规则ID查询
- 规则不存在时抛出异常

**返回**：QualityRule实体

#### 5. listRules - 查询规则列表
- 支持多条件筛选：tableId, ruleTemplate, enabled
- 支持分页：pageNum, pageSize
- 按创建时间倒序排列

**返回**：IPage<QualityRule> 分页结果

#### 6. enableRule - 启用规则
- 将规则设置为启用状态
- 更新修改时间

**返回**：是否成功

#### 7. disableRule - 禁用规则
- 将规则设置为禁用状态
- 更新修改时间

**返回**：是否成功

#### 8. testRule - 测试规则
- 查询规则
- 解析参数
- 构建表和字段元数据
- 调用规则模板生成检查SQL
- 不实际执行SQL（避免影响真实数据）
- 返回生成的SQL供用户检查

**返回**：Map<String, Object>
- ruleId, ruleName, templateName
- checkSql（生成的检查SQL）
- params（参数Map）
- success（是否成功）

#### 9. copyRule - 复制规则
- 查询原规则
- 创建新规则（复制所有属性）
- 新规则名称由用户指定
- 新规则默认禁用
- 描述中标注"复制自：xxx"

**请求参数**：ruleId, newRuleName
**返回**：新规则ID

---

### Controller层（QualityRuleController）✅

实现了11个RESTful接口：

| 方法 | 路径 | 说明 | 请求方式 |
|------|------|------|---------|
| createRule | /api/quality/rules | 创建规则 | POST |
| updateRule | /api/quality/rules/{id} | 更新规则 | PUT |
| deleteRule | /api/quality/rules/{id} | 删除规则 | DELETE |
| getRule | /api/quality/rules/{id} | 获取规则详情 | GET |
| listRules | /api/quality/rules | 查询规则列表 | GET |
| enableRule | /api/quality/rules/{id}/enable | 启用规则 | POST |
| disableRule | /api/quality/rules/{id}/disable | 禁用规则 | POST |
| testRule | /api/quality/rules/{id}/test | 测试规则 | POST |
| copyRule | /api/quality/rules/{id}/copy | 复制规则 | POST |
| batchDeleteRules | /api/quality/rules/batch | 批量删除规则 | DELETE |
| getRuleTemplates | /api/quality/rules/templates | 获取所有规则模板 | GET |

**接口特性**：
- 统一的Result响应格式
- 完善的异常处理
- 详细的日志记录
- 参数验证（@Valid）

---

### DTO类完善✅

#### RuleCreateRequest（创建请求）
新增字段：
- tableName（必填）
- database（必填）
- columnType（可选）
- columnDescription（可选）
- ruleParams（Map格式，替代checkParams）
- checkModes（List格式，替代checkMode）

#### RuleUpdateRequest（更新请求）
更新为支持部分更新，所有字段都是可选的。

---

## 接口使用示例

### 1. 创建规则

**请求**：
```json
POST /api/quality/rules
{
  "ruleName": "用户邮箱空值检查",
  "ruleType": "COLUMN",
  "ruleTemplate": "NULL_CHECK",
  "tableId": 1,
  "tableName": "users",
  "database": "user_db",
  "columnName": "email",
  "columnType": "String",
  "columnDescription": "用户邮箱",
  "ruleParams": {
    "maxNullRatio": 0.05
  },
  "priority": 2,
  "enabled": true,
  "checkModes": ["REALTIME", "SCHEDULED"],
  "alertOnFailure": true,
  "description": "检查用户邮箱字段的空值比例"
}
```

**响应**：
```json
{
  "code": 200,
  "message": "规则创建成功",
  "data": 1
}
```

### 2. 查询规则列表

**请求**：
```
GET /api/quality/rules?tableId=1&enabled=true&pageNum=1&pageSize=10
```

**响应**：
```json
{
  "code": 200,
  "data": {
    "records": [...],
    "total": 100,
    "size": 10,
    "current": 1,
    "pages": 10
  }
}
```

### 3. 测试规则

**请求**：
```
POST /api/quality/rules/1/test
```

**响应**：
```json
{
  "code": 200,
  "message": "规则测试成功",
  "data": {
    "ruleId": 1,
    "ruleName": "用户邮箱空值检查",
    "templateName": "NULL_CHECK",
    "checkSql": "SELECT COUNT(*) AS total_rows, SUM(CASE WHEN email IS NULL THEN 1 ELSE 0 END) AS null_rows, CAST(SUM(CASE WHEN email IS NULL THEN 1 ELSE 0 END) AS DOUBLE) / COUNT(*) AS null_ratio FROM user_db.users",
    "params": {
      "maxNullRatio": 0.05
    },
    "success": true
  }
}
```

---

## 核心功能特性

### 1. 完整的CRUD操作
- 创建、读取、更新、删除
- 支持批量操作
- 事务保证

### 2. 规则生命周期管理
- 启用/禁用规则
- 复制规则（便于创建相似规则）
- 测试规则（生成SQL预览）

### 3. 灵活的查询能力
- 多条件筛选
- 分页查询
- 排序支持

### 4. 参数验证
- 使用@Valid进行参数验证
- 友好的错误提示

### 5. 异常处理
- 统一的异常处理机制
- 详细的错误信息
- 完善的日志记录

---

## 文件清单

**Service层（1个）**
- `QualityRuleService.java` - 质量规则服务（约450行）

**Controller层（1个）**
- `QualityRuleController.java` - 质量规则控制器（约280行）

**DTO层（2个已完善）**
- `RuleCreateRequest.java` - 创建规则请求
- `RuleUpdateRequest.java` - 更新规则请求

**Mapper层（1个已存在）**
- `QualityRuleMapper.java` - MyBatis Mapper接口

**总计：5个文件**

---

## 技术亮点

### 1. 参数Map与JSON转换
- Service层接收Map格式的参数
- 使用Jackson ObjectMapper转换为JSON存储
- 保证参数的灵活性和可扩展性

### 2. 规则模板集成
- 与RuleTemplateFactory深度集成
- 验证规则模板存在性
- 测试时生成真实的检查SQL

### 3. 部分更新支持
- updateRule方法支持部分更新
- 只更新传入的非空字段
- 提高API灵活性

### 4. 复制规则功能
- 便于快速创建相似规则
- 新规则默认禁用（需手动启用）
- 保留原规则的所有配置

### 5. 测试规则功能
- 不实际执行SQL
- 生成SQL供用户检查
- 帮助用户验证规则配置是否正确

---

## 与其他模块的集成

### 1. 与质量检查引擎集成
- 质量检查引擎通过QualityRuleService查询启用规则
- 生成CheckTask并执行检查

### 2. 与规则模板引擎集成
- 验证规则模板存在性
- 测试时调用模板生成SQL

### 3. 与前端集成
- 提供11个RESTful接口
- 支持规则的完整管理功能

---

## 待完善功能

### 单元测试（后续补充）
- Service层单元测试
- Controller层集成测试
- 测试覆盖率 ≥ 80%

### 权限控制（3.13阶段）
- 添加权限注解
- 定义权限资源：
  - quality:rule:view
  - quality:rule:create
  - quality:rule:update
  - quality:rule:delete
  - quality:rule:enable

### 性能优化（可选）
- 添加缓存（规则模板列表）
- 批量操作优化

---

## 完成状态

✅ **3.5 质量规则管理开发完成**

已完成：
- ✅ Service层完整实现（9个方法）
- ✅ Controller层完整实现（11个接口）
- ✅ DTO类完善
- ✅ 完善的异常处理
- ✅ 详细的日志记录

核心功能已全部实现，具备了完整的质量规则管理能力！

下一步可以继续开发：
- 3.6 质量报告管理
- 3.7 质量大盘
- 3.9 异常管理

---
@author fengzhu
@since 2026-05-10
