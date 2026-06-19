<template>
  <el-dialog
    v-model="visible"
    :title="isEdit ? '编辑任务' : '新建任务'"
    width="680px"
    :close-on-click-modal="false"
    @open="handleOpen"
  >
    <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
      <el-tabs v-model="activeTab">
        <el-tab-pane label="基本信息" name="basic">
          <el-form-item label="任务名称" prop="taskName">
            <el-input v-model="form.taskName" placeholder="请输入任务名称" />
          </el-form-item>
          <el-form-item label="任务类型" prop="taskType">
            <el-radio-group v-model="form.taskType" :disabled="isEdit">
              <el-radio :label="TASK_TYPE.SQL">{{ TASK_TYPE_LABEL[TASK_TYPE.SQL] }}</el-radio>
              <el-radio :label="TASK_TYPE.SYNC">{{ TASK_TYPE_LABEL[TASK_TYPE.SYNC] }}</el-radio>
              <el-radio :label="TASK_TYPE.QUALITY">{{ TASK_TYPE_LABEL[TASK_TYPE.QUALITY] }}</el-radio>
            </el-radio-group>
          </el-form-item>
          <el-form-item label="任务描述">
            <el-input v-model="form.description" type="textarea" :rows="2" placeholder="请输入任务描述" />
          </el-form-item>
          <el-form-item label="责任人" prop="ownerUserId">
            <el-select v-model="form.ownerUserId" placeholder="请选择责任人" filterable style="width: 100%">
              <el-option v-for="u in users" :key="u.id" :label="u.nickname || u.username" :value="u.id" />
            </el-select>
          </el-form-item>
          <el-form-item label="优先级">
            <el-slider v-model="form.priority" :min="1" :max="10" :marks="priorityMarks" show-stops />
          </el-form-item>
          <el-form-item label="标签">
            <el-input v-model="form.tags" placeholder="多个标签用逗号分隔，如：核心,日报" />
          </el-form-item>
        </el-tab-pane>

        <el-tab-pane label="调度配置" name="schedule">
          <el-form-item label="Cron表达式">
            <CronPicker v-model="form.cronExpression" />
          </el-form-item>
          <el-form-item label="重试次数">
            <el-input-number v-model="form.retryTimes" :min="0" :max="10" />
          </el-form-item>
          <el-form-item label="重试间隔(秒)">
            <el-input-number v-model="form.retryInterval" :min="0" :max="3600" />
          </el-form-item>
          <el-form-item label="超时时间(秒)">
            <el-input-number v-model="form.timeoutSeconds" :min="0" :max="86400" />
          </el-form-item>
        </el-tab-pane>

        <el-tab-pane label="任务内容" name="content">
          <!-- SQL任务内容 -->
          <template v-if="form.taskType === TASK_TYPE.SQL">
            <el-form-item label="SQL内容" prop="sqlContent">
              <el-input
                v-model="form.sqlContent"
                type="textarea"
                :rows="10"
                placeholder="请输入SQL内容"
              />
            </el-form-item>
          </template>

          <!-- 同步任务内容 -->
          <template v-if="form.taskType === TASK_TYPE.SYNC">
            <el-form-item label="源数据源" prop="sourceDatasourceId">
              <el-select
                v-model="form.sourceDatasourceId"
                placeholder="请选择源数据源"
                style="width: 100%"
                @change="handleSourceDatasourceChange"
              >
                <el-option v-for="ds in datasources" :key="ds.id" :label="ds.datasourceName || ds.name" :value="ds.id" />
              </el-select>
            </el-form-item>
            <el-form-item label="源表名" prop="sourceTable">
              <el-select
                v-model="form.sourceTable"
                placeholder="请选择源表名"
                style="width: 100%"
                :disabled="!form.sourceDatasourceId"
                :loading="loadingSourceTables"
                filterable
              >
                <el-option v-for="table in sourceTables" :key="table" :label="table" :value="table" />
              </el-select>
            </el-form-item>
            <el-form-item label="目标数据源" prop="targetDatasourceIdForSync">
              <el-select
                v-model="form.targetDatasourceIdForSync"
                placeholder="请选择目标数据源"
                style="width: 100%"
              >
                <el-option v-for="ds in datasources" :key="ds.id" :label="ds.datasourceName || ds.name" :value="ds.id" />
              </el-select>
            </el-form-item>
            <el-form-item label="目标表名" prop="targetTable">
              <el-input v-model="form.targetTable" placeholder="请输入目标表名" />
            </el-form-item>
            <el-form-item label="同步类型" prop="syncType">
              <el-radio-group v-model="form.syncType">
                <el-radio :label="SYNC_TYPE.FULL">{{ SYNC_TYPE_LABEL[SYNC_TYPE.FULL] }}</el-radio>
                <el-radio :label="SYNC_TYPE.INCR">{{ SYNC_TYPE_LABEL[SYNC_TYPE.INCR] }}</el-radio>
              </el-radio-group>
            </el-form-item>
            <el-form-item v-if="form.syncType === SYNC_TYPE.INCR" label="增量字段">
              <el-input v-model="form.timeField" placeholder="请输入增量时间字段" />
            </el-form-item>
            <el-form-item label="同步条件">
              <el-input v-model="form.whereCondition" placeholder="WHERE条件（可选）" />
            </el-form-item>
          </template>

          <!-- 质量监控任务内容 -->
          <template v-if="form.taskType === TASK_TYPE.QUALITY">
            <el-form-item label="规则模板" prop="ruleTemplate">
              <el-select v-model="form.ruleTemplate" placeholder="请选择规则模板" style="width: 100%">
                <el-option
                  v-for="(label, key) in QUALITY_RULE_TEMPLATE_LABEL"
                  :key="key"
                  :label="label"
                  :value="key"
                />
              </el-select>
            </el-form-item>
            <el-form-item label="数据库名" prop="databaseName">
              <el-input v-model="form.databaseName" placeholder="请输入数据库名" />
            </el-form-item>
            <el-form-item label="表名" prop="tableName">
              <el-input v-model="form.tableName" placeholder="请输入表名" />
            </el-form-item>
            <el-form-item label="表ID" prop="tableId">
              <el-input-number v-model="form.tableId" :min="1" placeholder="请输入表ID" style="width: 100%" />
            </el-form-item>
            <el-form-item label="字段名">
              <el-input v-model="form.columnName" placeholder="字段级规则时填写" />
            </el-form-item>
            <el-form-item label="检查参数">
              <el-input
                v-model="form.checkParams"
                type="textarea"
                :rows="4"
                placeholder='检查参数 JSON，如：{"min": 0, "max": 100}'
              />
            </el-form-item>
          </template>
        </el-tab-pane>

        <!-- 依赖关系配置 -->
        <el-tab-pane label="依赖关系" name="dependency">
          <el-form-item label="选择上游任务">
            <div class="dependency-selector">
              <el-select
                v-model="dependencyForm.upstreamTasks"
                multiple
                filterable
                remote
                reserve-keyword
                placeholder="请选择依赖的上游任务"
                style="width: 100%"
                :remote-method="searchAvailableTasks"
                :loading="searchingTasks"
                @change="handleUpstreamTasksChange"
              >
                <el-option
                  v-for="task in availableTasks"
                  :key="task.id"
                  :label="`${task.taskName} (${task.taskCode})`"
                  :value="task.id"
                >
                  <div class="task-option">
                    <div class="task-name">{{ task.taskName }}</div>
                    <div class="task-info">
                      <el-tag size="small" :type="getTaskTypeTagType(task.taskType)">
                        {{ getTaskTypeLabel(task.taskType) }}
                      </el-tag>
                      <span class="task-code">{{ task.taskCode }}</span>
                    </div>
                  </div>
                </el-option>
              </el-select>
            </div>
            <div class="form-hint">
              <el-icon><InfoFilled /></el-icon>
              选择上游任务后，当前任务将在上游任务完成后按指定条件触发执行
            </div>
          </el-form-item>

          <el-form-item label="依赖类型">
            <el-radio-group v-model="dependencyForm.dependencyType">
              <el-radio label="SUCCESS">
                <div class="radio-option">
                  <div class="option-label">成功触发</div>
                  <div class="option-desc">上游任务执行成功时触发</div>
                </div>
              </el-radio>
              <el-radio label="FAILED">
                <div class="radio-option">
                  <div class="option-label">失败触发</div>
                  <div class="option-desc">上游任务执行失败时触发</div>
                </div>
              </el-radio>
              <el-radio label="ANY">
                <div class="radio-option">
                  <div class="option-label">任意完成</div>
                  <div class="option-desc">上游任务完成（成功或失败）时触发</div>
                </div>
              </el-radio>
            </el-radio-group>
          </el-form-item>

          <el-form-item label="触发条件（可选）">
            <el-input
              v-model="dependencyForm.conditionExpression"
              placeholder="如：UPSTREAM_AFFECTED_ROWS > 100"
            />
            <div class="form-hint">
              <el-icon><InfoFilled /></el-icon>
              支持 SpEL 表达式，可用变量：
              <el-tag size="small" type="info" @click="insertVariable('UPSTREAM_AFFECTED_ROWS')">UPSTREAM_AFFECTED_ROWS</el-tag>
              <el-tag size="small" type="info" @click="insertVariable('UPSTREAM_SYNC_COUNT')">UPSTREAM_SYNC_COUNT</el-tag>
              <el-tag size="small" type="info" @click="insertVariable('UPSTREAM_DURATION_MS')">UPSTREAM_DURATION_MS</el-tag>
            </div>
          </el-form-item>

          <el-form-item label="延迟触发（秒）">
            <el-input-number
              v-model="dependencyForm.delaySeconds"
              :min="0"
              :max="3600"
              placeholder="上游任务完成后延迟多少秒触发，默认立即触发"
              style="width: 100%"
            />
            <div class="form-hint">
              <el-icon><InfoFilled /></el-icon>
              设置延迟触发可避免资源竞争，适合分批执行场景
            </div>
          </el-form-item>

          <!-- 已选上游任务列表 -->
          <el-form-item v-if="selectedUpstreamTasksDetails.length > 0">
            <div class="selected-tasks-section">
              <div class="section-title">
                <el-icon><List /></el-icon> 已选择的依赖任务：
              </div>
              <el-table :data="selectedUpstreamTasksDetails" size="small" max-height="200px" border>
                <el-table-column prop="taskName" label="任务名称" min-width="140" />
                <el-table-column prop="taskCode" label="任务编码" width="120" />
                <el-table-column prop="taskType" label="类型" width="80" align="center">
                  <template #default="{ row }">
                    <el-tag :type="getTaskTypeTagType(row.taskType)" size="small">
                      {{ getTaskTypeLabel(row.taskType) }}
                    </el-tag>
                  </template>
                </el-table-column>
                <el-table-column prop="status" label="状态" width="70" align="center">
                  <template #default="{ row }">
                    <el-tag :type="getStatusTagType(row.status)" size="small">
                      {{ getStatusLabel(row.status) }}
                    </el-tag>
                  </template>
                </el-table-column>
                <el-table-column label="操作" width="60" align="center" fixed="right">
                  <template #default="{ row }">
                    <el-button type="danger" link size="small" @click="removeUpstreamTask(row.id)">
                      移除
                    </el-button>
                  </template>
                </el-table-column>
              </el-table>
            </div>
          </el-form-item>

          <!-- 依赖关系预览 -->
          <el-form-item v-if="selectedUpstreamTasksDetails.length > 0">
            <div class="dependency-preview-section">
              <div class="section-title">
                <el-icon><Connection /></el-icon> 执行流程预览：
              </div>
              <el-steps :active="0" finish-status="success" align-center>
                <el-step
                  v-for="task in dependencyPreview"
                  :key="task.id"
                  :title="task.taskName"
                  :description="getTaskTypeLabel(task.taskType)"
                />
                <el-step title="当前任务" :description="form.taskName || '新建任务'" />
              </el-steps>
            </div>
          </el-form-item>
        </el-tab-pane>
      </el-tabs>
    </el-form>

    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="saving" @click="handleSave">
        保存
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, reactive, computed, watch, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { InfoFilled, List, Connection, CaretRight } from '@element-plus/icons-vue'
import request from '../utils/request.js'
import CronPicker from '../components/CronPicker.vue'
import { validateCron } from '../utils/cron.js'
import {
  TASK_STATUS,
  TASK_STATUS_LABEL,
  TASK_STATUS_TAG_TYPE,
  TASK_TYPE,
  TASK_TYPE_LABEL,
  TASK_TYPE_TAG_TYPE,
  SYNC_TYPE,
  SYNC_TYPE_LABEL,
  QUALITY_RULE_TEMPLATE_LABEL,
  DEPENDENCY_TYPE,
  DEPENDENCY_TYPE_LABEL,
  DEPENDENCY_TYPE_TAG_TYPE
} from '../utils/task-constants.js'

