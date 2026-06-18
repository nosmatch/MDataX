<template>
  <div class="task-page">
    <div class="page-header">
      <h2>任务管理</h2>
      <div class="header-actions">
        <el-button type="primary" @click="openCreateDialog">
          <el-icon><Plus /></el-icon> 新建任务
        </el-button>
      </div>
    </div>

    <!-- 筛选区 -->
    <div class="filter-section">
      <el-row :gutter="16">
        <el-col :span="4">
          <el-select v-model="filters.taskType" placeholder="任务类型" clearable @change="fetchTasks">
            <el-option label="全部" value="" />
            <el-option :label="TASK_TYPE_LABEL[TASK_TYPE.SQL]" :value="TASK_TYPE.SQL" />
            <el-option :label="TASK_TYPE_LABEL[TASK_TYPE.SYNC]" :value="TASK_TYPE.SYNC" />
          </el-select>
        </el-col>
        <el-col :span="3">
          <el-select v-model="filters.status" placeholder="状态" clearable @change="fetchTasks">
            <el-option label="全部" value="" />
            <el-option :label="TASK_STATUS_LABEL[TASK_STATUS.ENABLED]" :value="TASK_STATUS.ENABLED" />
            <el-option :label="TASK_STATUS_LABEL[TASK_STATUS.DISABLED]" :value="TASK_STATUS.DISABLED" />
            <el-option :label="TASK_STATUS_LABEL[TASK_STATUS.DRAFT]" :value="TASK_STATUS.DRAFT" />
          </el-select>
        </el-col>
        <el-col :span="3">
          <el-select v-model="filters.lastExecutionStatus" placeholder="最近执行" clearable @change="fetchTasks">
            <el-option label="全部" value="" />
            <el-option label="成功" :value="EXECUTION_STATUS.SUCCESS" />
            <el-option label="失败" :value="EXECUTION_STATUS.FAILED" />
            <el-option label="运行中" :value="EXECUTION_STATUS.RUNNING" />
            <el-option label="超时" :value="EXECUTION_STATUS.TIMEOUT" />
            <el-option label="已终止" :value="EXECUTION_STATUS.KILLED" />
            <el-option label="未执行" value="NONE" />
          </el-select>
        </el-col>
        <el-col :span="4">
          <el-select v-model="filters.priority" placeholder="优先级" clearable @change="fetchTasks">
            <el-option label="全部" value="" />
            <el-option label="高优先级 (7-10)" value="7" />
            <el-option label="中优先级 (4-6)" value="4" />
            <el-option label="低优先级 (1-3)" value="1" />
          </el-select>
        </el-col>
        <el-col :span="4">
          <el-input
            v-model="filters.keyword"
            placeholder="搜索任务名称/编码/标签"
            clearable
            @keyup.enter="fetchTasks"
          >
            <template #append>
              <el-button @click="fetchTasks">
                <el-icon><Search /></el-icon>
              </el-button>
            </template>
          </el-input>
        </el-col>
        <el-col :span="6">
          <el-date-picker
            v-model="filters.dateRange"
            type="daterange"
            range-separator="至"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
            format="YYYY-MM-DD"
            value-format="YYYY-MM-DD HH:mm:ss"
            @change="fetchTasks"
          />
        </el-col>
      </el-row>
    </div>

    <!-- 任务列表 -->
    <el-table :data="taskList" v-loading="loading" stripe @row-click="handleRowClick">
      <el-table-column type="selection" width="55" align="center" />
      <el-table-column prop="taskCode" label="任务编码" width="140">
        <template #default="{ row }">
          <el-tag size="small" type="info">{{ row.taskCode }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="taskName" label="任务名称" min-width="160">
        <template #default="{ row }">
          <div style="display: flex; align-items: center; gap: 8px">
            <span>{{ row.taskName }}</span>
            <el-tag v-if="row.tags" size="small" type="warning">{{ row.tags }}</el-tag>
          </div>
        </template>
      </el-table-column>
      <el-table-column prop="taskType" label="类型" width="80" align="center">
        <template #default="{ row }">
          <el-tag :type="TASK_TYPE_TAG_TYPE[row.taskType]" size="small">
            {{ TASK_TYPE_LABEL[row.taskType] || row.taskType }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="ownerUserName" label="责任人" width="100" />
      <el-table-column prop="priority" label="优先级" width="80" align="center">
        <template #default="{ row }">
          <el-tag :type="getPriorityTagType(row.priority)" size="small">{{ getPriorityLabel(row.priority) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="cronExpression" label="调度" width="140">
        <template #default="{ row }">
          {{ row.cronExpression || '-' }}
        </template>
      </el-table-column>
      <el-table-column prop="status" label="状态" width="80" align="center">
        <template #default="{ row }">
          <el-tag :type="TASK_STATUS_TAG_TYPE[row.status]" size="small">
            {{ TASK_STATUS_LABEL[row.status] }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="最近执行" width="170" align="center">
        <template #default="{ row }">
          <LastExecutionBadge :status="row.lastExecutionStatus" :time="row.lastExecutionTime" />
        </template>
      </el-table-column>
      <el-table-column label="依赖" width="100" align="center">
        <template #default="{ row }">
          <el-button type="info" link size="small" @click.stop="openDetailDrawer(row, 'dependencies')">
            上{{ row.upstreamCount || 0 }}/下{{ row.downstreamCount || 0 }}
          </el-button>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="280" fixed="right">
        <template #default="{ row }">
          <el-button
            type="success"
            link
            size="small"
            :disabled="row.status !== TASK_STATUS.ENABLED"
            :loading="executingId === row.id"
            @click.stop="handleExecute(row)"
          >
            执行
          </el-button>
          <el-button type="primary" link size="small" @click.stop="handleToggle(row)">
            {{ row.status === TASK_STATUS.ENABLED ? '停用' : '启用' }}
          </el-button>
          <el-button type="primary" link size="small" @click.stop="handleEdit(row)">
            编辑
          </el-button>
          <el-button type="info" link size="small" @click.stop="openDetailDrawer(row, 'history')">
            历史
          </el-button>
          <el-button type="danger" link size="small" @click.stop="handleDelete(row)">
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

    <!-- 创建/编辑对话框 -->
    <TaskFormDialog
      v-model="taskDialogVisible"
      :task-id="currentTaskId"
      @saved="fetchTasks"
    />

    <!-- 任务详情抽屉 -->
    <el-drawer
      v-model="detailDrawerVisible"
      :title="`任务详情 - ${currentTask?.taskName || ''}`"
      size="900px"
      :close-on-click-modal="false"
      @closed="handleDrawerClosed"
    >
      <template v-if="currentTask">
        <div class="drawer-header-info">
          <el-tag :type="TASK_TYPE_TAG_TYPE[currentTask.taskType]" size="small">
            {{ TASK_TYPE_LABEL[currentTask.taskType] || currentTask.taskType }}
          </el-tag>
          <el-tag
            :type="TASK_STATUS_TAG_TYPE[currentTask.status]"
            size="small"
          >
            {{ TASK_STATUS_LABEL[currentTask.status] }}
          </el-tag>
          <LastExecutionBadge
            v-if="currentTask.lastExecutionStatus"
            :status="currentTask.lastExecutionStatus"
            :time="currentTask.lastExecutionTime"
          />
          <el-button
            type="success"
            size="small"
            :disabled="currentTask.status !== 1"
            :loading="drawerExecuting"
            @click="handleExecuteFromDrawer"
          >
            手动执行
          </el-button>
          <el-button type="primary" size="small" @click="handleEditFromDrawer">编辑任务</el-button>
        </div>

        <el-tabs v-model="activeTab" class="detail-tabs" @tab-change="handleTabChange">
          <el-tab-pane label="基础配置" name="basic">
            <TaskBasicConfig :task="currentTask" :detail="currentTaskDetail" />
          </el-tab-pane>
          <el-tab-pane label="依赖 DAG" name="dependencies" lazy>
            <DependencyGraph
              v-if="detailDrawerVisible"
              ref="dependencyGraphRef"
              :task-id="Number(currentTask.id)"
              @select-task="handleSelectTaskInGraph"
            />
          </el-tab-pane>
          <el-tab-pane label="执行历史" name="history">
            <ExecutionHistoryPanel
              :key="currentTask.id"
              :task-id="Number(currentTask.id)"
              embedded
            />
          </el-tab-pane>
        </el-tabs>
      </template>
    </el-drawer>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Search } from '@element-plus/icons-vue'
import request from '../utils/request.js'
import DependencyGraph from '../components/DependencyGraph.vue'
import LastExecutionBadge from '../components/LastExecutionBadge.vue'
import TaskFormDialog from '../components/TaskFormDialog.vue'
import ExecutionHistoryPanel from '../components/ExecutionHistoryPanel.vue'
import TaskBasicConfig from '../components/TaskBasicConfig.vue'
import { useAuthStore } from '../stores/auth.js'
import {
  TASK_STATUS,
  TASK_STATUS_LABEL,
  TASK_STATUS_TAG_TYPE,
  TASK_TYPE,
  TASK_TYPE_LABEL,
  TASK_TYPE_TAG_TYPE,
  EXECUTION_STATUS,
  getPriorityLabel,
  getPriorityTagType
} from '../utils/task-constants.js'

const loading = ref(false)
const taskList = ref([])
const page = ref(1)
const size = ref(10)
const total = ref(0)

const authStore = useAuthStore()
const currentUserId = computed(() => authStore.user?.id || null)

const taskDialogVisible = ref(false)
const currentTaskId = ref(null)
const executingId = ref(null)
const drawerExecuting = ref(false)

const detailDrawerVisible = ref(false)
const currentTask = ref(null)
const currentTaskDetail = ref(null)
const activeTab = ref('basic')
const dependencyGraphRef = ref(null)

const filters = reactive({
  taskType: '',
  status: '',
  lastExecutionStatus: '',
  priority: '',
  keyword: '',
  dateRange: null
})

const fetchTasks = async () => {
  loading.value = true
  try {
    const params = {
      page: page.value,
      size: size.value,
      keyword: filters.keyword,
      taskType: filters.taskType,
      status: filters.status ? parseInt(filters.status) : null,
      priority: filters.priority ? parseInt(filters.priority) : null,
      startTime: filters.dateRange?.[0],
      endTime: filters.dateRange?.[1],
      lastExecutionStatus: filters.lastExecutionStatus || null
    }

    const res = await request.get('/task/page', { params })
    taskList.value = res.data.records || []
    total.value = res.data.total || 0
  } catch (error) {
    ElMessage.error(error.message || '获取数据失败')
  } finally {
    loading.value = false
  }
}

const resetFilters = () => {
  filters.taskType = ''
  filters.status = ''
  filters.lastExecutionStatus = ''
  filters.priority = ''
  filters.keyword = ''
  filters.dateRange = null
  fetchTasks()
}

const openCreateDialog = () => {
  currentTaskId.value = null
  taskDialogVisible.value = true
}

const handleEdit = (row) => {
  currentTaskId.value = row.id
  taskDialogVisible.value = true
}

const handleToggle = async (row) => {
  try {
    await request.post(`/task/${row.id}/toggle`)
    ElMessage.success('操作成功')
    fetchTasks()
  } catch (error) {
    ElMessage.error(error.message || '操作失败')
  }
}

const handleExecute = async (row) => {
  executingId.value = row.id
  try {
    const res = await request.post(`/task/${row.id}/execute`, {
      triggerUserId: currentUserId.value
    })
    ElMessage.success('执行成功，执行ID: ' + res.data)
    fetchTasks()
  } catch (error) {
    ElMessage.error(error.message || '执行失败')
  } finally {
    executingId.value = null
  }
}

const handleExecuteFromDrawer = async () => {
  if (!currentTask.value) return
  drawerExecuting.value = true
  try {
    const res = await request.post(`/task/${currentTask.value.id}/execute`, {
      triggerUserId: currentUserId.value
    })
    ElMessage.success('执行成功，执行ID: ' + res.data)
    fetchTasks()
  } catch (error) {
    ElMessage.error(error.message || '执行失败')
  } finally {
    drawerExecuting.value = false
  }
}

const handleEditFromDrawer = () => {
  if (!currentTask.value) return
  currentTaskId.value = currentTask.value.id
  taskDialogVisible.value = true
}

const loadTaskDetail = async (taskId) => {
  try {
    const res = await request.get(`/task/${taskId}`)
    if (res.code === 200 && res.data) {
      currentTask.value = res.data.task || res.data
      currentTaskDetail.value = res.data.detail || null
    }
  } catch (error) {
    ElMessage.error(error.message || '获取任务详情失败')
  }
}

const openDetailDrawer = (row, tab) => {
  currentTask.value = row
  currentTaskDetail.value = null
  activeTab.value = tab || 'basic'
  detailDrawerVisible.value = true
  loadTaskDetail(row.id)
}

const handleRowClick = (row) => {
  openDetailDrawer(row, 'basic')
}

const handleDrawerClosed = () => {
  currentTask.value = null
  currentTaskDetail.value = null
  activeTab.value = 'basic'
}

const handleSelectTaskInGraph = (taskId) => {
  loadTaskDetail(taskId)
}

const handleTabChange = (tabName) => {
  if (tabName === 'dependencies' && dependencyGraphRef.value) {
    setTimeout(() => {
      dependencyGraphRef.value.renderDag?.()
    }, 300)
  }
}

const handleDelete = (row) => {
  ElMessageBox.confirm(`确定删除任务 "${row.taskName}" 吗？`, '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
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

onMounted(() => {
  fetchTasks()
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

.filter-section {
  margin-bottom: 16px;
  padding: 16px;
  background: #f5f7fa;
  border-radius: 4px;
}

.pagination-wrapper {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}

.drawer-header-info {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;
  padding-bottom: 16px;
  border-bottom: 1px solid #ebeef5;
}

.detail-tabs :deep(.el-tabs__content) {
  padding-top: 8px;
}

:deep(.el-table__row) {
  cursor: pointer;
}
</style>
