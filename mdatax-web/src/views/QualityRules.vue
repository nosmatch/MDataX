<template>
  <div class="quality-rules-page">
    <div class="page-header">
      <div>
        <h2>质量规则管理</h2>
        <p class="page-subtitle">配置数据质量检查规则，监控数据资产的准确性、完整性和一致性</p>
      </div>
      <div class="header-actions">
        <el-button type="info" :icon="QuestionFilled" @click="showTemplateHelp">规则模板说明</el-button>
        <el-button type="primary" :icon="Plus" @click="openCreateDialog">新建规则</el-button>
      </div>
    </div>

    <!-- 筛选条件 -->
    <el-card class="filter-card">
      <el-form :inline="true" :model="queryParams" class="filter-form">
        <el-form-item label="规则名称">
          <el-input
            v-model="queryParams.ruleName"
            placeholder="请输入规则名称"
            clearable
            style="width: 200px"
          />
        </el-form-item>
        <el-form-item label="规则类型">
          <el-select
            v-model="queryParams.ruleType"
            placeholder="请选择规则类型"
            clearable
            style="width: 150px"
          >
            <el-option label="表级规则" value="TABLE" />
            <el-option label="字段级规则" value="COLUMN" />
          </el-select>
        </el-form-item>
        <el-form-item label="规则模板">
          <el-select
            v-model="queryParams.ruleTemplate"
            placeholder="请选择规则模板"
            clearable
            style="width: 200px"
          >
            <el-option v-for="tpl in templates" :key="tpl" :label="tpl" :value="tpl" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select
            v-model="queryParams.enabled"
            placeholder="请选择状态"
            clearable
            style="width: 120px"
          >
            <el-option label="启用" :value="true" />
            <el-option label="禁用" :value="false" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="loadRules">查询</el-button>
          <el-button @click="resetQuery">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 规则列表 -->
    <el-card>
      <el-table :data="rules" stripe v-loading="loading" border>
        <el-table-column type="selection" width="55" />
        <el-table-column prop="id" label="ID" width="60" />
        <el-table-column prop="ruleName" label="规则名称" min-width="160" show-overflow-tooltip />
        <el-table-column prop="ruleType" label="类型" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="row.ruleType === 'TABLE' ? 'primary' : 'success'" size="small">
              {{ row.ruleType === 'TABLE' ? '表级' : '字段级' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="ruleTemplate" label="模板" min-width="150" show-overflow-tooltip />
        <el-table-column prop="tableName" label="表名" min-width="120" show-overflow-tooltip />
        <el-table-column prop="columnName" label="字段名" min-width="100" show-overflow-tooltip />
        <el-table-column prop="enabled" label="状态" width="80" align="center">
          <template #default="{ row }">
            <el-switch
              v-model="row.enabled"
              @change="toggleRuleEnabled(row)"
              :loading="row.toggleLoading"
            />
          </template>
        </el-table-column>
        <el-table-column prop="updatedAt" label="更新时间" width="160" />
        <el-table-column label="操作" width="200" align="center" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" size="small" link @click="openEditDialog(row)">编辑</el-button>
            <el-button type="primary" size="small" link @click="copyRule(row)">复制</el-button>
            <el-button type="primary" size="small" link @click="viewRuleSql(row)">SQL</el-button>
            <el-button type="danger" size="small" link @click="deleteRule(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-model:current-page="queryParams.pageNum"
        v-model:page-size="queryParams.pageSize"
        :total="total"
        :page-sizes="[10, 20, 50, 100]"
        layout="total, sizes, prev, pager, next, jumper"
        @size-change="loadRules"
        @current-change="loadRules"
        style="margin-top: 16px; justify-content: flex-end"
      />
    </el-card>

    <!-- 创建/编辑规则对话框 -->
    <el-dialog
      v-model="dialogVisible"
      :title="dialogTitle"
      width="700px"
      @close="resetForm"
    >
      <el-form ref="formRef" :model="form" :rules="formRules" label-width="100px">
        <el-form-item label="规则名称" prop="ruleName">
          <el-input v-model="form.ruleName" placeholder="请输入规则名称" style="width: 100%" />
        </el-form-item>
        <el-form-item label="规则类型" prop="ruleType">
          <el-radio-group v-model="form.ruleType">
            <el-radio label="TABLE">表级规则</el-radio>
            <el-radio label="COLUMN">字段级规则</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="规则模板" prop="ruleTemplate">
          <el-select
            v-model="form.ruleTemplate"
            placeholder="请选择规则模板"
            @change="onTemplateChange"
            style="width: 100%"
          >
            <el-option
              v-for="tpl in templates"
              :key="tpl"
              :label="`${tpl} - ${getTemplateDesc(tpl)}`"
              :value="tpl"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="数据库" prop="database">
          <el-select
            v-model="form.database"
            placeholder="请选择数据库"
            filterable
            clearable
            @change="onDatabaseChange"
            style="width: 100%"
            :loading="databaseLoading"
          >
            <el-option
              v-for="db in databases"
              :key="db"
              :label="db"
              :value="db"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="表名" prop="tableName">
          <el-select
            v-model="form.tableName"
            placeholder="请先选择数据库"
            filterable
            clearable
            @change="onTableChange"
            style="width: 100%"
            :loading="tableLoading"
            :disabled="!form.database"
          >
            <el-option
              v-for="table in tables"
              :key="table.id"
              :label="table.name"
              :value="table.name"
            />
          </el-select>
        </el-form-item>
        <el-form-item v-if="form.ruleType === 'COLUMN'" label="字段名" prop="columnName">
          <el-select
            v-model="form.columnName"
            placeholder="请选择字段"
            filterable
            clearable
            allow-create
            style="width: 100%"
            :loading="columnLoading"
            :disabled="!form.tableName"
          >
            <el-option
              v-for="column in columns"
              :key="column"
              :label="column"
              :value="column"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="规则参数">
          <div v-for="(value, key) in form.ruleParams" :key="key" class="param-item">
            <span class="param-label">{{ key }}:</span>
            <el-input v-model="form.ruleParams[key]" placeholder="请输入参数值" style="width: 100%" />
          </div>
          <el-text type="info" size="small">根据选择的模板自动填充参数</el-text>
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="form.description" type="textarea" :rows="3" placeholder="请输入规则描述" style="width: 100%" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitForm" :loading="submitLoading">确定</el-button>
      </template>
    </el-dialog>

    <!-- SQL预览对话框 -->
    <el-dialog v-model="sqlDialogVisible" title="规则SQL预览" width="900px" class="sql-preview-dialog">
      <div class="sql-preview-container">
        <div class="sql-preview-header">
          <el-button size="small" @click="copySql" :icon="DocumentCopy">
            {{ sqlCopied ? '已复制' : '复制SQL' }}
          </el-button>
          <el-button size="small" @click="formatSql" :icon="MagicStick">
            格式化SQL
          </el-button>
        </div>
        <div class="sql-preview-content">
          <pre class="sql-code-block" v-html="highlightSql(formattedSql)"></pre>
        </div>
        <div class="sql-preview-info">
          <el-descriptions :column="2" size="small" border>
            <el-descriptions-item label="规则ID">{{ currentRuleInfo.id }}</el-descriptions-item>
            <el-descriptions-item label="规则名称">{{ currentRuleInfo.ruleName }}</el-descriptions-item>
            <el-descriptions-item label="规则模板">{{ currentRuleInfo.templateName }}</el-descriptions-item>
            <el-descriptions-item label="检查类型">{{ currentRuleInfo.checkType }}</el-descriptions-item>
          </el-descriptions>
        </div>
      </div>
    </el-dialog>

    <!-- 规则模板说明对话框 -->
    <el-dialog v-model="templateHelpVisible" title="规则模板说明" width="900px" class="template-help-dialog">
      <el-tabs v-model="activeTemplateTab" type="border-card">
        <el-tab-pane label="表级规则模板" name="table">
          <div class="template-list">
            <div v-for="template in tableTemplates" :key="template.name" class="template-item">
              <div class="template-header">
                <h4>{{ template.name }}</h4>
                <el-tag type="primary">表级</el-tag>
              </div>
              <p class="template-desc">{{ template.description }}</p>
              <div class="template-scenario">
                <strong>适用场景：</strong>{{ template.scenario }}
              </div>
              <div class="template-params">
                <strong>参数说明：</strong>
                <ul>
                  <li v-for="param in template.params" :key="param.name">
                    <code>{{ param.name }}</code>: {{ param.desc }}
                    <span v-if="param.default !== undefined" class="param-default">（默认: {{ param.default }}）</span>
                  </li>
                </ul>
              </div>
              <div class="template-example">
                <strong>示例：</strong>
                <pre>{{ template.example }}</pre>
              </div>
            </div>
          </div>
        </el-tab-pane>

        <el-tab-pane label="字段级规则模板" name="column">
          <div class="template-list">
            <div v-for="template in columnTemplates" :key="template.name" class="template-item">
              <div class="template-header">
                <h4>{{ template.name }}</h4>
                <el-tag type="success">字段级</el-tag>
              </div>
              <p class="template-desc">{{ template.description }}</p>
              <div class="template-scenario">
                <strong>适用场景：</strong>{{ template.scenario }}
              </div>
              <div class="template-params">
                <strong>参数说明：</strong>
                <ul>
                  <li v-for="param in template.params" :key="param.name">
                    <code>{{ param.name }}</code>: {{ param.desc }}
                    <span v-if="param.default !== undefined" class="param-default">（默认: {{ param.default }}）</span>
                  </li>
                </ul>
              </div>
              <div class="template-example">
                <strong>示例：</strong>
                <pre>{{ template.example }}</pre>
              </div>
            </div>
          </div>
        </el-tab-pane>

        <el-tab-pane label="使用指南" name="guide">
          <div class="guide-content">
            <h3>如何创建质量规则</h3>
            <ol>
              <li>点击"新建规则"按钮</li>
              <li>填写基本信息：
                <ul>
                  <li><strong>规则名称</strong>：给规则起一个易于识别的名称</li>
                  <li><strong>规则类型</strong>：选择表级或字段级</li>
                  <li><strong>规则模板</strong>：选择合适的检查模板</li>
                </ul>
              </li>
              <li>选择检查对象：
                <ul>
                  <li>从下拉框选择数据库、表名</li>
                  <li>字段级规则需选择字段名</li>
                </ul>
              </li>
              <li>配置规则参数：
                <ul>
                  <li>根据选择的模板，系统会自动显示可配置的参数</li>
                  <li>填写参数值（如阈值、范围等）</li>
                </ul>
              </li>
              <li>点击"确定"保存规则</li>
            </ol>

            <h3>规则执行方式</h3>
            <ul>
              <li><strong>手动执行</strong>：在规则列表中点击"SQL"按钮预览，手动触发检查</li>
              <li><strong>定时执行</strong>：配置Cron表达式，系统按计划自动执行检查</li>
              <li><strong>实时执行</strong>：数据变更时自动触发检查（待实现）</li>
            </ul>

            <h3>常见使用场景</h3>
            <div class="scenario-box">
              <h4>1. 数据完整性检查</h4>
              <p>使用 <code>NULL_CHECK</code> 模板检查关键字段的空值率</p>
              <p>例如：用户表的邮箱字段空值率不超过5%</p>
            </div>

            <div class="scenario-box">
              <h4>2. 数据唯一性检查</h4>
              <p>使用 <code>UNIQUE_CHECK</code> 模板确保唯一字段不重复</p>
              <p>例如：用户ID、订单号等唯一标识字段</p>
            </div>

            <div class="scenario-box">
              <h4>3. 数据合理性检查</h4>
              <p>使用 <code>NUMERIC_RANGE_CHECK</code> 或 <code>DATE_RANGE_CHECK</code> 检查数据范围</p>
              <p>例如：年龄在0-120之间，注册日期不能晚于当前日期</p>
            </div>

            <div class="scenario-box">
              <h4>4. 数据格式检查</h4>
              <p>使用 <code>REGEX_CHECK</code> 模板验证数据格式</p>
              <p>例如：邮箱、手机号、身份证号等格式验证</p>
            </div>

            <div class="scenario-box">
              <h4>5. 数据量监控</h4>
              <p>使用 <code>ROW_COUNT_CHECK</code> 或 <code>ROW_COUNT_FLUCTUATION</code> 监控表数据量</p>
              <p>例如：每日订单量、用户增长量等数据异常检测</p>
            </div>

            <h3>查看详细文档</h3>
            <p>完整的规则模板说明文档请参考：<code>RULE_TEMPLATES_GUIDE.md</code></p>
          </div>
        </el-tab-pane>
      </el-tabs>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, QuestionFilled, DocumentCopy, MagicStick } from '@element-plus/icons-vue'
import { qualityRuleApi } from '../api/quality'

const loading = ref(false)
const rules = ref([])
const total = ref(0)
const templates = ref([])

// 下拉框数据
const databases = ref([])
const tables = ref([])
const columns = ref([])
const databaseLoading = ref(false)
const tableLoading = ref(false)
const columnLoading = ref(false)

const queryParams = ref({
  ruleName: '',
  ruleType: '',
  ruleTemplate: '',
  enabled: null,
  pageNum: 1,
  pageSize: 20
})

const dialogVisible = ref(false)
const dialogTitle = ref('新建规则')
const submitLoading = ref(false)
const formRef = ref(null)
const isEdit = ref(false)

const form = ref({
  id: null,
  ruleName: '',
  ruleType: 'COLUMN',
  ruleTemplate: '',
  database: '',
  tableName: '',
  columnName: '',
  ruleParams: {},
  description: ''
})

const formRules = {
  ruleName: [{ required: true, message: '请输入规则名称', trigger: 'blur' }],
  ruleType: [{ required: true, message: '请选择规则类型', trigger: 'change' }],
  ruleTemplate: [{ required: true, message: '请选择规则模板', trigger: 'change' }],
  database: [{ required: true, message: '请输入数据库名', trigger: 'blur' }],
  tableName: [{ required: true, message: '请输入表名', trigger: 'blur' }],
  columnName: [
    { required: true, message: '请输入字段名', trigger: 'blur' }
  ]
}

const sqlDialogVisible = ref(false)
const currentSql = ref('')
const sqlCopied = ref(false)
const currentRuleInfo = ref({
  id: '',
  ruleName: '',
  templateName: '',
  checkType: ''
})

// 格式化SQL
const formattedSql = computed(() => {
  if (!currentSql.value) {
    return '-- 点击SQL按钮生成检查语句'
  }

  let sql = currentSql.value

  // SQL格式化逻辑
  if (sql.includes('SELECT') && sql.includes('FROM')) {
    // 按关键字分割并换行
    const keywords = ['SELECT', 'FROM', 'WHERE', 'AND', 'OR', 'GROUP BY', 'ORDER BY', 'HAVING', 'LIMIT']
    let formatted = sql

    // 在关键字前添加换行和缩进
    keywords.forEach(keyword => {
      const regex = new RegExp(`\\s+${keyword}\\s+`, 'gi')
      formatted = formatted.replace(regex, `\n  ${keyword} `)
    })

    // 修复SELECT后的换行
    formatted = formatted.replace(/\n\s+SELECT\s+/, '\nSELECT\n  ')

    // 清理开头的换行
    formatted = formatted.replace(/^\n+/, '')

    return formatted
  }

  return sql
})

// SQL语法高亮
const highlightSql = (sql) => {
  if (!sql) {
    return '-- 点击SQL按钮生成检查语句'
  }

  // SQL关键字列表
  const keywords = [
    'SELECT', 'FROM', 'WHERE', 'AND', 'OR', 'NOT', 'IN', 'LIKE', 'BETWEEN',
    'JOIN', 'LEFT', 'RIGHT', 'INNER', 'OUTER', 'ON', 'AS', 'ORDER', 'BY',
    'GROUP', 'HAVING', 'LIMIT', 'OFFSET', 'UNION', 'ALL', 'DISTINCT',
    'COUNT', 'SUM', 'AVG', 'MAX', 'MIN', 'CASE', 'WHEN', 'THEN', 'ELSE', 'END',
    'IF', 'IFNULL', 'NULL', 'IS', 'NOT NULL', 'EXISTS', 'ASC', 'DESC'
  ]

  // SQL函数列表
  const functions = [
    'CAST', 'CONVERT', 'COALESCE', 'CONCAT', 'SUBSTRING', 'TRIM',
    'UPPER', 'LOWER', 'LENGTH', 'ABS', 'ROUND', 'CEIL', 'FLOOR'
  ]

  let formatted = sql

  // 高亮关键字
  keywords.forEach(keyword => {
    const regex = new RegExp(`\\b${keyword}\\b`, 'gi')
    formatted = formatted.replace(regex, `<span class="sql-keyword">${keyword}</span>`)
  })

  // 高亮函数
  functions.forEach(func => {
    const regex = new RegExp(`\\b${func}\\b`, 'gi')
    formatted = formatted.replace(regex, `<span class="sql-function">${func}</span>`)
  })

  // 高亮字符串
  formatted = formatted.replace(/'([^']*)'/g, `<span class="sql-string">'$1'</span>`)

  // 高亮数字
  formatted = formatted.replace(/\b(\d+)\b/g, `<span class="sql-number">$1</span>`)

  // 高亮表名和字段名（点号分隔的）
  formatted = formatted.replace(/([a-zA-Z_][a-zA-Z0-9_]*)\.([a-zA-Z_][a-zA-Z0-9_]*)/g, `<span class="sql-identifier">$1.$2</span>`)

  return formatted
}

