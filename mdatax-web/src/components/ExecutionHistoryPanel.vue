<template>
  <div class="execution-history-panel" :class="{ embedded }">
    <!-- 筛选区 -->
    <div class="filter-section">
      <el-row :gutter="16">
        <el-col :span="4">
          <el-select v-model="filters.status" placeholder="状态" clearable @change="fetchExecutions">
            <el-option label="全部" value="" />
            <el-option label="等待中" :value="EXECUTION_STATUS.PENDING" />
            <el-option label="运行中" :value="EXECUTION_STATUS.RUNNING" />
            <el-option label="成功" :value="EXECUTION_STATUS.SUCCESS" />
            <el-option label="失败" :value="EXECUTION_STATUS.FAILED" />
            <el-option label="超时" :value="EXECUTION_STATUS.TIMEOUT" />
            <el-option label="已终止" :value="EXECUTION_STATUS.KILLED" />
          </el-select>
        </el-col>
        <el-col :span="4">
          <el-select v-model="filters.triggerType" placeholder="触发方式" clearable @change="fetchExecutions">
            <el-option label="全部" value="" />
            <el-option label="定时触发" :value="TRIGGER_TYPE.SCHEDULE" />
            <el-option label="手动触发" :value="TRIGGER_TYPE.MANUAL" />
            <el-option label="依赖触发" :value="TRIGGER_TYPE.DEPENDENCY" />
          </el-select>
        </el-col>
        <el-col :span="8">
          <el-date-picker
            v-model="filters.dateRange"
            type="datetimerange"
            range-separator="至"
            start-placeholder="开始时间"
            end-placeholder="结束时间"
            format="YYYY-MM-DD HH:mm:ss"
            value-format="YYYY-MM-DD HH:mm:ss"
            @change="fetchExecutions"
          />
        </el-col>
        <el-col :span="3">
          <el-button type="primary" link @click="resetFilters">
            <el-icon><RefreshLeft /></el-icon> 重置
          </el-button>
        </el-col>
      </el-row>
    </div>

    <!-- 执行记录列表 -->
    <el-table :data="executionList" v-loading="loading" stripe size="small">
      <el-table-column prop="executionId" label="执行ID" width="180">
        <template #default="{ row }">
          <el-tag size="small" type="info">{{ row.executionId }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column v-if="showTaskName" prop="taskName" label="任务名称" min-width="140">
        <template #default="{ row }">
          {{ row.taskName || '-' }}
        </template>
      </el-table-column>
      <el-table-column prop="triggerType" label="触发方式" width="100" align="center">
        <template #default="{ row }">
          <el-tag :type="getTriggerTagType(row.triggerType)" size="small">
            {{ getTriggerName(row.triggerType) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="status" label="状态" width="90" align="center">
        <template #default="{ row }">
          <el-tag :type="getStatusTagType(row.status)" size="small">
            {{ getStatusName(row.status) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="startTime" label="开始时间" min-width="160" />
      <el-table-column prop="endTime" label="结束时间" min-width="160">
        <template #default="{ row }">
          {{ row.endTime || '-' }}
        </template>
      </el-table-column>
      <el-table-column prop="durationMs" label="耗时" width="100" align="center">
        <template #default="{ row }">
          {{ formatDuration(row.durationMs) }}
        </template>
      </el-table-column>
      <el-table-column prop="attemptNumber" label="尝试次数" width="90" align="center" />
      <el-table-column label="操作" width="180" align="center" fixed="right">
        <template #default="{ row }">
          <el-button type="primary" link size="small" @click="openDetail(row)">
            详情
          </el-button>
          <el-button
            v-if="row.status === EXECUTION_STATUS.RUNNING"
            type="danger"
            link
            size="small"
            :loading="killingId === row.executionId"
            @click="handleKill(row)"
          >
            终止
          </el-button>
          <el-button
            v-if="row.status === EXECUTION_STATUS.FAILED || row.status === EXECUTION_STATUS.TIMEOUT"
            type="warning"
            link
            size="small"
            :loading="retryingId === row.executionId"
            @click="handleRetry(row)"
          >
            重试
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <div class="pagination-wrapper">
      <el-pagination
        v-model:current-page="page"
        v-model:page-size="size"
        :total="total"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next"
        @size-change="fetchExecutions"
        @current-change="fetchExecutions"
      />
    </div>

    <!-- 执行详情抽屉 -->
    <ExecutionDetailDrawer
      v-model="detailDrawerVisible"
      :executionId="currentExecutionId"
      @retry="fetchExecutions"
      @kill="fetchExecutions"
    />
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, onBeforeUnmount, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { RefreshLeft } from '@element-plus/icons-vue'
import request from '../utils/request.js'
import ExecutionDetailDrawer from './ExecutionDetailDrawer.vue'
import {
  getStatusName,
  getStatusTagType,
  getTriggerName,
  getTriggerTagType,
  formatDuration
} from '../utils/execution.js'
import { EXECUTION_STATUS, TRIGGER_TYPE } from '../utils/task-constants.js'

const props = defineProps({
  taskId: {
    type: Number,
    default: undefined
  },
  showTaskName: {
    type: Boolean,
    default: false
  },
  embedded: {
    type: Boolean,
    default: false
  },
  refreshInterval: {
    type: Number,
    default: 5000
  }
})

const loading = ref(false)
const executionList = ref([])
const page = ref(1)
const size = ref(10)
const total = ref(0)

const detailDrawerVisible = ref(false)
const currentExecutionId = ref('')
const killingId = ref('')
const retryingId = ref('')

const filters = reactive({
  status: '',
  triggerType: '',
  dateRange: null
})

let refreshTimer = null

const hasRunningExecution = () => {
  return executionList.value.some(row => row.status === EXECUTION_STATUS.RUNNING)
}

const startAutoRefresh = () => {
  stopAutoRefresh()
  if (props.refreshInterval <= 0) return
  if (!hasRunningExecution()) return
  refreshTimer = setInterval(() => {
    if (hasRunningExecution()) {
      fetchExecutions()
    } else {
      stopAutoRefresh()
    }
  }, props.refreshInterval)
}

const stopAutoRefresh = () => {
  if (refreshTimer) {
    clearInterval(refreshTimer)
    refreshTimer = null
  }
}

const fetchExecutions = async () => {
  loading.value = true
  try {
    const params = {
      page: page.value,
      size: size.value,
      status: filters.status,
      triggerType: filters.triggerType,
      startTime: filters.dateRange?.[0],
      endTime: filters.dateRange?.[1]
    }
    if (props.taskId != null) {
      params.taskId = props.taskId
    }

    const res = await request.get('/task-execution/page', { params })
    executionList.value = res.data.records || []
    total.value = res.data.total || 0
    startAutoRefresh()
  } catch (error) {
    ElMessage.error(error.message || '获取执行记录失败')
  } finally {
    loading.value = false
  }
}

const resetFilters = () => {
  filters.status = ''
  filters.triggerType = ''
  filters.dateRange = null
  page.value = 1
  fetchExecutions()
}

const openDetail = (row) => {
  currentExecutionId.value = row.executionId
  detailDrawerVisible.value = true
}

const handleKill = (row) => {
  ElMessageBox.confirm('确定终止此执行吗？', '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(async () => {
    killingId.value = row.executionId
    try {
      await request.post(`/task-execution/${row.executionId}/kill`)
      ElMessage.success('终止成功')
      fetchExecutions()
    } catch (error) {
      ElMessage.error(error.message || '终止失败')
    } finally {
      killingId.value = ''
    }
  }).catch(() => {})
}

const handleRetry = (row) => {
  retryingId.value = row.executionId
  request.post(`/task-execution/${row.executionId}/retry`)
    .then(() => {
      ElMessage.success('重试成功')
      fetchExecutions()
    })
    .catch((error) => {
      ElMessage.error(error.message || '重试失败')
    })
    .finally(() => {
      retryingId.value = ''
    })
}

watch(() => props.taskId, () => {
  page.value = 1
  fetchExecutions()
})

defineExpose({
  fetchExecutions,
  resetFilters
})

onMounted(() => {
  fetchExecutions()
})

onBeforeUnmount(() => {
  stopAutoRefresh()
})
</script>

<style scoped>
.execution-history-panel {
  padding: 8px 0;
}

.execution-history-panel.embedded {
  padding: 0;
}

.filter-section {
  margin-bottom: 16px;
  padding: 16px;
  background: #f5f7fa;
  border-radius: 4px;
}

.pagination-wrapper {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}
</style>
