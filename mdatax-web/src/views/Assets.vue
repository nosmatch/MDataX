<template>
  <div class="assets-page">
    <div class="page-header">
      <h2>数据目录</h2>
      <el-button type="primary" :loading="collecting" @click="handleCollect">
        <el-icon><Refresh /></el-icon>
        同步元数据
      </el-button>
    </div>

    <el-card class="search-card">
      <el-input
        v-model="keyword"
        placeholder="搜索表名或数据库名"
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
    </el-card>

    <el-card>
      <el-table :data="tableList" v-loading="loading" stripe>
        <el-table-column prop="tableName" label="表名" min-width="180">
          <template #default="{ row }">
            <el-link type="primary" @click="goDetail(row.id)">{{ row.tableName }}</el-link>
          </template>
        </el-table-column>
        <el-table-column prop="databaseName" label="所属库" min-width="140" />
        <el-table-column prop="engine" label="引擎" min-width="140" />
        <el-table-column prop="totalRows" label="数据行数" min-width="120">
          <template #default="{ row }">
            {{ formatNumber(row.totalRows) }}
          </template>
        </el-table-column>
        <el-table-column prop="tableComment" label="描述" min-width="180" show-overflow-tooltip />
        <el-table-column prop="ownerName" label="责任人" min-width="100" />
        <el-table-column label="读权限" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="row.read ? 'success' : 'danger'" size="small">
              {{ row.read ? '有' : '无' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="写权限" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="row.write ? 'success' : 'danger'" size="small">
              {{ row.write ? '有' : '无' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间" min-width="160" />
      </el-table>

      <div class="pagination-wrapper">
        <el-pagination
          v-model:current-page="page"
          v-model:page-size="size"
          :total="total"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next"
          @size-change="handleSizeChange"
          @current-change="handlePageChange"
        />
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Refresh, Search } from '@element-plus/icons-vue'
import request from '../utils/request.js'

const router = useRouter()

const loading = ref(false)
const collecting = ref(false)
const keyword = ref('')
const tableList = ref([])
const page = ref(1)
const size = ref(10)
const total = ref(0)

const fetchData = async () => {
  loading.value = true
  try {
    const res = await request.get('/metadata/tables', {
      params: {
        page: page.value,
        size: size.value,
        keyword: keyword.value
      }
    })
    tableList.value = res.data.records
    total.value = res.data.total
  } catch (error) {
    ElMessage.error(error.message || '获取数据失败')
  } finally {
    loading.value = false
  }
}

const handleSearch = () => {
  page.value = 1
  fetchData()
}

const handlePageChange = (val) => {
  page.value = val
  fetchData()
}

const handleSizeChange = (val) => {
  size.value = val
  page.value = 1
  fetchData()
}

const handleCollect = async () => {
  collecting.value = true
  try {
    await request.post('/metadata/collect')
    ElMessage.success('元数据采集成功')
    fetchData()
  } catch (error) {
    ElMessage.error(error.message || '采集失败')
  } finally {
    collecting.value = false
  }
}

const goDetail = (id) => {
  router.push(`/assets/detail/${id}`)
}

const formatNumber = (num) => {
  if (num === null || num === undefined) return '-'
  return num.toLocaleString()
}

onMounted(() => {
  fetchData()
})
</script>

<style scoped>
.assets-page {
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
.search-card {
  margin-bottom: 16px;
}
.pagination-wrapper {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}
</style>
