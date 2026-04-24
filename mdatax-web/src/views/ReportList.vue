<template>
  <div class="report-list-page">
    <div class="page-header">
      <h2>数据报表</h2>
      <el-button type="primary" @click="handleCreate">
        <el-icon><Plus /></el-icon>
        新建报表
      </el-button>
    </div>

    <el-card class="search-card">
      <div class="search-row">
        <el-input
          v-model="keyword"
          placeholder="搜索报表名称"
          clearable
          style="width: 300px"
          @keyup.enter="handleSearch"
        >
          <template #append>
            <el-button @click="handleSearch">
              <el-icon><Search /></el-icon>
            </el-button>
          </template>
        </el-input>

        <el-select
          v-model="visibilityFilter"
          placeholder="筛选权限"
          style="width: 150px; margin-left: 10px"
          @change="handleSearch"
        >
          <el-option label="全部" value="" />
          <el-option label="我的报表" value="mine" />
          <el-option label="私有报表" value="private" />
          <el-option label="公开报表" value="public" />
        </el-select>
      </div>
    </el-card>

    <el-card>
      <el-table :data="reportList" v-loading="loading" stripe>
        <el-table-column prop="name" label="报表名称" min-width="200" show-overflow-tooltip />
        <el-table-column label="图表数量" width="120" align="center">
          <template #default="{ row }">
            <el-tag size="small" type="info">
              {{ row.chartCount || 0 }} 个图表
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="图表类型" width="150">
          <template #default="{ row }">
            <div v-if="row.chartTypes && row.chartTypes.length > 0" class="chart-types">
              <el-tag
                v-for="type in row.chartTypes"
                :key="type"
                size="small"
                :type="getChartTypeTagType(type)"
                style="margin-right: 4px;"
              >
                {{ getChartTypeLabel(type) }}
              </el-tag>
            </div>
            <span v-else class="text-muted">-</span>
          </template>
        </el-table-column>
        <el-table-column label="权限" width="100">
          <template #default="{ row }">
            <el-tag :type="getVisibilityTagType(row.visibility)" size="small">
              {{ getVisibilityLabel(row.visibility) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag v-if="row.status === 1" type="success" size="small">启用</el-tag>
            <el-tag v-else type="info" size="small">停用</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间" min-width="160" />
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="handleView(row)">查看</el-button>
            <el-button v-if="row.canEdit" link type="primary" size="small" @click="handleEdit(row)">编辑</el-button>
            <el-button v-if="row.canDelete" link type="danger" size="small" @click="handleDelete(row)">删除</el-button>
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
          @size-change="handleSearch"
          @current-change="handleSearch"
        />
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '../utils/request.js'

const router = useRouter()
const loading = ref(false)
const reportList = ref([])
const keyword = ref('')
const visibilityFilter = ref('')
const page = ref(1)
const size = ref(10)
const total = ref(0)

const chartTypeMap = {
  line: { label: '折线图', type: '' },
  bar: { label: '柱状图', type: 'warning' },
  pie: { label: '饼图', type: 'success' },
  table: { label: '表格', type: 'info' }
}

const visibilityMap = {
  private: { label: '私有', type: 'warning' },
  public: { label: '公开', type: 'success' }
}

function getChartTypeLabel(type) {
  return chartTypeMap[type]?.label || type
}

function getChartTypeTagType(type) {
  return chartTypeMap[type]?.type || 'info'
}

function getVisibilityLabel(visibility) {
  return visibilityMap[visibility]?.label || visibility || '私有'
}

function getVisibilityTagType(visibility) {
  return visibilityMap[visibility]?.type || 'info'
}

async function loadData() {
  loading.value = true
  try {
    const res = await request.get('/report/page', {
      params: {
        page: page.value,
        size: size.value,
        keyword: keyword.value || undefined,
        visibility: visibilityFilter.value || undefined
      }
    })
    reportList.value = res.data.records || []
    total.value = res.data.total || 0
  } catch (err) {
    ElMessage.error(err.message || '加载报表列表失败')
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  page.value = 1
  loadData()
}

function handleCreate() {
  router.push('/report/edit')
}

function handleEdit(row) {
  router.push(`/report/edit/${row.id}`)
}

function handleView(row) {
  router.push(`/report/view/${row.id}`)
}

async function handleDelete(row) {
  try {
    await ElMessageBox.confirm(`确定删除报表「${row.name}」吗？`, '提示', { type: 'warning' })
    await request.delete(`/report/${row.id}`)
    ElMessage.success('删除成功')
    loadData()
  } catch (err) {
    if (err !== 'cancel') {
      ElMessage.error(err.message || '删除失败')
    }
  }
}

onMounted(loadData)
</script>

<style scoped>
.report-list-page {
  padding: 20px;
}
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}
.page-header h2 {
  margin: 0;
}
.search-card {
  margin-bottom: 16px;
}
.search-row {
  display: flex;
  align-items: center;
}
.pagination-wrapper {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}
.chart-types {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}
.text-muted {
  color: #999;
  font-size: 13px;
}
</style>
