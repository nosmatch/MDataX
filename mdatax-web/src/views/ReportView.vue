<template>
  <div class="report-view-page">
    <div class="page-header">
      <div>
        <h2>{{ report.name }}</h2>
        <p v-if="report.description" class="desc">{{ report.description }}</p>
      </div>
      <div class="actions">
        <el-button :loading="loading" @click="loadData">
          <el-icon><Refresh /></el-icon>
          刷新
        </el-button>
        <el-button @click="handleBack">返回</el-button>
      </div>
    </div>

    <el-card v-loading="loading">
      <div v-if="error" class="error-message">
        <el-alert :title="error" type="error" show-icon :closable="false" />
      </div>

      <div v-else-if="result" class="result-area">
        <div class="result-meta">
          <el-tag size="small">行数: {{ result.rowCount }}</el-tag>
          <el-tag size="small">耗时: {{ result.executionTime }}ms</el-tag>
        </div>

        <!-- 图表展示 -->
        <div v-show="report.chartType !== 'table'" class="chart-wrapper">
          <div ref="chartRef" class="chart-container"></div>
        </div>

        <!-- 表格展示 -->
        <div class="table-wrapper">
          <el-table :data="result.rows" size="small" stripe max-height="500">
            <el-table-column
              v-for="col in result.columns"
              :key="col"
              :prop="col"
              :label="col"
              min-width="120"
              show-overflow-tooltip
            />
          </el-table>
        </div>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted, onUpdated, onUnmounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import * as echarts from 'echarts'
import request from '../utils/request.js'

const route = useRoute()
const router = useRouter()
const reportId = ref(Number(route.params.id))

const report = ref({})
const result = ref(null)
const loading = ref(false)
const error = ref('')
const chartRef = ref(null)
let chartInstance = null

async function loadReportInfo() {
  try {
    const res = await request.get(`/report/${reportId.value}`)
    report.value = res.data
  } catch (err) {
    ElMessage.error(err.message || '加载报表信息失败')
  }
}

async function loadData() {
  loading.value = true
  error.value = ''
  try {
    const res = await request.get(`/report/${reportId.value}/data`)
    result.value = res.data
  } catch (err) {
    error.value = err.message || '数据加载失败'
  } finally {
    loading.value = false
  }
}

/**
 * 大小写不敏感地从对象中获取值
 * ClickHouse JDBC 驱动返回的列名通常是大写的
 */
function getFieldValue(row, fieldName) {
  if (!fieldName) return undefined
  // 优先精确匹配
  if (fieldName in row) return row[fieldName]
  // 大小写不敏感匹配
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
  // 优先精确匹配
  if (columns.includes(fieldName)) return fieldName
  // 大小写不敏感匹配
  const lowerField = fieldName.toLowerCase()
  for (const col of columns) {
    if (col.toLowerCase() === lowerField) {
      return col
    }
  }
  return null
}

function renderChart() {
  if (!chartRef.value || !result.value) return

  const type = report.value.chartType
  if (type === 'table') return

  const xField = report.value.xAxisField
  const yField = report.value.yAxisField
  const columns = result.value.columns || []
  const rows = result.value.rows || []

  if (!xField || !yField) {
    console.warn('xAxisField 或 yAxisField 未配置')
    return
  }

  const actualXField = getActualColumnName(columns, xField)
  const actualYField = getActualColumnName(columns, yField)

  if (!actualXField) {
    console.warn(`找不到列: ${xField}，可用列: ${columns.join(', ')}`)
    return
  }
  if (!actualYField) {
    console.warn(`找不到列: ${yField}，可用列: ${columns.join(', ')}`)
    return
  }

  if (chartInstance) {
    chartInstance.dispose()
  }

  chartInstance = echarts.init(chartRef.value)

  const xData = rows.map(r => getFieldValue(r, xField))
  const yData = rows.map(r => getFieldValue(r, yField))

  let option = {}

  if (type === 'pie') {
    option = {
      tooltip: { trigger: 'item' },
      legend: {
        orient: 'vertical',
        left: 'left'
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
      }]
    }
  } else {
    option = {
      tooltip: { trigger: 'axis' },
      xAxis: {
        type: 'category',
        data: xData,
        axisLabel: { rotate: xData.length > 10 ? 45 : 0 }
      },
      yAxis: { type: 'value' },
      series: [{
        type: type,
        data: yData,
        smooth: type === 'line',
        itemStyle: {
          color: type === 'bar' ? '#409EFF' : undefined
        }
      }],
      grid: {
        left: '3%',
        right: '4%',
        bottom: '3%',
        containLabel: true
      }
    }
  }

  chartInstance.setOption(option)
}

function handleBack() {
  router.push('/report')
}

function handleResize() {
  if (chartInstance) {
    chartInstance.resize()
  }
}

// 监听数据变化，自动渲染图表
watch([() => report.value.chartType, () => result.value], () => {
  if (report.value.chartType && report.value.chartType !== 'table' && result.value) {
    renderChart()
  }
}, { flush: 'post' })

onMounted(() => {
  loadReportInfo().then(() => loadData())
  window.addEventListener('resize', handleResize)
})

onUpdated(() => {
  // DOM 更新后尝试渲染图表
  if (report.value.chartType && report.value.chartType !== 'table' && result.value) {
    renderChart()
  }
})

onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
  if (chartInstance) {
    chartInstance.dispose()
    chartInstance = null
  }
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
.error-message {
  margin-bottom: 16px;
}
.result-meta {
  display: flex;
  gap: 8px;
  margin-bottom: 16px;
}
.chart-wrapper {
  margin-bottom: 20px;
}
.chart-container {
  width: 100%;
  height: 400px;
}
.table-wrapper {
  margin-top: 16px;
}
</style>