// 规则模板说明
const templateHelpVisible = ref(false)
const activeTemplateTab = ref('table')

// 表级规则模板数据
const tableTemplates = ref([
  {
    name: 'ROW_COUNT_CHECK',
    description: '检查表的行数是否在指定范围内',
    scenario: '监控数据量是否异常（突然减少或增长），确保数据采集正常',
    params: [
      { name: 'minRows', desc: '最小行数', default: '0' },
      { name: 'maxRows', desc: '最大行数', default: '无限制' }
    ],
    example: `示例：订单表行数监控
规则参数：
  minRows: 1000
  maxRows: 1000000

说明：订单表的行数应在1000到1000000之间`
  },
  {
    name: 'ROW_COUNT_FLUCTUATION',
    description: '检查表行数相对于历史基线的波动幅度',
    scenario: '检测数据量异常波动，发现数据缺失或激增问题',
    params: [
      { name: 'threshold', desc: '波动阈值（0-1之间的小数，如0.1表示10%）', default: '0.1' },
      { name: 'baselineDays', desc: '基线天数（用于计算历史平均值）', default: '7' }
    ],
    example: `示例：每日交易数据波动检查
规则参数：
  threshold: 0.1
  baselineDays: 7

说明：今日交易量相对于过去7天平均值的波动不超过10%`
  }
])

