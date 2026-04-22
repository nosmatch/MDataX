<template>
  <div class="detail-page">
    <el-page-header @back="goBack" title="表详情" />

    <div class="table-title">
      <h3>{{ tableInfo.databaseName }}.{{ tableInfo.tableName }}</h3>
      <el-tag v-if="tableInfo.engine" type="info" size="small">{{ tableInfo.engine }}</el-tag>
    </div>

    <el-tabs v-model="activeTab" type="border-card">
      <!-- Tab1: 字段信息 -->
      <el-tab-pane label="字段信息" name="columns">
        <el-table :data="columns" v-loading="loadingColumns" stripe>
          <el-table-column prop="columnName" label="字段名" min-width="160" />
          <el-table-column prop="dataType" label="数据类型" min-width="180" />
          <el-table-column prop="columnComment" label="注释" min-width="200" show-overflow-tooltip />
          <el-table-column prop="columnDefault" label="默认值" min-width="140" show-overflow-tooltip />
          <el-table-column prop="ordinalPosition" label="顺序" width="80" />
        </el-table>
      </el-tab-pane>

      <!-- Tab2: 基本信息 -->
      <el-tab-pane label="基本信息" name="basic">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="表名">{{ tableInfo.tableName }}</el-descriptions-item>
          <el-descriptions-item label="所属库">{{ tableInfo.databaseName }}</el-descriptions-item>
          <el-descriptions-item label="引擎">{{ tableInfo.engine || '-' }}</el-descriptions-item>
          <el-descriptions-item label="数据行数">{{ formatNumber(tableInfo.totalRows) }}</el-descriptions-item>
          <el-descriptions-item label="数据大小">{{ formatBytes(tableInfo.totalBytes) }}</el-descriptions-item>
          <el-descriptions-item label="表注释">{{ tableInfo.tableComment || '-' }}</el-descriptions-item>
          <el-descriptions-item label="责任人">{{ tableInfo.ownerName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="创建时间">{{ tableInfo.createTime || '-' }}</el-descriptions-item>
          <el-descriptions-item label="元数据更新时间">{{ tableInfo.updateTime || '-' }}</el-descriptions-item>
          <el-descriptions-item label="数据最近更新时间">{{ tableInfo.lastDataUpdateTime || '-' }}</el-descriptions-item>
        </el-descriptions>
      </el-tab-pane>

      <!-- Tab3: 数据预览 -->
      <el-tab-pane label="数据预览" name="preview">
        <div v-loading="loadingPreview">
          <el-table :data="previewRows" stripe v-if="previewColumns.length > 0">
            <el-table-column
              v-for="col in previewColumns"
              :key="col"
              :prop="col"
              :label="col"
              min-width="160"
              show-overflow-tooltip
            />
          </el-table>
          <el-empty v-else description="暂无数据" />
        </div>
      </el-tab-pane>

      <!-- Tab4: 访问历史 -->
      <el-tab-pane label="访问历史" name="history">
        <div v-loading="loadingHistory">
          <el-table :data="accessHistory" stripe v-if="accessHistory.length > 0">
            <el-table-column prop="username" label="操作人" min-width="120" />
            <el-table-column prop="accessType" label="操作类型" width="100" align="center">
              <template #default="{ row }">
                <el-tag :type="row.accessType === 'READ' ? 'success' : 'warning'" size="small">
                  {{ row.accessType === 'READ' ? '读' : '写' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="accessTime" label="访问时间" min-width="160" />
          </el-table>
          <el-empty v-else description="暂无访问记录" />
          <div class="pagination-wrapper" v-if="historyTotal > 0">
            <el-pagination
              v-model:current-page="historyPage"
              v-model:page-size="historySize"
              :total="historyTotal"
              :page-sizes="[10, 20, 50]"
              layout="total, sizes, prev, pager, next"
              @size-change="handleHistorySizeChange"
              @current-change="handleHistoryPageChange"
            />
          </div>
        </div>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import request from '../utils/request.js'

const route = useRoute()
const router = useRouter()
const tableId = route.params.id

const activeTab = ref('columns')
const loadingColumns = ref(false)
const loadingHistory = ref(false)
const loadingPreview = ref(false)

const tableInfo = ref({})
const columns = ref([])
const accessHistory = ref([])
const previewColumns = ref([])
const previewRows = ref([])

const historyPage = ref(1)
const historySize = ref(10)
const historyTotal = ref(0)

const fetchTableInfo = async () => {
  try {
    const res = await request.get(`/metadata/tables/${tableId}`)
    tableInfo.value = res.data
  } catch (error) {
    ElMessage.error(error.message || '获取表信息失败')
  }
}

const fetchColumns = async () => {
  loadingColumns.value = true
  try {
    const res = await request.get(`/metadata/tables/${tableId}/columns`)
    columns.value = res.data
  } catch (error) {
    ElMessage.error(error.message || '获取字段信息失败')
  } finally {
    loadingColumns.value = false
  }
}

const fetchAccessHistory = async () => {
  loadingHistory.value = true
  try {
    const res = await request.get(`/metadata/tables/${tableId}/access-history`, {
      params: {
        page: historyPage.value,
        size: historySize.value
      }
    })
    accessHistory.value = res.data.records || []
    historyTotal.value = res.data.total || 0
  } catch (error) {
    ElMessage.error(error.message || '获取访问历史失败')
  } finally {
    loadingHistory.value = false
  }
}

const handleHistoryPageChange = (val) => {
  historyPage.value = val
  fetchAccessHistory()
}

const handleHistorySizeChange = (val) => {
  historySize.value = val
  historyPage.value = 1
  fetchAccessHistory()
}

const fetchPreview = async () => {
  loadingPreview.value = true
  try {
    const res = await request.get(`/metadata/tables/${tableId}/preview`)
    previewColumns.value = res.data.columns || []
    previewRows.value = res.data.rows || []
  } catch (error) {
    ElMessage.error(error.message || '获取数据预览失败')
  } finally {
    loadingPreview.value = false
  }
}

const goBack = () => {
  router.back()
}

const formatNumber = (num) => {
  if (num === null || num === undefined) return '-'
  return num.toLocaleString()
}

const formatBytes = (bytes) => {
  if (bytes === null || bytes === undefined) return '-'
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(2) + ' KB'
  if (bytes < 1024 * 1024 * 1024) return (bytes / 1024 / 1024).toFixed(2) + ' MB'
  return (bytes / 1024 / 1024 / 1024).toFixed(2) + ' GB'
}

onMounted(() => {
  fetchTableInfo()
  fetchColumns()
  fetchPreview()
  fetchAccessHistory()
})
</script>

<style scoped>
.detail-page {
  padding-bottom: 20px;
}
.table-title {
  display: flex;
  align-items: center;
  gap: 12px;
  margin: 16px 0;
}
.table-title h3 {
  margin: 0;
  font-size: 18px;
}
.pagination-wrapper {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}
</style>
