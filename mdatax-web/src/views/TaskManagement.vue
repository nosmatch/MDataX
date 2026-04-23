<template>
  <div class="task-page">
    <div class="page-header">
      <h2>任务管理</h2>
    </div>

    <div class="toolbar">
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
      <el-table-column prop="description" label="任务描述" min-width="160">
        <template #default="{ row }">
          {{ row.description || '-' }}
        </template>
      </el-table-column>
      <el-table-column prop="cronExpression" label="Cron表达式" min-width="140">
        <template #default="{ row }">
          {{ row.cronExpression || '-' }}
        </template>
      </el-table-column>
      <el-table-column prop="workflowName" label="所属工作流" min-width="140">
        <template #default="{ row }">
          {{ getWorkflowName(row.workflowId) || '-' }}
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
          <el-button
            type="success"
            link
            size="small"
            :disabled="row.status !== 1"
            :loading="executingId === row.id"
            @click="handleTaskExecute(row)"
          >
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
      :title="isEdit ? '编辑SQL任务' : '新建SQL任务'"
      width="640px"
      :close-on-click-modal="false"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="任务名称" prop="taskName">
          <el-input v-model="form.taskName" placeholder="请输入任务名称" />
        </el-form-item>
        <el-form-item label="任务描述" prop="description">
          <el-input v-model="form.description" placeholder="请输入任务描述" />
        </el-form-item>
        <el-form-item label="所属工作流">
          <el-select
            v-model="form.workflowId"
            placeholder="请选择工作流"
            clearable
            :disabled="isEdit"
            style="width: 100%"
            @change="(val) => { form.dependTaskIds = []; loadWorkflowTasks(val, form.id) }"
          >
            <el-option
              v-for="wf in workflows"
              :key="wf.id"
              :label="wf.workflowName"
              :value="wf.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item v-if="form.workflowId" label="上游依赖">
          <el-select
            v-model="form.dependTaskIds"
            multiple
            placeholder="选择上游依赖任务"
            style="width: 100%"
          >
            <el-option
              v-for="t in workflowTasks"
              :key="t.id + '-' + t.taskType"
              :label="t.taskName + (t.taskType === 'SYNC' ? ' [同步]' : ' [SQL]')"
              :value="t.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item v-else label="Cron表达式" prop="cronExpression">
          <CronPicker v-model="form.cronExpression" />
        </el-form-item>
        <el-form-item label="SQL内容" prop="sqlContent">
          <el-input
            v-model="form.sqlContent"
            type="textarea"
            :rows="8"
            :disabled="isEdit"
            placeholder="请输入SQL内容"
          />
          <div v-if="isEdit" style="color: #909399; font-size: 12px; margin-top: 4px">
            SQL 内容请到 SQL 开发页面编辑
          </div>
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
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Search } from '@element-plus/icons-vue'
import request from '../utils/request.js'
import CronPicker from '../components/CronPicker.vue'
import { validateCron } from '../utils/cron.js'

const router = useRouter()

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
  description: '',
  cronExpression: '',
  status: 0,
  workflowId: null,
  dependTaskIds: []
})

const workflows = ref([])
const workflowTasks = ref([])

const rules = {
  taskName: [{ required: true, message: '请输入任务名称', trigger: 'blur' }],
  sqlContent: [{ required: true, message: '请输入SQL内容', trigger: 'blur' }],
  description: [{ required: true, message: '请输入任务描述', trigger: 'blur' }],
  cronExpression: [{
    validator: (rule, value, callback) => {
      if (!value) return callback()
      const { valid, message } = validateCron(value)
      if (!valid) callback(new Error(message))
      else callback()
    }, trigger: 'change'
  }]
}

const resetForm = () => {
  form.id = null
  form.taskName = ''
  form.sqlContent = ''
  form.description = ''
  form.cronExpression = ''
  form.status = 0
  form.workflowId = null
  form.dependTaskIds = []
  workflowTasks.value = []
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

const loadWorkflows = async () => {
  try {
    const res = await request.get('/sql-task-workflow/page', {
      params: { page: 1, size: 1000 }
    })
    workflows.value = res.data.records || []
  } catch (error) {
    // silent
  }
}

const getWorkflowName = (wfId) => {
  if (!wfId) return ''
  const wf = workflows.value.find(w => w.id === wfId)
  return wf ? wf.workflowName : ''
}

const loadWorkflowTasks = async (wfId, excludeId) => {
  workflowTasks.value = []
  if (!wfId) return
  try {
    const [sqlRes, syncRes] = await Promise.all([
      request.get('/sql-task/page', { params: { page: 1, size: 1000, keyword: '' } }),
      request.get('/sync-task/page', { params: { page: 1, size: 1000, keyword: '' } })
    ])
    const sqlTasks = (sqlRes.data.records || [])
      .filter(t => t.workflowId === wfId && t.id !== excludeId)
      .map(t => ({ ...t, taskType: 'SQL' }))
    const syncTasks = (syncRes.data.records || [])
      .filter(t => t.workflowId === wfId && t.id !== excludeId)
      .map(t => ({ ...t, taskType: 'SYNC' }))
    workflowTasks.value = [...sqlTasks, ...syncTasks]
  } catch (error) {
    // silent
  }
}

const loadTaskDependencies = async (taskId) => {
  try {
    const res = await request.get(`/sql-task/${taskId}/dependencies`)
    form.dependTaskIds = res.data || []
  } catch (error) {
    form.dependTaskIds = []
  }
}

const openCreateDialog = () => {
  isEdit.value = false
  resetForm()
  dialogVisible.value = true
}

const handleEdit = async (row) => {
  isEdit.value = true
  resetForm()
  Object.assign(form, row)
  if (row.workflowId) {
    await loadWorkflowTasks(row.workflowId, row.id)
    await loadTaskDependencies(row.id)
  }
  dialogVisible.value = true
}

const loadTaskToEditor = (row) => {
  router.push({ path: '/development', query: { taskId: row.id } })
}

const handleSave = async () => {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  saving.value = true
  try {
    const payload = {
      taskName: form.taskName,
      sqlContent: form.sqlContent,
      description: form.description,
      cronExpression: form.workflowId ? null : (form.cronExpression || null),
      workflowId: form.workflowId || null,
      dependTaskIds: form.workflowId ? (form.dependTaskIds || []) : null
    }
    if (isEdit.value) {
      await request.put(`/sql-task/${form.id}`, payload)
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
  if (row.status === 1) {
    ElMessage.warning('启用状态的任务不能删除，请先停用')
    return
  }
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
  loadWorkflows()
})
</script>

<style scoped>
.task-page {
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
.toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}
.pagination-wrapper {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}
</style>