// 字段级规则模板数据
const columnTemplates = ref([
  {
    name: 'NULL_CHECK',
    description: '检查字段的空值比例是否超过阈值',
    scenario: '确保关键字段的数据完整性，如用户邮箱、手机号等',
    params: [
      { name: 'maxNullRatio', desc: '最大空值比例（0-1之间的小数）', default: '0.05' }
    ],
    example: `示例：用户邮箱空值检查
规则参数：
  maxNullRatio: 0.05

说明：email字段的空值率不超过5%`
  },
  {
    name: 'UNIQUE_CHECK',
    description: '检查字段值的重复率是否超过阈值',
    scenario: '确保唯一字段（如用户ID、订单号）的唯一性',
    params: [
      { name: 'maxDuplicateRatio', desc: '最大重复比例（0-1之间的小数）', default: '0.0001' }
    ],
    example: `示例：用户ID唯一性检查
规则参数：
  maxDuplicateRatio: 0.0001

说明：user_id字段的重复率不超过0.01%`
  },
  {
    name: 'ENUM_CHECK',
    description: '检查字段值是否在指定的枚举列表中',
    scenario: '验证状态字段、类型字段等枚举类型字段的有效性',
    params: [
      { name: 'allowedValues', desc: '允许的值列表（逗号分隔的字符串）', default: '无' }
    ],
    example: `示例：订单状态枚举检查
规则参数：
  allowedValues: PENDING,PAID,SHIPPED,COMPLETED,CANCELLED

说明：status字段的值必须是这5个值之一`
  },
  {
    name: 'REGEX_CHECK',
    description: '检查字段值是否符合正则表达式模式',
    scenario: '验证数据格式，如邮箱、手机号、身份证号等',
    params: [
      { name: 'pattern', desc: '正则表达式模式', default: '无' }
    ],
    example: `示例：邮箱格式检查
规则参数：
  pattern: ^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$

说明：email字段必须符合标准邮箱格式`
  },
  {
    name: 'NUMERIC_RANGE_CHECK',
    description: '检查数值字段是否在指定范围内',
    scenario: '确保数值字段的合理性，如年龄、金额、分数等',
    params: [
      { name: 'minValue', desc: '最小值', default: '无限制' },
      { name: 'maxValue', desc: '最大值', default: '无限制' }
    ],
    example: `示例：用户年龄范围检查
规则参数：
  minValue: 0
  maxValue: 120

说明：age字段的值应在0到120之间`
  },
  {
    name: 'DATE_RANGE_CHECK',
    description: '检查日期字段是否在指定范围内',
    scenario: '确保日期字段的合理性，如生日、交易时间等',
    params: [
      { name: 'minDate', desc: '最小日期（格式：YYYY-MM-DD）', default: '无限制' },
      { name: 'maxDate', desc: '最大日期（格式：YYYY-MM-DD）', default: '无限制' }
    ],
    example: `示例：注册日期范围检查
规则参数：
  minDate: 2020-01-01
  maxDate: 当前日期

说明：注册日期应在2020年1月1日到当前日期之间`
  },
  {
    name: 'BUSINESS_RULE',
    description: '自定义SQL检查规则',
    scenario: '复杂业务逻辑检查，如跨表关联、复杂计算等',
    params: [
      { name: 'customSql', desc: '自定义SQL查询语句', default: '无' }
    ],
    example: `示例：订单金额业务规则检查
规则参数：
  customSql: SELECT COUNT(*) FROM orders WHERE amount <= 0

说明：检查订单表中金额小于等于0的记录数`
  }
])

