/**
 * 任务相关常量枚举
 */

// 任务状态
export const TASK_STATUS = {
  ENABLED: 1,
  DISABLED: 0,
  DRAFT: 2
}

export const TASK_STATUS_LABEL = {
  [TASK_STATUS.ENABLED]: '启用',
  [TASK_STATUS.DISABLED]: '停用',
  [TASK_STATUS.DRAFT]: '草稿'
}

export const TASK_STATUS_TAG_TYPE = {
  [TASK_STATUS.ENABLED]: 'success',
  [TASK_STATUS.DISABLED]: 'warning',
  [TASK_STATUS.DRAFT]: 'info'
}

// 任务类型
export const TASK_TYPE = {
  SQL: 'SQL',
  SYNC: 'SYNC'
}

export const TASK_TYPE_LABEL = {
  [TASK_TYPE.SQL]: 'SQL任务',
  [TASK_TYPE.SYNC]: '同步任务'
}

export const TASK_TYPE_TAG_TYPE = {
  [TASK_TYPE.SQL]: 'success',
  [TASK_TYPE.SYNC]: 'primary'
}

// 执行状态
export const EXECUTION_STATUS = {
  PENDING: 'PENDING',
  WAITING_UPSTREAM: 'WAITING_UPSTREAM',
  RUNNING: 'RUNNING',
  SUCCESS: 'SUCCESS',
  FAILED: 'FAILED',
  TIMEOUT: 'TIMEOUT',
  KILLED: 'KILLED',
  STOPPED: 'STOPPED',
  SKIPPED: 'SKIPPED'
}

// 触发方式
export const TRIGGER_TYPE = {
  SCHEDULE: 'SCHEDULE',
  MANUAL: 'MANUAL',
  DEPENDENCY: 'DEPENDENCY',
  API: 'API'
}

export const TRIGGER_TYPE_LABEL = {
  [TRIGGER_TYPE.SCHEDULE]: '定时',
  [TRIGGER_TYPE.MANUAL]: '手动',
  [TRIGGER_TYPE.DEPENDENCY]: '依赖',
  [TRIGGER_TYPE.API]: 'API'
}

export const TRIGGER_TYPE_TAG_TYPE = {
  [TRIGGER_TYPE.SCHEDULE]: '',
  [TRIGGER_TYPE.MANUAL]: 'success',
  [TRIGGER_TYPE.DEPENDENCY]: 'warning',
  [TRIGGER_TYPE.API]: 'info'
}

// 优先级
export const PRIORITY_LABEL = {
  high: { min: 7, max: 10, label: '高' },
  medium: { min: 4, max: 6, label: '中' },
  low: { min: 1, max: 3, label: '低' }
}

export function getPriorityLabel(priority) {
  if (priority >= PRIORITY_LABEL.high.min) return PRIORITY_LABEL.high.label
  if (priority >= PRIORITY_LABEL.medium.min) return PRIORITY_LABEL.medium.label
  return PRIORITY_LABEL.low.label
}

export function getPriorityTagType(priority) {
  if (priority >= PRIORITY_LABEL.high.min) return 'danger'
  if (priority >= PRIORITY_LABEL.medium.min) return 'warning'
  return 'info'
}

// 同步类型
export const SYNC_TYPE = {
  FULL: 'FULL',
  INCR: 'INCR'
}

export const SYNC_TYPE_LABEL = {
  [SYNC_TYPE.FULL]: '全量同步',
  [SYNC_TYPE.INCR]: '增量同步'
}

// 依赖类型
export const DEPENDENCY_TYPE = {
  SUCCESS: 'SUCCESS',
  FAILED: 'FAILED',
  ANY: 'ANY'
}

export const DEPENDENCY_TYPE_LABEL = {
  [DEPENDENCY_TYPE.SUCCESS]: '成功触发',
  [DEPENDENCY_TYPE.FAILED]: '失败触发',
  [DEPENDENCY_TYPE.ANY]: '任意触发'
}

export const DEPENDENCY_TYPE_TAG_TYPE = {
  [DEPENDENCY_TYPE.SUCCESS]: 'success',
  [DEPENDENCY_TYPE.FAILED]: 'danger',
  [DEPENDENCY_TYPE.ANY]: 'warning'
}
