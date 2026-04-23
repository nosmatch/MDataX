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
            <el-form-item label="SQL 语句" prop="sqlContent">
              <el-input
                v-model="form.sqlContent"
                type="textarea"
                :rows="8"
                placeholder="请输入 SELECT 查询语句"
              />
            </el-form-item>
            <el-form-item label="图表类型" prop="chartType">
              <el-select v-model="form.chartType" placeholder="请选择图表类型" style="width: 200px">
                <el-option label="折线图" value="line" />
                <el-option label="柱状图" value="bar" />
                <el-option label="饼图" value="pie" />
                <el-option label="表格" value="table" />
              </el-select>
            </el-form-item>
            <el-form-item label="X轴字段" prop="xAxisField" v-if="form.chartType !== 'table'">
              <el-input v-model="form.xAxisField" placeholder="用于图表分类或标签的字段名" />
            </el-form-item>
            <el-form-item label="Y轴字段" prop="yAxisField" v-if="form.chartType !== 'table'">
              <el-input v-model="form.yAxisField" placeholder="用于图表数值的字段名" />
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
        </el-card>
      </el-col>

      <el-col :span="8">
        <el-card>
          <template #header>
            <div style="display: flex; justify-content: space-between; align-items: center">
              <span>数据预览</span>
              <el-button type="primary" size="small" :loading="previewLoading" @click="handlePreview">
                测试执行
              </el-button>
            </div>
          </template>

          <div v-if="previewResult" class="preview-area">
            <div class="preview-info">
              <span>行数: {{ previewResult.rowCount }}</span>
              <span>耗时: {{ previewResult.executionTime }}ms</span>
            </div>
            <el-table :data="previewResult.rows" size="small" max-height="400" stripe>
              <el-table-column
                v-for="col in previewResult.columns"
                :key="col"
                :prop="col"
                :label="col"
                min-width="120"
                show-overflow-tooltip
              />
            </el-table>
            <div v-if="form.chartType !== 'table'" class="chart-wrapper">
              <div ref="chartRef" class="chart-container"></div>
            </div>
          </div>
          <el-empty v-else description="点击「测试执行」预览数据" />
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, watch, onUnmounted, toRaw } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import * as echarts from 'echarts'
import request from '../utils/request.js'

const route = useRoute()
const router = useRouter()
const formRef = ref(null)
const saving = ref(false)
const previewLoading = ref(false)
const previewResult = ref(null)
const chartRef = ref(null)
let chartInstance = null

const isEdit = ref(false)
const reportId = ref(null)

const form = reactive({
  name: '',
  sqlContent: '',
  chartType: 'line',
  xAxisField: '',
  yAxisField: '',
  description: '',
  status: 1
})

const rules = {
  name: [{ required: true, message: '请输入报表名称', trigger: 'blur' }],
  sqlContent: [{ required: true, message: '请输入 SQL 语句', trigger: 'blur' }],
  chartType: [{ required: true, message: '请选择图表类型', trigger: 'change' }]
}

async function loadReport() {
  if (!reportId.value) return
  try {
    const res = await request.get(`/report/${reportId.value}`)
    const data = res.data

    // 调试日志
    console.log('=== 后端返回的报表数据 ===')
    console.log('完整数据:', JSON.stringify(data, null, 2))
    console.log('xAxisField:', data.xAxisField)
    console.log('yAxisField:', data.yAxisField)
    console.log('XAxisField:', data.XAxisField)  // 检查是否是大写开头
    console.log('YAxisField:', data.YAxisField)
    console.log('========================')

    form.name = data.name || ''
    form.sqlContent = data.sqlContent || ''
    form.chartType = data.chartType || 'line'
    form.xAxisField = data.xAxisField || ''
    form.yAxisField = data.yAxisField || ''
    form.description = data.description || ''
    form.status = data.status ?? 1

    console.log('=== 赋值后的 form ===')
    console.log('form.xAxisField:', form.xAxisField)
    console.log('form.yAxisField:', form.yAxisField)
    console.log('==================')
  } catch (err) {
    ElMessage.error(err.message || '加载报表失败')
  }
}