// 加载规则模板
const loadTemplates = async () => {
  try {
    const { data } = await qualityRuleApi.getTemplates()
    templates.value = data
  } catch (error) {
    console.error('加载模板失败:', error)
  }
}

// 加载规则列表
const loadRules = async () => {
  loading.value = true
  try {
    const { data } = await qualityRuleApi.getList(queryParams.value)
    // IPage对象返回records和total
    rules.value = data.records || []
    total.value = data.total || 0
  } catch (error) {
    ElMessage.error('加载规则列表失败')
    console.error(error)
  } finally {
    loading.value = false
  }
}

// 打开创建对话框
const openCreateDialog = () => {
  isEdit.value = false
  dialogTitle.value = '新建规则'
  dialogVisible.value = true
}

// 打开编辑对话框
const openEditDialog = async (row) => {
  isEdit.value = true
  dialogTitle.value = '编辑规则'

  console.log('=== 编辑规则调试信息 ===')
  console.log('原始 row 数据:', row)

  // 处理ruleParams，确保是对象而不是字符串
  let params = {}
  if (typeof row.ruleParams === 'string') {
    try {
      params = JSON.parse(row.ruleParams)
    } catch (e) {
      console.error('解析ruleParams失败', e)
      params = {}
    }
  } else if (typeof row.ruleParams === 'object' && row.ruleParams !== null) {
    params = { ...row.ruleParams }
  }

  // 保存原有的表名和字段名
  const originalTableName = row.tableName
  const originalColumnName = row.columnName

  form.value = {
    id: row.id,
    ruleName: row.ruleName,
    ruleType: row.ruleType,
    ruleTemplate: row.ruleTemplate,
    database: row.database,
    tableName: row.tableName,
    columnName: row.columnName,
    ruleParams: params,
    description: row.description || ''
  }

  console.log('设置 form.value:', form.value)

  dialogVisible.value = true

  // 加载该数据库下的表列表
  if (row.database) {
    await loadTablesForDatabase(row.database)
    // 恢复表名
    if (originalTableName) {
      form.value.tableName = originalTableName
    }
  }

  // 如果是字段级规则，加载字段列表
  if (row.ruleType === 'COLUMN' && originalTableName) {
    await loadColumnsForTable(row.database, originalTableName)
    // 恢复字段名
    if (originalColumnName) {
      form.value.columnName = originalColumnName
    }
  }

  console.log('最终 form.value:', form.value)
  console.log('=====================')
}

