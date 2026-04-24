<template>
  <div class="report-edit-page">
    <div class="page-header">
      <h2>{{ isEdit ? '编辑报表' : '新建报表' }}</h2>
      <div>
        <el-button @click="handleCancel">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleSave">保存</el-button>
      </div>
    </div>

    <el-row :gutter="20">
      <el-col :span="16">
        <el-card>
          <el-form :model="form" label-width="100px" :rules="rules" ref="formRef">
            <el-form-item label="报表名称" prop="name">
              <el-input v-model="form.name" placeholder="请输入报表名称" maxlength="128" show-word-limit />
            </el-form-item>
            <el-form-item label="描述">
              <el-input
                v-model="form.description"
                type="textarea"
                :rows="2"
                placeholder="报表描述（可选）"
                maxlength="512"
                show-word-limit
              />
            </el-form-item>
            <el-form-item label="状态">
              <el-radio-group v-model="form.status">
                <el-radio :label="1">启用</el-radio>
                <el-radio :label="0">停用</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-form>

          <el-divider content-position="left">图表配置</el-divider>

          <div class="charts-container">
            <div v-for="(chart, index) in charts" :key="chart.id || chart.tempId" class="chart-item">
              <el-card class="chart-card">
                <template #header>
                  <div class="chart-header">
                    <div class="chart-header-left">
                      <span class="chart-number">图表 {{ index + 1 }}</span>
                      <el-tag :type="getChartTypeTagClass(chart.chartType)" size="small">
                        {{ getChartTypeLabel(chart.chartType) }}
                      </el-tag>
                    </div>
                    <div class="chart-actions">
                      <el-button
                        type="primary"
                        size="small"
                        link
                        @click="moveUp(index)"
                        :disabled="index === 0"
                      >
                        上移
                      </el-button>
                      <el-button
                        type="primary"
                        size="small"
                        link
                        @click="moveDown(index)"
                        :disabled="index === charts.length - 1"
                      >
                        下移
                      </el-button>
                      <el-button
                        type="danger"
                        size="small"
                        link
                        @click="removeChart(index)"
                        :disabled="charts.length === 1"
                      >
                        删除
                      </el-button>
                    </div>
                  </div>
                </template>
                <el-form :model="chart" label-width="100px" :rules="chartRules" :ref="el => setChartFormRef(chart, el)">
                  <el-form-item label="SQL 语句" prop="sqlContent">
                    <el-input
                      v-model="chart.sqlContent"
                      type="textarea"
                      :rows="6"
                      placeholder="请输入此图表的 SELECT 查询语句"
                    />
                  </el-form-item>
                  <el-form-item>
                    <el-button
                      type="primary"
                      size="small"
                      @click="testChart(chart)"
                      :loading="chart.testing"
                    >
                      <el-icon><VideoPlay /></el-icon>
                      测试执行
                    </el-button>
                    <el-button
                      v-if="chart.previewResult"
                      type="success"
                      size="small"
                      @click="autoFillFields(chart)"
                    >
                      <el-icon><MagicStick /></el-icon>
                      自动填充字段
                    </el-button>
                  </el-form-item>
                  <el-row :gutter="10">
                    <el-col :span="12">
                      <el-form-item label="图表标题" prop="title">
                        <el-input v-model="chart.title" placeholder="请输入图表标题" />
                      </el-form-item>
                    </el-col>
                    <el-col :span="12">
                      <el-form-item label="图表类型" prop="chartType">
                        <el-select v-model="chart.chartType" placeholder="请选择图表类型">
                          <el-option label="📈 折线图" value="line" />
                          <el-option label="📊 柱状图" value="bar" />
                          <el-option label="🥧 饼图" value="pie" />
                          <el-option label="📋 表格" value="table" />
                        </el-select>
                      </el-form-item>
                    </el-col>
                  </el-row>
                  <el-form-item label="图表说明" prop="chartDescription">
                    <el-input
                      v-model="chart.chartDescription"
                      type="textarea"
                      :rows="2"
                      placeholder="图表说明（可选），将显示在图表卡片头部"
                      maxlength="512"
                      show-word-limit
                    />
                  </el-form-item>
                  <el-row :gutter="10" v-if="chart.chartType !== 'table'">
                    <el-col :span="12">
                      <el-form-item label="X轴字段" :prop="`${chart.id || chart.tempId}.xAxisField`">
                        <el-input v-model="chart.xAxisField" placeholder="日期/分类字段（如：order_date）" />
                      </el-form-item>
                    </el-col>
                    <el-col :span="12">
                      <el-form-item label="X轴标签">
                        <el-input v-model="chart.xAxisLabel" placeholder="X轴显示名称（如：日期）" />
                      </el-form-item>
                    </el-col>
                  </el-row>
                  <el-row :gutter="10" v-if="chart.chartType !== 'table'">
                    <el-col :span="12">
                      <el-form-item label="Y轴字段" :prop="`${chart.id || chart.tempId}.yAxisField`">
                        <el-input v-model="chart.yAxisField" placeholder="数值字段（如：order_count）" />
                      </el-form-item>
                    </el-col>
                    <el-col :span="12">
                      <el-form-item label="Y轴标签">
                        <el-input v-model="chart.yAxisLabel" placeholder="Y轴显示名称（如：订单数）" />
                      </el-form-item>
                    </el-col>
                  </el-row>
                  <el-form-item label="布局宽度" prop="layoutSpan">
                    <el-radio-group v-model="chart.layoutSpan">
                      <el-radio :label="12">全宽 (100%)</el-radio>
                      <el-radio :label="6">半宽 (50%)</el-radio>
                      <el-radio :label="4">三分之一 (33%)</el-radio>
                    </el-radio-group>
                  </el-form-item>

                  <!-- 预览区域 -->
                  <div v-if="chart.previewResult" class="preview-area">
                    <el-divider content-position="left">数据预览</el-divider>
                    <div class="preview-info">
                      <el-tag size="small">行数: {{ chart.previewResult.rowCount }}</el-tag>
                      <el-tag size="small">耗时: {{ chart.previewResult.executionTime }}ms</el-tag>
                    </div>

                    <!-- 表格类型：只显示数据表格 -->
                    <div v-if="chart.chartType === 'table'" class="table-preview-wrapper">
                      <el-table :data="chart.previewResult.rows" size="small" max-height="400" stripe>
                        <el-table-column
                          v-for="col in chart.previewResult.columns"
                          :key="col"
                          :prop="col"
                          :label="col"
                          min-width="120"
                          show-overflow-tooltip
                        />
                      </el-table>
                    </div>

                    <!-- 图表类型：显示数据预览表格 + 图表 -->
                    <template v-else>
                      <el-table :data="chart.previewResult.rows" size="small" max-height="200" stripe>
                        <el-table-column
                          v-for="col in chart.previewResult.columns"
                          :key="col"
                          :prop="col"
                          :label="col"
                          min-width="120"
                          show-overflow-tooltip
                        />
                      </el-table>
                      <div v-if="chart.chartType && chart.chartType !== 'table'" class="chart-wrapper">
                        <div :ref="el => setPreviewChartRef(chart, el)" class="chart-container"></div>
                      </div>
                    </template>
                  </div>
                </el-form>
              </el-card>
            </div>

            <el-button type="dashed" @click="addChart" class="add-chart-btn">
              <el-icon><Plus /></el-icon>
              添加图表
            </el-button>
          </div>
        </el-card>
      </el-col>

      <el-col :span="8">
        <el-card>
          <template #header>
            <span>提示</span>
          </template>
          <div class="tips-content">
            <h4>📝 图表配置说明</h4>
            <ul>
              <li>每个图表都需要配置独立的SQL语句</li>
              <li><strong>X轴字段</strong>：日期、时间或分类字段（如：order_date、category）</li>
              <li><strong>Y轴字段</strong>：数值字段（如：order_count、amount）</li>
              <li>点击「测试执行」预览数据</li>
              <li>点击「自动填充字段」智能识别X/Y轴字段</li>
            </ul>
            <h4>🎨 混合图表类型</h4>
            <ul>
              <li><strong>支持混合</strong>：一个报表可以包含不同类型的图表（折线图+柱状图+饼图）</li>
              <li><strong>灵活配置</strong>：每个图表独立选择图表类型，互不影响</li>
              <li><strong>布局调整</strong>：可设置每个图表的宽度（全宽/半宽/三分之一）</li>
            </ul>
            <h4>💡 最佳实践</h4>
            <ul>
              <li>SQL查询结果建议不超过5000行</li>
              <li>使用WHERE条件限制数据范围</li>
              <li>数值字段用于Y轴，分类/日期字段用于X轴</li>
              <li>为每个图表添加说明文字，便于理解</li>
            </ul>
          </div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, watch, onUnmounted, toRaw, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Plus, VideoPlay, MagicStick } from '@element-plus/icons-vue'
