<template>
  <div class="query-page">
    <div class="page-header">
      <h2>数据查询</h2>
      <p class="desc">输入 SQL 查询语句，执行后查看结果（仅支持 SELECT）</p>
    </div>

    <div class="main-layout">
      <!-- 左侧：有权限的表 + 字段 -->
      <div class="left-panel" :style="{ width: leftWidth + 'px' }">
        <div class="table-list-area" :style="{ height: topHeight + 'px' }">
          <el-card class="panel-card">
            <template #header>
              <div>
                <span>可读的表</span>
                <el-input
                  v-model="tableKeyword"
                  placeholder="搜索表"
                  size="small"
                  clearable
                  style="margin-top: 8px"
                />
              </div>
            </template>
            <div class="menu-wrapper">
              <el-menu
                :default-active="String(selectedTableId)"
                @select="handleTableSelect"
                style="border-right: none"
              >
                <el-menu-item
                  v-for="t in filteredTables"
                  :key="t.id"
                  :index="String(t.id)"
                >
                  {{ t.databaseName }}.{{ t.tableName }}
                </el-menu-item>
              </el-menu>
            </div>
          </el-card>
        </div>

        <div class="h-divider" @mousedown="startHResize"></div>

        <div class="columns-area" :style="{ height: `calc(100% - ${topHeight + 6}px)` }">
          <el-card class="panel-card" v-if="selectedTableColumns.length > 0">
            <template #header>
              <span>{{ selectedTableName }} 字段</span>
            </template>
            <div class="table-wrapper">
              <el-table :data="selectedTableColumns" size="small" stripe>
                <el-table-column label="字段名" min-width="120" show-overflow-tooltip>
                  <template #default="{ row }">
                    <span
                      class="copyable-field"
                      :title="'点击复制: ' + row.columnName"
                      @click="copyToClipboard(row.columnName)"
                    >
                      {{ row.columnName }}
                    </span>
                  </template>
                </el-table-column>
                <el-table-column prop="dataType" label="类型" width="100" />
              </el-table>
            </div>
          </el-card>
          <el-empty v-else description="点击左侧表查看字段" style="height: 100%" />
        </div>
      </div>

      <!-- 垂直分割线 -->
      <div class="v-divider" @mousedown="startVResize"></div>

      <!-- 右侧：编辑器 + 结果 -->
      <div class="right-panel">
        <el-card class="editor-card">
          <div class="toolbar">
            <el-button type="primary" :loading="executing" @click="executeQuery">
              <el-icon><VideoPlay /></el-icon> 执行
            </el-button>
            <el-button @click="formatSql">
              <el-icon><MagicStick /></el-icon> 格式化
            </el-button>
            <el-button @click="clearSql">
              <el-icon><Delete /></el-icon> 清空
            </el-button>
          </div>
          <div class="editor-wrapper">
            <MonacoEditor v-model="sql" language="sql" theme="vs" :options="editorOptions" :suggestions="editorSuggestions" />
          </div>
        </el-card>

        <el-card v-if="resultLoaded" class="result-card" v-loading="executing">
          <template #header>
            <div class="result-header">
              <span>查询结果</span>
              <div class="result-meta">
                <el-tag type="info" size="small">行数: {{ result.rowCount }}</el-tag>
                <el-tag type="success" size="small" style="margin-left: 8px">
                  耗时: {{ result.executionTime }}ms
                </el-tag>
              </div>
            </div>
          </template>

          <div class="result-table-wrapper">
            <el-table
              :data="result.rows"
              stripe
              border
              v-if="result.columns.length > 0"
              style="width: 100%"
            >
              <el-table-column
                v-for="col in result.columns"
                :key="col"
                :prop="col"
                :label="col"
                min-width="160"
                show-overflow-tooltip
              />
            </el-table>
            <el-empty v-else description="查询结果为空" />
          </div>
        </el-card>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { VideoPlay, Delete, MagicStick } from '@element-plus/icons-vue'
import { format } from 'sql-formatter'
import MonacoEditor from '../components/MonacoEditor.vue'
import request from '../utils/request.js'

// === 拖拽调整大小 ===
const leftWidth = ref(260)
const topHeight = ref(300)

let vResizing = false
let hResizing = false
let startX = 0
let startY = 0
let startWidth = 0
let startHeight = 0