// 模板变更
const onTemplateChange = (template) => {
  // 根据模板设置默认参数
  const paramDefaults = {
    'NULL_CHECK': { maxNullRatio: 0.05 },
    'ROW_COUNT_CHECK': { minRows: 1 },
    'ROW_COUNT_FLUCTUATION': { threshold: 0.1 },
    'UNIQUE_CHECK': { maxDuplicateRatio: 0.01 },
    'ENUM_CHECK': { enumValues: '' },
    'REGEX_CHECK': { pattern: '^.*$' },
    'NUMERIC_RANGE_CHECK': { minValue: 0, maxValue: 100 },
    'DATE_RANGE_CHECK': { minDate: '', maxDate: '' },
    'BUSINESS_RULE': { customSql: '' }
  }
  form.value.ruleParams = { ...paramDefaults[template] } || {}
}

// 提交表单
const submitForm = async () => {
  await formRef.value.validate()
  submitLoading.value = true

  console.log('=== 提交表单 ===')
  console.log('操作类型:', isEdit.value ? '更新' : '创建')
  console.log('表单数据:', JSON.stringify(form.value, null, 2))
  console.log('ruleParams类型:', typeof form.value.ruleParams)
  console.log('ruleParams值:', form.value.ruleParams)
  console.log('===============')

  try {
    if (isEdit.value) {
      await qualityRuleApi.update(form.value.id, form.value)
      ElMessage.success('规则更新成功')
    } else {
      await qualityRuleApi.create(form.value)
      ElMessage.success('规则创建成功')
    }
    dialogVisible.value = false
    loadRules()
  } catch (error) {
    ElMessage.error(isEdit.value ? '更新失败' : '创建失败')
    console.error(error)
  } finally {
    submitLoading.value = false
  }
}

