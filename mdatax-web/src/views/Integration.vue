<template>
  <div class="integration-page">
    <div class="page-header">
      <h2>数据集成</h2>
    </div>

    <!-- 数据源管理 -->
    <div v-if="route.path === '/datasource' || route.path === '/integration'" class="tab-content">
      <div class="tab-toolbar">
            <el-input
              v-model="dsKeyword"
              placeholder="搜索数据源名称"
              clearable
              style="width: 300px"
              @keyup.enter="fetchDatasources"
            >
              <template #append>
                <el-button @click="fetchDatasources">
                  <el-icon><Search /></el-icon>
                </el-button>
              </template>
            </el-input>
            <el-button type="primary" @click="openDsDialog">
              <el-icon><Plus /></el-icon> 新增数据源
            </el-button>
          </div>

          <el-table :data="dsList" v-loading="dsLoading" stripe>
            <el-table-column prop="name" label="数据源名称" min-width="160" />
            <el-table-column prop="type" label="类型" width="100" />
            <el-table-column prop="host" label="主机地址" min-width="160" />
            <el-table-column prop="port" label="端口" width="80" />
            <el-table-column prop="databaseName" label="数据库名" min-width="140" />
            <el-table-column prop="username" label="用户名" min-width="120" />
            <el-table-column prop="status" label="状态" width="90" align="center">
              <template #default="{ row }">
                <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">
                  {{ row.status === 1 ? '启用' : '禁用' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="createTime" label="创建时间" min-width="160" />
            <el-table-column label="操作" width="240" fixed="right">
              <template #default="{ row }">
                <el-button type="primary" link size="small" @click="handleDsTest(row)">
                  测试连接
                </el-button>
                <el-button type="primary" link size="small" @click="handleDsEdit(row)">
                  编辑
                </el-button>
                <el-button type="danger" link size="small" @click="handleDsDelete(row)">
                  删除
                </el-button>
              </template>
            </el-table-column>
          </el-table>

          <div class="pagination-wrapper">
            <el-pagination
              v-model:current-page="dsPage"
              v-model:page-size="dsSize"
              :total="dsTotal"
              :page-sizes="[10, 20, 50]"
              layout="total, sizes, prev, pager, next"
              @size-change="fetchDatasources"
              @current-change="fetchDatasources"
            />
          </div>
        </div>

        <!-- 同步任务 -->
        <div v-if="route.path === '/sync-task'" class="tab-content">
          <div class="tab-toolbar">
            <el-input
              v-model="taskKeyword"
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
            <el-button type="primary" @click="openTaskDialog">
              <el-icon><Plus /></el-icon> 新建同步任务
            </el-button>
          </div>

          <el-table :data="taskList" v-loading="taskLoading" stripe>
            <el-table-column prop="taskName" label="任务名称" min-width="180" />
            <el-table-column label="来源" min-width="200">
              <template #default="{ row }">
                {{ row.sourceDatasourceName || '-' }} / {{ row.sourceTable || '-' }}
              </template>
            </el-table-column>
            <el-table-column label="目标" min-width="200">
              <template #default="{ row }">
                {{ row.targetDatasourceName || '-' }} / {{ row.targetTable || '-' }}
              </template>
            </el-table-column>
            <el-table-column prop="syncType" label="同步类型" width="100" align="center">
              <template #default="{ row }">
                <el-tag :type="row.syncType === 'FULL' ? 'primary' : 'warning'" size="small">
                  {{ row.syncType === 'FULL' ? '全量' : '增量' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="status" label="状态" width="90" align="center">
              <template #default="{ row }">
                <el-tag :type="row.status === TASK_STATUS.ENABLED ? 'success' : 'info'" size="small">
                  {{ TASK_STATUS_LABEL[row.status] || '-' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="lastSyncTime" label="最后同步" min-width="160" />
            <el-table-column prop="createTime" label="创建时间" min-width="160" />
            <el-table-column label="操作" width="340" fixed="right">
              <template #default="{ row }">
                <el-button
                  type="success"
                  link
                  size="small"
                  :disabled="row.status !== TASK_STATUS.ENABLED"
                  :loading="executingId === row.id"
                  @click="handleTaskExecute(row)"
                >
                  执行同步
                </el-button>
                <el-button type="info" link size="small" @click="openLogDialog(row)">
                  日志
                </el-button>
                <el-button type="primary" link size="small" @click="handleTaskToggle(row)">
                  {{ row.status === TASK_STATUS.ENABLED ? '停用' : '启用' }}
                </el-button>
                <el-button type="primary" link size="small" @click="handleTaskEdit(row)">
                  编辑
                </el-button>
                <el-button type="danger" link size="small" @click="handleTaskDelete(row)">
                  删除
                </el-button>
              </template>
            </el-table-column>
          </el-table>

          <div class="pagination-wrapper">
            <el-pagination
              v-model:current-page="taskPage"
              v-model:page-size="taskSize"
              :total="taskTotal"
              :page-sizes="[10, 20, 50]"
              layout="total, sizes, prev, pager, next"
              @size-change="fetchTasks"
              @current-change="fetchTasks"
            />
          </div>
        </div>

    <!-- 数据源对话框 -->
    <el-dialog
      v-model="dsDialogVisible"
      :title="isDsEdit ? '编辑数据源' : '新增数据源'"
      width="560px"
      :close-on-click-modal="false"
    >
      <el-form ref="dsFormRef" :model="dsForm" :rules="dsRules" label-width="100px">
        <el-form-item label="数据源名称" prop="name">
          <el-input v-model="dsForm.name" placeholder="请输入数据源名称" />
        </el-form-item>
        <el-form-item label="类型" prop="type">
          <el-select v-model="dsForm.type" placeholder="请选择类型" style="width: 100%" @change="onDsTypeChange">
            <el-option label="MySQL" value="MySQL" />
            <el-option label="ClickHouse" value="ClickHouse" />
            <el-option label="Elasticsearch" value="Elasticsearch" />
            <el-option label="Kafka" value="Kafka" />
            <el-option label="本地Excel" value="本地Excel" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="dsForm.type !== '本地Excel'" label="主机地址" prop="host">
          <el-input v-model="dsForm.host" placeholder="例如: 127.0.0.1" />
        </el-form-item>
        <el-form-item v-if="dsForm.type !== '本地Excel'" label="端口" prop="port">
          <el-input-number v-model="dsForm.port" :min="1" :max="65535" style="width: 100%" />
        </el-form-item>
        <el-form-item v-if="dsForm.type === 'MySQL' || dsForm.type === 'ClickHouse'" label="数据库名" prop="databaseName">
          <el-input v-model="dsForm.databaseName" placeholder="请输入数据库名" />
        </el-form-item>
        <el-form-item v-if="dsForm.type === 'MySQL' || dsForm.type === 'ClickHouse' || dsForm.type === 'Elasticsearch'" label="用户名" prop="username">
          <el-input v-model="dsForm.username" placeholder="请输入用户名（可选）" />
        </el-form-item>
        <el-form-item v-if="dsForm.type === 'MySQL' || dsForm.type === 'ClickHouse' || dsForm.type === 'Elasticsearch'" label="密码" prop="password">
          <el-input v-model="dsForm.password" type="password" show-password placeholder="请输入密码（可选）" />
        </el-form-item>
        <el-form-item v-if="dsForm.type === '本地Excel'" label="文件路径" prop="extraConfig">
          <el-input v-model="dsForm.extraConfig" placeholder="例如: /data/file.xlsx" />
        </el-form-item>
        <el-form-item v-if="isDsEdit" label="状态">
          <el-radio-group v-model="dsForm.status">
            <el-radio :label="1">启用</el-radio>
            <el-radio :label="0">禁用</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dsDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="dsTesting" @click="handleDsTestBeforeSave">
          测试连接
        </el-button>
        <el-button type="primary" :loading="dsSaving" @click="handleDsSave">
          保存
        </el-button>
      </template>
    </el-dialog>

    <!-- 同步日志对话框 -->
    <el-dialog
      v-model="logDialogVisible"
      title="同步日志"
      width="720px"
      :close-on-click-modal="false"
    >
      <el-table :data="logList" v-loading="logLoading" stripe size="small">
        <el-table-column prop="startTime" label="开始时间" min-width="160" />
        <el-table-column prop="endTime" label="结束时间" min-width="160" />
        <el-table-column prop="status" label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag
              :type="row.status === 'SUCCESS' ? 'success' : row.status === 'RUNNING' ? 'warning' : 'danger'"
              size="small"
            >
              {{ row.status === 'SUCCESS' ? '成功' : row.status === 'RUNNING' ? '运行中' : '失败' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="durationMs" label="耗时(ms)" width="100" align="right" />
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

    <!-- 统一任务表单对话框 -->
    <TaskFormDialog
      v-model="taskDialogVisible"
      :task-id="editingTaskId"
      :prefill-data="taskPrefillData"
      @saved="handleTaskSaved"
    />
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Search } from '@element-plus/icons-vue'
import request from '../utils/request.js'
import CronPicker from '../components/CronPicker.vue'
import TaskFormDialog from '../components/TaskFormDialog.vue'
import { validateCron } from '../utils/cron.js'
import { useAuthStore } from '../stores/auth.js'
import { useRoute, useRouter } from 'vue-router'
import { TASK_TYPE, SYNC_TYPE, TASK_STATUS, TASK_STATUS_LABEL } from '../utils/task-constants.js'

const authStore = useAuthStore()
const currentUserId = computed(() => authStore.user?.userId)
const isAdmin = ref(false)
const route = useRoute()
const router = useRouter()

const loadCurrentUser = async () => {
  try {
    const res = await request.get('/user/current')
    isAdmin.value = res.data.isAdmin || false
  } catch (e) {
    // ignore
  }
}

// ===== 数据源 =====
const dsLoading = ref(false)
const dsKeyword = ref('')
const dsList = ref([])
const dsPage = ref(1)
const dsSize = ref(10)
const dsTotal = ref(0)

const dsDialogVisible = ref(false)
const isDsEdit = ref(false)
const dsSaving = ref(false)
const dsTesting = ref(false)
const dsFormRef = ref(null)

const dsForm = reactive({
  id: null,
  name: '',
  type: 'MySQL',
  host: '',
  port: 3306,
  databaseName: '',
  username: '',
  password: '',
  extraConfig: '',
  status: 1
})

const dsDefaultPorts = {
  MySQL: 3306,
  ClickHouse: 8123,
  Elasticsearch: 9200,
  Kafka: 9092,
  本地Excel: null
}

const dsRules = computed(() => {
  const rules = {
    name: [{ required: true, message: '请输入数据源名称', trigger: 'blur' }],
    type: [{ required: true, message: '请选择类型', trigger: 'change' }]
  }
  if (dsForm.type !== '本地Excel') {
    rules.host = [{ required: true, message: '请输入主机地址', trigger: 'blur' }]
    rules.port = [{ required: true, message: '请输入端口', trigger: 'blur' }]
  }
  if (dsForm.type === 'MySQL' || dsForm.type === 'ClickHouse') {
    rules.databaseName = [{ required: true, message: '请输入数据库名', trigger: 'blur' }]
    rules.username = [{ required: true, message: '请输入用户名', trigger: 'blur' }]
    rules.password = [{ required: true, message: '请输入密码', trigger: 'blur' }]
  }
  if (dsForm.type === '本地Excel') {
    rules.extraConfig = [{ required: true, message: '请输入文件路径配置', trigger: 'blur' }]
  }
  return rules
})

const resetDsForm = () => {
  dsForm.id = null
  dsForm.name = ''
  dsForm.type = 'MySQL'
  dsForm.host = ''
  dsForm.port = 3306
  dsForm.databaseName = ''
  dsForm.username = ''
  dsForm.password = ''
  dsForm.extraConfig = ''
  dsForm.status = 1
}

const onDsTypeChange = (val) => {
  dsForm.port = dsDefaultPorts[val] || null
  dsForm.host = ''
  dsForm.databaseName = ''
  dsForm.username = ''
  dsForm.password = ''
  dsForm.extraConfig = ''
  if (dsFormRef.value) {
    dsFormRef.value.clearValidate()
  }
}

const fetchDatasources = async () => {
  dsLoading.value = true
  try {
    const res = await request.get('/datasource/page', {
      params: { page: dsPage.value, size: dsSize.value, keyword: dsKeyword.value }
    })
    dsList.value = res.data.records
    dsTotal.value = res.data.total
  } catch (error) {
    ElMessage.error(error.message || '获取数据失败')
  } finally {
    dsLoading.value = false
  }
}

const openDsDialog = () => {
  isDsEdit.value = false
  resetDsForm()
  dsDialogVisible.value = true
}

const handleDsEdit = (row) => {
  isDsEdit.value = true
  Object.assign(dsForm, row)
  dsDialogVisible.value = true
}

const doTestConnection = async (payload) => {
  dsTesting.value = true
  try {
    const res = await request.post('/datasource/test', payload)
    if (res.data.success) {
      ElMessage.success(res.data.message)
      return true
    } else {
      ElMessage.error(res.data.message)
      return false
    }
  } catch (error) {
    ElMessage.error(error.message || '测试连接失败')
    return false
  } finally {
    dsTesting.value = false
  }
}

const handleDsTest = async (row) => {
  await doTestConnection({
    type: row.type,
    host: row.host, port: row.port, databaseName: row.databaseName,
    username: row.username, password: row.password,
    extraConfig: row.extraConfig
  })
}

const handleDsTestBeforeSave = async () => {
  const valid = await dsFormRef.value.validate().catch(() => false)
  if (!valid) return
  await doTestConnection({
    type: dsForm.type,
    host: dsForm.host, port: dsForm.port, databaseName: dsForm.databaseName,
    username: dsForm.username, password: dsForm.password,
    extraConfig: dsForm.extraConfig
  })
}

const handleDsSave = async () => {
  const valid = await dsFormRef.value.validate().catch(() => false)
  if (!valid) return
  dsSaving.value = true
  try {
    if (isDsEdit.value) {
      await request.put(`/datasource/${dsForm.id}`, {
        name: dsForm.name, host: dsForm.host, port: dsForm.port,
        databaseName: dsForm.databaseName, username: dsForm.username,
        password: dsForm.password, status: dsForm.status,
        extraConfig: dsForm.extraConfig
      })
      ElMessage.success('更新成功')
    } else {
      await request.post('/datasource', {
        name: dsForm.name, type: dsForm.type, host: dsForm.host, port: dsForm.port,
        databaseName: dsForm.databaseName, username: dsForm.username,
        password: dsForm.password, extraConfig: dsForm.extraConfig
      })
      ElMessage.success('创建成功')
    }
    dsDialogVisible.value = false
    fetchDatasources()
  } catch (error) {
    ElMessage.error(error.message || '保存失败')
  } finally {
    dsSaving.value = false
  }
}

const handleDsDelete = (row) => {
  ElMessageBox.confirm(`确定删除数据源 "${row.name}" 吗？`, '提示', {
    confirmButtonText: '确定', cancelButtonText: '取消', type: 'warning'
  }).then(async () => {
    try {
      await request.delete(`/datasource/${row.id}`)
      ElMessage.success('删除成功')
      fetchDatasources()
    } catch (error) {
      ElMessage.error(error.message || '删除失败')
    }
  }).catch(() => {})
}

// ===== 同步任务 =====
const taskLoading = ref(false)
const taskKeyword = ref('')
const taskList = ref([])
const taskPage = ref(1)
const taskSize = ref(10)
const taskTotal = ref(0)

const taskDialogVisible = ref(false)
const editingTaskId = ref(null)
const taskPrefillData = ref({})
const executingId = ref(null)

const logDialogVisible = ref(false)
const logLoading = ref(false)
const logList = ref([])
const logPage = ref(1)
const logSize = ref(10)
const logTotal = ref(0)
const currentLogTaskId = ref(null)

const fetchTasks = async () => {
  taskLoading.value = true
  try {
    const res = await request.get('/task/page', {
      params: {
        page: taskPage.value,
        size: taskSize.value,
        keyword: taskKeyword.value,
        taskType: TASK_TYPE.SYNC
      }
    })
    taskList.value = res.data.records || []
    taskTotal.value = res.data.total || 0
  } catch (error) {
    ElMessage.error(error.message || '获取数据失败')
  } finally {
    taskLoading.value = false
  }
}

const openTaskDialog = () => {
  editingTaskId.value = null
  taskPrefillData.value = {
    taskType: TASK_TYPE.SYNC,
    syncType: SYNC_TYPE.FULL,
    taskName: `同步任务-${new Date().toLocaleString()}`,
    status: TASK_STATUS.DRAFT
  }
  taskDialogVisible.value = true
}

const handleTaskEdit = (row) => {
  // 跳转到任务管理页面进行编辑
  router.push({ path: '/task', query: { taskId: row.id, action: 'edit' } })
}

const handleTaskSaved = () => {
  ElMessage.success('任务保存成功')
  fetchTasks()
}

const handleTaskToggle = async (row) => {
  try {
    const newStatus = row.status === TASK_STATUS.ENABLED ? TASK_STATUS.DISABLED : TASK_STATUS.ENABLED
    await request.put(`/task/${row.id}`, { status: newStatus })
    ElMessage.success('操作成功')
    fetchTasks()
  } catch (error) {
    ElMessage.error(error.message || '操作失败')
  }
}

const handleTaskDelete = (row) => {
  if (row.status === TASK_STATUS.ENABLED) {
    ElMessage.warning('启用状态的任务不能删除，请先停用')
    return
  }
  ElMessageBox.confirm(`确定删除同步任务 "${row.taskName}" 吗？`, '提示', {
    confirmButtonText: '确定', cancelButtonText: '取消', type: 'warning'
  }).then(async () => {
    try {
      await request.delete(`/task/${row.id}`)
      ElMessage.success('删除成功')
      fetchTasks()
    } catch (error) {
      ElMessage.error(error.message || '删除失败')
    }
  }).catch(() => {})
}

const handleTaskExecute = async (row) => {
  executingId.value = row.id
  try {
    await request.post(`/task/${row.id}/execute`)
    ElMessage.success('同步任务执行成功')
    fetchTasks()
  } catch (error) {
    ElMessage.error(error.message || '同步任务执行失败')
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
    // 使用统一任务执行记录接口
    const res = await request.get(`/task-execution/page`, {
      params: {
        page: logPage.value,
        size: logSize.value,
        taskId: currentLogTaskId.value
      }
    })
    logList.value = res.data.records || []
    logTotal.value = res.data.total || 0
  } catch (error) {
    ElMessage.error(error.message || '获取日志失败')
  } finally {
    logLoading.value = false
  }
}

onMounted(() => {
  fetchDatasources()
  fetchTasks()
  loadCurrentUser()
})
</script>

<style scoped>
.integration-page {
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
.pagination-wrapper {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}
</style>