const props = defineProps({
  modelValue: {
    type: Boolean,
    default: false
  },
  taskId: {
    type: Number,
    default: null
  },
  prefillData: {
    type: Object,
    default: () => ({})
  }
})

const emit = defineEmits(['update:modelValue', 'saved'])

const visible = computed({
  get: () => props.modelValue,
  set: (val) => emit('update:modelValue', val)
})

const isEdit = computed(() => !!props.taskId)
const saving = ref(false)
const formRef = ref(null)
const activeTab = ref('basic')

const users = ref([])
const datasources = ref([])
const sourceTables = ref([])
const loadingSourceTables = ref(false)

const priorityMarks = {
  1: '低',
  5: '中',
  10: '高'
}

const form = reactive({
  id: null,
  taskName: '',
  taskType: TASK_TYPE.SQL,
  description: '',
  ownerUserId: null,
  priority: 5,
  tags: '',
  cronExpression: '',
  retryTimes: 0,
  retryInterval: 0,
  timeoutSeconds: 0,
  status: TASK_STATUS.DRAFT,
  // SQL任务字段
  sqlContent: '',
  targetDatasourceId: null,
  // 同步任务字段
  sourceDatasourceId: null,
  sourceTable: '',
  targetDatasourceIdForSync: null,
  targetTable: '',
  syncType: SYNC_TYPE.FULL,
  timeField: '',
  whereCondition: '',
  // 质量监控任务字段
  ruleTemplate: '',
  databaseName: '',
  tableName: '',
  tableId: null,
  columnName: '',
  checkParams: ''
})