// 重置表单
const resetForm = () => {
  formRef.value?.resetFields()
  form.value = {
    id: null,
    ruleName: '',
    ruleType: 'COLUMN',
    ruleTemplate: '',
    database: '',
    tableName: '',
    columnName: '',
    ruleParams: {},
    description: ''
  }
}

// 重置查询
const resetQuery = () => {
  queryParams.value = {
    ruleName: '',
    ruleType: '',
    ruleTemplate: '',
    enabled: null,
    pageNum: 1,
    pageSize: 20
  }
  loadRules()
}

// 切换启用状态
const toggleRuleEnabled = async (row) => {
  row.toggleLoading = true
  try {
    if (row.enabled) {
      await qualityRuleApi.enable(row.id)
    } else {
      await qualityRuleApi.disable(row.id)
    }
    ElMessage.success(row.enabled ? '规则已启用' : '规则已禁用')
  } catch (error) {
    ElMessage.error('操作失败')
    row.enabled = !row.enabled
    console.error(error)
  } finally {
    row.toggleLoading = false
  }
}

// 复制规则
const copyRule = async (row) => {
  try {
    const newRuleName = `${row.ruleName}_副本`
    await qualityRuleApi.copy(row.id, newRuleName)
    ElMessage.success('规则复制成功')
    loadRules()
  } catch (error) {
    ElMessage.error('复制失败')
    console.error(error)
  }
}

// 删除规则
const deleteRule = async (row) => {
  try {
    await ElMessageBox.confirm('确定要删除该规则吗？', '提示', {
      type: 'warning'
    })
    await qualityRuleApi.delete(row.id)
    ElMessage.success('删除成功')
    loadRules()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('删除失败')
      console.error(error)
    }
  }
}

// 查看规则SQL
const viewRuleSql = async (row) => {
  try {
    const { data } = await qualityRuleApi.testRule(row.id)
    currentSql.value = data.checkSql || '无法生成SQL'

    // 保存规则信息
    currentRuleInfo.value = {
      id: row.id,
      ruleName: row.ruleName,
      templateName: data.templateName || row.ruleTemplate,
      checkType: data.checkType || 'MANUAL'
    }

    sqlDialogVisible.value = true
    sqlCopied.value = false
  } catch (error) {
    ElMessage.error('生成SQL失败')
    console.error(error)
  }
}

// 复制SQL
const copySql = () => {
  if (!currentSql.value) {
    ElMessage.warning('没有可复制的SQL')
    return
  }

  navigator.clipboard.writeText(currentSql.value).then(() => {
    sqlCopied.value = true
    ElMessage.success('SQL已复制到剪贴板')

    // 2秒后重置复制状态
    setTimeout(() => {
      sqlCopied.value = false
    }, 2000)
  }).catch(err => {
    ElMessage.error('复制失败: ' + err.message)
  })
}

