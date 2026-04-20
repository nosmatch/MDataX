<template>
  <div>
    <h2>首页（工作台）</h2>
    <p>数据概览、最近任务、快捷入口</p>
    <el-card class="box-card" v-if="healthStatus">
      <template #header>
        <span>后端服务状态</span>
      </template>
      <div>服务名: {{ healthStatus.service }}</div>
      <div>状态: {{ healthStatus.status }}</div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import request from '../utils/request'

const healthStatus = ref(null)

onMounted(async () => {
  try {
    const res = await request.get('/health')
    healthStatus.value = res.data
  } catch (err) {
    console.error(' health check failed:', err)
  }
})
</script>

<style scoped>
.box-card {
  margin-top: 20px;
  max-width: 400px;
}
</style>