// 依赖关系配置
const dependencyForm = reactive({
  upstreamTasks: [],          // 选中的上游任务ID数组
  dependencyType: 'SUCCESS',  // 依赖类型
  conditionExpression: '',    // SpEL条件表达式
  delaySeconds: 0             // 延迟秒数
})

const availableTasks = ref([])         // 可选择的任务列表
const searchingTasks = ref(false)      // 搜索任务状态
const selectedUpstreamTasksDetails = ref([]) // 已选上游任务详情
const dependencyPreview = ref([])     // 依赖关系预览数据

const rules = {
  taskName: [{ required: true, message: '请输入任务名称', trigger: 'blur' }],
  taskType: [{ required: true, message: '请选择任务类型', trigger: 'change' }],
  ownerUserId: [{ required: true, message: '请选择责任人', trigger: 'change' }],
  sqlContent: [{ required: true, message: '请输入SQL内容', trigger: 'blur', validator: (rule, value, callback) => {
    if (form.taskType === TASK_TYPE.SQL && !value) {
      callback(new Error('请输入SQL内容'))
    } else {
      callback()
    }
  } }],
  sourceDatasourceId: [{ required: true, message: '请选择源数据源', trigger: 'change', validator: (rule, value, callback) => {
    if (form.taskType === TASK_TYPE.SYNC && !value) {
      callback(new Error('请选择源数据源'))
    } else {
      callback()
    }
  } }],
  sourceTable: [{ required: true, message: '请输入源表名', trigger: 'blur', validator: (rule, value, callback) => {
    if (form.taskType === TASK_TYPE.SYNC && !value) {
      callback(new Error('请输入源表名'))
    } else {
      callback()
    }
  } }],
  targetTable: [{ required: true, message: '请输入目标表名', trigger: 'blur', validator: (rule, value, callback) => {
    if (form.taskType === TASK_TYPE.SYNC && !value) {
      callback(new Error('请输入目标表名'))
    } else {
      callback()
    }
  } }],
  ruleTemplate: [{ required: true, message: '请选择规则模板', trigger: 'change', validator: (rule, value, callback) => {
    if (form.taskType === TASK_TYPE.QUALITY && !value) {
      callback(new Error('请选择规则模板'))
    } else {
      callback()
    }
  } }],
  databaseName: [{ required: true, message: '请输入数据库名', trigger: 'blur', validator: (rule, value, callback) => {
    if (form.taskType === TASK_TYPE.QUALITY && !value) {
      callback(new Error('请输入数据库名'))
    } else {
      callback()
    }
  } }],
  tableName: [{ required: true, message: '请输入表名', trigger: 'blur', validator: (rule, value, callback) => {
    if (form.taskType === TASK_TYPE.QUALITY && !value) {
      callback(new Error('请输入表名'))
    } else {
      callback()
    }
  } }],
  tableId: [{ required: true, message: '请输入表ID', trigger: 'blur', validator: (rule, value, callback) => {
    if (form.taskType === TASK_TYPE.QUALITY && !value) {
      callback(new Error('请输入表ID'))
    } else {
      callback()
    }
  } }],
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
  form.taskName = props.prefillData.taskName || ''
  form.taskType = props.prefillData.taskType || TASK_TYPE.SQL
  form.description = props.prefillData.description || ''
  form.ownerUserId = props.prefillData.ownerUserId || null
  form.priority = props.prefillData.priority || 5
  form.tags = props.prefillData.tags || ''
  form.cronExpression = props.prefillData.cronExpression || ''
  form.retryTimes = props.prefillData.retryTimes || 0
  form.retryInterval = props.prefillData.retryInterval || 0
  form.timeoutSeconds = props.prefillData.timeoutSeconds || 0
  form.status = props.prefillData.status || TASK_STATUS.DRAFT
  form.sqlContent = props.prefillData.sqlContent || ''
  form.targetDatasourceId = props.prefillData.targetDatasourceId || null
  form.sourceDatasourceId = props.prefillData.sourceDatasourceId || null
  form.sourceTable = props.prefillData.sourceTable || ''
  form.targetDatasourceIdForSync = props.prefillData.targetDatasourceIdForSync || null
  form.targetTable = props.prefillData.targetTable || ''
  form.syncType = props.prefillData.syncType || SYNC_TYPE.FULL
  form.timeField = props.prefillData.timeField || ''
  form.whereCondition = props.prefillData.whereCondition || ''
  form.ruleTemplate = props.prefillData.ruleTemplate || ''
  form.databaseName = props.prefillData.databaseName || ''
  form.tableName = props.prefillData.tableName || ''
  form.tableId = props.prefillData.tableId || null
  form.columnName = props.prefillData.columnName || ''
  form.checkParams = props.prefillData.checkParams || ''
  activeTab.value = 'basic'
}