// 格式化SQL
const formatSql = () => {
  if (!currentSql.value) {
    ElMessage.warning('没有可格式化的SQL')
    return
  }

  // 使用sqlFormatter库或简单的格式化逻辑
  let sql = currentSql.value

  // 简单的SQL格式化
  sql = sql
    .replace(/\s+/g, ' ') // 压缩空格
    .replace(/\s*,\s*/g, ',\n  ') // 逗号后换行
    .replace(/\bSELECT\b/gi, '\nSELECT')
    .replace(/\bFROM\b/gi, '\nFROM')
    .replace(/\bWHERE\b/gi, '\nWHERE')
    .replace(/\bAND\b/gi, '\n  AND')
    .replace(/\bOR\b/gi, '\n  OR')
    .replace(/\bLEFT JOIN\b/gi, '\nLEFT JOIN')
    .replace(/\bRIGHT JOIN\b/gi, '\nRIGHT JOIN')
    .replace(/\bINNER JOIN\b/gi, '\nINNER JOIN')
    .replace(/\bGROUP BY\b/gi, '\nGROUP BY')
    .replace(/\bORDER BY\b/gi, '\nORDER BY')
    .replace(/\bLIMIT\b/gi, '\nLIMIT')
    .replace(/^\n+/, '') // 移除开头的换行
    .replace(/\n\s+\n/g, '\n') // 移除多余的空行

  currentSql.value = sql
  ElMessage.success('SQL格式化完成')
}

// 获取模板描述
const getTemplateDesc = (template) => {
  const descs = {
    'NULL_CHECK': '空值检查',
    'ROW_COUNT_CHECK': '行数检查',
    'ROW_COUNT_FLUCTUATION': '行数波动检查',
    'UNIQUE_CHECK': '唯一性检查',
    'ENUM_CHECK': '枚举值检查',
    'REGEX_CHECK': '正则匹配检查',
    'NUMERIC_RANGE_CHECK': '数值范围检查',
    'DATE_RANGE_CHECK': '日期范围检查',
    'BUSINESS_RULE': '业务规则检查'
  }
  return descs[template] || template
}

// 加载数据库列表
const loadDatabases = async () => {
  databaseLoading.value = true
  try {
    const { data } = await qualityRuleApi.getDatabases()
    databases.value = data || []
  } catch (error) {
    console.error('加载数据库列表失败:', error)
    ElMessage.error('加载数据库列表失败')
  } finally {
    databaseLoading.value = false
  }
}

// 数据库改变时加载表列表
const onDatabaseChange = async (database) => {
  // 清空表和字段选择
  form.value.tableName = ''
  form.value.columnName = ''
  tables.value = []
  columns.value = []

  if (!database) {
    return
  }

  tableLoading.value = true
  try {
    const { data } = await qualityRuleApi.getTables(database)
    tables.value = data || []
  } catch (error) {
    console.error('加载表列表失败:', error)
    ElMessage.error('加载表列表失败')
  } finally {
    tableLoading.value = false
  }
}

// 表改变时加载字段列表
const onTableChange = async (tableName) => {
  // 清空字段选择
  form.value.columnName = ''
  columns.value = []

  if (!form.value.database || !tableName) {
    return
  }

  columnLoading.value = true
  try {
    const { data } = await qualityRuleApi.getColumns(form.value.database, tableName)
    columns.value = data || []
  } catch (error) {
    console.error('加载字段列表失败:', error)
    ElMessage.warning('加载字段列表失败，请手动输入字段名')
  } finally {
    columnLoading.value = false
  }
}

// 加载指定数据库的表列表（不清空表单）
const loadTablesForDatabase = async (database) => {
  if (!database) {
    return
  }

  tableLoading.value = true
  try {
    const { data } = await qualityRuleApi.getTables(database)
    tables.value = data || []
  } catch (error) {
    console.error('加载表列表失败:', error)
    ElMessage.error('加载表列表失败')
  } finally {
    tableLoading.value = false
  }
}

// 加载指定表的字段列表（不清空表单）
const loadColumnsForTable = async (database, tableName) => {
  if (!database || !tableName) {
    return
  }

  columnLoading.value = true
  try {
    const { data } = await qualityRuleApi.getColumns(database, tableName)
    columns.value = data || []
  } catch (error) {
    console.error('加载字段列表失败:', error)
    ElMessage.warning('加载字段列表失败，请手动输入字段名')
  } finally {
    columnLoading.value = false
  }
}

onMounted(() => {
  loadTemplates()
  loadRules()
  loadDatabases() // 加载数据库列表
})

// 显示规则模板说明
const showTemplateHelp = () => {
  templateHelpVisible.value = true
  activeTemplateTab.value = 'table' // 默认显示表级规则
}
</script>

<style scoped>
.quality-rules-page {
  padding: 0;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 20px;
}

.page-header h2 {
  margin: 0 0 8px 0;
  font-size: 20px;
  font-weight: 600;
}