async function handlePreview() {
  if (!form.sqlContent.trim()) {
    ElMessage.warning('请先输入 SQL 语句')
    return
  }
  previewLoading.value = true
  try {
    const res = await request.post('/query/execute', {
      sql: form.sqlContent,
      readonly: true
    })
    previewResult.value = res.data
    if (res.data.columns && res.data.columns.length > 0) {
      if (!form.xAxisField) {
        form.xAxisField = res.data.columns[0]
      }
      if (!form.yAxisField && res.data.columns.length > 1) {
        form.yAxisField = res.data.columns[1]
      }
    }
    ElMessage.success('执行成功')
  } catch (err) {
    ElMessage.error(err.message || '执行失败')
  } finally {
    previewLoading.value = false
  }
}

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

function renderChart() {
  if (!chartRef.value || !previewResult.value) return
  if (form.chartType === 'table') return

  const xField = form.xAxisField
  const yField = form.yAxisField
  const columns = previewResult.value.columns || []
  const rows = previewResult.value.rows || []

  if (!xField || !yField) return

  const actualXField = getActualColumnName(columns, xField)
  const actualYField = getActualColumnName(columns, yField)
  if (!actualXField || !actualYField) return

  if (chartInstance) {
    chartInstance.dispose()
  }
  chartInstance = echarts.init(chartRef.value)

  const xData = rows.map(r => getFieldValue(r, xField))
  const yData = rows.map(r => getFieldValue(r, yField))

  let option = {}
  if (form.chartType === 'pie') {
    option = {
      tooltip: { trigger: 'item' },
      legend: { orient: 'vertical', left: 'left' },
      series: [{
        type: 'pie',
        radius: ['40%', '70%'],
        avoidLabelOverlap: false,
        itemStyle: { borderRadius: 10, borderColor: '#fff', borderWidth: 2 },
        label: { show: true, formatter: '{b}: {c} ({d}%)' },
        data: rows.map(r => ({ name: getFieldValue(r, xField), value: getFieldValue(r, yField) })),
        emphasis: {
          itemStyle: { shadowBlur: 10, shadowOffsetX: 0, shadowColor: 'rgba(0, 0, 0, 0.5)' }
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
        type: form.chartType,
        data: yData,
        smooth: form.chartType === 'line',
        itemStyle: {
          color: form.chartType === 'bar' ? '#409EFF' : undefined
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

function handleResize() {
  if (chartInstance) {
    chartInstance.resize()
  }
}

function handleSave() {
  formRef.value.validate(async (valid) => {
    if (!valid) return
    saving.value = true

    // 使用 toRaw() 转换 reactive 对象为普通对象
    const dataToSend = toRaw(form)

    // 调试日志
    console.log('=== 保存报表时的数据 ===')
    console.log('完整 form:', JSON.stringify(dataToSend, null, 2))
    console.log('xAxisField:', dataToSend.xAxisField)
    console.log('yAxisField:', dataToSend.yAxisField)
    console.log('=======================')

    try {
      if (isEdit.value) {
        await request.put(`/report/${reportId.value}`, dataToSend)
      } else {
        await request.post('/report', dataToSend)
      }
      ElMessage.success('保存成功')
      router.push('/report')
    } catch (err) {
      ElMessage.error(err.message || '保存失败')
    } finally {
      saving.value = false
    }
  })
}

function handleCancel() {
  router.push('/report')
}

// 监听数据和图表类型变化，自动渲染图表
watch([() => form.chartType, () => previewResult.value], () => {
  if (form.chartType && form.chartType !== 'table' && previewResult.value) {
    renderChart()
  }
}, { flush: 'post' })

onMounted(() => {
  const id = route.params.id
  if (id) {
    isEdit.value = true
    reportId.value = Number(id)
    loadReport()
  }
  window.addEventListener('resize', handleResize)
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
.preview-area {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.preview-info {
  display: flex;
  gap: 16px;
  font-size: 12px;
  color: #666;
}
.chart-wrapper {
  margin-top: 16px;
}
.chart-container {
  width: 100%;
  height: 300px;
}
</style>