const loadUsers = async () => {
  try {
    const res = await request.get('/user/page', { params: { page: 1, size: 1000 } })
    users.value = res.data.records || []
  } catch (error) {
    console.error('加载用户列表失败', error)
    ElMessage.warning(error.message || '加载用户列表失败')
  }
}

const loadDatasources = async () => {
  try {
    const res = await request.get('/datasource/page', { params: { page: 1, size: 1000 } })
    datasources.value = res.data.records || []
  } catch (error) {
    console.error('加载数据源列表失败', error)
    ElMessage.warning(error.message || '加载数据源列表失败')
  }
}

const handleSourceDatasourceChange = async (datasourceId) => {
  form.sourceTable = ''
  sourceTables.value = []
  if (!datasourceId) return

  loadingSourceTables.value = true
  try {
    const res = await request.get(`/task/datasource/${datasourceId}/tables`)
    sourceTables.value = res.data || []
  } catch (error) {
    console.error('加载源表列表失败', error)
    ElMessage.warning(error.message || '加载源表列表失败')
  } finally {
    loadingSourceTables.value = false
  }
}

const loadTaskDetail = async () => {
  if (!props.taskId) return
  try {
    const res = await request.get(`/task/${props.taskId}`)
    const task = res.data?.task
    const detail = res.data?.detail
    if (task) {
      Object.assign(form, task)
    }
    if (detail) {
      if (form.taskType === TASK_TYPE.SQL) {
        form.sqlContent = detail.sqlContent
        form.targetDatasourceId = detail.targetDatasourceId
      } else if (form.taskType === TASK_TYPE.SYNC) {
        form.sourceDatasourceId = detail.sourceDatasourceId
        form.sourceTable = detail.sourceTable
        form.targetDatasourceIdForSync = detail.targetDatasourceId
        form.targetTable = detail.targetTable
        form.syncType = detail.syncType
        form.timeField = detail.timeField
        form.whereCondition = detail.whereCondition
        // 加载源表列表
        if (form.sourceDatasourceId) {
          await handleSourceDatasourceChange(form.sourceDatasourceId)
        }
        form.targetTable = detail.targetTable
        form.syncType = detail.syncType
        form.timeField = detail.timeField
        form.whereCondition = detail.whereCondition
      } else if (form.taskType === TASK_TYPE.QUALITY) {
        form.ruleTemplate = detail.ruleTemplate
        form.databaseName = detail.databaseName
        form.tableName = detail.tableName
        form.tableId = detail.tableId
        form.columnName = detail.columnName
        form.checkParams = detail.checkParams
      }
    }
  } catch (error) {
    ElMessage.error(error.message || '加载任务详情失败')
  }
}

