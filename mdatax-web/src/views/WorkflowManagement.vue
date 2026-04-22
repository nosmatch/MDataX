<template>
  <div class="workflow-page">
    <div class="page-header">
      <h2>工作流管理</h2>
    </div>

    <div class="toolbar">
      <el-input
        v-model="keyword"
        placeholder="搜索工作流名称"
        clearable
        style="width: 300px"
        @keyup.enter="fetchWorkflows"
      >
        <template #append>
          <el-button @click="fetchWorkflows">
            <el-icon><Search /></el-icon>
          </el-button>
        </template>
      </el-input>
      <el-button type="primary" @click="openCreateDialog">
        <el-icon><Plus /></el-icon> 新建工作流
      </el-button>
    </div>

    <el-table :data="workflowList" v-loading="loading" stripe>
      <el-table-column prop="workflowName" label="工作流名称" min-width="160" />
      <el-table-column prop="description" label="描述" min-width="120">
        <template #default="{ row }">
          {{ row.description || '-' }}
        </template>
      </el-table-column>
      <el-table-column prop="cronExpression" label="Cron表达式" min-width="130">
        <template #default="{ row }">
          {{ row.cronExpression || '-' }}
        </template>
      </el-table-column>
      <el-table-column label="调度状态" width="90" align="center">
        <template #default="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">
            {{ row.status === 1 ? '启用' : '停用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="最近执行" min-width="160">
        <template #default="{ row }">
          <div v-if="row._lastExecution">
            <el-tag
              :type="row._lastExecution.status === 'SUCCESS' ? 'success'
                : row._lastExecution.status === 'FAILED' ? 'danger'
                : row._lastExecution.status === 'RUNNING' ? 'warning' : 'info'"
              size="small"
            >
              {{ formatStatus(row._lastExecution.status) }}
            </el-tag>
            <div class="sub-text">{{ formatTime(row._lastExecution.startTime) }}</div>
          </div>
          <span v-else class="sub-text">-</span>
        </template>
      </el-table-column>
      <el-table-column label="下次执行" min-width="160">
        <template #default="{ row }">
          <span class="sub-text">{{ row._nextExecution || '-' }}</span>
        </template>
      </el-table-column>
      <el-table-column label="DS入口" width="90" align="center">
        <template #default="{ row }">
          <el-button v-if="row._dsUrl" type="primary" link size="small" @click="openDsUrl(row)">
            打开
          </el-button>
          <span v-else class="sub-text">-</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="340" fixed="right">
        <template #default="{ row }">
          <el-button
            type="success"
            link
            size="small"
            :disabled="row.status !== 1"
            :loading="executingId === row.id"
            @click="handleExecute(row)"
          >
            执行
          </el-button>
          <el-button type="warning" link size="small" @click="openDagDialog(row)">
            DAG
          </el-button>
          <el-button type="info" link size="small" @click="openHistoryDialog(row)">
            历史
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
        @size-change="fetchWorkflows"
        @current-change="fetchWorkflows"
      />
    </div>

    <!-- 创建/编辑对话框 -->
    <el-dialog
      v-model="dialogVisible"
      :title="isEdit ? '编辑工作流' : '新建工作流'"
      width="560px"
      :close-on-click-modal="false"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="工作流名称" prop="workflowName">
          <el-input v-model="form.workflowName" placeholder="请输入工作流名称" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="form.description" placeholder="请输入描述" />
        </el-form-item>
        <el-form-item label="Cron表达式" prop="cronExpression">
          <CronPicker v-model="form.cronExpression" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleSave">
          保存
        </el-button>
      </template>
    </el-dialog>

    <!-- DAG 流程图对话框 -->
    <el-dialog
      v-model="dagDialogVisible"
      title="DAG 流程图"
      width="900px"
      :close-on-click-modal="false"
    >
      <div v-if="dagLoading" style="text-align: center; padding: 60px">
        <el-icon class="is-loading" style="font-size: 32px"><Loading /></el-icon>
      </div>
      <div v-else-if="dagError" style="color: #f56c6c; padding: 20px">
        {{ dagError }}
      </div>
      <div v-else-if="dagNodes.length === 0" style="text-align: center; padding: 60px; color: #909399">
        该工作流暂无任务
      </div>
      <div v-else class="dag-wrapper" :style="{ width: dagWidth + 'px', height: dagHeight + 'px' }">
        <svg class="dag-svg" :width="dagWidth" :height="dagHeight">
          <defs>
            <marker id="arrow" markerWidth="10" markerHeight="10" refX="9" refY="3" orient="auto">
              <path d="M0,0 L0,6 L9,3 z" fill="#909399" />
            </marker>
          </defs>
          <path
            v-for="(edge, i) in dagEdges"
            :key="'e' + i"
            :d="edge.path"
            fill="none"
            stroke="#909399"
            stroke-width="1.5"
            marker-end="url(#arrow)"
          />
        </svg>
        <div
          v-for="node in dagNodes"
          :key="node.id"
          class="dag-node"
          :class="{ 'node-active': node.status === 1 }"
          :style="{ left: node.x + 'px', top: node.y + 'px', width: node.width + 'px', height: node.height + 'px' }"
        >
          <div class="node-title">{{ node.name }}</div>
          <div class="node-code">{{ node.dsTaskCode || '-' }}</div>
        </div>
      </div>
      <template #footer>
        <el-button @click="dagDialogVisible = false">关闭</el-button>
      </template>
    </el-dialog>

    <!-- 执行历史对话框 -->
    <el-dialog
      v-model="historyDialogVisible"
      title="执行历史"
      width="800px"
      :close-on-click-modal="false"
    >
      <div v-if="historyLoading" style="text-align: center; padding: 40px">
        <el-icon class="is-loading" style="font-size: 24px"><Loading /></el-icon>
      </div>
      <div v-else>
        <el-table :data="historyList" stripe size="small">
          <el-table-column prop="dsInstanceId" label="DS实例ID" min-width="120" />
          <el-table-column label="触发方式" width="90" align="center">
            <template #default="{ row }">
              <el-tag :type="row.triggerType === 'MANUAL' ? 'primary' : 'info'" size="small">
                {{ row.triggerType === 'MANUAL' ? '手动' : '定时' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="90" align="center">
            <template #default="{ row }">
              <el-tag
                :type="row.status === 'SUCCESS' ? 'success' : row.status === 'FAILED' ? 'danger' : 'warning'"
                size="small"
              >
                {{ formatStatus(row.status) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="startTime" label="开始时间" min-width="160" />
          <el-table-column prop="endTime" label="结束时间" min-width="160">
            <template #default="{ row }">
              {{ row.endTime || '-' }}
            </template>
          </el-table-column>
          <el-table-column label="操作" width="100" align="center">
            <template #default="{ row }">
              <el-button type="primary" link size="small" @click="openLogDialog(row)">
                日志
              </el-button>
            </template>
          </el-table-column>
        </el-table>
        <div class="pagination-wrapper">
          <el-pagination
            v-model:current-page="historyPage"
            v-model:page-size="historySize"
            :total="historyTotal"
            :page-sizes="[10, 20, 50]"
            layout="total, sizes, prev, pager, next"
            @size-change="fetchHistory"
            @current-change="fetchHistory"
          />
        </div>
      </div>
      <template #footer>
        <el-button @click="historyDialogVisible = false">关闭</el-button>
      </template>
    </el-dialog>

    <!-- 日志详情对话框 -->
    <el-dialog
      v-model="logDialogVisible"
      title="执行日志"
      width="800px"
      :close-on-click-modal="false"
    >
      <div v-if="logLoading" style="text-align: center; padding: 40px">
        <el-icon class="is-loading" style="font-size: 24px"><Loading /></el-icon>
      </div>
      <div v-else-if="logList.length === 0" style="text-align: center; padding: 40px; color: #909399">
        暂无日志
      </div>
      <div v-else>
        <el-table :data="logList" stripe size="small">
          <el-table-column prop="taskId" label="任务ID" width="80" />
          <el-table-column prop="startTime" label="开始时间" min-width="160" />
          <el-table-column prop="endTime" label="结束时间" min-width="160">
            <template #default="{ row }">
              {{ row.endTime || '-' }}
            </template>
          </el-table-column>
          <el-table-column label="状态" width="80" align="center">
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
      </div>
      <template #footer>
        <el-button @click="logDialogVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Search, Loading } from '@element-plus/icons-vue'
import request from '../utils/request.js'
import CronPicker from '../components/CronPicker.vue'
import { validateCron } from '../utils/cron.js'

const loading = ref(false)
const keyword = ref('')
const workflowList = ref([])
const page = ref(1)
const size = ref(10)
const total = ref(0)

const dialogVisible = ref(false)
const isEdit = ref(false)
const saving = ref(false)
const formRef = ref(null)
const executingId = ref(null)

const dagDialogVisible = ref(false)
const dagLoading = ref(false)
const dagError = ref('')
const dagNodes = ref([])
const dagEdges = ref([])
const dagWidth = ref(800)
const dagHeight = ref(400)

const historyDialogVisible = ref(false)
const historyLoading = ref(false)
const historyList = ref([])
const historyPage = ref(1)
const historySize = ref(10)
const historyTotal = ref(0)
const currentHistoryWorkflowId = ref(null)

const logDialogVisible = ref(false)
const logLoading = ref(false)
const logList = ref([])

const form = reactive({
  id: null,
  workflowName: '',
  description: '',
  cronExpression: ''
})

const rules = {
  workflowName: [{ required: true, message: '请输入工作流名称', trigger: 'blur' }],
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
  form.workflowName = ''
  form.description = ''
  form.cronExpression = ''
}

const fetchWorkflows = async () => {
  loading.value = true
  try {
    const res = await request.get('/sql-task-workflow/page', {
      params: { page: page.value, size: size.value, keyword: keyword.value }
    })
    workflowList.value = res.data.records
    total.value = res.data.total
    // 并行加载每个工作流的最近执行和下次执行
    await loadExtraInfo(workflowList.value)
  } catch (error) {
    ElMessage.error(error.message || '获取数据失败')
  } finally {
    loading.value = false
  }
}

const loadExtraInfo = async (list) => {
  const promises = list.map(async (row) => {
    try {
      const [lastRes, nextRes, dsRes] = await Promise.allSettled([
        request.get(`/sql-task-workflow/${row.id}/last-execution`),
        request.get(`/sql-task-workflow/${row.id}/next-execution`),
        request.get(`/sql-task-workflow/${row.id}/ds-url`).catch(() => null)
      ])
      if (lastRes.status === 'fulfilled' && lastRes.value.data) {
        row._lastExecution = lastRes.value.data
      }
      if (nextRes.status === 'fulfilled' && nextRes.value.data) {
        row._nextExecution = formatTime(nextRes.value.data)
      }
      if (dsRes && dsRes.status === 'fulfilled' && dsRes.value.data) {
        row._dsUrl = dsRes.value.data.dsUrl
      }
    } catch (e) {
      // silent
    }
  })
  await Promise.all(promises)
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

const handleSave = async () => {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  saving.value = true
  try {
    const payload = {
      workflowName: form.workflowName,
      description: form.description || null,
      cronExpression: form.cronExpression || null
    }
    if (isEdit.value) {
      await request.put(`/sql-task-workflow/${form.id}`, payload)
      ElMessage.success('更新成功')
    } else {
      await request.post('/sql-task-workflow', payload)
      ElMessage.success('保存成功')
    }
    dialogVisible.value = false
    fetchWorkflows()
  } catch (error) {
    ElMessage.error(error.message || '保存失败')
  } finally {
    saving.value = false
  }
}

const handleToggle = async (row) => {
  try {
    await request.post(`/sql-task-workflow/${row.id}/toggle`)
    ElMessage.success('操作成功')
    fetchWorkflows()
  } catch (error) {
    ElMessage.error(error.message || '操作失败')
  }
}

const handleExecute = async (row) => {
  executingId.value = row.id
  try {
    const res = await request.post(`/sql-task-workflow/${row.id}/execute`)
    ElMessage.success('执行成功，实例ID: ' + res.data)
    fetchWorkflows()
  } catch (error) {
    ElMessage.error(error.message || '执行失败')
  } finally {
    executingId.value = null
  }
}

const openDsUrl = (row) => {
  if (row._dsUrl) {
    window.open(row._dsUrl, '_blank')
  } else {
    ElMessage.warning('DS 地址未配置')
  }
}

const handleDelete = (row) => {
  ElMessageBox.confirm(`确定删除工作流 "${row.workflowName}" 吗？`, '提示', {
    confirmButtonText: '确定', cancelButtonText: '取消', type: 'warning'
  }).then(async () => {
    try {
      await request.delete(`/sql-task-workflow/${row.id}`)
      ElMessage.success('删除成功')
      fetchWorkflows()
    } catch (error) {
      ElMessage.error(error.message || '删除失败')
    }
  }).catch(() => {})
}

// ==================== DAG ====================

const openDagDialog = async (row) => {
  dagDialogVisible.value = true
  dagLoading.value = true
  dagError.value = ''
  dagNodes.value = []
  dagEdges.value = []
  try {
    const res = await request.get(`/sql-task-workflow/${row.id}/dag`)
    const { nodes, edges } = res.data
    const layout = computeDagLayout(nodes, edges)
    dagNodes.value = layout.nodes
    dagEdges.value = layout.edges
    dagWidth.value = layout.width
    dagHeight.value = layout.height
  } catch (error) {
    dagError.value = error.message || '获取DAG数据失败'
  } finally {
    dagLoading.value = false
  }
}

function computeDagLayout(nodes, edges) {
  if (!nodes || nodes.length === 0) {
    return { nodes: [], edges: [], width: 800, height: 400 }
  }

  const nodeWidth = 140
  const nodeHeight = 56
  const levelGap = 220
  const nodeGap = 80

  const nodeMap = new Map()
  const inDegree = new Map()
  const outEdges = new Map()

  nodes.forEach(n => {
    nodeMap.set(n.id, n)
    inDegree.set(n.id, 0)
    outEdges.set(n.id, [])
  })

  edges.forEach(e => {
    if (outEdges.has(e.from) && nodeMap.has(e.to)) {
      outEdges.get(e.from).push(e.to)
      inDegree.set(e.to, (inDegree.get(e.to) || 0) + 1)
    }
  })

  // Kahn 拓扑排序计算层级
  const levels = new Map()
  const queue = []
  nodes.forEach(n => {
    if (inDegree.get(n.id) === 0) {
      queue.push(n.id)
      levels.set(n.id, 0)
    }
  })

  while (queue.length > 0) {
    const id = queue.shift()
    const level = levels.get(id)
    for (const to of outEdges.get(id)) {
      levels.set(to, Math.max(levels.get(to) || 0, level + 1))
      inDegree.set(to, inDegree.get(to) - 1)
      if (inDegree.get(to) === 0) {
        queue.push(to)
      }
    }
  }

  // 按层级分组
  const levelGroups = new Map()
  let maxLevel = 0
  nodes.forEach(n => {
    const lv = levels.get(n.id) || 0
    maxLevel = Math.max(maxLevel, lv)
    if (!levelGroups.has(lv)) levelGroups.set(lv, [])
    levelGroups.get(lv).push(n.id)
  })

  // 计算画布大小
  let maxGroupHeight = 0
  levelGroups.forEach(group => {
    const h = group.length * nodeHeight + (group.length - 1) * nodeGap
    maxGroupHeight = Math.max(maxGroupHeight, h)
  })

  const width = Math.max(800, (maxLevel + 1) * levelGap + nodeWidth + 60)
  const height = Math.max(400, maxGroupHeight + 80)

  // 计算节点位置
  const layoutNodes = []
  const posMap = new Map()

  const sortedLevels = Array.from(levelGroups.keys()).sort((a, b) => a - b)
  sortedLevels.forEach(lv => {
    const group = levelGroups.get(lv)
    const totalHeight = group.length * nodeHeight + (group.length - 1) * nodeGap
    const startY = (height - totalHeight) / 2

    group.forEach((nodeId, idx) => {
      const x = lv * levelGap + 30
      const y = startY + idx * (nodeHeight + nodeGap)
      const n = nodeMap.get(nodeId)
      posMap.set(nodeId, { x, y })
      layoutNodes.push({
        ...n,
        x,
        y,
        width: nodeWidth,
        height: nodeHeight
      })
    })
  })

  // 计算边的 path
  const layoutEdges = edges.map(e => {
    const from = posMap.get(e.from)
    const to = posMap.get(e.to)
    if (!from || !to) return null
    const x1 = from.x + nodeWidth
    const y1 = from.y + nodeHeight / 2
    const x2 = to.x
    const y2 = to.y + nodeHeight / 2
    const cpx = (x1 + x2) / 2
    return { path: `M${x1},${y1} C${cpx},${y1} ${cpx},${y2} ${x2},${y2}` }
  }).filter(Boolean)

  return { nodes: layoutNodes, edges: layoutEdges, width, height }
}

// ==================== 历史 ====================

const openHistoryDialog = async (row) => {
  currentHistoryWorkflowId.value = row.id
  historyDialogVisible.value = true
  historyPage.value = 1
  await fetchHistory()
}

const fetchHistory = async () => {
  if (!currentHistoryWorkflowId.value) return
  historyLoading.value = true
  try {
    const res = await request.get(`/sql-task-workflow/${currentHistoryWorkflowId.value}/history`, {
      params: { page: historyPage.value, size: historySize.value }
    })
    historyList.value = res.data.records
    historyTotal.value = res.data.total
  } catch (error) {
    ElMessage.error(error.message || '获取历史失败')
  } finally {
    historyLoading.value = false
  }
}

const openLogDialog = async (row) => {
  logDialogVisible.value = true
  logLoading.value = true
  logList.value = []
  try {
    const res = await request.get(`/sql-task-workflow/${currentHistoryWorkflowId.value}/history/${row.dsInstanceId}/logs`)
    logList.value = res.data || []
  } catch (error) {
    ElMessage.error(error.message || '获取日志失败')
  } finally {
    logLoading.value = false
  }
}

// ==================== 工具函数 ====================

function formatTime(value) {
  if (!value) return '-'
  const d = new Date(value)
  if (isNaN(d.getTime())) return value
  const pad = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}

function formatStatus(status) {
  if (status === 'SUCCESS') return '成功'
  if (status === 'FAILED') return '失败'
  if (status === 'RUNNING') return '运行中'
  return status || '-'
}

onMounted(() => {
  fetchWorkflows()
})
</script>

<style scoped>
.workflow-page {
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
.sub-text {
  font-size: 12px;
  color: #909399;
  margin-top: 2px;
}

/* DAG */
.dag-wrapper {
  position: relative;
  margin: 0 auto;
  overflow: auto;
}
.dag-svg {
  position: absolute;
  top: 0;
  left: 0;
  z-index: 1;
}
.dag-node {
  position: absolute;
  z-index: 2;
  background: #fff;
  border: 2px solid #dcdfe6;
  border-radius: 8px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  font-size: 13px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.08);
  transition: border-color 0.2s;
}
.dag-node.node-active {
  border-color: #67c23a;
}
.node-title {
  font-weight: 600;
  color: #303133;
  max-width: 120px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.node-code {
  font-size: 11px;
  color: #909399;
  margin-top: 2px;
}
</style>
