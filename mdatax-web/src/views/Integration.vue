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
            <el-table-column prop="datasourceName" label="数据源" min-width="140" />
            <el-table-column prop="sourceTable" label="来源表" min-width="140" />
            <el-table-column prop="targetTable" label="目标表" min-width="140" />
            <el-table-column prop="syncType" label="同步类型" width="100" align="center">
              <template #default="{ row }">
                <el-tag :type="row.syncType === 'FULL' ? 'primary' : 'warning'" size="small">
                  {{ row.syncType === 'FULL' ? '全量' : '增量' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="workflowName" label="所属工作流" min-width="140">
              <template #default="{ row }">
                {{ row.workflowName || '-' }}
              </template>
            </el-table-column>
            <el-table-column prop="status" label="状态" width="90" align="center">
              <template #default="{ row }">
                <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">
                  {{ row.status === 1 ? '启用' : '停用' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="lastSyncTime" label="最后同步" min-width="160" />
            <el-table-column prop="createTime" label="创建时间" min-width="160" />
            <el-table-column label="操作" width="340" fixed="right">
              <template #default="{ row }">
                <template v-if="row.canOperate">
                  <el-button
                    type="success"
                    link
                    size="small"
                    :disabled="row.status !== 1"
                    :loading="executingId === row.id"
                    @click="handleTaskExecute(row)"
                  >
                    执行同步
                  </el-button>
                  <el-button type="info" link size="small" @click="openLogDialog(row)">
                    日志
                  </el-button>
                  <el-button type="primary" link size="small" @click="handleTaskToggle(row)">
                    {{ row.status === 1 ? '停用' : '启用' }}
                  </el-button>
                  <el-button type="primary" link size="small" @click="handleTaskEdit(row)">
                    编辑
                  </el-button>
                  <el-button type="danger" link size="small" @click="handleTaskDelete(row)">
                    删除
                  </el-button>
                </template>
                <template v-else>
                  <el-button type="info" link size="small" @click="openLogDialog(row)">
                    日志
                  </el-button>
                </template>
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
        <el-table-column prop="rowCount" label="同步行数" width="100" align="right" />
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

    <!-- 同步任务对话框 -->
    <el-dialog
      v-model="taskDialogVisible"
      :title="isTaskEdit ? '编辑同步任务' : '新建同步任务'"
      width="600px"
      :close-on-click-modal="false"
    >
      <el-form ref="taskFormRef" :model="taskForm" :rules="taskRules" label-width="110px">
        <el-form-item label="任务名称" prop="taskName">
          <el-input v-model="taskForm.taskName" placeholder="请输入任务名称" />
        </el-form-item>
        <el-form-item label="数据源" prop="datasourceId">
          <el-select
            v-model="taskForm.datasourceId"
            placeholder="请选择数据源"
            style="width: 100%"
            @change="onDatasourceChange"
          >
            <el-option
              v-for="ds in allDatasources"
              :key="ds.id"
              :label="ds.name"
              :value="ds.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="来源表" prop="sourceTable">
          <el-select
            v-model="taskForm.sourceTable"
            placeholder="先选择数据源"
            style="width: 100%"
            :disabled="!taskForm.datasourceId || tableOptionsLoading"
            :loading="tableOptionsLoading"
          >
            <el-option
              v-for="t in tableOptions"
              :key="t"
              :label="t"
              :value="t"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="目标表" prop="targetTable">
          <el-input v-model="taskForm.targetTable" placeholder="ClickHouse 中的目标表名" />
        </el-form-item>
        <el-form-item label="同步类型" prop="syncType">
          <el-radio-group v-model="taskForm.syncType">
            <el-radio label="FULL">全量同步</el-radio>
            <el-radio label="INCREMENTAL">增量同步</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item
          v-if="taskForm.syncType === 'INCREMENTAL'"
          label="时间字段"
          prop="timeField"
        >
          <el-input v-model="taskForm.timeField" placeholder="用于增量判断的时间字段名" />
        </el-form-item>
        <el-form-item label="所属工作流">
          <el-select
            v-model="taskForm.workflowId"
            placeholder="请选择工作流（可选）"
            clearable
            style="width: 100%"
          >
            <el-option
              v-for="wf in allWorkflows"
              :key="wf.id"
              :label="wf.workflowName"
              :value="wf.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item v-if="!taskForm.workflowId" label="Cron表达式" prop="cronExpression">
          <CronPicker v-model="taskForm.cronExpression" />
        </el-form-item>

        <!-- 协作者管理（仅编辑时显示，创建人或管理员可操作） -->
        <el-form-item
          label="协作者"
          v-if="isTaskEdit && (taskForm.createUserId === currentUserId || isAdmin)"
        >
          <div style="display: flex; gap: 8px; margin-bottom: 8px; width: 100%">
            <el-select
              v-model="selectedCollaboratorId"
              placeholder="选择用户添加协作者"
              style="width: 0; flex: 1"
              clearable
              filterable
            >
              <el-option
                v-for="u in allUsers"
                :key="u.id"
                :label="u.nickname || u.username"
                :value="u.id"
              />
            </el-select>
            <el-button type="primary" :loading="addingCollaborator" @click="handleAddCollaborator"
            >添加</el-button>
          </div>
          <div>
            <el-tag
              v-for="c in collaborators"
              :key="c.id"
              closable
              style="margin-right: 8px; margin-bottom: 8px"
              @close="handleRemoveCollaborator(c)"
            >
              {{ c.userName }}
            </el-tag>
            <span v-if="collaborators.length === 0" style="color: #909399; font-size: 13px">暂无协作者</span>
          </div>
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="taskDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="taskSaving" @click="handleTaskSave">
          保存
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Search } from '@element-plus/icons-vue'
import request from '../utils/request.js'
import CronPicker from '../components/CronPicker.vue'
import { validateCron } from '../utils/cron.js'
import { useAuthStore } from '../stores/auth.js'
import { useRoute, useRouter } from 'vue-router'

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
const isTaskEdit = ref(false)
const taskSaving = ref(false)
const taskFormRef = ref(null)
const allDatasources = ref([])
const allWorkflows = ref([])
const tableOptions = ref([])
const tableOptionsLoading = ref(false)

const executingId = ref(null)

const taskForm = reactive({
  id: null,
  taskName: '',
  datasourceId: null,
  sourceTable: '',
  targetTable: '',
  syncType: 'FULL',
  timeField: '',
  cronExpression: '',
  status: 0,
  workflowId: null,
  createUserId: null,
  createUserName: ''
})

// 协作者管理
const allUsers = ref([])
const collaborators = ref([])
const selectedCollaboratorId = ref(null)
const addingCollaborator = ref(false)

const logDialogVisible = ref(false)
const logLoading = ref(false)
const logList = ref([])
const logPage = ref(1)
const logSize = ref(10)
const logTotal = ref(0)
const currentLogTaskId = ref(null)

const taskRules = {
  taskName: [{ required: true, message: '请输入任务名称', trigger: 'blur' }],
  datasourceId: [{ required: true, message: '请选择数据源', trigger: 'change' }],
  sourceTable: [{ required: true, message: '请选择来源表', trigger: 'change' }],
  targetTable: [{ required: true, message: '请输入目标表', trigger: 'blur' }],
  syncType: [{ required: true, message: '请选择同步类型', trigger: 'change' }],
  timeField: [{ required: true, message: '请输入时间字段', trigger: 'blur' }],
  cronExpression: [{
    validator: (rule, value, callback) => {
      if (!value) return callback()
      const { valid, message } = validateCron(value)
      if (!valid) callback(new Error(message))
      else callback()
    }, trigger: 'change'
  }]
}

const resetTaskForm = () => {
  taskForm.id = null
  taskForm.taskName = ''
  taskForm.datasourceId = null
  taskForm.sourceTable = ''
  taskForm.targetTable = ''
  taskForm.syncType = 'FULL'
  taskForm.timeField = ''
  taskForm.cronExpression = ''
  taskForm.status = 0
  taskForm.workflowId = null
  taskForm.createUserId = null
  taskForm.createUserName = ''
  tableOptions.value = []
  collaborators.value = []
  selectedCollaboratorId.value = null
}

const loadWorkflows = async () => {
  try {
    const res = await request.get('/sql-task-workflow/page', {
      params: { page: 1, size: 1000 }
    })
    allWorkflows.value = res.data.records || []
  } catch (error) {
    // silent
  }
}

const getWorkflowName = (wfId) => {
  if (!wfId) return ''
  const wf = allWorkflows.value.find(w => w.id === wfId)
  return wf ? wf.workflowName : ''
}

const fetchTasks = async () => {
  taskLoading.value = true
  try {
    const res = await request.get('/sync-task/page', {
      params: { page: taskPage.value, size: taskSize.value, keyword: taskKeyword.value }
    })
    taskList.value = res.data.records
    taskTotal.value = res.data.total
  } catch (error) {
    ElMessage.error(error.message || '获取数据失败')
  } finally {
    taskLoading.value = false
  }
}

const loadAllDatasources = async () => {
  try {
    const res = await request.get('/datasource/list')
    allDatasources.value = res.data || []
  } catch (error) {
    ElMessage.error(error.message || '加载数据源失败')
  }
}

const onDatasourceChange = async (datasourceId) => {
  taskForm.sourceTable = ''
  tableOptions.value = []
  if (!datasourceId) return
  tableOptionsLoading.value = true
  try {
    const res = await request.get(`/sync-task/datasource/${datasourceId}/tables`)
    tableOptions.value = res.data || []
  } catch (error) {
    ElMessage.error(error.message || '加载表列表失败')
  } finally {
    tableOptionsLoading.value = false
  }
}

const openTaskDialog = async () => {
  isTaskEdit.value = false
  resetTaskForm()
  await loadAllDatasources()
  await loadWorkflows()
  taskDialogVisible.value = true
}

const handleTaskEdit = async (row) => {
  isTaskEdit.value = true
  resetTaskForm()
  await loadAllDatasources()
  await loadWorkflows()
  Object.assign(taskForm, row)
  if (taskForm.datasourceId) {
    await onDatasourceChange(taskForm.datasourceId)
  }
  if (row.id) {
    await loadCollaborators(row.id, 'SYNC')
  }
  taskDialogVisible.value = true
}

const handleTaskSave = async () => {
  const valid = await taskFormRef.value.validate().catch(() => false)
  if (!valid) return
  taskSaving.value = true
  try {
    const payload = {
      taskName: taskForm.taskName,
      datasourceId: taskForm.datasourceId,
      sourceTable: taskForm.sourceTable,
      targetTable: taskForm.targetTable,
      syncType: taskForm.syncType,
      timeField: taskForm.syncType === 'INCREMENTAL' ? taskForm.timeField : null,
      cronExpression: taskForm.workflowId ? null : (taskForm.cronExpression || null),
      workflowId: taskForm.workflowId || null
    }
    if (isTaskEdit.value) {
      await request.put(`/sync-task/${taskForm.id}`, payload)
      ElMessage.success('更新成功')
    } else {
      await request.post('/sync-task', payload)
      ElMessage.success('创建成功')
    }
    taskDialogVisible.value = false
    fetchTasks()
  } catch (error) {
    ElMessage.error(error.message || '保存失败')
  } finally {
    taskSaving.value = false
  }
}

const handleTaskToggle = async (row) => {
  try {
    await request.post(`/sync-task/${row.id}/toggle`)
    ElMessage.success('操作成功')
    fetchTasks()
  } catch (error) {
    ElMessage.error(error.message || '操作失败')
  }
}

const handleTaskDelete = (row) => {
  if (row.status === 1) {
    ElMessage.warning('启用状态的任务不能删除，请先停用')
    return
  }
  ElMessageBox.confirm(`确定删除同步任务 "${row.taskName}" 吗？`, '提示', {
    confirmButtonText: '确定', cancelButtonText: '取消', type: 'warning'
  }).then(async () => {
    try {
      await request.delete(`/sync-task/${row.id}`)
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
    await request.post(`/sync-task/${row.id}/execute`)
    ElMessage.success('同步任务执行成功')
    fetchTasks()
  } catch (error) {
    ElMessage.error(error.message || '同步任务执行失败')
  } finally {
    executingId.value = null
  }
}

// ===== 协作者管理 =====
const loadAllUsers = async () => {
  try {
    const res = await request.get('/user/page', { params: { page: 1, size: 1000 } })
    allUsers.value = (res.data.records || []).filter(u => u.id !== currentUserId.value)
  } catch (error) {
    // silent
  }
}

const loadCollaborators = async (taskId, taskType) => {
  try {
    const res = await request.get(`/task-collaborator/${taskId}/${taskType}`)
    collaborators.value = res.data || []
  } catch (error) {
    collaborators.value = []
  }
}

const handleAddCollaborator = async () => {
  if (!selectedCollaboratorId.value) {
    ElMessage.warning('请选择用户')
    return
  }
  addingCollaborator.value = true
  try {
    await request.post('/task-collaborator', {
      taskId: taskForm.id,
      taskType: 'SYNC',
      userId: selectedCollaboratorId.value
    })
    ElMessage.success('添加成功')
    selectedCollaboratorId.value = null
    await loadCollaborators(taskForm.id, 'SYNC')
  } catch (error) {
    ElMessage.error(error.response?.data?.message || '添加失败')
  } finally {
    addingCollaborator.value = false
  }
}

const handleRemoveCollaborator = async (c) => {
  try {
    await request.delete(`/task-collaborator/${c.id}`)
    ElMessage.success('移除成功')
    await loadCollaborators(taskForm.id, 'SYNC')
  } catch (error) {
    ElMessage.error(error.response?.data?.message || '移除失败')
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
    const res = await request.get(`/sync-task/${currentLogTaskId.value}/logs`, {
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

onMounted(() => {
  fetchDatasources()
  fetchTasks()
  loadAllUsers()
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
