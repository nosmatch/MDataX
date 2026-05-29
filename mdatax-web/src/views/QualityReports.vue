<template>
  <div class="quality-reports-page">
    <div class="page-header">
      <h2>质量报告查询</h2>
    </div>

    <!-- 查询表单 -->
    <el-card class="filter-card">
      <el-form :inline="true" :model="queryParams">
        <el-form-item label="数据库">
          <el-input v-model="queryParams.database" placeholder="请输入数据库名" clearable />
        </el-form-item>
        <el-form-item label="表名">
          <el-input v-model="queryParams.tableName" placeholder="请输入表名" clearable />
        </el-form-item>
        <el-form-item label="质量等级">
          <el-select v-model="queryParams.qualityLevel" placeholder="请选择质量等级" clearable style="width: 180px">
            <el-option label="优秀 (EXCELLENT)" value="EXCELLENT">
              <span>优秀 (EXCELLENT)</span>
              <span style="color: #909399; font-size: 12px; margin-left: 8px">≥95分</span>
            </el-option>
            <el-option label="良好 (GOOD)" value="GOOD">
              <span>良好 (GOOD)</span>
              <span style="color: #909399; font-size: 12px; margin-left: 8px">85-94分</span>
            </el-option>
            <el-option label="及格 (PASS)" value="PASS">
              <span>及格 (PASS)</span>
              <span style="color: #909399; font-size: 12px; margin-left: 8px">70-84分</span>
            </el-option>
            <el-option label="不及格 (FAIL)" value="FAIL">
              <span>不及格 (FAIL)</span>
              <span style="color: #909399; font-size: 12px; margin-left: 8px">&lt;70分</span>
            </el-option>
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="loadReports">查询</el-button>
          <el-button @click="resetQuery">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 报告列表 -->
    <el-card>
      <el-table :data="reports" stripe v-loading="loading" border>
        <el-table-column prop="tableId" label="表ID" width="80" />
        <el-table-column prop="database" label="数据库" min-width="120" show-overflow-tooltip />
        <el-table-column prop="tableName" label="表名" min-width="140" show-overflow-tooltip />
        <el-table-column prop="qualityScore" label="质量分数" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="getScoreTagType(row.qualityScore)" size="large">
              {{ row.qualityScore }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="qualityLevel" label="质量等级" width="120" align="center">
          <template #default="{ row }">
            <el-tag :type="getLevelTagType(row.qualityLevel)" size="small">
              {{ getLevelText(row.qualityLevel) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="totalRules" label="总规则数" width="90" align="center" />
        <el-table-column prop="passedRules" label="通过" width="70" align="center">
          <template #default="{ row }">
            <span style="color: #67c23a">{{ row.passedRules }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="failedRules" label="失败" width="70" align="center">
          <template #default="{ row }">
            <span style="color: #f56c6c">{{ row.failedRules }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="warnedRules" label="警告" width="70" align="center">
          <template #default="{ row }">
            <span style="color: #e6a23c">{{ row.warnedRules }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="reportDate" label="报告日期" width="100" />
        <el-table-column prop="createdAt" label="生成时间" width="160" />
        <el-table-column label="操作" width="180" align="center" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" size="small" @click="viewDetail(row)">详情</el-button>
            <el-button type="primary" size="small" link @click="viewTrend(row)">趋势</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-model:current-page="queryParams.pageNum"
        v-model:page-size="queryParams.pageSize"
        :total="total"
        :page-sizes="[10, 20, 50, 100]"
        layout="total, sizes, prev, pager, next, jumper"
        @size-change="loadReports"
        @current-change="loadReports"
        style="margin-top: 16px; justify-content: flex-end"
      />
    </el-card>

    <!-- 报告详情对话框 -->
    <el-dialog
      v-model="detailDialogVisible"
      title="质量报告详情"
      width="900px"
      @close="closeDetailDialog"
    >
      <div v-if="currentReport" class="report-detail">
        <!-- 基本信息 -->
        <el-descriptions :column="2" border>
          <el-descriptions-item label="数据库">{{ currentReport.database }}</el-descriptions-item>
          <el-descriptions-item label="表名">{{ currentReport.tableName }}</el-descriptions-item>
          <el-descriptions-item label="质量分数">
            <el-tag :type="getScoreTagType(currentReport.qualityScore)" size="large">
              {{ currentReport.qualityScore }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="质量等级">
            <el-tag :type="getLevelTagType(currentReport.qualityLevel)">
              {{ getLevelText(currentReport.qualityLevel) }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="总规则数">{{ currentReport.totalRules }}</el-descriptions-item>
          <el-descriptions-item label="通过率">
            {{ ((currentReport.passedRules / currentReport.totalRules) * 100).toFixed(1) }}%
          </el-descriptions-item>
          <el-descriptions-item label="报告日期">{{ currentReport.reportDate }}</el-descriptions-item>
          <el-descriptions-item label="生成时间">{{ currentReport.createdAt }}</el-descriptions-item>
        </el-descriptions>

        <!-- 检查结果列表 -->
        <div class="results-section">
          <h4>检查结果明细</h4>
          <el-table :data="results" size="small" stripe v-loading="resultsLoading">
            <el-table-column prop="ruleName" label="规则名称" min-width="150" show-overflow-tooltip />
            <el-table-column prop="status" label="状态" width="80" align="center">
              <template #default="{ row }">
                <el-tag :type="getStatusTagType(row.status)" size="small">
                  {{ getStatusText(row.status) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="actualValue" label="实际值" width="100" />
            <el-table-column prop="expectedValue" label="期望值" width="100" />
            <el-table-column prop="errorMessage" label="错误信息" min-width="200" show-overflow-tooltip />
            <el-table-column prop="checkDuration" label="耗时(ms)" width="80" align="center" />
          </el-table>
        </div>
      </div>
    </el-dialog>

    <!-- 趋势图对话框 -->
    <el-dialog
      v-model="trendDialogVisible"
      title="质量趋势"
      width="800px"
    >
      <div ref="trendChartRef" class="trend-chart"></div>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted } from 'vue'
import { ElMessage } from 'element-plus'
import { qualityReportApi } from '../api/quality'
import * as echarts from 'echarts'

const loading = ref(false)
const reports = ref([])
const total = ref(0)

const queryParams = ref({
  database: '',
  tableName: '',
  qualityLevel: '',
  pageNum: 1,
  pageSize: 20
})

const detailDialogVisible = ref(false)
const trendDialogVisible = ref(false)
const currentReport = ref(null)
const results = ref([])
const resultsLoading = ref(false)

const trendChartRef = ref(null)
let trendChart = null

// 加载报告列表
const loadReports = async () => {
  loading.value = true
  try {
    const { data } = await qualityReportApi.getHistory(queryParams.value)
    reports.value = data.records || []
    total.value = data.total || 0
  } catch (error) {
    ElMessage.error('加载报告列表失败')
    console.error(error)
  } finally {
    loading.value = false
  }
}

// 查看详情
const viewDetail = async (row) => {
  currentReport.value = row
  detailDialogVisible.value = true
  loadResults(row.id)
}

// 加载检查结果
const loadResults = async (reportId) => {
  resultsLoading.value = true
  try {
    const { data } = await qualityReportApi.getResults(reportId)
    results.value = data
  } catch (error) {
    ElMessage.error('加载结果明细失败')
    console.error(error)
  } finally {
    resultsLoading.value = false
  }
}

// 关闭详情对话框
const closeDetailDialog = () => {
  currentReport.value = null
  results.value = []
}

// 查看趋势
const viewTrend = async (row) => {
  trendDialogVisible.value = true
  try {
    const { data } = await qualityReportApi.getTrend(row.tableId, 30)
    renderTrendChart(data, row.tableName)
  } catch (error) {
    ElMessage.error('加载趋势数据失败')
    console.error(error)
  }
}

// 渲染趋势图
const renderTrendChart = (data, tableName) => {
  if (!trendChart) {
    trendChart = echarts.init(trendChartRef.value)
  }

  const dates = data.map(item => item.date)
  const scores = data.map(item => item.qualityScore)

  const option = {
    title: {
      text: `${tableName} - 质量趋势`,
      left: 'center'
    },
    tooltip: {
      trigger: 'axis'
    },
    xAxis: {
      type: 'category',
      data: dates
    },
    yAxis: {
      type: 'value',
      name: '质量分数',
      min: 0,
      max: 100
    },
    series: [
      {
        name: '质量分数',
        type: 'line',
        data: scores,
        smooth: true,
        itemStyle: { color: '#409EFF' },
        areaStyle: {
          color: {
            type: 'linear',
            x: 0,
            y: 0,
            x2: 0,
            y2: 1,
            colorStops: [
              { offset: 0, color: 'rgba(64, 158, 255, 0.3)' },
              { offset: 1, color: 'rgba(64, 158, 255, 0.05)' }
            ]
          }
        }
      }
    ]
  }

  trendChart.setOption(option)
}

// 重置查询
const resetQuery = () => {
  queryParams.value = {
    database: '',
    tableName: '',
    qualityLevel: '',
    pageNum: 1,
    pageSize: 20
  }
  loadReports()
}

// 获取分数对应的标签类型
const getScoreTagType = (score) => {
  if (score >= 90) return 'success'
  if (score >= 75) return 'primary'
  if (score >= 60) return 'warning'
  return 'danger'
}

// 获取等级对应的标签类型
const getLevelTagType = (level) => {
  const map = {
    'EXCELLENT': 'success',
    'GOOD': 'primary',
    'PASS': 'warning',
    'FAIL': 'danger'
  }
  return map[level] || 'info'
}

// 获取等级文本
const getLevelText = (level) => {
  const map = {
    'EXCELLENT': '优秀',
    'GOOD': '良好',
    'PASS': '及格',
    'FAIL': '不及格'
  }
  return map[level] || level
}

// 获取状态对应的标签类型
const getStatusTagType = (status) => {
  const map = {
    'PASS': 'success',
    'FAIL': 'danger',
    'WARN': 'warning',
    'ERROR': 'danger'
  }
  return map[status] || 'info'
}

// 获取状态文本
const getStatusText = (status) => {
  const map = {
    'PASS': '通过',
    'FAIL': '失败',
    'WARN': '警告',
    'ERROR': '错误'
  }
  return map[status] || status
}

onMounted(() => {
  loadReports()
})

onUnmounted(() => {
  trendChart?.dispose()
})
</script>

<style scoped>
.quality-reports-page {
  padding: 0;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}

.page-header h2 {
  margin: 0;
  font-size: 20px;
  font-weight: 600;
}

.filter-card {
  margin-bottom: 16px;
}

.report-detail h4 {
  margin-top: 20px;
  margin-bottom: 12px;
  font-size: 16px;
  font-weight: 600;
}

.results-section {
  margin-top: 20px;
}

.trend-chart {
  height: 400px;
}
</style>
