import { defineStore } from 'pinia'
import { ref } from 'vue'

export const useEditorStore = defineStore('editor', () => {
  // 数据查询
  const querySql = ref('')
  // SQL 开发
  const developmentSql = ref('')

  const setQuerySql = (v) => { querySql.value = v }
  const setDevelopmentSql = (v) => { developmentSql.value = v }
  const clearQuerySql = () => { querySql.value = '' }
  const clearDevelopmentSql = () => { developmentSql.value = '' }

  return {
    querySql,
    developmentSql,
    setQuerySql,
    setDevelopmentSql,
    clearQuerySql,
    clearDevelopmentSql
  }
})
