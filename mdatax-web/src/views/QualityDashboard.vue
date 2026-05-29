<template>
  <div class="quality-dashboard-page">
    <div class="page-header">
      <h2>数据质量监控大盘</h2>
      <el-button type="primary" :icon="Refresh" @click="refreshData">刷新数据</el-button>
    </div>

    <!-- 整体概况卡片 -->
    <div class="stats-row">
      <el-card class="stat-card">
        <div class="stat-value">{{ overview.totalTables || 0 }}</div>
        <div class="stat-label">监控表总数</div>
      </el-card>
      <el-card class="stat-card excellent">
        <div class="stat-value">{{ overview.excellentTables || 0 }}</div>
        <div class="stat-label">优秀 (≥90分)</div>
        <div class="stat-sub">{{ percentage(overview.excellentTables, overview.totalTables) }}%</div>
      </el-card>
      <el-card class="stat-card good">
        <div class="stat-value">{{ overview.goodTables || 0 }}</div>
        <div class="stat-label">良好 (75-89分)</div>
        <div class="stat-sub">{{ percentage(overview.goodTables, overview.totalTables) }}%</div>
      </el-card>
      <el-card class="stat-card pass">
        <div class="stat-value">{{ overview.passTables || 0 }}</div>
        <div class="stat-label">及格 (60-74分)</div>
        <div class="stat-sub">{{ percentage(overview.passTables, overview.totalTables) }}%</div>
      </el-card>
      <el-card class="stat-card fail">
        <div class="stat-value">{{ overview.failTables || 0 }}</div>
        <div class="stat-label">不及格 (&lt;60分)</div>
        <div class="stat-sub">{{ percentage(overview.failTables, overview.totalTables) }}%</div>
      </el-card>
    </div>

    <el-row :gutter="16" class="charts-row">
      <!-- 质量趋势图 -->
      <el-col :span="12">
        <el-card>
          <template #header>
            <div class="card-header">
              <span>质量趋势（近7天）</span>
            </div>
          </template>
          <div ref="trendChartRef" class="chart-container"></div>
        </el-card>
      </el-col>

      <!-- 质量分布图 -->
      <el-col :span="12">
        <el-card>
          <template #header>
            <div class="card-header">
              <span>质量分布</span>
            </div>
          </template>
          <div ref="distributionChartRef" class="chart-container"></div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="16" class="tables-row">
      <!-- TOP榜单 -->
      <el-col :span="12">
        <el-card>
          <template #header>
            <div class="card-header">
              <span>质量评分 TOP10</span>
            </div>
          </template>
          <el-table :data="topTables" size="small" stripe v-loading="topLoading">
            <el-table-column type="index" label="排名" width="60" align="center" />
            <el-table-column prop="tableName" label="表名" min-width="150" show-overflow-tooltip />
            <el-table-column prop="database" label="数据库" min-width="120" show-overflow-tooltip />
            <el-table-column prop="qualityScore" label="质量分" width="80" align="center">
              <template #default="{ row }">
                <el-tag :type="getScoreTagType(row.qualityScore)" size="small">
                  {{ row.qualityScore }}
                </el-tag>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>

      <!-- 异常列表 -->
      <el-col :span="12">
        <el-card>
          <template #header>
            <div class="card-header">
              <span>最近异常</span>
              <el-button type="text" @click="goToRules">查看全部</el-button>
            </div>
          </template>
          <el-table :data="alerts" size="small" stripe v-loading="alertsLoading">
            <el-table-column prop="tableName" label="表名" min-width="120" show-overflow-tooltip />
            <el-table-column prop="ruleName" label="规则名称" min-width="120" show-overflow-tooltip />
            <el-table-column prop="checkStatus" label="状态" width="70" align="center">
              <template #default="{ row }">
                <el-tag :type="getStatusTagType(row.checkStatus)" size="small">
                  {{ row.checkStatus }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="errorMessage" label="错误信息" min-width="150" show-overflow-tooltip />
            <el-table-column prop="checkTime" label="检查时间" width="140" />
          </el-table>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import { qualityDashboardApi } from '../api/quality'
import * as echarts from 'echarts'

const overview = ref({})
const trendData = ref([])
const topTables = ref([])
const alerts = ref([])
const topLoading = ref(false)
const alertsLoading = ref(false)

const trendChartRef = ref(null)
const distributionChartRef = ref(null)
let trendChart = null
let distributionChart = null

// 加载整体概况
const loadOverview = async () => {
  try {
    const { data } = await qualityDashboardApi.getOverview()
    overview.value = data
  } catch (error) {
    console.error('加载概况失败:', error)
  }
}

// 加载质量趋势
const loadTrend = async () => {
  try {
    const { data } = await qualityDashboardApi.getTrend(7)
    trendData.value = data
    renderTrendChart()
  } catch (error) {
    console.error('加载趋势失败:', error)
  }
}

// 加载TOP榜单
const loadTopRanking = async () => {
  topLoading.value = true
  try {
    const { data } = await qualityDashboardApi.getTopRanking('score', 10)
    topTables.value = data
  } catch (error) {
    console.error('加载TOP榜单失败:', error)
  } finally {
    topLoading.value = false
  }
}

// 加载异常列表
const loadAlerts = async () => {
  alertsLoading.value = true
  try {
    const { data } = await qualityDashboardApi.getAlerts({ pageSize: 10 })
    alerts.value = data
  } catch (error) {
    console.error('加载异常列表失败:', error)
  } finally {
    alertsLoading.value = false
  }
}

// 加载质量分布并渲染图表
const loadDistribution = async () => {
  try {
    const { data } = await qualityDashboardApi.getDistribution()
    renderDistributionChart(data)
  } catch (error) {
    console.error('加载质量分布失败:', error)
  }
}

// 渲染趋势图
const renderTrendChart = () => {
  if (!trendChart) {
    trendChart = echarts.init(trendChartRef.value)
  }

  const dates = trendData.value.map(item => item.date)
  const scores = trendData.value.map(item => item.qualityScore)
  const counts = trendData.value.map(item => item.checkCount)

  const option = {
    tooltip: {
      trigger: 'axis'
    },
    legend: {
      data: ['质量分数', '检查次数']
    },
    xAxis: {
      type: 'category',
      data: dates
    },
    yAxis: [
      {
        type: 'value',
        name: '质量分数',
        min: 0,
        max: 100
      },
      {
        type: 'value',
        name: '检查次数'
      }
    ],
    series: [
      {
        name: '质量分数',
        type: 'line',
        data: scores,
        smooth: true,
        itemStyle: { color: '#409EFF' }
      },
      {
        name: '检查次数',
        type: 'bar',
        yAxisIndex: 1,
        data: counts,
        itemStyle: { color: '#67C23A' }
      }
    ]
  }

  trendChart.setOption(option)
}

// 渲染分布图
const renderDistributionChart = (distData) => {
  if (!distributionChart) {
    distributionChart = echarts.init(distributionChartRef.value)
  }

  const option = {
    tooltip: {
      trigger: 'item',
      formatter: '{b}: {c} ({d}%)'
    },
    legend: {
      orient: 'vertical',
      left: 'left'
    },
    series: [
      {
        type: 'pie',
        radius: '60%',
        data: [
          { value: distData.excellent, name: '优秀', itemStyle: { color: '#67C23A' } },
          { value: distData.good, name: '良好', itemStyle: { color: '#409EFF' } },
          { value: distData.pass, name: '及格', itemStyle: { color: '#E6A23C' } },
          { value: distData.fail, name: '不及格', itemStyle: { color: '#F56C6C' } }
        ]
      }
    ]
  }

  distributionChart.setOption(option)
}

// 刷新数据
const refreshData = () => {
  loadOverview()
  loadTrend()
  loadTopRanking()
  loadAlerts()
  loadDistribution()
  ElMessage.success('数据已刷新')
}

// 跳转到规则管理
const goToRules = () => {
  window.location.href = '#/quality/rules'
}

// 计算百分比
const percentage = (value, total) => {
  if (!total || total === 0) return 0
  return ((value / total) * 100).toFixed(1)
}

// 获取分数对应的标签类型
const getScoreTagType = (score) => {
  if (score >= 90) return 'success'
  if (score >= 75) return 'primary'
  if (score >= 60) return 'warning'
  return 'danger'
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

onMounted(() => {
  refreshData()

  // 响应式图表
  window.addEventListener('resize', () => {
    trendChart?.resize()
    distributionChart?.resize()
  })
})

onUnmounted(() => {
  trendChart?.dispose()
  distributionChart?.dispose()
})
</script>

<style scoped>
.quality-dashboard-page {
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

.stats-row {
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  gap: 16px;
  margin-bottom: 16px;
}

.stat-card {
  text-align: center;
}

.stat-card.excellent :deep(.el-card__body) {
  border-top: 3px solid #67c23a;
}

.stat-card.good :deep(.el-card__body) {
  border-top: 3px solid #409eff;
}

.stat-card.pass :deep(.el-card__body) {
  border-top: 3px solid #e6a23c;
}

.stat-card.fail :deep(.el-card__body) {
  border-top: 3px solid #f56c6c;
}

.stat-value {
  font-size: 28px;
  font-weight: bold;
  color: #303133;
  margin-bottom: 8px;
}

.stat-label {
  font-size: 14px;
  color: #909399;
  margin-bottom: 4px;
}

.stat-sub {
  font-size: 12px;
  color: #c0c4cc;
}

.charts-row,
.tables-row {
  margin-bottom: 16px;
}

.chart-container {
  height: 300px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
</style>