import * as echarts from 'echarts'
import request from '../utils/request.js'

const route = useRoute()
const router = useRouter()
const formRef = ref(null)
const saving = ref(false)

const isEdit = ref(false)
const reportId = ref(null)

// 表单数据
const form = reactive({
  name: '',
  description: '',
  status: 1
})

// 图表配置列表
const charts = ref([])
const chartFormRefs = new Map()
const previewChartInstances = new Map()
let tempIdCounter = 0

const rules = {
  name: [{ required: true, message: '请输入报表名称', trigger: 'blur' }]
}

const chartRules = {
  title: [{ required: true, message: '请输入图表标题', trigger: 'blur' }],
  sqlContent: [{ required: true, message: '请输入SQL语句', trigger: 'blur' }],
  chartType: [{ required: true, message: '请选择图表类型', trigger: 'change' }]
}

// 获取图表类型标签
function getChartTypeLabel(type) {
  const typeMap = {
    line: '折线图',
    bar: '柱状图',
    pie: '饼图',
    table: '表格'
  }
  return typeMap[type] || type
}

// 获取图表类型标签颜色
function getChartTypeTagClass(type) {
  const typeMap = {
    line: '',
    bar: 'warning',
    pie: 'success',
    table: 'info'
  }
  return typeMap[type] || ''
}

