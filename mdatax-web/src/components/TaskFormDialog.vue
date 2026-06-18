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
              <el-select v-model="form.sourceDatasourceId" placeholder="请选择源数据源" style="width: 100%">
                <el-option v-for="ds in datasources" :key="ds.id" :label="ds.datasourceName" :value="ds.id" />
              </el-select>
            </el-form-item>
            <el-form-item label="源表名" prop="sourceTable">
              <el-input v-model="form.sourceTable" placeholder="请输入源表名" />
            </el-form-item>
            <el-form-item label="目标数据源" prop="targetDatasourceIdForSync">
              <el-select v-model="form.targetDatasourceIdForSync" placeholder="请选择目标数据源" style="width: 100%">
                <el-option v-for="ds in datasources" :key="ds.id" :label="ds.datasourceName" :value="ds.id" />
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
import { ref, reactive, computed, watch } from 'vue'
import { ElMessage } from 'element-plus'
import request from '../utils/request.js'
import CronPicker from '../components/CronPicker.vue'
import { validateCron } from '../utils/cron.js'
import {
  TASK_STATUS,
  TASK_TYPE,
  TASK_TYPE_LABEL,
  SYNC_TYPE,
  SYNC_TYPE_LABEL
} from '../utils/task-constants.js'

const props = defineProps({
  modelValue: {
    type: Boolean,
    default: false
  },
  taskId: {
    type: Number,
    default: null
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
  whereCondition: ''
})

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
  form.taskType = TASK_TYPE.SQL
  form.description = ''
  form.ownerUserId = null
  form.priority = 5
  form.tags = ''
  form.cronExpression = ''
  form.retryTimes = 0
  form.retryInterval = 0
  form.timeoutSeconds = 0
  form.status = TASK_STATUS.DRAFT
  form.sqlContent = ''
  form.targetDatasourceId = null
  form.sourceDatasourceId = null
  form.sourceTable = ''
  form.targetDatasourceIdForSync = null
  form.targetTable = ''
  form.syncType = SYNC_TYPE.FULL
  form.timeField = ''
  form.whereCondition = ''
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
      }
    }
  } catch (error) {
    ElMessage.error(error.message || '加载任务详情失败')
  }
}

const handleOpen = () => {
  resetForm()
  loadUsers()
  loadDatasources()
  if (props.taskId) {
    loadTaskDetail()
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
    }

    if (isEdit.value) {
      await request.put(`/task/${form.id}`, payload)
      ElMessage.success('更新成功')
    } else {
      await request.post('/task', payload)
      ElMessage.success('创建成功')
    }

    visible.value = false
    emit('saved')
  } catch (error) {
    ElMessage.error(error.message || '保存失败')
  } finally {
    saving.value = false
  }
}

watch(() => props.taskId, () => {
  if (visible.value) {
    handleOpen()
  }
})
</script>
