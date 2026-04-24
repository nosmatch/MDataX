<template>
  <div class="report-view-page">
    <div class="page-header">
      <div>
        <h2>{{ report.name }}</h2>
        <p v-if="report.description" class="desc">{{ report.description }}</p>
      </div>
      <div class="actions">
        <el-button :loading="loading" @click="loadAllChartsData">
          <el-icon><Refresh /></el-icon>
          刷新
        </el-button>
        <el-button v-if="canEdit" type="primary" @click="handleEdit">
          <el-icon><Edit /></el-icon>
          编辑
        </el-button>
        <el-button v-if="canDelete" type="danger" @click="handleDelete">
          <el-icon><Delete /></el-icon>
          删除
        </el-button>
        <el-button @click="handleBack">返回</el-button>
      </div>
    </div>

    <el-alert
      v-if="!isOwner && report.visibility === 'private'"
      type="info"
      :closable="false"
      class="permission-tip"
    >
      <template #title>
        <span v-if="userRole === 'viewer'">您当前是「查看者」，只能查看和执行报表。如需编辑或删除，请联系报表所有者。</span>
        <span v-else-if="userRole === 'editor'">您当前是「编辑者」，可以查看和编辑报表。</span>
      </template>
    </el-alert>

    <el-card v-loading="loading">
      <div v-if="error" class="error-message">
        <el-alert :title="error" type="error" show-icon :closable="false" />
      </div>

      <div v-else-if="charts.length > 0" class="result-area">
        <!-- 网格布局展示多个图表 -->
        <el-row :gutter="20" class="charts-grid">
          <el-col
            v-for="chart in charts"
            :key="chart.id"
            :span="(chart.layoutSpan || 12) * 2"
            class="chart-col"
          >
            <el-card class="chart-card" v-loading="chart.loading">
              <template #header>
                <div class="chart-card-header">
                  <div class="chart-title-section">
                    <span class="chart-title">{{ chart.title }}</span>
                    <el-tag size="small" :type="getChartTypeTag(chart.chartType)">
                      {{ getChartTypeLabel(chart.chartType) }}
                    </el-tag>
                  </div>
                </div>
                <div v-if="chart.chartDescription" class="chart-description">
                  {{ chart.chartDescription }}
                </div>
              </template>
              <div
                :ref="el => setChartRef(chart.id, el)"
                class="chart-container"
                :class="{ 'table-container': chart.chartType === 'table' }"
              >
                <!-- 图表错误提示 -->
                <div v-if="chartErrors.has(chart.id)" class="chart-error">
                  <el-alert
                    :title="chartErrors.get(chart.id)"
                    type="error"
                    :closable="false"
                    show-icon
                  />
                </div>

                <!-- 表格类型：显示数据表格 -->
                <div v-if="chart.result && chart.chartType === 'table'" class="table-content">
                  <div class="table-wrapper">
                    <el-table
                      :data="chart.result.rows"
                      stripe
                      :height="Math.min(chart.result.rowCount * 40 + 100, 500)"
                      style="width: 100%"
                    >
                      <el-table-column
                        v-for="col in chart.result.columns"
                        :key="col"
                        :prop="col"
                        :label="col"
                        min-width="120"
                        show-overflow-tooltip
                      />
                    </el-table>
                  </div>
                </div>

                <!-- 图表类型：显示 ECharts 图表 -->
                <div v-else-if="chart.result && chart.chartType !== 'table'" class="chart-content">
                  <div class="chart-meta">
                    <el-tag size="small" effect="plain">
                      行数: {{ chart.result.rowCount }}
                    </el-tag>
                    <el-tag size="small" effect="plain">
                      耗时: {{ chart.result.executionTime }}ms
                    </el-tag>
                  </div>
                </div>
              </div>
            </el-card>
          </el-col>
        </el-row>
      </div>

      <el-empty v-else description="暂无图表配置" />
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted, watch, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh, Edit, Delete } from '@element-plus/icons-vue'
import * as echarts from 'echarts'
import request from '../utils/request.js'
import { useAuthStore } from '../stores/auth.js'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const reportId = ref(Number(route.params.id))

const report = ref({})
const charts = ref([])
const loading = ref(false)
const error = ref('')
const chartInstances = new Map()
const chartErrors = new Map()

// 权限相关
const currentUserId = ref(authStore.user?.id || null)
const isOwner = ref(false)
const userRole = ref(null) // owner, editor, viewer
const canEdit = ref(false)
const canDelete = ref(false)

