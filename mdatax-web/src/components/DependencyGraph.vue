<template>
  <div class="dependency-page">
    <div class="page-header">
      <h2>依赖管理</h2>
      <div class="header-info">
        <span>当前任务：</span>
        <el-tag type="primary">{{ currentTask?.taskCode }}</el-tag>
        <span style="margin: 0 8px">{{ currentTask?.taskName }}</span>
      </div>
    </div>

    <el-row :gutter="20">
      <!-- 左侧：上游依赖 -->
      <el-col :span="12">
        <div class="section">
          <div class="section-header">
            <h3>上游依赖（前置任务）</h3>
            <el-button type="primary" size="small" @click="openAddDialog">
              <el-icon><Plus /></el-icon> 添加依赖
            </el-button>
          </div>
          <el-table :data="upstreamList" v-loading="loading" stripe size="small">
            <el-table-column prop="taskCode" label="任务编码" width="120" />
            <el-table-column prop="taskName" label="任务名称" min-width="120">
              <template #default="{ row }">
                <el-link type="primary" :underline="false" @click="goToTask(row.upstreamTaskId)">
                  {{ row.taskName }}
                </el-link>
              </template>
            </el-table-column>
            <el-table-column prop="dependencyType" label="依赖类型" width="100">
              <template #default="{ row }">
                <el-tag size="small" :type="getDependencyTypeTag(row.dependencyType)">
                  {{ getDependencyTypeName(row.dependencyType) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="delaySeconds" label="延迟(秒)" width="80" align="center">
              <template #default="{ row }">
                {{ row.delaySeconds || 0 }}
              </template>
            </el-table-column>
            <el-table-column label="操作" width="120" align="center">
              <template #default="{ row }">
                <el-button type="primary" link size="small" @click="goToTask(row.upstreamTaskId)">
                  查看
                </el-button>
                <el-button type="danger" link size="small" @click="handleRemove(row)">
                  删除
                </el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-empty v-if="upstreamList.length === 0" description="暂无上游依赖" />
        </div>
      </el-col>

      <!-- 右侧：下游任务 -->
      <el-col :span="12">
        <div class="section">
          <div class="section-header">
            <h3>下游任务（依赖此任务）</h3>
          </div>
          <el-table :data="downstreamList" v-loading="loading" stripe size="small">
            <el-table-column prop="taskCode" label="任务编码" width="120" />
            <el-table-column prop="taskName" label="任务名称" min-width="120">
              <template #default="{ row }">
                <el-link type="primary" :underline="false" @click="goToTask(row.downstreamTaskId)">
                  {{ row.taskName }}
                </el-link>
              </template>
            </el-table-column>
            <el-table-column prop="dependencyType" label="依赖类型" width="100">
              <template #default="{ row }">
                <el-tag size="small" :type="getDependencyTypeTag(row.dependencyType)">
                  {{ getDependencyTypeName(row.dependencyType) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="delaySeconds" label="延迟(秒)" width="80" align="center">
              <template #default="{ row }">
                {{ row.delaySeconds || 0 }}
              </template>
            </el-table-column>
            <el-table-column label="操作" width="80" align="center">
              <template #default="{ row }">
                <el-button type="primary" link size="small" @click="handleView(row)">
                  查看
                </el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-empty v-if="downstreamList.length === 0" description="暂无下游任务" />
        </div>
      </el-col>
    </el-row>

    <!-- DAG可视化图 -->
    <div class="section" style="margin-top: 20px">
      <div class="section-header">
        <h3>DAG关系图</h3>
        <div>
          <el-button type="primary" link size="small" @click="goToTask(props.taskId)">
            查看任务配置
          </el-button>
        </div>
      </div>
      <div class="dag-hint">展示当前任务的一层上游和一层下游依赖，点击节点可查看任务详情</div>
      <div ref="dagChartRef" v-loading="dagLoading" class="dag-chart"></div>
    </div>

    <!-- 添加依赖对话框 -->
    <el-dialog v-model="addDialogVisible" title="添加上游依赖" width="600px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="选择任务" prop="upstreamTaskId">
          <el-select
            v-model="form.upstreamTaskId"
            placeholder="搜索并选择上游任务"
            filterable
            remote
            :remote-method="searchTasks"
            :loading="searching"
            style="width: 100%"
          >
            <el-option
              v-for="t in searchResults"
              :key="t.id"
              :label="`${t.taskCode} - ${t.taskName}`"
              :value="t.id"
            >
              <div style="display: flex; justify-content: space-between; align-items: center">
                <span>{{ t.taskCode }} - {{ t.taskName }}</span>
                <el-tag size="small" :type="TASK_TYPE_TAG_TYPE[t.taskType] || 'info'"
                  >{{ t.taskType }}</el-tag
                >
              </div>
            </el-option>
          </el-select>
        </el-form-item>
        <el-form-item label="依赖类型" prop="dependencyType">
          <el-radio-group v-model="form.dependencyType">
            <el-radio label="SUCCESS">成功后触发</el-radio>
            <el-radio label="FAILED">失败后触发</el-radio>
            <el-radio label="ANY">任意完成状态</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="延迟执行">
          <el-input-number v-model="form.delaySeconds" :min="0" :max="3600" />
          <span style="margin-left: 8px; color: #909399">秒</span>
        </el-form-item>
        <el-form-item label="条件表达式">
          <el-input
            v-model="form.conditionExpression"
            placeholder="如：UPSTREAM_AFFECTED_ROWS > 0（可选）"
          />
          <div style="color: #909399; font-size: 12px; margin-top: 4px">
            支持变量：UPSTREAM_AFFECTED_ROWS（影响行数）、UPSTREAM_SYNC_COUNT（同步数量）、UPSTREAM_DURATION_MS（耗时）
          </div>
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="addDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleAdd">
          确认添加
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, onBeforeUnmount, nextTick, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import * as echarts from 'echarts'
import request from '../utils/request.js'
import {
  DEPENDENCY_TYPE,
  DEPENDENCY_TYPE_LABEL,
  DEPENDENCY_TYPE_TAG_TYPE,
  TASK_TYPE,
  TASK_TYPE_LABEL
} from '../utils/task-constants.js'
import { getStatusName, getStatusTagType } from '../utils/execution.js'

const props = defineProps({
  taskId: {
    type: Number,
    required: true
  }
})

const emit = defineEmits(['select-task'])

const loading = ref(false)
const dagLoading = ref(false)
const currentTask = ref(null)
const upstreamList = ref([])
const downstreamList = ref([])

const addDialogVisible = ref(false)
const saving = ref(false)
const searching = ref(false)
const searchResults = ref([])

const dagChartRef = ref(null)
let dagChart = null

const formRef = ref(null)
const form = reactive({
  upstreamTaskId: null,
  dependencyType: 'SUCCESS',
  delaySeconds: 0,
  conditionExpression: ''
})

const rules = {
  upstreamTaskId: [{ required: true, message: '请选择上游任务', trigger: 'change' }]
}

const loadDependencies = async () => {
  loading.value = true
  try {
    const res = await request.get(`/task/${props.taskId}/dependencies`)
    currentTask.value = res.data?.currentTask || null
    upstreamList.value = res.data?.upstreamDependencies || []
    downstreamList.value = res.data?.downstreamDependencies || []
  } catch (error) {
    ElMessage.error(error.message || '获取依赖关系失败')
  } finally {
    loading.value = false
  }
}

const renderDag = async () => {
  if (!dagChartRef.value) return

  dagLoading.value = true
  disposeChart()

  try {
    const dagData = await loadDagData()
    if (!dagData || !dagData.nodes || dagData.nodes.length === 0) {
      dagLoading.value = false
      return
    }

    await nextTick()
    await nextTick()

    const width = dagChartRef.value.clientWidth
    const height = dagChartRef.value.clientHeight
    if (width === 0 || height === 0) {
      dagLoading.value = false
      return
    }

    dagChart = echarts.init(dagChartRef.value)

    const statusColorMap = {
      success: '#67c23a',
      danger: '#f56c6c',
      warning: '#e6a23c',
      primary: '#409eff',
      info: '#909399'
    }

    const nodes = dagData.nodes.map(node => {
      const isCurrent = node.current
      const color = isCurrent ? '#67c23a' : (node.taskType === TASK_TYPE.SQL ? '#409eff' : '#e6a23c')
      const borderColor = isCurrent ? '#52a12e' : '#909399'
      const statusColor = node.lastExecutionStatus
        ? statusColorMap[getStatusTagType(node.lastExecutionStatus)] || '#909399'
        : null

      return {
        id: String(node.id),
        name: node.taskCode || String(node.id),
        value: node.taskName || node.taskCode || String(node.id),
        taskId: node.id,
        taskType: node.taskType,
        lastExecutionStatus: node.lastExecutionStatus,
        symbolSize: isCurrent ? 70 : 60,
        itemStyle: {
          color,
          borderColor,
          borderWidth: isCurrent ? 3 : 1,
          shadowBlur: statusColor ? 8 : 0,
          shadowColor: statusColor || 'transparent'
        },
        label: {
          show: true,
          formatter: `{b}\n{c}`,
          fontSize: 11,
          color: '#333',
          lineHeight: 16
        },
        emphasis: {
          scale: 1.1,
          focus: 'adjacency'
        }
      }
    })

    const edges = dagData.edges.map(edge => ({
      source: String(edge.source),
      target: String(edge.target),
      symbolSize: [8, 12],
      lineStyle: {
        curveness: 0.2,
        width: 2
      },
      label: {
        show: true,
        formatter: getDependencyTypeName(edge.dependencyType),
        fontSize: 10,
        color: '#909399'
      }
    }))

    const option = {
      tooltip: {
        formatter: function (params) {
          if (params.dataType === 'node') {
            return `<div>
              <strong>${params.data.value || params.name}</strong><br/>
              编码：${params.name}<br/>
              类型：${TASK_TYPE_LABEL[params.data.taskType] || params.data.taskType}<br/>
              最近执行：${params.data.lastExecutionStatus ? getStatusName(params.data.lastExecutionStatus) : '无'}
            </div>`
          }
          return `${getDependencyTypeName(params.data.label?.formatter || 'SUCCESS')}`
        }
      },
      series: [{
        type: 'graph',
        layout: 'force',
        animation: true,
        roam: true,
        draggable: true,
        label: {
          show: true,
          position: 'inside'
        },
        force: {
          repulsion: 400,
          edgeLength: [120, 200],
          gravity: 0.1,
          layoutAnimation: true
        },
        edgeSymbol: ['none', 'arrow'],
        edgeSymbolSize: [4, 10],
        data: nodes,
        links: edges,
        emphasis: {
          focus: 'adjacency',
          lineStyle: {
            width: 4
          }
        }
      }]
    }

    dagChart.setOption(option)

    dagChart.on('click', function (params) {
      if (params.dataType === 'node' && params.data.taskId) {
        emit('select-task', params.data.taskId)
      }
    })

    window.addEventListener('resize', handleResize)
  } catch (error) {
    console.error('渲染DAG失败', error)
    ElMessage.error('DAG图渲染失败')
  } finally {
    dagLoading.value = false
  }
}

const loadDagData = async () => {
  try {
    const res = await request.get(`/task/${props.taskId}/dag`)
    if (res.code === 200) {
      return res.data
    }
    return null
  } catch (error) {
    console.error('加载DAG数据失败', error)
    return null
  }
}

const disposeChart = () => {
  window.removeEventListener('resize', handleResize)
  if (dagChart) {
    dagChart.dispose()
    dagChart = null
  }
}

const handleResize = () => {
  if (dagChart) {
    dagChart.resize()
  }
}

const getDependencyTypeName = (type) => {
  return DEPENDENCY_TYPE_LABEL[type] || type
}

const getDependencyTypeTag = (type) => {
  return DEPENDENCY_TYPE_TAG_TYPE[type] || ''
}

const openAddDialog = () => {
  form.upstreamTaskId = null
  form.dependencyType = DEPENDENCY_TYPE.SUCCESS
  form.delaySeconds = 0
  form.conditionExpression = ''
  searchResults.value = []
  addDialogVisible.value = true
  setTimeout(() => searchTasks(''), 100)
}

const searchTasks = async (keyword) => {
  searching.value = true
  try {
    const res = await request.get('/task/search-for-dependency', {
      params: {
        currentTaskId: props.taskId,
        keyword: keyword || '',
        page: 1,
        size: 50
      }
    })
    const data = res.data || {}
    searchResults.value = data.records || []
  } catch (error) {
    searchResults.value = []
    console.error('搜索可添加任务失败', error)
    ElMessage.warning(error.message || '搜索可添加任务失败')
  } finally {
    searching.value = false
  }
}

const handleAdd = async () => {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  saving.value = true
  try {
    await request.post(`/task/${props.taskId}/dependencies`, {
      upstreamTaskId: form.upstreamTaskId,
      dependencyType: form.dependencyType,
      delaySeconds: form.delaySeconds,
      conditionExpression: form.conditionExpression,
      createUserId: 1
    })
    ElMessage.success('添加成功')
    addDialogVisible.value = false
    await loadDependencies()
    await renderDag()
  } catch (error) {
    ElMessage.error(error.message || '添加失败')
  } finally {
    saving.value = false
  }
}

const handleRemove = async (row) => {
  try {
    await ElMessageBox.confirm('确定删除此依赖关系吗？', '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
    await request.delete(`/task/${props.taskId}/dependencies/${row.id}`)
    ElMessage.success('删除成功')
    await loadDependencies()
    await renderDag()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error(error.message || '删除失败')
    }
  }
}

const handleView = (row) => {
  const targetId = row.downstreamTaskId || row.upstreamTaskId
  if (targetId) {
    goToTask(targetId)
  }
}

const goToTask = (taskId) => {
  if (taskId) {
    emit('select-task', taskId)
  }
}

onMounted(() => {
  loadDependencies().then(() => {
    // 延迟渲染DAG，确保容器已经有尺寸
    setTimeout(() => {
      renderDag()
    }, 300)
  })
})

onBeforeUnmount(() => {
  disposeChart()
})

watch(() => props.taskId, () => {
  loadDependencies().then(() => {
    setTimeout(() => {
      renderDag()
    }, 300)
  })
})
</script>

<style scoped>
.dependency-page {
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

.header-info {
  display: flex;
  align-items: center;
  gap: 8px;
}

.section {
  margin-bottom: 20px;
}

.section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}

.section-header h3 {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
}

.dag-hint {
  color: #909399;
  font-size: 12px;
  margin-bottom: 8px;
}

.dag-chart {
  width: 100%;
  height: 450px;
  background: #f5f7fa;
  border-radius: 4px;
}
</style>