// 设置图表表单ref
function setChartFormRef(chart, el) {
  if (el) {
    chartFormRefs.set(chart.id || chart.tempId, el)
  }
}

// 设置预览图表ref
function setPreviewChartRef(chart, el) {
  if (el) {
    const existing = previewChartInstances.get(chart.id || chart.tempId) || {}
    previewChartInstances.set(chart.id || chart.tempId, { ...existing, ref: el })
    // 渲染预览图表
    nextTick(() => renderPreviewChart(chart))
  }
}

// 添加图表
function addChart() {
  const newChart = {
    tempId: `temp_${tempIdCounter++}`,
    sqlContent: '',
    chartType: 'line',
    title: `图表 ${charts.value.length + 1}`,
    chartDescription: '',
    xAxisField: '',
    xAxisLabel: '',
    yAxisField: '',
    yAxisLabel: '',
    layoutSpan: 12,
    testing: false
  }
  charts.value.push(newChart)
}

// 删除图表
function removeChart(index) {
  const chart = charts.value[index]
  // 清理预览图表实例
  const chartKey = chart.id || chart.tempId
  if (previewChartInstances.has(chartKey)) {
    const instance = previewChartInstances.get(chartKey).instance
    if (instance) {
      instance.dispose()
    }
    previewChartInstances.delete(chartKey)
  }
  charts.value.splice(index, 1)
}

// 上移图表
function moveUp(index) {
  if (index === 0) return
  const temp = charts.value[index]
  charts.value[index] = charts.value[index - 1]
  charts.value[index - 1] = temp
}

// 下移图表
function moveDown(index) {
  if (index === charts.value.length - 1) return
  const temp = charts.value[index]
  charts.value[index] = charts.value[index + 1]
  charts.value[index + 1] = temp
}

// 测试图表SQL
async function testChart(chart) {
  if (!chart.sqlContent.trim()) {
    ElMessage.warning('请先输入SQL语句')
    return
  }

  chart.testing = true
  try {
    // 使用通用查询接口测试
    const res = await request.post('/query/execute', {
      sql: chart.sqlContent,
      readonly: true
    })
    chart.previewResult = res.data
    ElMessage.success('执行成功')

    // 清除之前的预览图表
    const chartKey = chart.id || chart.tempId
    if (previewChartInstances.has(chartKey)) {
      const instance = previewChartInstances.get(chartKey).instance
      if (instance) {
        instance.dispose()
      }
    }
  } catch (err) {
    ElMessage.error(err.message || '执行失败')
    chart.previewResult = null
  } finally {
    chart.testing = false
  }
}

// 自动填充字段
function autoFillFields(chart) {
  if (!chart.previewResult || !chart.previewResult.columns) {
    ElMessage.warning('请先执行SQL查询')
    return
  }

  const columns = chart.previewResult.columns
  const rows = chart.previewResult.rows || []

  if (rows.length === 0) {
    ElMessage.warning('没有数据可供分析')
    return
  }

  // 智能识别：找到日期/字符串字段作为X轴，数值字段作为Y轴
  let dateField = null
  let numberField = null

  for (const col of columns) {
    const value = rows[0][col]
    if (value === null || value === undefined) continue

    // 检查是否为日期字段
    const lowerCol = col.toLowerCase()
    if ((lowerCol.includes('date') || lowerCol.includes('time') || lowerCol.includes('year')) && !dateField) {
      dateField = col
    }
    // 检查是否为数值字段
    else if (typeof value === 'number' && !numberField) {
      numberField = col
    }
  }

  // 如果没有找到明确的日期字段，取第一个字段作为X轴
  if (!dateField && columns.length > 0) {
    dateField = columns[0]
  }

  // 如果没有找到数值字段，取第二个字段作为Y轴
  if (!numberField && columns.length > 1) {
    numberField = columns[1]
  }

  if (dateField) {
    chart.xAxisField = dateField
    // 自动生成X轴标签（从字段名转换）
    if (!chart.xAxisLabel) {
      chart.xAxisLabel = generateAxisLabel(dateField)
    }
  }
  if (numberField) {
    chart.yAxisField = numberField
    // 自动生成Y轴标签（从字段名转换）
    if (!chart.yAxisLabel) {
      chart.yAxisLabel = generateAxisLabel(numberField)
    }
  }

  if (dateField && numberField) {
    ElMessage.success(`已自动填充：X轴=${dateField}, Y轴=${numberField}`)
  } else {
    ElMessage.warning('无法自动识别字段，请手动配置')
  }
}

