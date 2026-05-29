/**
 * 质量监控 API
 *
 * @author fengzhu
 * @since 2026-05-10
 */

import request from '../utils/request'

/**
 * 质量规则 API
 */
export const qualityRuleApi = {
  /**
   * 获取规则模板列表
   */
  getTemplates() {
    return request.get('/quality/rules/templates')
  },

  /**
   * 获取规则列表
   */
  getList(params) {
    return request.get('/quality/rules', { params })
  },

  /**
   * 获取规则详情
   */
  getDetail(id) {
    return request.get(`/quality/rules/${id}`)
  },

  /**
   * 创建规则
   */
  create(data) {
    return request.post('/quality/rules', data)
  },

  /**
   * 更新规则
   */
  update(id, data) {
    return request.put(`/quality/rules/${id}`, data)
  },

  /**
   * 删除规则
   */
  delete(id) {
    return request.delete(`/quality/rules/${id}`)
  },

  /**
   * 启用规则
   */
  enable(id) {
    return request.post(`/quality/rules/${id}/enable`)
  },

  /**
   * 禁用规则
   */
  disable(id) {
    return request.post(`/quality/rules/${id}/disable`)
  },

  /**
   * 测试规则
   */
  testRule(id) {
    return request.post(`/quality/rules/${id}/test`)
  },

  /**
   * 复制规则
   */
  copy(id, newRuleName) {
    return request.post(`/quality/rules/${id}/copy`, null, {
      params: { newRuleName }
    })
  },

  /**
   * 获取所有数据库列表
   */
  getDatabases() {
    return request.get('/quality/rules/metadata/databases')
  },

  /**
   * 根据数据库获取表列表
   */
  getTables(database) {
    return request.get('/quality/rules/metadata/tables', {
      params: { database }
    })
  },

  /**
   * 根据表获取字段列表
   */
  getColumns(database, table) {
    return request.get('/quality/rules/metadata/columns', {
      params: { database, table }
    })
  }
}

/**
 * 质量报告 API
 */
export const qualityReportApi = {
  /**
   * 获取表的最新报告
   */
  getLatest(tableId) {
    return request.get(`/quality/reports/table/${tableId}`)
  },

  /**
   * 获取报告详情
   */
  getDetail(reportId) {
    return request.get(`/quality/reports/report/${reportId}`)
  },

  /**
   * 获取报告历史趋势
   */
  getTrend(tableId, days = 30) {
    return request.get(`/quality/reports/table/${tableId}/trend`, { params: { days } })
  },

  /**
   * 获取检查历史
   */
  getHistory(params) {
    return request.get('/quality/reports/history', { params })
  },

  /**
   * 获取报告详情结果列表
   */
  getResults(reportId) {
    return request.get(`/quality/reports/report/${reportId}/results`)
  }
}

/**
 * 质量大盘 API
 */
export const qualityDashboardApi = {
  /**
   * 获取整体概况
   */
  getOverview() {
    return request.get('/quality/dashboard/overview')
  },

  /**
   * 获取质量趋势
   */
  getTrend(days = 7) {
    return request.get('/quality/dashboard/trend', { params: { days } })
  },

  /**
   * 获取质量分布
   */
  getDistribution() {
    return request.get('/quality/dashboard/distribution')
  },

  /**
   * 获取TOP榜单
   */
  getTopRanking(type = 'score', limit = 10) {
    return request.get('/quality/dashboard/top', { params: { type, limit } })
  },

  /**
   * 获取异常列表
   */
  getAlerts(params) {
    return request.get('/quality/dashboard/alerts', { params })
  }
}
