<template>
  <div class="dashboard-page">
    <div class="page-header">
      <h2>首页工作台</h2>
    </div>

    <!-- 数据概览卡片 -->
    <div class="stats-row">
      <el-card class="stat-card">
        <div class="stat-value">{{ stats.tableCount || 0 }}</div>
        <div class="stat-label">数据表数量</div>
      </el-card>
      <el-card class="stat-card">
        <div class="stat-value">{{ stats.syncTaskCountToday || 0 }}</div>
        <div class="stat-label">今日同步任务</div>
        <div class="stat-sub">成功率 {{ stats.syncSuccessRate || 0 }}%</div>
      </el-card>
      <el-card class="stat-card">
        <div class="stat-value">{{ stats.sqlTaskCountToday || 0 }}</div>
        <div class="stat-label">今日 SQL 任务</div>
        <div class="stat-sub">成功率 {{ stats.sqlSuccessRate || 0 }}%</div>
      </el-card>
      <el-card class="stat-card quick-card" @click="goIntegration">
        <div class="quick-icon">
          <el-icon :size="32"><Connection /></el-icon>
        </div>
        <div class="quick-label">新建同步任务</div>
      </el-card>
      <el-card class="stat-card quick-card" @click="goDevelopment">
        <div class="quick-icon">
          <el-icon :size="32"><Document /></el-icon>
        </div>
        <div class="quick-label">新建 SQL 任务</div>
      </el-card>
    </div>

    <el-row :gutter="16" class="bottom-row">
      <!-- 最近任务 -->
      <el-col :span="12">
        <el-card>
          <template #header>
            <div class="card-header">
              <span>最近运行任务</span>
            </div>
          </template>
          <el-table :data="recentTasks" size="small" stripe v-loading="tasksLoading">
            <el-table-column prop="name" label="任务名称" min-width="160" show-overflow-tooltip />
            <el-table-column prop="type" label="类型" width="80" align="center">
              <template #default="{ row }">
                <el-tag :type="row.type === '同步' ? 'primary' : 'success'" size="small">
                  {{ row.type }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="status" label="状态" width="80" align="center">
              <template #default="{ row }">
                <el-tag
                  :type="row.status === 'SUCCESS' ? 'success' : row.status === 'RUNNING' ? 'warning' : 'danger'"
                  size="small"
                >
                  {{ row.status === 'SUCCESS' ? '成功' : row.status === 'RUNNING' ? '运行中' : '失败' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="time" label="时间" width="160" />
          </el-table>
        </el-card>
      </el-col>

      <!-- 最近访问 -->
      <el-col :span="12">
        <el-card>
          <template #header>
            <div class="card-header">
              <span>最近访问数据</span>
            </div>
          </template>
          <el-table :data="recentVisits" size="small" stripe v-loading="visitsLoading">
            <el-table-column prop="tableName" label="表名" min-width="200" show-overflow-tooltip />
            <el-table-column prop="visitCount" label="访问次数" width="90" align="center" />
            <el-table-column prop="lastVisitTime" label="最后访问" width="160" />
          </el-table>
          <el-empty v-if="!visitsLoading && recentVisits.length === 0" description="暂无访问记录" />
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Connection, Document } from '@element-plus/icons-vue'
import request from '../utils/request.js'

const router = useRouter()

const stats = ref({})
const recentTasks = ref([])
const recentVisits = ref([])
const tasksLoading = ref(false)
const visitsLoading = ref(false)

const fetchStats = async () => {
  try {
    const res = await request.get('/dashboard/stats')
    stats.value = res.data || {}
  } catch (error) {
    ElMessage.error(error.message || '获取统计数据失败')
  }
}

const fetchRecentTasks = async () => {
  tasksLoading.value = true
  try {
    const res = await request.get('/dashboard/recent-tasks')
    recentTasks.value = res.data || []
  } catch (error) {
    ElMessage.error(error.message || '获取最近任务失败')
  } finally {
    tasksLoading.value = false
  }
}

const fetchRecentVisits = async () => {
  visitsLoading.value = true
  try {
    const res = await request.get('/dashboard/recent-visits')
    recentVisits.value = res.data || []
  } catch (error) {
    ElMessage.error(error.message || '获取访问记录失败')
  } finally {
    visitsLoading.value = false
  }
}

const goIntegration = () => {
  router.push('/integration')
}

const goDevelopment = () => {
  router.push('/development')
}

onMounted(() => {
  fetchStats()
  fetchRecentTasks()
  fetchRecentVisits()
})
</script>

<style scoped>
.dashboard-page {
  padding-bottom: 20px;
}
.page-header {
  margin-bottom: 16px;
}
.page-header h2 {
  margin: 0;
  font-size: 20px;
  font-weight: 600;
}
.stats-row {
  display: flex;
  gap: 16px;
  margin-bottom: 16px;
}
.stats-row .el-card {
  flex: 1;
  min-width: 0;
}
.stat-card {
  text-align: center;
  padding: 12px 0;
}
.stat-value {
  font-size: 32px;
  font-weight: bold;
  color: #303133;
  line-height: 1.2;
}
.stat-label {
  font-size: 14px;
  color: #606266;
  margin-top: 4px;
}
.stat-sub {
  font-size: 12px;
  color: #909399;
  margin-top: 2px;
}
.quick-card {
  cursor: pointer;
  transition: all 0.2s;
}
.quick-card:hover {
  box-shadow: 0 2px 12px rgba(64, 158, 255, 0.2);
  border-color: #409eff;
}
.quick-icon {
  color: #409eff;
  margin-bottom: 8px;
}
.quick-label {
  font-size: 14px;
  color: #606266;
}
.card-header {
  font-weight: 600;
}
.bottom-row {
  margin-top: 8px;
}
</style>
