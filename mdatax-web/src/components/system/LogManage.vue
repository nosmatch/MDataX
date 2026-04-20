<template>
  <div>
    <div class="toolbar">
      <el-input v-model="query.username" placeholder="用户名" clearable style="width: 180px; margin-right: 12px" />
      <el-input v-model="query.operation" placeholder="操作描述" clearable style="width: 180px; margin-right: 12px" />
      <el-button type="primary" @click="handleSearch">查询</el-button>
      <el-button @click="handleReset">重置</el-button>
    </div>

    <el-table :data="tableData" border v-loading="loading" style="margin-top: 16px">
      <el-table-column prop="id" label="ID" width="80" />
      <el-table-column prop="username" label="用户" width="120" />
      <el-table-column prop="operation" label="操作" width="180" />
      <el-table-column prop="method" label="方法" show-overflow-tooltip />
      <el-table-column prop="ip" label="IP地址" width="140" />
      <el-table-column prop="duration" label="耗时(ms)" width="100" />
      <el-table-column prop="status" label="状态" width="80">
        <template #default="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : 'danger'">{{ row.status === 1 ? '成功' : '失败' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createTime" label="时间" width="180" />
    </el-table>

    <el-pagination
      style="margin-top: 16px; justify-content: flex-end"
      v-model:current-page="page"
      v-model:page-size="size"
      :total="total"
      :page-sizes="[10, 20, 50]"
      layout="total, sizes, prev, pager, next"
      @size-change="handleSearch"
      @current-change="handleSearch"
    />
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { ElMessage } from 'element-plus'
import request from '../../utils/request.js'

const loading = ref(false)
const tableData = ref([])
const page = ref(1)
const size = ref(20)
const total = ref(0)
const query = reactive({
  username: '',
  operation: ''
})

const loadData = async () => {
  loading.value = true
  try {
    const res = await request.get('/operation-log/page', {
      params: {
        page: page.value,
        size: size.value,
        username: query.username || undefined,
        operation: query.operation || undefined
      }
    })
    tableData.value = res.data.records
    total.value = res.data.total
  } catch (e) {
    ElMessage.error('加载失败')
  } finally {
    loading.value = false
  }
}

const handleSearch = () => {
  page.value = 1
  loadData()
}

const handleReset = () => {
  query.username = ''
  query.operation = ''
  page.value = 1
  loadData()
}

loadData()
</script>

<style scoped>
.toolbar {
  display: flex;
  align-items: center;
}
</style>
