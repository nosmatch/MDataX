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

    <!-- 失败任务（重点关注） -->
    <el-card v-if="failedExecutions.length > 0" class="failed-card" shadow="never">
      <template #header>
        <div class="card-header">
          <span class="card-title">
            <el-icon><Warning /></el-icon> 失败任务（需重点关注）
          </span>
          <el-button type="primary" link size="small" @click="viewAllFailed">查看全部失败</el-button>
        </div>
      </template>
      <el-table :data="failedExecutions" size="small" stripe @row-click="handleRowClick">
        <el-table-column prop="executionId" label="执行ID" width="180">
          <template #default="{ row }">
            <el-tag size="small" type="info">{{ row.executionId }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="taskName" label="任务名称" min-width="160" />
        <el-table-column prop="taskType" label="类型" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="TASK_TYPE_TAG_TYPE[row.taskType] || 'info'" size="small">
              {{ TASK_TYPE_LABEL[row.taskType] || row.taskType }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="triggerType" label="触发方式" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="getTriggerTagType(row.triggerType)" size="small">
              {{ getTriggerName(row.triggerType) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="startTime" label="失败时间" width="160" />
        <el-table-column prop="errorMessage" label="失败原因" min-width="200" show-overflow-tooltip>
          <template #default="{ row }">
            {{ row.errorMessage || row.statusDetail || '-' }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="180" align="center" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click.stop="openDetail(row)">详情</el-button>
            <el-button type="warning" link size="small" :loading="retryingId === row.executionId" @click.stop="handleRetry(row)">重试</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 执行记录列表 -->
    <el-card class="history-card" shadow="never">
      <template #header>
        <div class="card-header">
          <span class="card-title">执行记录列表</span>
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
import { Warning, Refresh } from '@element-plus/icons-vue'
import request from '../utils/request.js'
import ExecutionHistoryPanel from '../components/ExecutionHistoryPanel.vue'
import ExecutionDetailDrawer from '../components/ExecutionDetailDrawer.vue'
import { getTriggerName, getTriggerTagType, formatDuration } from '../utils/execution.js'
import {
  EXECUTION_STATUS,
  TASK_TYPE,
  TASK_TYPE_LABEL,
  TASK_TYPE_TAG_TYPE
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
const failedExecutions = ref([])
const retryingId = ref('')
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

const loadFailedExecutions = async () => {
  try {
    const { startTime, endTime } = getTimeRange()
    const res = await request.get('/task-execution/page', {
      params: {
        page: 1,
        size: 10,
        status: EXECUTION_STATUS.FAILED,
        startTime,
        endTime
      }
    })
    failedExecutions.value = res.data?.records || []
  } catch (error) {
    failedExecutions.value = []
    console.error('加载失败执行记录失败', error)
    ElMessage.warning(error.message || '加载失败执行记录失败')
  }
}

const applyTimeRangeToHistory = () => {
  if (!historyPanelRef.value) return
  const { startTime, endTime } = getTimeRange()
  historyPanelRef.value.filters.status = activeStatKey.value === 'total' ? '' : activeStatKey.value.toUpperCase()
  historyPanelRef.value.filters.dateRange = [startTime, endTime]
  historyPanelRef.value.fetchExecutions()
}

const handleTimeRangeChange = () => {
  activeStatKey.value = ''
  refreshAll()
}

const handleStatClick = (key) => {
  activeStatKey.value = activeStatKey.value === key ? '' : key
  applyTimeRangeToHistory()
}

const viewAllFailed = () => {
  activeStatKey.value = 'failed'
  applyTimeRangeToHistory()
}

const refreshHistory = () => {
  if (historyPanelRef.value) {
    historyPanelRef.value.fetchExecutions()
  }
}

const refreshAll = () => {
  loadStatistics()
  loadFailedExecutions()
  refreshHistory()
}

const startAutoRefresh = () => {
  stopAutoRefresh()
  if (!autoRefresh.value) return
  refreshTimer = setInterval(() => {
    loadStatistics()
    loadFailedExecutions()
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

const openDetail = (row) => {
  currentExecutionId.value = row.executionId
  detailDrawerVisible.value = true
}

const handleRowClick = (row) => {
  openDetail(row)
}

const handleRetry = (row) => {
  retryingId.value = row.executionId
  request.post(`/task-execution/${row.executionId}/retry`)
    .then(() => {
      ElMessage.success('重试成功')
      refreshAll()
    })
    .catch((error) => {
      ElMessage.error(error.message || '重试失败')
    })
    .finally(() => {
      retryingId.value = ''
    })
}

onMounted(() => {
  const { startTime, endTime } = getTimeRange()
  setTimeout(() => {
    applyTimeRangeToHistory()
  }, 0)
  loadStatistics()
  loadFailedExecutions()
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

.failed-card {
  margin-bottom: 16px;
  border-left: 4px solid #f56c6c;
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