.page-subtitle {
  margin: 0;
  font-size: 14px;
  color: #909399;
}

.header-actions {
  display: flex;
  gap: 12px;
}

.filter-card {
  margin-bottom: 16px;
}

.filter-form {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.filter-form .el-form-item {
  margin-bottom: 12px;
  margin-right: 8px;
}

.param-item {
  display: flex;
  align-items: center;
  margin-bottom: 8px;
}

.param-label {
  width: 120px;
  font-size: 14px;
  color: #606266;
}

/* SQL预览对话框样式 */
.sql-preview-dialog .sql-preview-container {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.sql-preview-header {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  padding-bottom: 12px;
  border-bottom: 1px solid #e4e7ed;
}

.sql-preview-content {
  background-color: #282c34;
  border-radius: 6px;
  padding: 16px;
  max-height: 400px;
  overflow: auto;
}

.sql-code-block {
  margin: 0;
  padding: 0;
  font-family: 'Monaco', 'Menlo', 'Ubuntu Mono', 'Consolas', 'source-code-pro', monospace;
  font-size: 13px;
  line-height: 1.6;
  color: #abb2bf;
  white-space: pre-wrap;
  word-wrap: break-word;
}

.sql-code-block code {
  font-family: inherit;
}

/* SQL语法高亮 */
:deep(.sql-keyword) {
  color: #c678dd;
  font-weight: bold;
}

:deep(.sql-function) {
  color: #61afef;
}

:deep(.sql-string) {
  color: #98c379;
}

:deep(.sql-number) {
  color: #d19a66;
}

:deep(.sql-comment) {
  color: #5c6370;
  font-style: italic;
}

:deep(.sql-identifier) {
  color: #e06c75;
}

.sql-preview-info {
  margin-top: 8px;
}

/* 规则模板说明对话框样式 */
.template-help-dialog .template-list {
  max-height: 600px;
  overflow-y: auto;
}

.template-item {
  padding: 20px;
  margin-bottom: 20px;
  border: 1px solid #e4e7ed;
  border-radius: 4px;
  background-color: #fafafa;
}

.template-item:last-child {
  margin-bottom: 0;
}

.template-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}

.template-header h4 {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
  color: #303133;
}

.template-desc {
  margin: 8px 0;
  font-size: 14px;
  color: #606266;
}

.template-scenario {
  margin: 12px 0;
  padding: 12px;
  background-color: #e6f7ff;
  border-left: 3px solid #409eff;
  border-radius: 4px;
  font-size: 14px;
}

.template-scenario strong {
  color: #409eff;
}

.template-params {
  margin: 12px 0;
  padding: 12px;
  background-color: #f0f9ff;
  border-radius: 4px;
}

.template-params strong {
  display: block;
  margin-bottom: 8px;
  color: #303133;
}

.template-params ul {
  margin: 0;
  padding-left: 20px;
}

.template-params li {
  margin: 6px 0;
  font-size: 14px;
  color: #606266;
}

.template-params code {
  padding: 2px 6px;
  background-color: #f5f7fa;
  border: 1px solid #dcdfe6;
  border-radius: 3px;
  color: #e6a23c;
  font-family: 'Courier New', monospace;
}

.param-default {
  color: #909399;
  font-size: 13px;
}

.template-example {
  margin: 12px 0;
  padding: 12px;
  background-color: #f6f8fa;
  border-radius: 4px;
}

.template-example strong {
  display: block;
  margin-bottom: 8px;
  color: #303133;
}

.template-example pre {
  margin: 0;
  padding: 12px;
  background-color: #282c34;
  color: #abb2bf;
  border-radius: 4px;
  font-size: 13px;
  line-height: 1.6;
  overflow-x: auto;
}

/* 使用指南样式 */
.guide-content {
  padding: 0 20px;
}

.guide-content h3 {
  margin-top: 0;
  margin-bottom: 20px;
  font-size: 18px;
  color: #303133;
  border-bottom: 2px solid #409eff;
  padding-bottom: 10px;
}

.guide-content h4 {
  margin-top: 20px;
  margin-bottom: 10px;
  font-size: 16px;
  color: #606266;
}

.guide-content ol,
.guide-content ul {
  margin: 12px 0;
  padding-left: 24px;
}

.guide-content li {
  margin: 8px 0;
  line-height: 1.6;
}

.guide-content code {
  padding: 2px 6px;
  background-color: #f5f7fa;
  border: 1px solid #dcdfe6;
  border-radius: 3px;
  color: #e6a23c;
  font-family: 'Courier New', monospace;
}

.scenario-box {
  margin: 20px 0;
  padding: 16px;
  background-color: #f0f9ff;
  border-left: 4px solid #409eff;
  border-radius: 4px;
}

.scenario-box h4 {
  margin: 0 0 8px 0;
  color: #409eff;
}

.scenario-box p {
  margin: 4px 0;
  color: #606266;
}
</style>
