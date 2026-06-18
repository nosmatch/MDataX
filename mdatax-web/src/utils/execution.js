/**
 * 任务执行相关工具函数
 */

import { EXECUTION_STATUS, TRIGGER_TYPE } from './task-constants.js'

export const STATUS_NAME_MAP = {
  [EXECUTION_STATUS.PENDING]: '等待中',
  [EXECUTION_STATUS.WAITING_UPSTREAM]: '等待上游',
  [EXECUTION_STATUS.RUNNING]: '运行中',
  [EXECUTION_STATUS.SUCCESS]: '成功',
  'FAILURE': '失败',
  [EXECUTION_STATUS.FAILED]: '失败',
  [EXECUTION_STATUS.TIMEOUT]: '超时',
  [EXECUTION_STATUS.KILLED]: '已终止',
  [EXECUTION_STATUS.STOPPED]: '已停止',
  [EXECUTION_STATUS.SKIPPED]: '已跳过'
}

export const STATUS_TAG_MAP = {
  [EXECUTION_STATUS.PENDING]: 'info',
  [EXECUTION_STATUS.WAITING_UPSTREAM]: 'info',
  [EXECUTION_STATUS.RUNNING]: 'primary',
  [EXECUTION_STATUS.SUCCESS]: 'success',
  'FAILURE': 'danger',
  [EXECUTION_STATUS.FAILED]: 'danger',
  [EXECUTION_STATUS.TIMEOUT]: 'warning',
  [EXECUTION_STATUS.KILLED]: 'info',
  [EXECUTION_STATUS.STOPPED]: 'info',
  [EXECUTION_STATUS.SKIPPED]: 'info'
}

export const TRIGGER_NAME_MAP = {
  [TRIGGER_TYPE.SCHEDULE]: '定时',
  [TRIGGER_TYPE.MANUAL]: '手动',
  [TRIGGER_TYPE.DEPENDENCY]: '依赖',
  [TRIGGER_TYPE.API]: 'API'
}

export const TRIGGER_TAG_MAP = {
  [TRIGGER_TYPE.SCHEDULE]: '',
  [TRIGGER_TYPE.MANUAL]: 'success',
  [TRIGGER_TYPE.DEPENDENCY]: 'warning',
  [TRIGGER_TYPE.API]: 'info'
}

export function getStatusName(status) {
  return STATUS_NAME_MAP[status] || status || '-'
}

export function getStatusTagType(status) {
  return STATUS_TAG_MAP[status] || 'info'
}

export function getTriggerName(triggerType) {
  return TRIGGER_NAME_MAP[triggerType] || triggerType || '-'
}

export function getTriggerTagType(triggerType) {
  return TRIGGER_TAG_MAP[triggerType] || ''
}

export function formatDuration(ms) {
  if (ms == null || ms === undefined || ms === '') {
    return '-'
  }
  const num = Number(ms)
  if (Number.isNaN(num)) {
    return '-'
  }
  if (num < 1000) {
    return num + 'ms'
  }
  if (num < 60 * 1000) {
    return (num / 1000).toFixed(1) + 's'
  }
  if (num < 60 * 60 * 1000) {
    const minutes = Math.floor(num / (60 * 1000))
    const seconds = Math.floor((num % (60 * 1000)) / 1000)
    return `${minutes}m${seconds}s`
  }
  const hours = Math.floor(num / (60 * 60 * 1000))
  const minutes = Math.floor((num % (60 * 60 * 1000)) / (60 * 1000))
  return `${hours}h${minutes}m`
}

export function formatTime(value) {
  if (!value) {
    return '-'
  }
  if (typeof value === 'string' && /^\d{4}-\d{2}-\d{2}/.test(value)) {
    return value
  }
  const d = new Date(value)
  if (isNaN(d.getTime())) {
    return value
  }
  const pad = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}