// ==================== 依赖关系相关方法 ====================

/**
 * 搜索可用的上游任务
 */
const searchAvailableTasks = async (query) => {
  if (!query) {
    availableTasks.value = []
    return
  }

  searchingTasks.value = true
  try {
    const currentTaskId = form.id || 0
    const res = await request.get('/task/search-for-dependency', {
      params: {
        currentTaskId,
        keyword: query,
        taskType: '',
        page: 1,
        size: 20
      }
    })
    availableTasks.value = res.data?.records || []
  } catch (error) {
    console.error('搜索任务失败', error)
    ElMessage.error('搜索任务失败')
  } finally {
    searchingTasks.value = false
  }
}

/**
 * 上游任务选择变化
 */
const handleUpstreamTasksChange = (selectedIds) => {
  updateSelectedTasksDetails()
  updateDependencyPreview()
}

/**
 * 更新已选任务详情
 */
const updateSelectedTasksDetails = () => {
  selectedUpstreamTasksDetails.value = availableTasks.value.filter(task =>
    dependencyForm.upstreamTasks.includes(task.id)
  )
}

/**
 * 更新依赖关系预览
 */
const updateDependencyPreview = () => {
  dependencyPreview.value = selectedUpstreamTasksDetails.value.map(task => ({
    id: task.id,
    taskName: task.taskName,
    taskType: task.taskType
  }))
}