const startVResize = (e) => {
  vResizing = true
  startX = e.clientX
  startWidth = leftWidth.value
  document.addEventListener('mousemove', onVMouseMove)
  document.addEventListener('mouseup', onVMouseUp)
}

const onVMouseMove = (e) => {
  if (!vResizing) return
  const delta = e.clientX - startX
  leftWidth.value = Math.max(180, Math.min(500, startWidth + delta))
}

const onVMouseUp = () => {
  vResizing = false
  document.removeEventListener('mousemove', onVMouseMove)
  document.removeEventListener('mouseup', onVMouseUp)
}

const startHResize = (e) => {
  hResizing = true
  startY = e.clientY
  startHeight = topHeight.value
  document.addEventListener('mousemove', onHMouseMove)
  document.addEventListener('mouseup', onHMouseUp)
}

const onHMouseMove = (e) => {
  if (!hResizing) return
  const delta = e.clientY - startY
  topHeight.value = Math.max(120, startHeight + delta)
}

const onHMouseUp = () => {
  hResizing = false
  document.removeEventListener('mousemove', onHMouseMove)
  document.removeEventListener('mouseup', onHMouseUp)
}

// === 左侧表列表 ===
const tables = ref([])
const tableKeyword = ref('')
const selectedTableId = ref(null)
const selectedTableColumns = ref([])
const selectedTableName = ref('')

const filteredTables = computed(() => {
  if (!tableKeyword.value) return tables.value
  const kw = tableKeyword.value.toLowerCase()
  return tables.value.filter(t =>
    (t.databaseName + '.' + t.tableName).toLowerCase().includes(kw)
  )
})

const loadAccessibleTables = async () => {
  try {
    const res = await request.get('/query/tables')
    tables.value = res.data || []
  } catch (error) {
    ElMessage.error(error.message || '加载表列表失败')
  }
}

const handleTableSelect = (index) => {
  const id = Number(index)
  selectedTableId.value = id
  const t = tables.value.find(item => item.id === id)
  if (t) {
    selectedTableName.value = t.databaseName + '.' + t.tableName
    selectedTableColumns.value = t.columns || []
  } else {
    selectedTableName.value = ''
    selectedTableColumns.value = []
  }
}

// === 右侧编辑器 ===
const sql = ref('')
const executing = ref(false)
const resultLoaded = ref(false)
const result = ref({
  columns: [],
  rows: [],
  rowCount: 0,
  executionTime: 0
})

const SQL_KEYWORDS = [
  'SELECT', 'FROM', 'WHERE', 'AND', 'OR', 'NOT', 'IN', 'EXISTS',
  'BETWEEN', 'LIKE', 'IS', 'NULL', 'AS', 'JOIN', 'INNER', 'LEFT',
  'RIGHT', 'FULL', 'OUTER', 'CROSS', 'ON', 'GROUP', 'BY', 'ORDER',
  'HAVING', 'LIMIT', 'OFFSET', 'UNION', 'ALL', 'DISTINCT',
  'COUNT', 'SUM', 'AVG', 'MAX', 'MIN', 'CASE', 'WHEN', 'THEN',
  'ELSE', 'END', 'CAST', 'COALESCE', 'IF', 'WITH', 'OVER',
  'PARTITION', 'ROW_NUMBER', 'RANK', 'DENSE_RANK',
  'ASC', 'DESC', 'TRUE', 'FALSE'
]

const editorSuggestions = computed(() => {
  const list = []
  // SQL 关键字（kind 17 = Keyword）
  SQL_KEYWORDS.forEach(kw => {
    list.push({ label: kw, kind: 17, detail: '关键字', insertText: kw })
  })
  // 有权限的表名（kind 6 = Class）
  tables.value.forEach(t => {
    const fullName = t.databaseName + '.' + t.tableName
    list.push({ label: fullName, kind: 6, detail: '表', insertText: fullName })
    list.push({ label: t.tableName, kind: 6, detail: '表 (' + fullName + ')', insertText: t.tableName })
  })
  // 当前选中表的字段（kind 4 = Field）
  selectedTableColumns.value.forEach(c => {
    list.push({ label: c.columnName, kind: 4, detail: '字段 (' + selectedTableName.value + ')', insertText: c.columnName })
  })
  return list
})

const editorOptions = {
  fontSize: 14,
  minimap: { enabled: false },
  automaticLayout: true,
  scrollBeyondLastLine: false,
  lineNumbers: 'on',
  roundedSelection: false
}

