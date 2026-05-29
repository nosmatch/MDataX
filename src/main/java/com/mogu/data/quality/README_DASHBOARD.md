# 质量大盘开发总结

## 概述
质量大盘是数据质量监控模块的仪表板，提供整体质量的可视化概览，包括质量概况、趋势分析、质量分布、TOP榜单和异常监控。

## 完成情况

### Service层（QualityDashboardService）✅

实现了5个核心查询方法 + 1个私有辅助方法：

#### 1. getOverview - 获取整体质量概况
- 统计所有表的质量等级分布
- 计算平均质量分数
- 统计规则执行情况
- 统计今日检查次数

**返回数据**：
- totalTables - 总表数
- excellentTables - 优秀表数
- goodTables - 良好表数
- passTables - 及格表数
- failTables - 不及格表数
- avgQualityScore - 平均质量分数
- totalRules/passedRules/failedRules/warnedRules - 规则统计
- checkToday - 今日检查次数

#### 2. getTrend - 获取质量趋势
- 查询指定天数范围内的报告
- 按日期分组统计
- 计算每天的平均质量分数
- 填充缺失日期（避免趋势图断裂）

**参数**：days（查询天数，默认30）
**返回**：List<TrendVO> - 日期、分数、检查次数

#### 3. getDistribution - 获取质量分布
- 统计各质量等级的表数量
- 提供当前整体质量分布快照

**返回数据**：
- excellent - 优秀表数量
- good - 良好表数量
- pass - 及格表数量
- fail - 不及格表数量

#### 4. getTopTables - 获取质量TOP榜
- 按质量分数排序
- 返回排名前N的表
- 包含表信息和质量指标

**参数**：limit（返回数量，默认10）
**返回**：List<TopTableVO>
- tableId, tableName, database
- qualityScore, qualityLevel, passRate
- checkTime

#### 5. getRecentAnomalies - 获取最近异常
- 查询有失败或警告的报告
- 按检查时间倒序
- 返回最近的异常列表

**参数**：limit（返回数量，默认10）
**返回**：List<AnomalyVO>
- 表信息、质量分数、失败规则数
- 检查时间和类型

#### 6. getAllLatestReports - 获取所有表的最新报告（私有方法）
- 查询所有报告
- 按tableId去重，保留每个表最新的报告
- 为其他查询提供基础数据

---

### Controller层（QualityDashboardController）✅

实现了6个RESTful接口：

| 接口 | 路径 | 说明 | 请求方式 |
|------|------|------|---------|
| getOverview | /api/quality/dashboard/overview | 整体质量概况 | GET |
| getTrend | /api/quality/dashboard/trend | 质量趋势 | GET |
| getDistribution | /api/quality/dashboard/distribution | 质量分布 | GET |
| getTopTables | /api/quality/dashboard/top | 质量TOP榜 | GET |
| getRecentAnomalies | /api/quality/dashboard/anomalies | 最近异常 | GET |
| getDashboardAll | /api/quality/dashboard/all | 完整大盘数据 | GET |

**接口特性**：
- 统一的Result响应格式
- 完善的异常处理
- 详细的日志记录
- 支持可配置参数
- 完整数据接口（一次性获取所有指标）

---

### VO类（QualityDashboardVO）✅

包含5个内部类：

1. **OverviewVO** - 整体概况
   - 8个统计指标

2. **TrendVO** - 趋势数据
   - 3个字段：日期、分数、检查次数

3. **DistributionVO** - 质量分布
   - 4个等级的表数量

4. **TopTableVO** - TOP榜表项
   - 表信息 + 质量指标

5. **AnomalyVO** - 异常表项
   - 表信息 + 异常详情

---

## 接口使用示例

### 1. 获取整体质量概况

**请求**：
```
GET /api/quality/dashboard/overview
```

**响应**：
```json
{
  "code": 200,
  "data": {
    "totalTables": 50,
    "excellentTables": 20,
    "goodTables": 15,
    "passTables": 10,
    "failTables": 5,
    "avgQualityScore": 82,
    "totalRules": 500,
    "passedRules": 450,
    "failedRules": 40,
    "warnedRules": 10,
    "checkToday": 25
  }
}
```

### 2. 获取质量趋势

**请求**：
```
GET /api/quality/dashboard/trend?days=7
```

**响应**：
```json
{
  "code": 200,
  "data": [
    {
      "date": "2026-05-04",
      "qualityScore": 85,
      "checkCount": 15
    },
    {
      "date": "2026-05-05",
      "qualityScore": 88,
      "checkCount": 18
    }
  ]
}
```

### 3. 获取质量TOP榜

**请求**：
```
GET /api/quality/dashboard/top?limit=5
```

**响应**：
```json
{
  "code": 200,
  "data": [
    {
      "tableId": 1,
      "tableName": "users",
      "database": "user_db",
      "qualityScore": 95,
      "qualityLevel": "EXCELLENT",
      "passRate": 100,
      "checkTime": "2026-05-10T18:00:00"
    }
  ]
}
```

### 4. 获取完整大盘数据

**请求**：
```
GET /api/quality/dashboard/all?trendDays=7&topLimit=10&anomalyLimit=10
```

