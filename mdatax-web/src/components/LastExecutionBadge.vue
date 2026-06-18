<template>
  <div v-if="status" class="last-execution-badge">
    <el-tag :type="tagType" size="small">{{ statusName }}</el-tag>
    <div v-if="time" class="sub-text">{{ formattedTime }}</div>
  </div>
  <span v-else class="no-execution">-</span>
</template>

<script setup>
import { computed } from 'vue'
import { getStatusName, getStatusTagType, formatTime } from '../utils/execution.js'

const props = defineProps({
  status: {
    type: String,
    default: ''
  },
  time: {
    type: String,
    default: ''
  }
})

const statusName = computed(() => getStatusName(props.status))
const tagType = computed(() => getStatusTagType(props.status))
const formattedTime = computed(() => formatTime(props.time))
</script>

<style scoped>
.last-execution-badge {
  display: inline-flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 2px;
}

.sub-text {
  font-size: 12px;
  color: #909399;
  line-height: 1.2;
}

.no-execution {
  color: #909399;
}
</style>