const executeQuery = async () => {
  const trimmed = sql.value.trim()
  if (!trimmed) {
    ElMessage.warning('请输入 SQL 语句')
    return
  }

  executing.value = true
  resultLoaded.value = true
  try {
    const res = await request.post('/query/execute', { sql: trimmed })
    if (res.code === 200) {
      result.value = res.data
      ElMessage.success('查询成功')
    } else {
      ElMessage.error(res.message || '查询失败')
      result.value = { columns: [], rows: [], rowCount: 0, executionTime: 0 }
    }
  } catch (error) {
    ElMessage.error(error.message || '查询失败')
    result.value = { columns: [], rows: [], rowCount: 0, executionTime: 0 }
  } finally {
    executing.value = false
  }
}

const formatSql = () => {
  const trimmed = sql.value.trim()
  if (!trimmed) {
    ElMessage.warning('请输入 SQL 语句')
    return
  }
  try {
    sql.value = format(trimmed, { language: 'sql' })
    ElMessage.success('格式化完成')
  } catch (e) {
    ElMessage.error('SQL 格式化失败，请检查语法')
  }
}

const clearSql = () => {
  sql.value = ''
  resultLoaded.value = false
}

const copyToClipboard = async (text) => {
  try {
    await navigator.clipboard.writeText(text)
    ElMessage.success(`已复制: ${text}`)
  } catch (err) {
    ElMessage.warning('复制失败，请手动复制')
  }
}

onMounted(() => {
  loadAccessibleTables()
})
</script>

<style scoped>
.query-page {
  display: flex;
  flex-direction: column;
  height: 100%;
}
.page-header {
  flex-shrink: 0;
  margin-bottom: 16px;
}
.page-header h2 {
  margin: 0;
}
.desc {
  color: #909399;
  font-size: 14px;
  margin-top: 4px;
}

.main-layout {
  flex: 1;
  min-height: 0;
  display: flex;
  overflow: hidden;
}

/* 左侧面板 */
.left-panel {
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.table-list-area {
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.panel-card {
  height: 100%;
  display: flex;
  flex-direction: column;
}
.panel-card :deep(.el-card__body) {
  flex: 1;
  min-height: 0;
  overflow: hidden;
  padding: 0;
}

.menu-wrapper {
  height: 100%;
  overflow: auto;
}
.menu-wrapper :deep(.el-menu-item) {
  height: 32px;
  line-height: 32px;
  padding: 0 12px;
  font-size: 13px;
}
.menu-wrapper :deep(.el-menu-item .el-menu-tooltip__trigger) {
  padding: 0 12px;
}

/* 水平分割线 */
.h-divider {
  height: 6px;
  flex-shrink: 0;
  cursor: row-resize;
  background: transparent;
  position: relative;
}
.h-divider::after {
  content: '';
  position: absolute;
  left: 30%;
  right: 30%;
  top: 2px;
  height: 2px;
  background: #dcdfe6;
  border-radius: 1px;
}
.h-divider:hover::after {
  background: #409eff;
}

.columns-area {
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.table-wrapper {
  height: 100%;
  overflow: auto;
}
.copyable-field {
  color: #409eff;
  cursor: pointer;
  user-select: none;
}
.copyable-field:hover {
  text-decoration: underline;
}

/* 垂直分割线 */
.v-divider {
  width: 6px;
  flex-shrink: 0;
  cursor: col-resize;
  background: transparent;
  position: relative;
}
.v-divider::after {
  content: '';
  position: absolute;
  top: 30%;
  bottom: 30%;
  left: 2px;
  width: 2px;
  background: #dcdfe6;
  border-radius: 1px;
}
.v-divider:hover::after {
  background: #409eff;
}

/* 右侧面板 */
.right-panel {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 16px;
  overflow: hidden;
}

.editor-card {
  flex-shrink: 0;
}
.toolbar {
  display: flex;
  gap: 8px;
  margin-bottom: 12px;
}
.editor-wrapper {
  height: 240px;
}
.result-card {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
}
.result-card :deep(.el-card__body) {
  flex: 1;
  min-height: 0;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}
.result-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.result-meta {
  display: flex;
  align-items: center;
}
.result-table-wrapper {
  flex: 1;
  min-height: 0;
  overflow: auto;
}
</style>