**响应**：
```json
{
  "code": 200,
  "data": {
    "overview": {...},
    "trend": [...],
    "distribution": {...},
    "topTables": [...],
    "recentAnomalies": [...]
  }
}
```

---

## 核心功能特性

### 1. 多维度统计分析
- 整体概况：表数量、质量分布、平均分
- 时间趋势：按日期统计质量变化
- 质量分布：等级分布快照
- TOP榜单：最佳质量表排名
- 异常监控：最近质量异常

### 2. 智能数据处理
- 自动去重：每个表只保留最新报告
- 缺失填充：趋势数据填充缺失日期
- 空数据处理：无数据时返回合理默认值

### 3. 性能优化
- 内存去重：避免多次查询数据库
- 流式计算：使用Java Stream高效统计
- 分页限制：TOP榜和异常列表限制返回数量

### 4. 可配置参数
- 趋势天数可配置
- TOP榜数量可配置
- 异常列表长度可配置

---

## 数据处理逻辑

### 整体概况统计
```
1. 查询所有表的最新报告
2. 统计表总数和质量等级分布
3. 计算平均质量分数
4. 汇总规则执行情况
5. 统计今日检查次数
```

### 趋势数据生成
```
1. 查询指定日期范围的所有报告
2. 按reportDate分组
3. 计算每天的平均质量分数
4. 填充缺失日期（避免折线图断裂）
5. 返回时间序列数据
```

### TOP榜排序
```
1. 获取所有表的最新报告
2. 按qualityScore降序排序
3. 取前N条记录
4. 转换为TopTableVO
```

### 异常检测
```
1. 查询qualityStatus为FAIL或WARN的报告
2. 按checkTime降序排序
3. 取前N条记录
4. 转换为AnomalyVO
```

---

## 文件清单

**Service层（1个）**
- `QualityDashboardService.java` - 质量大盘服务（约300行，6个方法）

**Controller层（1个）**
- `QualityDashboardController.java` - 质量大盘控制器（约180行，6个接口）

**VO层（1个已更新）**
- `QualityDashboardVO.java` - 质量大盘VO（5个内部类）

**总计：3个文件**

---

## 技术亮点

### 1. Java Stream API
- 使用Stream进行数据过滤、分组、统计
- mapToInt、average、count等聚合操作
- Collectors.groupingBy分组统计

### 2. LinkedHashMap去重
- 保持表报告的顺序
- 按tableId去重，每个表只保留最新报告

### 3. 缺失日期填充
- 遍历日期范围
- 为缺失日期补充默认数据
- 确保趋势图连续

### 4. 聚合查询接口
- getDashboardAll一次性返回所有指标
- 减少前端请求次数
- 提升页面加载性能

### 5. 灵活参数配置
- 所有查询都支持参数配置
- 默认值合理（趋势30天、TOP10、异常10条）
- 满足不同展示需求

---

## 与其他模块的集成

### 1. 与质量报告管理集成
- 查询质量报告数据
- 统计和分析报告数据

### 2. 与前端集成
- 提供仪表板数据接口
- 支持图表渲染：
  - 仪表盘：平均质量分数
  - 饼图：质量分布
  - 折线图：质量趋势
  - 表格：TOP榜单
  - 列表：异常监控

---

## 待完善功能

### 单元测试（后续补充）
- Service层单元测试
- Controller层集成测试
- 测试覆盖率 ≥ 80%

### 缓存优化（可选）
- 缓存整体概况（5分钟）
- 缓存趋势数据（10分钟）
- 缓存TOP榜单（5分钟）
- 减少数据库查询压力

### 实时优化（可选）
- WebSocket推送质量变化
- 异常实时告警
- 动态刷新大盘数据

---

## 前端展示建议

### 1. 整体概况卡片
```
┌─────────────────────────────────────┐
│  平均质量分数   优秀表   良好表    │
│     82分        20      15         │
│  🟢 仪表盘或数字                     │
└─────────────────────────────────────┘
```

### 2. 质量趋势折线图
```
质量分数
  100 ┤     ●
   90 ┤   ●   ●
   80 ┤ ●
   70 ┤
      └───────────────────────
        5/1  5/3  5/5  5/7
```

### 3. 质量分布饼图
```
   优秀  40%
   良好  30%
   及格  20%
   不及格 10%
```

### 4. TOP榜单表格
```
排名  表名       质量分数  质量等级
 1   users      95      EXCELLENT
 2   orders     90      EXCELLENT
 3   products   88      GOOD
 ...
```

### 5. 异常监控列表
```
表名      质量分数  失败规则  检查时间
users    55       3       18:30
orders   62       2       17:45
...
```

---

## 完成状态

✅ **3.7 质量大盘开发完成**

已完成：
- ✅ Service层完整实现（6个方法）
- ✅ Controller层完整实现（6个接口）
- ✅ VO类完善（5个内部类）
- ✅ 完善的异常处理
- ✅ 详细的日志记录

核心功能已全部实现，具备完整的数据质量大盘展示能力！

下一步可以继续开发：
- 3.9 异常管理
- 3.10 实时检查集成
- 3.11 定时检查集成

---
@author fengzhu
@since 2026-05-10
