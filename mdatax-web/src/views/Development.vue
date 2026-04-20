<template>
  <div class="development-page">
    <div class="page-header">
      <h2>数据开发</h2>
    </div>

    <el-tabs v-model="activeTab" type="border-card">
      <!-- SQL 开发 -->
      <el-tab-pane label="SQL 开发" name="editor">
        <div class="tab-content">
          <el-card class="editor-card">
            <div class="toolbar">
              <el-button type="primary" :loading="executing" @click="executeSql">
                <el-icon><VideoPlay /></el-icon> 执行
              </el-button>
              <el-button @click="formatSql">
                <el-icon><MagicStick /></el-icon> 格式化
              </el-button>
              <el-button @click="clearSql">
                <el-icon><Delete /></el-icon> 清空
              </el-button>
              <el-button type="success" @click="openSaveDialog">
                <el-icon><DocumentChecked /></el-icon> 保存为任务
              </el-button>
            </div>
            <div class="editor-wrapper">
              <MonacoEditor v-model="sql" language="sql" theme="vs" :options="editorOptions" />
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
      </el-tab-pane>

      <!-- 任务管理 -->
      <el-tab-pane label="任务管理" name="list">
        <div class="tab-content">
          <div class="tab-toolbar">
            <el-input
              v-model="keyword"
              placeholder="搜索任务名称"
              clearable
              style="width: 300px"
              @keyup.enter="fetchTasks"
            >
              <template #append>
                <el-button @click="fetchTasks">
                  <el-icon><Search /></el-icon>
                </el-button>
              </template>
            </el-input>
            <el-button type="primary" @click="openCreateDialog">
              <el-icon><Plus /></el-icon> 新建任务
            </el-button>
          </div>

          <el-table :data="taskList" v-loading="loading" stripe>
            <el-table-column prop="taskName" label="任务名称" min-width="180" />
            <el-table-column prop="targetTable" label="目标表" min-width="160">
              <template #default="{ row }">
                {{ row.targetTable || '-' }}
              </template>
            </el-table-column>
            <el-table-column prop="cronExpression" label="Cron表达式" min-width="140">
              <template #default="{ row }">
                {{ row.cronExpression || '-' }}
              </template>
            </el-table-column>
            <el-table-column prop="status" label="状态" width="90" align="center">
              <template #default="{ row }">
                <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">
                  {{ row.status === 1 ? '启用' : '停用' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="createTime" label="创建时间" min-width="160" />
            <el-table-column label="操作" width="340" fixed="right">
              <template #default="{ row }">
                <el-button type="success" link size="small" :loading="executingId === row.id" @click="handleTaskExecute(row)">
                  执行
                </el-button>
                <el-button type="info" link size="small" @click="openLogDialog(row)">
                  日志
                </el-button>
                <el-button type="success" link size="small" @click="loadTaskToEditor(row)">
                  编辑SQL
                </el-button>
                <el-button type="primary" link size="small" @click="handleToggle(row)">
                  {{ row.status === 1 ? '停用' : '启用' }}
                </el-button>
                <el-button type="primary" link size="small" @click="handleEdit(row)">
                  编辑
                </el-button>
                <el-button type="danger" link size="small" @click="handleDelete(row)">
                  删除
                </el-button>
              </template>
            </el-table-column>
          </el-table>

          <div class="pagination-wrapper">
            <el-pagination
              v-model:current-page="page"
              v-model:page-size="size"
              :total="total"
              :page-sizes="[10, 20, 50]"
              layout="total, sizes, prev, pager, next"
              @size-change="fetchTasks"
              @current-change="fetchTasks"
            />
          </div>
        </div>
      </el-tab-pane>
    </el-tabs>

    <!-- 执行日志对话框 -->
    <el-dialog
      v-model="logDialogVisible"
      title="执行日志"
      width="720px"
      :close-on-click-modal="false"
    >
      <el-table :data="logList" v-loading="logLoading" stripe size="small">
        <el-table-column prop="startTime" label="开始时间" min-width="160" />
        <el-table-column prop="endTime" label="结束时间" min-width="160" />
        <el-table-column prop="status" label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag
              :type="row.status === 'SUCCESS' ? 'success' : row.status === 'RUNNING' ? 'warning' : 'danger'"
              size="small"
            >
              {{ row.status === 'SUCCESS' ? '成功' : row.status === 'RUNNING' ? '运行中' : '失败' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="message" label="消息" min-width="200" show-overflow-tooltip />
      </el-table>
      <div class="pagination-wrapper">
        <el-pagination
          v-model:current-page="logPage"
          v-model:page-size="logSize"
          :total="logTotal"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next"
          @size-change="fetchLogs"
          @current-change="fetchLogs"
        />
      </div>
    </el-dialog>

    <!-- 保存/新建任务对话框 -->
    <el-dialog
      v-model="dialogVisible"
      :title="isEdit ? '编辑SQL任务' : '保存SQL任务'"
      width="640px"
      :close-on-click-modal="false"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="任务名称" prop="taskName">
          <el-input v-model="form.taskName" placeholder="请输入任务名称" />
        </el-form-item>
        <el-form-item label="目标表">
          <el-input v-model="form.targetTable" placeholder="可选，用于数据写入目标表" />
        </el-form-item>
        <el-form-item label="Cron表达式">
          <el-input v-model="form.cronExpression" placeholder="例如: 0 0 2 * * ?" />
        </el-form-item>
        <el-form-item label="SQL内容" prop="sqlContent">
          <el-input
            v-model="form.sqlContent"
            type="textarea"
            :rows="8"
            placeholder="请输入SQL内容"
          />
        </el-form-item>
        <el-form-item v-if="isEdit" label="状态">
          <el-radio-group v-model="form.status">
            <el-radio :label="1">启用</el-radio>
            <el-radio :label="0">停用</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleSave">
          保存
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { VideoPlay, Delete, MagicStick, DocumentChecked, Plus, Search } from '@element-plus/icons-vue'
import { format } from 'sql-formatter'
import MonacoEditor from '../components/MonacoEditor.vue'
import request from '../utils/request.js'

const activeTab = ref('editor')

// ===== SQL 编辑器 =====
const sql = ref('')
const executing = ref(false)
const resultLoaded = ref(false)
const result = ref({
  columns: [],
  rows: [],
  rowCount: 0,
  executionTime: 0
})

const editorOptions = {
  fontSize: 14,
  minimap: { enabled: false },
  automaticLayout: true,
  scrollBeyondLastLine: false,
  lineNumbers: 'on',
  roundedSelection: false
}

const executeSql = async () => {
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
      ElMessage.success('执行成功')
    } else {
      ElMessage.error(res.message || '执行失败')
      result.value = { columns: [], rows: [], rowCount: 0, executionTime: 0 }
    }
  } catch (error) {
    ElMessage.error(error.message || '执行失败')
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

// ===== 任务管理 =====
const loading = ref(false)
const keyword = ref('')
const taskList = ref([])
const page = ref(1)
const size = ref(10)
const total = ref(0)

const dialogVisible = ref(false)
const isEdit = ref(false)
const saving = ref(false)
const formRef = ref(null)
const executingId = ref(null)

const logDialogVisible = ref(false)
const logLoading = ref(false)
const logList = ref([])
const logPage = ref(1)
const logSize = ref(10)
const logTotal = ref(0)
const currentLogTaskId = ref(null)

const form = reactive({
  id: null,
  taskName: '',
  sqlContent: '',
  targetTable: '',
  cronExpression: '',
  status: 0
})

const rules = {
  taskName: [{ required: true, message: '请输入任务名称', trigger: 'blur' }],
  sqlContent: [{ required: true, message: '请输入SQL内容', trigger: 'blur' }]
}

const resetForm = () => {
  form.id = null
  form.taskName = ''
  form.sqlContent = ''
  form.targetTable = ''
  form.cronExpression = ''
  form.status = 0
}

const fetchTasks = async () => {
  loading.value = true
  try {
    const res = await request.get('/sql-task/page', {
      params: { page: page.value, size: size.value, keyword: keyword.value }
    })
    taskList.value = res.data.records
    total.value = res.data.total
  } catch (error) {
    ElMessage.error(error.message || '获取数据失败')
  } finally {
    loading.value = false
  }
}

const openSaveDialog = () => {
  isEdit.value = false
  resetForm()
  form.sqlContent = sql.value
  dialogVisible.value = true
}

const openCreateDialog = () => {
  isEdit.value = false
  resetForm()
  dialogVisible.value = true
}

const handleEdit = (row) => {
  isEdit.value = true
  resetForm()
  Object.assign(form, row)
  dialogVisible.value = true
}

const loadTaskToEditor = (row) => {
  sql.value = row.sqlContent || ''
  activeTab.value = 'editor'
  ElMessage.success('已加载到编辑器')
}

const handleSave = async () => {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  saving.value = true
  try {
    const payload = {
      taskName: form.taskName,
      sqlContent: form.sqlContent,
      targetTable: form.targetTable || null,
      cronExpression: form.cronExpression || null
    }
    if (isEdit.value) {
      await request.put(`/sql-task/${form.id}`, { ...payload, status: form.status })
      ElMessage.success('更新成功')
    } else {
      await request.post('/sql-task', payload)
      ElMessage.success('保存成功')
    }
    dialogVisible.value = false
    fetchTasks()
  } catch (error) {
    ElMessage.error(error.message || '保存失败')
  } finally {
    saving.value = false
  }
}

const handleToggle = async (row) => {
  try {
    await request.post(`/sql-task/${row.id}/toggle`)
    ElMessage.success('操作成功')
    fetchTasks()
  } catch (error) {
    ElMessage.error(error.message || '操作失败')
  }
}

const handleTaskExecute = async (row) => {
  executingId.value = row.id
  try {
    await request.post(`/sql-task/${row.id}/execute`)
    ElMessage.success('执行成功')
    fetchTasks()
  } catch (error) {
    ElMessage.error(error.message || '执行失败')
  } finally {
    executingId.value = null
  }
}

const openLogDialog = (row) => {
  currentLogTaskId.value = row.id
  logPage.value = 1
  logDialogVisible.value = true
  fetchLogs()
}

const fetchLogs = async () => {
  if (!currentLogTaskId.value) return
  logLoading.value = true
  try {
    const res = await request.get(`/sql-task/${currentLogTaskId.value}/logs`, {
      params: { page: logPage.value, size: logSize.value }
    })
    logList.value = res.data.records
    logTotal.value = res.data.total
  } catch (error) {
    ElMessage.error(error.message || '获取日志失败')
  } finally {
    logLoading.value = false
  }
}

const handleDelete = (row) => {
  ElMessageBox.confirm(`确定删除任务 "${row.taskName}" 吗？`, '提示', {
    confirmButtonText: '确定', cancelButtonText: '取消', type: 'warning'
  }).then(async () => {
    try {
      await request.delete(`/sql-task/${row.id}`)
      ElMessage.success('删除成功')
      fetchTasks()
    } catch (error) {
      ElMessage.error(error.message || '删除失败')
    }
  }).catch(() => {})
}

onMounted(() => {
  fetchTasks()
})
</script>

<style scoped>
.development-page {
  padding-bottom: 20px;
}
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}
.page-header h2 {
  margin: 0;
  font-size: 20px;
  font-weight: 600;
}
.tab-content {
  padding: 8px 0;
}
.tab-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}
.editor-card {
  margin-bottom: 16px;
}
.toolbar {
  display: flex;
  gap: 8px;
  margin-bottom: 12px;
}
.editor-wrapper {
  height: 280px;
}
.result-card {
  margin-top: 16px;
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
  overflow: auto;
}
.pagination-wrapper {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}
</style>
