<template>
  <div class="task-board-page">
    <div class="page-header">
      <h2>任务看板</h2>
      <div class="header-actions">
        <el-radio-group v-model="timeRange" size="small" @change="handleTimeRangeChange">
          <el-radio-button label="today">今日</el-radio-button>
          <el-radio-button label="yesterday">昨日</el-radio-button>
          <el-radio-button label="week">近7天</el-radio-button>
        </el-radio-group>
        <el-checkbox v-model="autoRefresh" size="small" style="margin-left: 16px">自动刷新</el-checkbox>
      </div>
    </div>

    <!-- 统计卡片区 -->
    <div class="stats-row">
      <el-card
        v-for="stat in displayStats"
        :key="stat.key"
        class="stat-card"
        :class="{ active: activeStatKey === stat.key }"
        shadow="hover"
        @click="handleStatClick(stat.key)"
      >
        <div class="stat-value" :style="{ color: stat.color }">{{ stat.value }}</div>
        <div class="stat-label">{{ stat.label }}</div>
      </el-card>
    </div>

    <!-- 当前筛选状态提示 -->
    <div v-if="activeStatKey" class="filter-hint">
      <el-tag type="info" size="small" closable @close="clearFilter">
        当前筛选: {{ getStatusLabel(activeStatKey) }}
      </el-tag>
    </div>

    <!-- 统一执行记录列表 -->
    <el-card class="history-card" shadow="never">
      <template #header>
        <div class="card-header">
          <span class="card-title">
            {{ activeStatKey ? `${getStatusLabel(activeStatKey)}任务列表` : '执行记录列表' }}
          </span>
          <el-button type="primary" link size="small" @click="refreshHistory">
            <el-icon><Refresh /></el-icon> 刷新
          </el-button>
        </div>
      </template>
      <ExecutionHistoryPanel
        ref="historyPanelRef"
        show-task-name
        :task-id="undefined"
      />
    </el-card>

    <!-- 执行详情抽屉 -->
    <ExecutionDetailDrawer
      v-model="detailDrawerVisible"
      :execution-id="currentExecutionId"
      @retry="refreshAll"
      @kill="refreshAll"
    />
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, computed, onBeforeUnmount } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import request from '../utils/request.js'
import ExecutionHistoryPanel from '../components/ExecutionHistoryPanel.vue'
import ExecutionDetailDrawer from '../components/ExecutionDetailDrawer.vue'
import {
  EXECUTION_STATUS
} from '../utils/task-constants.js'

const timeRange = ref('today')
const autoRefresh = ref(true)
const activeStatKey = ref('')
const statistics = reactive({
  total: 0,
  pendingCount: 0,
  runningCount: 0,
  successCount: 0,
  failedCount: 0,
  timeoutCount: 0,
  killedCount: 0
})

const historyPanelRef = ref(null)
const detailDrawerVisible = ref(false)
const currentExecutionId = ref('')

let refreshTimer = null

const displayStats = computed(() => [
  { key: 'total', label: '总执行', value: statistics.total, color: '#303133' },
  { key: 'running', label: '运行中', value: statistics.runningCount, color: '#409eff' },
  { key: 'success', label: '成功', value: statistics.successCount, color: '#67c23a' },
  { key: 'failed', label: '失败', value: statistics.failedCount, color: '#f56c6c' },
  { key: 'timeout', label: '超时', value: statistics.timeoutCount, color: '#e6a23c' },
  { key: 'killed', label: '已终止', value: statistics.killedCount, color: '#909399' }
])

const getStatusLabel = (key) => {
  const statusMap = {
    'total': '全部',
    'running': '运行中',
    'success': '成功',
    'failed': '失败',
    'timeout': '超时',
    'killed': '已终止',
    'pending': '等待中'
  }
  return statusMap[key] || key
}

const getTimeRange = () => {
  const now = new Date()
  const pad = (n) => String(n).padStart(2, '0')
  const format = (d) => `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`

  let start, end
  if (timeRange.value === 'today') {
    start = new Date(now.getFullYear(), now.getMonth(), now.getDate(), 0, 0, 0)
    end = new Date(now.getFullYear(), now.getMonth(), now.getDate(), 23, 59, 59)
  } else if (timeRange.value === 'yesterday') {
    const yesterday = new Date(now.getTime() - 24 * 60 * 60 * 1000)
    start = new Date(yesterday.getFullYear(), yesterday.getMonth(), yesterday.getDate(), 0, 0, 0)
    end = new Date(yesterday.getFullYear(), yesterday.getMonth(), yesterday.getDate(), 23, 59, 59)
  } else {
    const weekAgo = new Date(now.getTime() - 6 * 24 * 60 * 60 * 1000)
    start = new Date(weekAgo.getFullYear(), weekAgo.getMonth(), weekAgo.getDate(), 0, 0, 0)
    end = new Date(now.getFullYear(), now.getMonth(), now.getDate(), 23, 59, 59)
  }
  return { startTime: format(start), endTime: format(end) }
}

