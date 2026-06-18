<template>
  <el-drawer
    v-model="visible"
    title="执行详情"
    size="500px"
    :close-on-click-modal="false"
    @open="loadExecution"
  >
    <el-skeleton v-if="loading" :rows="8" animated />
    <template v-else-if="execution">
      <el-descriptions :column="2" border>
        <el-descriptions-item label="执行ID" :span="2">
          <el-tag size="small" type="info">{{ execution.executionId }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="任务名称" :span="2">{{ execution.taskName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="触发方式">
          <el-tag :type="getTriggerTagType(execution.triggerType)" size="small">
            {{ getTriggerName(execution.triggerType) }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="getStatusTagType(execution.status)" size="small">
            {{ getStatusName(execution.status) }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="开始时间">{{ execution.startTime || '-' }}</el-descriptions-item>
        <el-descriptions-item label="结束时间">{{ execution.endTime || '-' }}</el-descriptions-item>
        <el-descriptions-item label="耗时">{{ formatDuration(execution.durationMs) }}</el-descriptions-item>
        <el-descriptions-item label="尝试次数">{{ execution.attemptNumber }} / {{ execution.maxAttempts }}</el-descriptions-item>
        <el-descriptions-item v-if="execution.affectedRows != null" label="影响行数">{{ execution.affectedRows }}</el-descriptions-item>
        <el-descriptions-item v-if="execution.syncCount != null" label="同步数量">{{ execution.syncCount }}</el-descriptions-item>
        <el-descriptions-item v-if="execution.schedulerInstanceId" label="调度器实例" :span="2">{{ execution.schedulerInstanceId }}</el-descriptions-item>
        <el-descriptions-item v-if="execution.logUrl" label="日志链接" :span="2">
          <el-link :href="execution.logUrl" target="_blank" type="primary">查看日志</el-link>
        </el-descriptions-item>
      </el-descriptions>

      <div v-if="execution.errorMsg" class="error-section">
        <div class="error-title">错误信息</div>
        <pre class="error-content">{{ execution.errorMsg }}</pre>
      </div>
    </template>

    <template #footer>
      <div class="drawer-footer">
        <el-button @click="visible = false">关闭</el-button>
        <el-button
          v-if="execution?.status === EXECUTION_STATUS.RUNNING"
          type="danger"
          :loading="killing"
          @click="handleKill"
        >
          终止
        </el-button>
        <el-button
          v-if="execution?.status === EXECUTION_STATUS.FAILED || execution?.status === EXECUTION_STATUS.TIMEOUT"
          type="warning"
          :loading="retrying"
          @click="handleRetry"
        >
          重试
        </el-button>
      </div>
    </template>
  </el-drawer>
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import { ElMessage } from 'element-plus'
import request from '../utils/request.js'
import {
  getStatusName,
  getStatusTagType,
  getTriggerName,
  getTriggerTagType,
  formatDuration
} from '../utils/execution.js'
import { EXECUTION_STATUS } from '../utils/task-constants.js'

const props = defineProps({
  modelValue: {
    type: Boolean,
    default: false
  },
  executionId: {
    type: String,
    default: ''
  }
})

const emit = defineEmits(['update:modelValue', 'retry', 'kill'])

const visible = computed({
  get: () => props.modelValue,
  set: (val) => emit('update:modelValue', val)
})

const loading = ref(false)
const execution = ref(null)
const killing = ref(false)
const retrying = ref(false)

const loadExecution = async () => {
  if (!props.executionId) return
  loading.value = true
  try {
    const res = await request.get(`/task-execution/${props.executionId}`)
    if (res.code === 200 && res.data) {
      execution.value = res.data
    }
  } catch (error) {
    ElMessage.error(error.message || '获取执行详情失败')
  } finally {
    loading.value = false
  }
}

const handleKill = async () => {
  killing.value = true
  try {
    await request.post(`/task-execution/${props.executionId}/kill`)
    ElMessage.success('终止成功')
    emit('kill')
    loadExecution()
  } catch (error) {
    ElMessage.error(error.message || '终止失败')
  } finally {
    killing.value = false
  }
}

const handleRetry = async () => {
  retrying.value = true
  try {
    await request.post(`/task-execution/${props.executionId}/retry`)
    ElMessage.success('重试成功')
    emit('retry')
    loadExecution()
  } catch (error) {
    ElMessage.error(error.message || '重试失败')
  } finally {
    retrying.value = false
  }
}

watch(() => props.executionId, () => {
  if (visible.value && props.executionId) {
    loadExecution()
  }
})
</script>

<style scoped>
.error-section {
  margin-top: 16px;
}

.error-title {
  font-size: 14px;
  font-weight: 600;
  color: #303133;
  margin-bottom: 8px;
}

.error-content {
  background: #fdf2f2;
  color: #c45656;
  padding: 12px;
  border-radius: 4px;
  max-height: 300px;
  overflow-y: auto;
  white-space: pre-wrap;
  word-break: break-all;
  margin: 0;
  font-family: 'Courier New', monospace;
  font-size: 13px;
}

.drawer-footer {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}
</style>
