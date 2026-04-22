<template>
  <div class="cron-picker">
    <el-radio-group v-model="mode" size="small" @change="onModeChange">
      <el-radio-button label="hourly">每小时</el-radio-button>
      <el-radio-button label="daily">每天</el-radio-button>
      <el-radio-button label="weekly">每周一</el-radio-button>
      <el-radio-button label="monthly">每月1号</el-radio-button>
      <el-radio-button label="custom">自定义</el-radio-button>
    </el-radio-group>

    <div v-if="mode === 'custom'" class="custom-cron">
      <div class="cron-fields">
        <div v-for="(item, idx) in fieldMeta" :key="idx" class="field">
          <el-tooltip :content="item.tip" placement="top">
            <el-input
              v-model="parts[idx]"
              size="small"
              class="field-input"
              @input="onCustomChange"
            />
          </el-tooltip>
          <span class="field-label">{{ item.label }}</span>
        </div>
      </div>
    </div>

    <div v-if="nextExecText" class="preview">
      <el-text type="info" size="small">
        <el-icon><Clock /></el-icon>
        下次执行：{{ nextExecText }}
      </el-text>
    </div>
  </div>
</template>

<script setup>
import { ref, watch, computed } from 'vue'
import { Clock } from '@element-plus/icons-vue'
import { getNextExecution } from '../utils/cron.js'

const props = defineProps({
  modelValue: { type: String, default: '' }
})
const emit = defineEmits(['update:modelValue'])

const mode = ref('custom')
const parts = ref(['0', '0', '*', '*', '*', '?'])

const fieldMeta = [
  { label: '秒', tip: '0-59，如 0 或 0,30 或 */30' },
  { label: '分', tip: '0-59，如 0 或 0,30 或 */30' },
  { label: '时', tip: '0-23，如 2 或 2,14' },
  { label: '日', tip: '1-31 或 * 或 ?' },
  { label: '月', tip: '1-12 或 *' },
  { label: '周', tip: '0-6 或 * 或 ?（0=周日）' }
]

const presets = {
  hourly: '0 0 * * * ?',
  daily: '0 0 2 * * ?',
  weekly: '0 0 2 * * 1',
  monthly: '0 0 2 1 * ?'
}

const nextExecText = computed(() => {
  const cron = props.modelValue
  if (!cron) return ''
  try {
    const next = getNextExecution(cron)
    if (next) {
      return `${next.getFullYear()}-${pad(next.getMonth() + 1)}-${pad(next.getDate())} ${pad(next.getHours())}:${pad(next.getMinutes())}:${pad(next.getSeconds())}`
    }
  } catch (e) {
    // ignore
  }
  return ''
})

function pad(n) {
  return n < 10 ? '0' + n : n
}

function onModeChange(val) {
  if (val !== 'custom' && presets[val]) {
    emit('update:modelValue', presets[val])
  }
}

function onCustomChange() {
  const cron = parts.value.join(' ')
  emit('update:modelValue', cron)
}

// 从 props.modelValue 同步回 mode 和 parts
watch(() => props.modelValue, (val) => {
  if (!val) {
    mode.value = 'custom'
    parts.value = ['0', '0', '*', '*', '*', '?']
    return
  }
  // 检测是否是预设值
  const matchedPreset = Object.entries(presets).find(([, v]) => v === val)
  if (matchedPreset) {
    mode.value = matchedPreset[0]
    parts.value = val.split(' ')
  } else {
    mode.value = 'custom'
    const p = val.split(' ')
    while (p.length < 6) p.push('*')
    parts.value = p.slice(0, 6)
  }
}, { immediate: true })
</script>

<style scoped>
.cron-picker {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.custom-cron {
  margin-top: 4px;
}
.cron-fields {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}
.field {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 2px;
}
.field-input {
  width: 70px;
}
.field-label {
  font-size: 12px;
  color: #606266;
}
.preview {
  margin-top: 4px;
}
</style>