const loadStatistics = async () => {
  try {
    const { startTime, endTime } = getTimeRange()
    const res = await request.get('/task-execution-board/today-statistics', {
      params: { startTime, endTime }
    })
    if (res.code === 200 && res.data) {
      Object.assign(statistics, res.data)
    }
  } catch (error) {
    ElMessage.error(error.message || '获取统计数据失败')
  }
}

const applyTimeRangeToHistory = () => {
  if (!historyPanelRef.value) {
    console.log('historyPanelRef 未就绪，跳过筛选应用')
    return
  }

  const { startTime, endTime } = getTimeRange()

  // 状态映射
  const statusMap = {
    'running': 'RUNNING',
    'success': 'SUCCESS',
    'failed': 'FAILED',
    'timeout': 'TIMEOUT',
    'killed': 'KILLED',
    'pending': 'PENDING'
  }

  // 使用 updateFilters 方法确保响应式更新
  const newFilters = {
    dateRange: [startTime, endTime]
  }

  if (activeStatKey.value && activeStatKey.value !== 'total') {
    newFilters.status = statusMap[activeStatKey.value] || ''
    console.log('设置状态筛选:', activeStatKey.value, '->', newFilters.status)
  } else {
    newFilters.status = ''
    console.log('清除状态筛选')
  }

  historyPanelRef.value.updateFilters(newFilters)
}

const handleTimeRangeChange = () => {
  activeStatKey.value = ''
  refreshAll()
}

const handleStatClick = (key) => {
  // 再次点击同一状态则取消筛选
  activeStatKey.value = activeStatKey.value === key ? '' : key
  applyTimeRangeToHistory()
}

const clearFilter = () => {
  activeStatKey.value = ''
  applyTimeRangeToHistory()
}

const refreshHistory = () => {
  if (historyPanelRef.value) {
    historyPanelRef.value.fetchExecutions()
  }
}

const refreshAll = () => {
  loadStatistics()
  refreshHistory()
}

const startAutoRefresh = () => {
  stopAutoRefresh()
  if (!autoRefresh.value) return
  refreshTimer = setInterval(() => {
    loadStatistics()
    if (historyPanelRef.value) {
      historyPanelRef.value.fetchExecutions()
    }
  }, 30000)
}

const stopAutoRefresh = () => {
  if (refreshTimer) {
    clearInterval(refreshTimer)
    refreshTimer = null
  }
}

onMounted(() => {
  // 先加载统计数据
  loadStatistics()

  // 等待下一个tick，确保子组件完全挂载后再应用筛选
  setTimeout(() => {
    if (historyPanelRef.value) {
      applyTimeRangeToHistory()
    }
  }, 100)

  // 启动自动刷新
  startAutoRefresh()
})

onBeforeUnmount(() => {
  stopAutoRefresh()
})
</script>

<style scoped>
.task-board-page {
  padding-bottom: 20px;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.page-header h2 {
  margin: 0;
  font-size: 20px;
  font-weight: 600;
}

.header-actions {
  display: flex;
  align-items: center;
}

.stats-row {
  display: flex;
  gap: 16px;
  margin-bottom: 16px;
  flex-wrap: wrap;
}

.stat-card {
  flex: 1;
  min-width: 120px;
  text-align: center;
  cursor: pointer;
  transition: all 0.2s;
}

.stat-card:hover,
.stat-card.active {
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
}

.stat-card.active {
  border: 1px solid #409eff;
}

.stat-card :deep(.el-card__body) {
  padding: 12px;
}

.stat-value {
  font-size: 28px;
  font-weight: bold;
  line-height: 1.2;
}

.stat-label {
  font-size: 13px;
  color: #606266;
  margin-top: 4px;
}

.filter-hint {
  margin-bottom: 16px;
  display: flex;
  align-items: center;
  gap: 8px;
}

.history-card {
  margin-top: 16px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.card-title {
  font-weight: 600;
  display: flex;
  align-items: center;
  gap: 6px;
}

:deep(.el-table__row) {
  cursor: pointer;
}
</style>