/**
 * 移除上游任务
 */
const removeUpstreamTask = (taskId) => {
  const index = dependencyForm.upstreamTasks.indexOf(taskId)
  if (index > -1) {
    dependencyForm.upstreamTasks.splice(index, 1)
    handleUpstreamTasksChange(dependencyForm.upstreamTasks)
  }
}

/**
 * 插入变量到条件表达式
 */
const insertVariable = (variable) => {
  if (!dependencyForm.conditionExpression) {
    dependencyForm.conditionExpression = variable + ' > 0'
  } else {
    dependencyForm.conditionExpression += ' && ' + variable + ' > 0'
  }
}

/**
 * 获取任务类型标签类型
 */
const getTaskTypeTagType = (type) => {
  const tagTypes = {
    [TASK_TYPE.SQL]: 'success',
    [TASK_TYPE.SYNC]: 'primary',
    [TASK_TYPE.QUALITY]: 'warning'
  }
  return tagTypes[type] || 'info'
}

/**
 * 获取任务类型标签
 */
const getTaskTypeLabel = (type) => {
  return TASK_TYPE_LABEL[type] || type || '-'
}

/**
 * 获取状态标签类型
 */
const getStatusTagType = (status) => {
  if (status === TASK_STATUS.ENABLED) return 'success'
  if (status === TASK_STATUS.DISABLED) return 'warning'
  if (status === TASK_STATUS.DRAFT) return 'info'
  return 'info'
}