async function loadReportInfo() {
  try {
    const res = await request.get(`/report/${reportId.value}`)
    report.value = res.data

    // 使用后端返回的权限信息
    isOwner.value = res.data.ownerId === currentUserId.value
    userRole.value = res.data.userRole || 'viewer'
    canEdit.value = res.data.canEdit || false
    canDelete.value = res.data.canDelete || false

    console.log('当前用户ID:', currentUserId.value)
    console.log('报表所有者ID:', res.data.ownerId)
    console.log('用户角色:', userRole.value)
    console.log('可编辑:', canEdit.value)
    console.log('可删除:', canDelete.value)
  } catch (err) {
    ElMessage.error(err.message || '加载报表信息失败')
  }
}

async function loadCharts() {
  try {
    const res = await request.get(`/report/${reportId.value}/charts`)
    charts.value = (res.data || []).map(chart => ({
      ...chart,
      loading: false,
      result: null
    }))
  } catch (err) {
    ElMessage.error(err.message || '加载图表配置失败')
  }
}

async function loadAllChartsData() {
  loading.value = true
  error.value = ''
  chartErrors.clear()

  try {
    // 并行加载所有图表数据
    const res = await request.get(`/report/${reportId.value}/charts-data`)
    const dataMap = res.data

    // 为每个图表设置数据
    charts.value.forEach(chart => {
      chart.result = dataMap[chart.id]
      chart.loading = false

      if (!chart.result) {
        chartErrors.set(chart.id, `图表「${chart.title}」数据加载失败（可能缺少SQL配置）`)
      }
    })

    // 渲染所有图表
    nextTick(() => {
      charts.value.forEach(chart => {
        if (chart.result) {
          renderChart(chart)
        }
      })
    })

  } catch (err) {
    error.value = err.message || '加载图表数据失败'
    ElMessage.error(error.value)
  } finally {
    loading.value = false
  }
}

/**
 * 大小写不敏感地从对象中获取值
 */
function getFieldValue(row, fieldName) {
  if (!fieldName) return undefined
  if (fieldName in row) return row[fieldName]
  const lowerField = fieldName.toLowerCase()
  for (const key of Object.keys(row)) {
    if (key.toLowerCase() === lowerField) {
      return row[key]
    }
  }
  return undefined
}

/**
 * 获取与配置字段大小写匹配的列名
 */
function getActualColumnName(columns, fieldName) {
  if (!fieldName) return null
  if (columns.includes(fieldName)) return fieldName
  const lowerField = fieldName.toLowerCase()
  for (const col of columns) {
    if (col.toLowerCase() === lowerField) {
      return col
    }
  }
  return null
}

/**
 * 渲染单个图表
 */
function renderChart(chart) {
  const chartRef = chartInstances.get(chart.id)?.ref
  if (!chartRef || !chart.result) return

  const xField = chart.xAxisField
  const yField = chart.yAxisField
  const columns = chart.result.columns || []
  const rows = chart.result.rows || []

  // 清除之前的错误
  chartErrors.delete(chart.id)

  // 表格类型不需要轴字段验证
  if (chart.chartType === 'table') {
    // 不需要轴字段，直接渲染表格
    return
  }

  if (!xField || !yField) {
    chartErrors.set(chart.id, `图表「${chart.title}」缺少轴字段配置`)
    console.warn(`图表 ${chart.title} 缺少轴字段配置`)
    return
  }

  const actualXField = getActualColumnName(columns, xField)
  const actualYField = getActualColumnName(columns, yField)

  if (!actualXField) {
    chartErrors.set(chart.id, `图表「${chart.title}」找不到X轴字段: ${xField}`)
    console.warn(`图表 ${chart.title} 找不到列: ${xField}`)
    return
  }
  if (!actualYField) {
    chartErrors.set(chart.id, `图表「${chart.title}」找不到Y轴字段: ${yField}`)
    console.warn(`图表 ${chart.title} 找不到列: ${yField}`)
    return
  }

  // 获取或创建图表实例
  let instance = chartInstances.get(chart.id)?.instance
  if (instance) {
    instance.dispose()
  }
  instance = echarts.init(chartRef)
  chartInstances.set(chart.id, { ref: chartRef, instance })

  const xData = rows.map(r => getFieldValue(r, xField))
  const yData = rows.map(r => getFieldValue(r, yField))

  // 数据验证：检查Y轴数据是否为数值
  const hasNumericYData = yData.some(val => typeof val === 'number')

  // 如果Y轴数据不是数值，可能配置反了
  if (!hasNumericYData && chart.chartType !== 'pie') {
    const errorMsg = `图表「${chart.title}」数据配置可能有误：Y轴字段「${yField}」的值不是数值类型。请检查是否将X轴和Y轴字段配置反了。`
    chartErrors.set(chart.id, errorMsg)
    console.error(errorMsg, { xField, yField, xData: xData.slice(0, 3), yData: yData.slice(0, 3) })
    return
  }

  // 饼图特殊验证
  if (chart.chartType === 'pie' && !hasNumericYData) {
    const errorMsg = `图表「${chart.title}」数据配置有误：饼图需要Y轴字段为数值类型`
    chartErrors.set(chart.id, errorMsg)
    console.error(errorMsg, { xField, yField, xData: xData.slice(0, 3), yData: yData.slice(0, 3) })
    return
  }

  let option = {}

  if (chart.chartType === 'table') {
    // 表格类型不需要 ECharts 配置，直接返回
    return
  } else if (chart.chartType === 'pie') {
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
        label: {
          show: true,
          formatter: '{b}: {c} ({d}%)'
        },
        data: rows.map(r => ({ name: getFieldValue(r, xField), value: getFieldValue(r, yField) })),
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
    console.log(`图表「${chart.title}」渲染成功`, {
      type: chart.chartType,
      xField,
      yField,
      rows: chart.result.rowCount
    })
  } catch (err) {
    const errorMsg = `图表「${chart.title}」渲染失败: ${err.message}`
    chartErrors.set(chart.id, errorMsg)
    console.error(errorMsg, err)
  }
}