// 从字段名生成轴标签
function generateAxisLabel(fieldName) {
  if (!fieldName) return ''

  // 将下划线命名转换为中文显示
  const label = fieldName
    .replace(/_/g, ' ')
    .replace(/([A-Z])/g, ' $1')
    .trim()

  // 简单的映射表
  const commonMappings = {
    'order_date': '订单日期',
    'order_count': '订单数量',
    'order_amount': '订单金额',
    'category': '分类',
    'region': '区域',
    'date': '日期',
    'time': '时间',
    'count': '数量',
    'amount': '金额',
    'price': '价格',
    'total': '总计',
    'avg': '平均值',
    'sum': '总和'
  }

  return commonMappings[fieldName.toLowerCase()] || label
}

// 渲染预览图表
function renderPreviewChart(chart) {
  if (!chart.previewResult || !chart.chartType) return

  // 表格类型不需要渲染图表
  if (chart.chartType === 'table') {
    return
  }

  const chartKey = chart.id || chart.tempId
  const chartRef = previewChartInstances.get(chartKey)?.ref
  if (!chartRef) return

  const xField = chart.xAxisField
  const yField = chart.yAxisField
  const columns = chart.previewResult.columns || []
  const rows = chart.previewResult.rows || []

  if (!xField || !yField) return

  // 查找实际的列名（大小写不敏感）
  const actualXField = columns.find(col => col.toLowerCase() === xField.toLowerCase()) || xField
  const actualYField = columns.find(col => col.toLowerCase() === yField.toLowerCase()) || yField

  if (!columns.includes(actualXField) || !columns.includes(actualYField)) {
    return
  }

  // 获取或创建图表实例
  let instance = previewChartInstances.get(chartKey)?.instance
  if (instance) {
    instance.dispose()
  }
  instance = echarts.init(chartRef)
  previewChartInstances.set(chartKey, { ref: chartRef, instance })

  const xData = rows.map(r => r[actualXField])
  const yData = rows.map(r => r[actualYField])

  let option = {}

  if (chart.chartType === 'pie') {
    option = {
      tooltip: { trigger: 'item' },
      legend: {
        orient: 'vertical',
        left: 'left',
        top: 'center'
      },
      series: [{
        type: 'pie',
        radius: ['40%', '70%'],
        avoidLabelOverlap: false,
        itemStyle: {
          borderRadius: 10,
          borderColor: '#fff',
          borderWidth: 2
        },
        data: rows.map(r => ({ name: r[actualXField], value: r[actualYField] })),
        label: { show: true, formatter: '{b}: {c} ({d}%)' },
        emphasis: {
          itemStyle: {
            shadowBlur: 10,
            shadowOffsetX: 0,
            shadowColor: 'rgba(0, 0, 0, 0.5)'
          }
        }
      }],
      grid: {
        left: '5%',
        right: '5%',
        bottom: '5%',
        top: '5%',
        containLabel: true
      }
    }
  } else {
    option = {
      tooltip: { trigger: 'axis' },
      xAxis: {
        type: 'category',
        data: xData,
        name: chart.xAxisLabel || undefined,
        nameLocation: 'middle',
        nameGap: 35,
        nameTextStyle: {
          fontWeight: 'bold',
          padding: [0, 0, 0, 0]
        },
        axisLabel: {
          rotate: xData.length > 10 ? 45 : 0,
          margin: 12
        }
      },
      yAxis: {
        type: 'value',
        name: chart.yAxisLabel || undefined,
        nameLocation: 'middle',
        nameGap: 55,
        nameTextStyle: {
          fontWeight: 'bold'
        }
      },
      series: [{
        type: chart.chartType,
        data: yData,
        smooth: chart.chartType === 'line',
        itemStyle: {
          color: chart.chartType === 'bar' ? '#409EFF' : undefined
        }
      }],
      grid: {
        left: '8%',
        right: '5%',
        bottom: '15%',
        top: '10%',
        containLabel: true
      }
    }
  }

  try {
    instance.setOption(option)
  } catch (err) {
    console.error('预览图表渲染失败:', err)
  }
}

