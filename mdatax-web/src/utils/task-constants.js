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
  SYNC: 'SYNC',
  QUALITY: 'QUALITY'
}

export const TASK_TYPE_LABEL = {
  [TASK_TYPE.SQL]: 'SQL任务',
  [TASK_TYPE.SYNC]: '同步任务',
  [TASK_TYPE.QUALITY]: '质量监控任务'
}

export const TASK_TYPE_TAG_TYPE = {
  [TASK_TYPE.SQL]: 'success',
  [TASK_TYPE.SYNC]: 'primary',
  [TASK_TYPE.QUALITY]: 'warning'
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

// 质量规则模板
export const QUALITY_RULE_TEMPLATE = {
  NULL_CHECK: 'NULL_CHECK',
  ROW_COUNT_CHECK: 'ROW_COUNT_CHECK',
  ROW_COUNT_FLUCTUATION: 'ROW_COUNT_FLUCTUATION',
  UNIQUE_CHECK: 'UNIQUE_CHECK',
  ENUM_CHECK: 'ENUM_CHECK',
  REGEX_CHECK: 'REGEX_CHECK',
  NUMERIC_RANGE_CHECK: 'NUMERIC_RANGE_CHECK',
  DATE_RANGE_CHECK: 'DATE_RANGE_CHECK',
  BUSINESS_RULE: 'BUSINESS_RULE'
}

export const QUALITY_RULE_TEMPLATE_LABEL = {
  [QUALITY_RULE_TEMPLATE.NULL_CHECK]: '空值检查',
  [QUALITY_RULE_TEMPLATE.ROW_COUNT_CHECK]: '行数检查',
  [QUALITY_RULE_TEMPLATE.ROW_COUNT_FLUCTUATION]: '行数波动检查',
  [QUALITY_RULE_TEMPLATE.UNIQUE_CHECK]: '唯一性检查',
  [QUALITY_RULE_TEMPLATE.ENUM_CHECK]: '枚举值检查',
  [QUALITY_RULE_TEMPLATE.REGEX_CHECK]: '正则检查',
  [QUALITY_RULE_TEMPLATE.NUMERIC_RANGE_CHECK]: '数值范围检查',
  [QUALITY_RULE_TEMPLATE.DATE_RANGE_CHECK]: '日期范围检查',
  [QUALITY_RULE_TEMPLATE.BUSINESS_RULE]: '业务规则检查'
}

// 依赖类型
export const DEPENDENCY_TYPE = {
  SUCCESS: 'SUCCESS',
  FAILED: 'FAILED',
  ANY: 'ANY'
}

export const DEPENDENCY_TYPE_LABEL = {
  'SUCCESS': '成功触发',
  'FAILED': '失败触发',
  'ANY': '任意触发'
}

export const DEPENDENCY_TYPE_TAG_TYPE = {
  'SUCCESS': 'success',
  'FAILED': 'danger',
  'ANY': 'warning'
}