/**
 * 设置图表DOM引用
 */
function setChartRef(chartId, el) {
  if (el) {
    const existing = chartInstances.get(chartId) || {}
    chartInstances.set(chartId, { ...existing, ref: el })
  }
}

/**
 * 获取图表类型标签
 */
function getChartTypeLabel(type) {
  const typeMap = {
    line: '折线图',
    bar: '柱状图',
    pie: '饼图',
    table: '表格'
  }
  return typeMap[type] || type
}

/**
 * 获取图表类型标签颜色
 */
function getChartTypeTag(type) {
  const typeMap = {
    line: '',
    bar: 'warning',
    pie: 'success',
    table: 'info'
  }
  return typeMap[type] || ''
}

function handleBack() {
  router.push('/report')
}

function handleEdit() {
  router.push(`/report/edit/${reportId.value}`)
}

async function handleDelete() {
  try {
    await ElMessageBox.confirm(`确定删除报表「${report.value.name}」吗？`, '提示', { type: 'warning' })
    await request.delete(`/report/${reportId.value}`)
    ElMessage.success('删除成功')
    router.push('/report')
  } catch (err) {
    if (err !== 'cancel') {
      ElMessage.error(err.message || '删除失败')
    }
  }
}

function handleResize() {
  chartInstances.forEach(({ instance }) => {
    if (instance) {
      instance.resize()
    }
  })
}

onMounted(() => {
  loadReportInfo().then(() => {
    loadCharts().then(() => {
      loadAllChartsData()
    })
  })
  window.addEventListener('resize', handleResize)
})

onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
  chartInstances.forEach(({ instance }) => {
    if (instance) {
      instance.dispose()
    }
  })
  chartInstances.clear()
  chartErrors.clear()
})
</script>

<style scoped>
.report-view-page {
  padding: 20px;
}
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 16px;
}
.page-header h2 {
  margin: 0 0 4px 0;
}
.page-header .desc {
  margin: 0;
  color: #666;
  font-size: 14px;
}
.actions {
  display: flex;
  gap: 8px;
}
.permission-tip {
  margin-bottom: 16px;
}
.error-message {
  margin-bottom: 16px;
}
.charts-grid {
  margin-top: 16px;
}
.chart-col {
  margin-bottom: 20px;
}
.chart-card {
  height: 100%;
}
.chart-card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.chart-title-section {
  display: flex;
  align-items: center;
  gap: 8px;
}
.chart-title {
  font-weight: 500;
  font-size: 15px;
}
.chart-description {
  margin-top: 8px;
  padding-top: 8px;
  border-top: 1px solid #ebeef5;
  color: #666;
  font-size: 13px;
  line-height: 1.5;
}
.chart-container {
  width: 100%;
  height: 400px;
  position: relative;
}
.chart-container.table-container {
  height: auto;
  min-height: 300px;
}
.table-wrapper {
  overflow: auto;
}
.table-wrapper table {
  margin: 0;
}
.table-content {
  width: 100%;
  height: 100%;
}
.table-wrapper {
  width: 100%;
  overflow: auto;
}
.chart-error {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  z-index: 10;
  padding: 10px;
}
.chart-meta {
  position: absolute;
  top: 10px;
  right: 10px;
  display: flex;
  gap: 8px;
  z-index: 5;
}
.chart-content {
  width: 100%;
  height: 100%;
}
.chart-meta {
  position: absolute;
  top: 10px;
  right: 10px;
  display: flex;
  gap: 8px;
  z-index: 5;
}
</style>