// 加载报表信息
async function loadReport() {
  if (!reportId.value) return
  try {
    const res = await request.get(`/report/${reportId.value}`)
    const data = res.data

    form.name = data.name || ''
    form.description = data.description || ''
    form.status = data.status ?? 1

    // 加载图表配置
    const chartsRes = await request.get(`/report/${reportId.value}/charts`)
    if (chartsRes.data && chartsRes.data.length > 0) {
      charts.value = chartsRes.data.map(chart => ({
        ...chart,
        testing: false,
        previewResult: null
      }))
    }
  } catch (err) {
    ElMessage.error(err.message || '加载报表失败')
  }
}

async function handleSave() {
  // 验证报表表单
  const formValid = await formRef.value.validate().catch(() => false)
  if (!formValid) return

  // 验证所有图表表单
  for (const chart of charts.value) {
    const chartFormRef = chartFormRefs.get(chart.id || chart.tempId)

    // 动态设置验证规则
    if (chartFormRef) {
      // 清除旧的规则
      chartFormRef.clearValidate()

      // 根据图表类型设置规则
      if (chart.chartType !== 'table') {
        chartFormRef.validateField(`${chart.id || chart.tempId}.xAxisField`, () => !!chart.xAxisField)
        chartFormRef.validateField(`${chart.id || chart.tempId}.yAxisField`, () => !!chart.yAxisField)
      }
    }

    // 表格类型不需要验证轴字段
    if (chart.chartType === 'table') {
      if (!chart.title || !chart.sqlContent) {
        ElMessage.warning(`请完善「${chart.title}」的配置（需要标题和SQL）`)
        return
      }
    } else {
      // 其他类型需要完整验证
      if (chartFormRef) {
        const valid = await chartFormRef.validate().catch(() => false)
        if (!valid) {
          ElMessage.warning(`请完善「${chart.title}」的配置`)
          return
        }
      }
    }
  }

  saving.value = true

  try {
    const dataToSend = toRaw(form)

    if (isEdit.value) {
      await request.put(`/report/${reportId.value}`, dataToSend)
      // 批量保存图表配置
      await request.put(`/report/${reportId.value}/charts/batch`, charts.value)
    } else {
      const createRes = await request.post('/report', dataToSend)
      if (!createRes.data || !createRes.data.id) {
        throw new Error('创建报表失败：未返回报表ID')
      }
      const newReportId = createRes.data.id
      // 批量保存图表配置
      await request.put(`/report/${newReportId}/charts/batch`, charts.value)
    }

    ElMessage.success('保存成功')
    router.push('/report')
  } catch (err) {
    console.error('保存报表失败:', err)
    ElMessage.error(err.message || '保存失败')
  } finally {
    saving.value = false
  }
}

function handleCancel() {
  router.push('/report')
}

onMounted(() => {
  const id = route.params.id
  if (id) {
    isEdit.value = true
    reportId.value = Number(id)
    loadReport()
  } else {
    // 新建报表时默认添加一个图表
    addChart()
  }
})

onUnmounted(() => {
  // 清理所有预览图表实例
  previewChartInstances.forEach(({ instance }) => {
    if (instance) {
      instance.dispose()
    }
  })
  previewChartInstances.clear()
})
</script>

<style scoped>
.report-edit-page {
  padding: 20px;
}
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}
.page-header h2 {
  margin: 0;
}
.charts-container {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.chart-item {
  position: relative;
}
.chart-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.chart-header-left {
  display: flex;
  align-items: center;
  gap: 8px;
}
.chart-number {
  font-weight: 500;
  color: #333;
}
.chart-actions {
  display: flex;
  gap: 8px;
}
.add-chart-btn {
  width: 100%;
  border-style: dashed;
}
.preview-area {
  margin-top: 16px;
}
.preview-info {
  display: flex;
  gap: 8px;
  margin-bottom: 8px;
}
.table-preview-wrapper {
  width: 100%;
}
.chart-wrapper {
  margin-top: 16px;
}
.chart-container {
  width: 100%;
  height: 300px;
}
.tips-content h4 {
  margin: 10px 0;
  color: #333;
}
.tips-content ul {
  margin: 5px 0;
  padding-left: 20px;
}
.tips-content li {
  margin: 5px 0;
  color: #666;
  font-size: 14px;
}
</style>