/**
 * 获取状态标签
 */
const getStatusLabel = (status) => {
  return TASK_STATUS_LABEL[status] || '-'
}

/**
 * 初始化时加载可用任务
 */
const initializeAvailableTasks = async () => {
  try {
    const currentTaskId = form.id || 0
    const res = await request.get('/task/search-for-dependency', {
      params: {
        currentTaskId,
        taskType: '',
        keyword: '',
        page: 1,
        size: 100
      }
    })
    availableTasks.value = res.data?.records || []
  } catch (error) {
    console.error('加载可用任务失败', error)
  }
}

const handleOpen = () => {
  resetForm()
  loadUsers()
  loadDatasources()
  // 初始化依赖关系数据
  if (!isEdit.value) {
    initializeAvailableTasks()
  }
  if (props.taskId) {
    loadTaskDetail()
    loadTaskDependencies()
  }
}

/**
 * 加载任务依赖关系
 */
const loadTaskDependencies = async () => {
  if (!props.taskId) return
  try {
    const res = await request.get(`/task/${props.taskId}/dependencies`)
    const vo = res.data
    if (vo && vo.upstreamDependencies) {
      dependencyForm.upstreamTasks = vo.upstreamDependencies.map(dep => dep.upstreamTaskId)

      // 如果有依赖关系，加载第一个依赖的配置信息
      if (vo.upstreamDependencies.length > 0) {
        const firstDep = vo.upstreamDependencies[0]
        dependencyForm.dependencyType = firstDep.dependencyType || 'SUCCESS'
        dependencyForm.conditionExpression = firstDep.conditionExpression || ''
        dependencyForm.delaySeconds = firstDep.delaySeconds || 0
      }

      updateSelectedTasksDetails()
      updateDependencyPreview()
    }
  } catch (error) {
    console.error('加载任务依赖失败', error)
  }
}

