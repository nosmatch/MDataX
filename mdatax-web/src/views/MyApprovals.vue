<template>
  <div>
    <div class="page-header">
      <h2>我的审批</h2>
      <p class="desc">审批您作为责任人或所有者的权限申请</p>
    </div>

    <el-card>
      <el-tabs v-model="activeTab" @tab-change="onTabChange">
        <!-- 待审批 -->
        <el-tab-pane label="待审批" name="pending">
          <el-table :data="approvalList" v-loading="loading" stripe border>
            <el-table-column prop="createTime" label="申请时间" min-width="160" />
            <el-table-column prop="applicantName" label="申请人" width="120" />
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
            <el-table-column label="操作" width="180" align="center" fixed="right">
              <template #default="{ row }">
                <el-button type="success" size="small" @click="handleApprove(row)">通过</el-button>
                <el-button type="danger" size="small" @click="handleReject(row)">拒绝</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <!-- 审批历史 -->
        <el-tab-pane label="审批历史" name="history">
          <el-table :data="historyList" v-loading="loading" stripe border>
            <el-table-column prop="createTime" label="申请时间" min-width="160" />
            <el-table-column prop="applicantName" label="申请人" width="120" />
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
            <el-table-column prop="applyReason" label="申请理由" min-width="160" show-overflow-tooltip />
            <el-table-column label="审批结果" width="100" align="center">
              <template #default="{ row }">
                <el-tag :type="row.status === 1 ? 'success' : row.status === 2 ? 'danger' : 'info'" size="small">
                  {{ row.status === 1 ? '已通过' : row.status === 2 ? '已拒绝' : '未知' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="approveTime" label="审批时间" min-width="160" />
            <el-table-column prop="approveComment" label="审批意见" min-width="160" show-overflow-tooltip />
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

    <!-- 拒绝弹窗 -->
    <el-dialog v-model="rejectVisible" title="拒绝申请" width="480px" :close-on-click-modal="false">
      <el-form :model="rejectForm" :rules="rejectRules" ref="rejectFormRef" label-width="80px">
        <el-form-item label="申请人">{{ currentRow?.applicantName }}</el-form-item>
        <el-form-item v-if="currentRow?.applyType === 'table'" label="目标表">
          {{ currentRow?.databaseName }}.{{ currentRow?.tableName }}
        </el-form-item>
        <el-form-item v-else label="报表名称">
          {{ currentRow?.reportName }}
        </el-form-item>
        <el-form-item label="审批意见" prop="comment">
          <el-input
            v-model="rejectForm.comment"
            type="textarea"
            :rows="3"
            placeholder="请填写拒绝理由"
            maxlength="500"
            show-word-limit
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="rejectVisible = false">取消</el-button>
        <el-button type="primary" @click="confirmReject" :loading="rejectLoading">确认拒绝</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import request from '../utils/request.js'

const activeTab = ref('pending')
const loading = ref(false)
const page = ref(1)
const size = ref(10)
const total = ref(0)

const approvalList = ref([])
const historyList = ref([])

const rejectVisible = ref(false)
const rejectLoading = ref(false)
const rejectFormRef = ref(null)
const currentRow = ref(null)
const rejectForm = ref({ comment: '' })
const rejectRules = {
  comment: [
    { required: true, message: '请填写拒绝理由', trigger: 'blur' },
    { min: 2, max: 500, message: '理由长度在 2 到 500 个字符', trigger: 'blur' }
  ]
}

const fetchData = async () => {
  loading.value = true
  try {
    if (activeTab.value === 'pending') {
      // 并行请求两个待审批API
      const [tableRes, reportRes] = await Promise.all([
        request.get('/permission/apply/approval/pending', {
          params: { page: page.value, size: size.value }
        }).catch(() => ({ data: { records: [], total: 0 } })),
        request.get('/report-apply/pending', {
          params: { page: page.value, size: size.value }
        }).catch(() => ({ data: { records: [], total: 0 } }))
      ])

      // 合并数据并添加类型标识
      const tableApprovals = (tableRes.data.records || []).map(item => ({
        ...item,
        applyType: 'table',
        permissionType: item.applyType
      }))

      const reportApprovals = (reportRes.data.records || []).map(item => ({
        ...item,
        applyType: 'report',
        roleType: item.applyRole
      }))

      // 合并并按时间排序
      approvalList.value = [...tableApprovals, ...reportApprovals].sort((a, b) => {
        return new Date(b.createTime) - new Date(a.createTime)
      })

      total.value = Math.max(tableRes.data.total || 0, reportRes.data.total || 0)
    } else {
      // 并行请求两个审批历史API
      const [tableRes, reportRes] = await Promise.all([
        request.get('/permission/apply/approval/history', {
          params: { page: page.value, size: size.value }
        }).catch(() => ({ data: { records: [], total: 0 } })),
        request.get('/report-apply/history', {
          params: { page: page.value, size: size.value }
        }).catch(() => ({ data: { records: [], total: 0 } }))
      ])

      // 合并数据并添加类型标识
      const tableHistory = (tableRes.data.records || []).map(item => ({
        ...item,
        applyType: 'table',
        permissionType: item.applyType
      }))

      const reportHistory = (reportRes.data.records || []).map(item => ({
        ...item,
        applyType: 'report',
        roleType: item.applyRole
      }))

      // 合并并按时间排序
      historyList.value = [...tableHistory, ...reportHistory].sort((a, b) => {
        return new Date(b.createTime) - new Date(a.createTime)
      })

      total.value = Math.max(tableRes.data.total || 0, reportRes.data.total || 0)
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

const handleApprove = async (row) => {
  try {
    // 根据类型调用不同的API
    const url = row.applyType === 'table'
      ? `/permission/apply/approval/${row.id}`
      : `/report-apply/${row.id}`

    await request.put(url, {
      action: 'APPROVE',
      comment: '同意'
    })
    ElMessage.success('已通过')
    fetchData()
  } catch (error) {
    ElMessage.error(error.response?.data?.message || '操作失败')
  }
}

const handleReject = (row) => {
  currentRow.value = row
  rejectForm.value.comment = ''
  rejectVisible.value = true
}

const confirmReject = async () => {
  if (!rejectFormRef.value) return
  await rejectFormRef.value.validate(async (valid) => {
    if (!valid) return
    rejectLoading.value = true
    try {
      // 根据类型调用不同的API
      const url = currentRow.value.applyType === 'table'
        ? `/permission/apply/approval/${currentRow.value.id}`
        : `/report-apply/${currentRow.value.id}`

      await request.put(url, {
        action: 'REJECT',
        comment: rejectForm.value.comment
      })
      ElMessage.success('已拒绝')
      rejectVisible.value = false
      fetchData()
    } catch (error) {
      ElMessage.error(error.response?.data?.message || '操作失败')
    } finally {
      rejectLoading.value = false
    }
  })
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
