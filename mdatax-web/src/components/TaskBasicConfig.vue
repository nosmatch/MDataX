<template>
  <div v-if="task" class="task-basic-config">
    <!-- 执行统计 -->
    <div v-if="statistics" class="stats-row">
      <el-card v-for="stat in statList" :key="stat.label" class="stat-card" shadow="never">
        <div class="stat-value">{{ stat.value }}</div>
        <div class="stat-label">{{ stat.label }}</div>
      </el-card>
    </div>

    <!-- 基本信息 -->
    <el-card class="config-card" shadow="never">
      <template #header>
        <span>基本信息</span>
      </template>
      <el-descriptions :column="2" border>
        <el-descriptions-item label="任务编码">{{ task.taskCode }}</el-descriptions-item>
        <el-descriptions-item label="任务名称">{{ task.taskName }}</el-descriptions-item>
        <el-descriptions-item label="任务类型">
          {{ TASK_TYPE_LABEL[task.taskType] || task.taskType }}
        </el-descriptions-item>
        <el-descriptions-item label="责任人">{{ task.ownerUserName || task.ownerUserId || '-' }}</el-descriptions-item>
        <el-descriptions-item label="优先级">{{ task.priority }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          {{ TASK_STATUS_LABEL[task.status] || task.status }}
        </el-descriptions-item>
        <el-descriptions-item label="Cron表达式">{{ task.cronExpression || '-' }}</el-descriptions-item>
        <el-descriptions-item label="下次执行">{{ nextExecutionTime }}</el-descriptions-item>
        <el-descriptions-item label="最近执行">
          <LastExecutionBadge :status="task.lastExecutionStatus" :time="task.lastExecutionTime" />
        </el-descriptions-item>
        <el-descriptions-item label="标签">{{ task.tags || '-' }}</el-descriptions-item>
        <el-descriptions-item label="重试次数">{{ task.retryTimes || 0 }}</el-descriptions-item>
        <el-descriptions-item label="重试间隔">{{ (task.retryInterval || 0) + '秒' }}</el-descriptions-item>
        <el-descriptions-item label="超时时间">
          {{ (task.timeoutSeconds || 0) === 0 ? '不限制' : task.timeoutSeconds + '秒' }}
        </el-descriptions-item>
        <el-descriptions-item label="创建时间">{{ task.createTime }}</el-descriptions-item>
        <el-descriptions-item label="描述" :span="2">{{ task.description || '-' }}</el-descriptions-item>
      </el-descriptions>
    </el-card>

    <!-- 任务配置 -->
    <el-card v-if="detail" class="config-card" shadow="never">
      <template #header>
        <span>任务配置</span>
      </template>
      <el-descriptions v-if="task.taskType === TASK_TYPE.SQL" :column="1" border>
        <el-descriptions-item label="SQL内容">
          <pre class="sql-content">{{ detail.sqlContent || '无' }}</pre>
        </el-descriptions-item>
        <el-descriptions-item label="目标数据源ID">{{ detail.targetDatasourceId || '-' }}</el-descriptions-item>
      </el-descriptions>

      <el-descriptions v-else-if="task.taskType === TASK_TYPE.SYNC" :column="2" border>
        <el-descriptions-item label="源数据源ID">{{ detail.sourceDatasourceId }}</el-descriptions-item>
        <el-descriptions-item label="源表">{{ detail.sourceTable }}</el-descriptions-item>
        <el-descriptions-item label="目标数据源ID">{{ detail.targetDatasourceId }}</el-descriptions-item>
        <el-descriptions-item label="目标表">{{ detail.targetTable }}</el-descriptions-item>
        <el-descriptions-item label="同步类型">{{ SYNC_TYPE_LABEL[detail.syncType] || detail.syncType }}</el-descriptions-item>
        <el-descriptions-item label="时间字段">{{ detail.timeField || '-' }}</el-descriptions-item>
        <el-descriptions-item label="过滤条件" :span="2">{{ detail.whereCondition || '-' }}</el-descriptions-item>
      </el-descriptions>
    </el-card>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, watch } from 'vue'
import { ElMessage } from 'element-plus'
import request from '../utils/request.js'
import LastExecutionBadge from './LastExecutionBadge.vue'
import { formatDuration, formatTime } from '../utils/execution.js'
import {
  TASK_STATUS_LABEL,
  TASK_TYPE,
  TASK_TYPE_LABEL,
  SYNC_TYPE_LABEL
} from '../utils/task-constants.js'

const props = defineProps({
  task: {
    type: Object,
    required: true
  },
  detail: {
    type: Object,
    default: null
  }
})

const statistics = ref(null)
const nextExecutionTime = ref('-')

const statList = computed(() => [
  { label: '总执行次数', value: statistics.value?.totalExecutions || 0 },
  { label: '成功率', value: (statistics.value?.successRate || 0) + '%' },
  { label: '平均耗时', value: formatDuration(statistics.value?.avgDurationMs) },
  { label: '失败次数', value: statistics.value?.failedCount || 0 }
])

const loadStatistics = async () => {
  try {
    const res = await request.get(`/task/${props.task.id}/statistics`)
    statistics.value = res.data
  } catch (error) {
    statistics.value = null
    console.error('加载任务统计失败', error)
    ElMessage.warning(error.message || '加载任务统计失败')
  }
}

const calculateNextExecution = () => {
  if (!props.task?.cronExpression) {
    nextExecutionTime.value = '-'
    return
  }
  // 简单计算下一次执行时间，实际项目中可以使用 cron-parser 库
  nextExecutionTime.value = '-'
}

onMounted(() => {
  loadStatistics()
  calculateNextExecution()
})

watch(() => props.task?.id, () => {
  loadStatistics()
  calculateNextExecution()
})
</script>

<style scoped>
.task-basic-config {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.stats-row {
  display: flex;
  gap: 16px;
  flex-wrap: wrap;
}

.stat-card {
  flex: 1;
  min-width: 120px;
  text-align: center;
}

.stat-card :deep(.el-card__body) {
  padding: 12px;
}

.stat-value {
  font-size: 24px;
  font-weight: bold;
  color: #303133;
  line-height: 1.2;
}

.stat-label {
  font-size: 13px;
  color: #606266;
  margin-top: 4px;
}

.config-card :deep(.el-card__header) {
  font-weight: 600;
}

.sql-content {
  margin: 0;
  padding: 12px;
  background: #f5f7fa;
  border-radius: 4px;
  font-family: 'Courier New', Consolas, monospace;
  font-size: 13px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-all;
  max-height: 400px;
  overflow-y: auto;
}
</style>