const handleSave = async () => {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  saving.value = true
  try {
    const payload = {
      taskName: form.taskName,
      taskType: form.taskType,
      description: form.description,
      ownerUserId: form.ownerUserId,
      ownerUserName: users.value.find(u => u.id === form.ownerUserId)?.nickname || '',
      createUserId: form.ownerUserId,
      priority: form.priority,
      tags: form.tags,
      cronExpression: form.cronExpression,
      retryTimes: form.retryTimes,
      retryInterval: form.retryInterval,
      timeoutSeconds: form.timeoutSeconds,
      status: form.status
    }

    if (form.taskType === TASK_TYPE.SQL) {
      payload.sqlContent = form.sqlContent
      payload.targetDatasourceId = form.targetDatasourceId
    } else if (form.taskType === TASK_TYPE.SYNC) {
      payload.sourceDatasourceId = form.sourceDatasourceId
      payload.sourceTable = form.sourceTable
      payload.targetDatasourceIdForSync = form.targetDatasourceIdForSync
      payload.targetTable = form.targetTable
      payload.syncType = form.syncType
      payload.timeField = form.timeField
      payload.whereCondition = form.whereCondition
    } else if (form.taskType === TASK_TYPE.QUALITY) {
      payload.ruleTemplate = form.ruleTemplate
      payload.databaseName = form.databaseName
      payload.tableName = form.tableName
      payload.tableId = form.tableId
      payload.columnName = form.columnName
      payload.checkParams = form.checkParams
    }

    if (isEdit.value) {
      await request.put(`/task/${form.id}`, payload)
      ElMessage.success('更新成功')
    } else {
      const res = await request.post('/task', payload)
      form.id = res.data
      ElMessage.success('创建成功')
    }

    // 保存依赖关系
    if (dependencyForm.upstreamTasks.length > 0) {
      await saveTaskDependencies(form.id)
    }

    visible.value = false
    emit('saved')
  } catch (error) {
    ElMessage.error(error.message || '保存失败')
  } finally {
    saving.value = false
  }
}

/**
 * 保存任务依赖关系
 */
const saveTaskDependencies = async (taskId) => {
  try {
    const dependencyPromises = dependencyForm.upstreamTasks.map(upstreamId => {
      return request.post(`/task/${taskId}/dependencies`, {
        upstreamTaskId: upstreamId,
        dependencyType: dependencyForm.dependencyType,
        conditionExpression: dependencyForm.conditionExpression,
        delaySeconds: dependencyForm.delaySeconds,
        createUserId: form.ownerUserId
      })
    })

    await Promise.all(dependencyPromises)
    console.log(`成功创建 ${dependencyPromises.length} 个依赖关系`)
  } catch (error) {
    ElMessage.error('保存依赖关系失败：' + error.message)
    throw error
  }
}

watch(() => props.taskId, () => {
  if (visible.value) {
    handleOpen()
  }
})
</script>

<style scoped>
.form-hint {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 6px;
  font-size: 12px;
  color: #909399;
  line-height: 1.4;
}

.form-hint .el-icon {
  flex-shrink: 0;
}

.form-hint .el-tag {
  cursor: pointer;
  margin: 0 2px;
}

.dependency-selector {
  width: 100%;
}

.task-option {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.task-name {
  font-weight: 500;
  color: #303133;
}

.task-info {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
  color: #606266;
}

.task-code {
  font-family: monospace;
  color: #909399;
}

.radio-option {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.option-label {
  font-weight: 500;
  color: #303133;
}

.option-desc {
  font-size: 12px;
  color: #909399;
}

.selected-tasks-section {
  width: 100%;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  padding: 12px;
  background-color: #f5f7fa;
}

.section-title {
  display: flex;
  align-items: center;
  gap: 6px;
  font-weight: 600;
  color: #303133;
  margin-bottom: 8px;
  font-size: 14px;
}

.dependency-preview-section {
  width: 100%;
  border: 1px solid #e6f7ff;
  border-radius: 4px;
  padding: 12px;
  background-color: #f0f9ff;
}

:deep(.el-steps) {
  margin: 16px 0;
}

:deep(.el-radio) {
  display: flex;
  align-items: flex-start;
  margin-bottom: 12px;
  white-space: normal;
}

:deep(.el-radio__label) {
  white-space: normal;
  line-height: 1.4;
}
</style>
