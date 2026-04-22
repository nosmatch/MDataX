/**
 * Cron 表达式工具函数
 */

export function validateCron(cron) {
  if (!cron || !cron.trim()) {
    return { valid: true }
  }
  const tokens = cron.trim().split(/\s+/)
  if (tokens.length !== 6) {
    return { valid: false, message: 'Cron 表达式需要 6 个字段（秒 分 时 日 月 周）' }
  }
  const ranges = [
    { min: 0, max: 59, name: '秒' },
    { min: 0, max: 59, name: '分' },
    { min: 0, max: 23, name: '时' },
    { min: 1, max: 31, name: '日' },
    { min: 1, max: 12, name: '月' },
    { min: 0, max: 6, name: '周' }
  ]
  for (let i = 0; i < 6; i++) {
    const token = tokens[i]
    if (!isValidField(token, ranges[i].min, ranges[i].max)) {
      return { valid: false, message: `第 ${i + 1} 个字段（${ranges[i].name}）格式不正确` }
    }
  }
  return { valid: true }
}

function isValidField(expr, min, max) {
  if (!expr) return false
  const validPattern = /^[\d*,?/\-]+$/
  if (!validPattern.test(expr)) return false
  const nums = expr.match(/\d+/g)
  if (nums) {
    for (const n of nums) {
      const v = parseInt(n, 10)
      if (v < min || v > max) return false
    }
  }
  return true
}

export function getNextExecution(cron) {
  const tokens = cron.trim().split(/\s+/)
  if (tokens.length < 6) return null
  const [secExp, minExp, hourExp, dayExp, monthExp, weekExp] = tokens

  const now = new Date()
  now.setMilliseconds(0)
  now.setSeconds(now.getSeconds() + 1)

  const maxIter = 366 * 24 * 60 * 60 * 4
  for (let i = 0; i < maxIter; i++) {
    if (matchDate(now, secExp, minExp, hourExp, dayExp, monthExp, weekExp)) {
      return now
    }
    now.setSeconds(now.getSeconds() + 1)
  }
  return null
}

function matchDate(d, secExp, minExp, hourExp, dayExp, monthExp, weekExp) {
  return matchField(d.getSeconds(), secExp, 0, 59) &&
    matchField(d.getMinutes(), minExp, 0, 59) &&
    matchField(d.getHours(), hourExp, 0, 23) &&
    matchField(d.getDate(), dayExp, 1, 31) &&
    matchField(d.getMonth() + 1, monthExp, 1, 12) &&
    matchField(d.getDay(), weekExp, 0, 6)
}

function matchField(value, expr, min, max) {
  expr = expr.trim()
  if (expr === '*' || expr === '?') return true
  if (expr.includes(',')) {
    return expr.split(',').some(part => matchField(value, part.trim(), min, max))
  }
  if (expr.includes('/')) {
    const [base, step] = expr.split('/')
    const start = base === '*' ? min : parseInt(base, 10)
    const s = parseInt(step, 10)
    for (let v = start; v <= max; v += s) {
      if (value === v) return true
    }
    return false
  }
  if (expr.includes('-')) {
    const [start, end] = expr.split('-').map(Number)
    return value >= start && value <= end
  }
  return value === parseInt(expr, 10)
}
