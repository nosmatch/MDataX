<template>
  <div>
    <div class="page-header">
      <h2>我的申请</h2>
      <p class="desc">查看您提交的权限申请及审批状态</p>
    </div>

    <el-card>
      <el-table :data="applyList" v-loading="loading" stripe border>
        <el-table-column prop="createTime" label="申请时间" min-width="160" />
        <el-table-column label="申请类型" width="120" align="center">
          <template #default="{ row }">
            <el-tag :type="row.applyType === 'table' ? 'primary' : 'success'" size="small">
              {{ row.applyType === 'table' ? '📊 表权限' : '📈 报表权限' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="目标对象" min-width="200">
          <template #default="{ row }">
            <span v-if="row.applyType === 'table'">
              {{ row.databaseName }}.{{ row.tableName }}
              <el-tag v-if="row.tableComment" type="info" size="small" style="margin-left: 8px">{{ row.tableComment }}</el-tag>
            </span>
            <span v-else>
              {{ row.reportName }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="申请内容" width="100" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.applyType === 'table'" :type="row.permissionType === 'READ' ? 'success' : 'warning'" size="small">
              {{ row.permissionType === 'READ' ? '读权限' : '写权限' }}
            </el-tag>
            <el-tag v-else :type="row.roleType === 'editor' ? 'warning' : 'info'" size="small">
              {{ row.roleType === 'editor' ? '编辑者' : '查看者' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="applyReason" label="申请理由" min-width="200" show-overflow-tooltip />
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status)" size="small">
              {{ statusLabel(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="ownerName" label="审批人" width="100" />
        <el-table-column prop="approveTime" label="审批时间" min-width="160">
          <template #default="{ row }">
            {{ row.approveTime || '-' }}
          </template>
        </el-table-column>
        <el-table-column prop="approveComment" label="审批意见" min-width="200" show-overflow-tooltip>
          <template #default="{ row }">
            {{ row.approveComment || '-' }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="100" align="center" fixed="right">
          <template #default="{ row }">
            <el-button v-if="row.status === 0" type="danger" size="small" @click="handleCancel(row)" :loading="row._canceling">
              撤回
            </el-button>
            <span v-else>-</span>
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
          @size-change="handleSizeChange"
          @current-change="handlePageChange"
        />
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '../utils/request.js'

const loading = ref(false)
const applyList = ref([])
const page = ref(1)
const size = ref(10)
const total = ref(0)

const statusMap = {
  0: { label: '待审批', type: 'warning' },
  1: { label: '已通过', type: 'success' },
  2: { label: '已拒绝', type: 'danger' }
}

const statusLabel = (status) => {
  return statusMap[status]?.label || '未知'
}

const statusType = (status) => {
  return statusMap[status]?.type || 'info'
}

const fetchData = async () => {
  loading.value = true
  try {
    // 并行请求两个API
    const [tableRes, reportRes] = await Promise.all([
      request.get('/permission/apply/my-list', {
        params: { page: page.value, size: size.value }
      }).catch(() => ({ data: { records: [], total: 0 } })),
      request.get('/report-apply/my-list', {
        params: { page: page.value, size: size.value }
      }).catch(() => ({ data: { records: [], total: 0 } }))
    ])

    // 合并数据并添加类型标识
    const tableApplies = (tableRes.data.records || []).map(item => ({
      ...item,
      applyType: 'table',
      permissionType: item.applyType,
      _canceling: false
    }))

    const reportApplies = (reportRes.data.records || []).map(item => ({
      ...item,
      applyType: 'report',
      roleType: item.applyRole,
      _canceling: false
    }))

    // 合并并按时间排序
    applyList.value = [...tableApplies, ...reportApplies].sort((a, b) => {
      return new Date(b.createTime) - new Date(a.createTime)
    })

    // 取较大的total值（因为两个列表可能有重叠）
    total.value = Math.max(tableRes.data.total || 0, reportRes.data.total || 0)
  } catch (error) {
    ElMessage.error(error.message || '获取数据失败')
  } finally {
    loading.value = false
  }
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

const handleCancel = async (row) => {
  try {
    await ElMessageBox.confirm('确认撤回该申请？', '提示', { type: 'warning' })
    row._canceling = true

    // 根据类型调用不同的API
    const url = row.applyType === 'table'
      ? `/permission/apply/${row.id}/cancel`
      : `/report-apply/${row.id}/cancel`

    await request.put(url)
    ElMessage.success('已撤回')
    fetchData()
  } catch (e) {
    if (e !== 'cancel') {
      ElMessage.error(e.response?.data?.message || '撤回失败')
    }
  } finally {
    row._canceling = false
  }
}

onMounted(() => {
  fetchData()
})
</script>

<style scoped>
.page-header {
  margin-bottom: 20px;
}
.page-header h2 {
  margin: 0;
  font-size: 20px;
  font-weight: 600;
}
.desc {
  color: #909399;
  font-size: 14px;
  margin-top: 4px;
}
.pagination-wrapper {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}
</style>
