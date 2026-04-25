<template>
  <div>
    <div class="page-header">
      <h2>我的申请</h2>
      <p class="desc">查看您提交的权限申请及审批状态</p>
    </div>

    <el-card>
      <el-tabs v-model="activeTab" @tab-change="onTabChange">
        <!-- 表权限申请 -->
        <el-tab-pane label="表权限申请" name="table">
          <el-table :data="tableApplyList" v-loading="loading" stripe border>
            <el-table-column prop="createTime" label="申请时间" min-width="160" />
            <el-table-column label="目标表" min-width="200">
              <template #default="{ row }">
                <span>{{ row.databaseName }}.{{ row.tableName }}</span>
                <el-tag v-if="row.tableComment" type="info" size="small" style="margin-left: 8px">{{ row.tableComment }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="权限类型" width="100" align="center">
              <template #default="{ row }">
                <el-tag :type="row.applyType === 'READ' ? 'success' : 'warning'" size="small">
                  {{ row.applyType === 'READ' ? '读权限' : '写权限' }}
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
                <el-button v-if="row.status === 0" type="danger" size="small" @click="handleCancelTable(row)" :loading="row._canceling">
                  撤回
                </el-button>
                <span v-else>-</span>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <!-- 报表权限申请 -->
        <el-tab-pane label="报表权限申请" name="report">
          <el-table :data="reportApplyList" v-loading="loading" stripe border>
            <el-table-column prop="createTime" label="申请时间" min-width="160" />
            <el-table-column label="报表名称" min-width="200">
              <template #default="{ row }">
                {{ row.reportName }}
              </template>
            </el-table-column>
            <el-table-column label="申请角色" width="100" align="center">
              <template #default="{ row }">
                <el-tag :type="row.applyRole === 'editor' ? 'warning' : 'info'" size="small">
                  {{ row.applyRole === 'editor' ? '编辑者' : '查看者' }}
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
                <el-button v-if="row.status === 0" type="danger" size="small" @click="handleCancelReport(row)" :loading="row._canceling">
                  撤回
                </el-button>
                <span v-else>-</span>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>
      </el-tabs>

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

const activeTab = ref('table')
const loading = ref(false)
const tableApplyList = ref([])
const reportApplyList = ref([])
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
    if (activeTab.value === 'table') {
      const res = await request.get('/permission/apply/my-list', {
        params: { page: page.value, size: size.value }
      })
      tableApplyList.value = (res.data.records || []).map(r => ({ ...r, _canceling: false }))
      total.value = res.data.total
    } else {
      const res = await request.get('/report-apply/my-list', {
        params: { page: page.value, size: size.value }
      })
      reportApplyList.value = (res.data.records || []).map(r => ({ ...r, _canceling: false }))
      total.value = res.data.total
    }
  } catch (error) {
    ElMessage.error(error.message || '获取数据失败')
  } finally {
    loading.value = false
  }
}

const onTabChange = () => {
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

const handleCancelTable = async (row) => {
  try {
    await ElMessageBox.confirm('确认撤回该申请？', '提示', { type: 'warning' })
    row._canceling = true
    await request.put(`/permission/apply/${row.id}/cancel`)
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

const handleCancelReport = async (row) => {
  try {
    await ElMessageBox.confirm('确认撤回该申请？', '提示', { type: 'warning' })
    row._canceling = true
    await request.put(`/report-apply/${row.id}/cancel`)
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
