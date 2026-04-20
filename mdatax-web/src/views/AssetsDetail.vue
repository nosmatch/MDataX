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
          <el-descriptions-item label="更新时间">{{ tableInfo.updateTime || '-' }}</el-descriptions-item>
        </el-descriptions>
      </el-tab-pane>

      <!-- Tab3: 权限信息 -->
      <el-tab-pane label="权限信息" name="permission">
        <div v-loading="loadingPermission">
          <el-descriptions :column="1" border>
            <el-descriptions-item label="读权限">
              <el-tag :type="permission.read ? 'success' : 'danger'">
                {{ permission.read ? '有权限' : '无权限' }}
              </el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="写权限">
              <el-tag :type="permission.write ? 'success' : 'danger'">
                {{ permission.write ? '有权限' : '无权限' }}
              </el-tag>
            </el-descriptions-item>
          </el-descriptions>
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
const loadingPermission = ref(false)

const tableInfo = ref({})
const columns = ref([])
const permission = ref({ read: false, write: false })

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

const fetchPermission = async () => {
  loadingPermission.value = true
  try {
    const res = await request.get(`/metadata/tables/${tableId}/permission`)
    permission.value = res.data
  } catch (error) {
    ElMessage.error(error.message || '获取权限信息失败')
  } finally {
    loadingPermission.value = false
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
  fetchPermission()
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
</style>
