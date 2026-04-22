<template>
  <div class="layout">
    <el-header class="header">
      <div class="logo">MDataX</div>
      <div class="user-info">
        <span style="margin-right: 12px">{{ displayName }}</span>
        <el-dropdown @command="handleCommand">
          <span class="el-dropdown-link">
            <el-icon><ArrowDown /></el-icon>
          </span>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="logout">退出登录</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>
    </el-header>

    <div class="main-container">
      <el-aside class="aside" width="200px">
        <el-menu
          :default-active="$route.path"
          router
          class="menu"
          background-color="#304156"
          text-color="#bfcbd9"
          active-text-color="#409EFF"
        >
          <el-menu-item index="/dashboard">
            <el-icon><HomeFilled /></el-icon>
            <span>首页</span>
          </el-menu-item>
          <el-menu-item index="/integration">
            <el-icon><Connection /></el-icon>
            <span>数据集成</span>
          </el-menu-item>
          <el-sub-menu index="/development">
            <template #title>
              <el-icon><Document /></el-icon>
              <span>数据开发</span>
            </template>
            <el-menu-item index="/query">SQL查询</el-menu-item>
            <el-menu-item index="/development">SQL开发</el-menu-item>
            <el-menu-item index="/development/tasks">任务管理</el-menu-item>
            <el-menu-item index="/workflows">工作流管理</el-menu-item>
          </el-sub-menu>
          <el-menu-item index="/assets">
            <el-icon><Folder /></el-icon>
            <span>数据资产</span>
          </el-menu-item>
          <el-menu-item index="/permission">
            <el-icon><Lock /></el-icon>
            <span>权限管理</span>
          </el-menu-item>
          <el-menu-item index="/system">
            <el-icon><Setting /></el-icon>
            <span>系统管理</span>
          </el-menu-item>
        </el-menu>
      </el-aside>

      <el-main class="main">
        <router-view />
      </el-main>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '../stores/auth.js'
import { HomeFilled, Connection, Document, Folder, Lock, Setting, ArrowDown } from '@element-plus/icons-vue'

const router = useRouter()
const authStore = useAuthStore()

const displayName = computed(() => {
  return authStore.user?.nickname || authStore.user?.username || ''
})

const handleCommand = (cmd) => {
  if (cmd === 'logout') {
    authStore.clearAuth()
    ElMessage.success('已退出登录')
    router.push('/login')
  }
}
</script>

<style scoped>
.layout {
  height: 100vh;
  display: flex;
  flex-direction: column;
}
.header {
  height: 50px;
  background-color: #2b2f3a;
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 20px;
}
.logo {
  font-size: 20px;
  font-weight: bold;
}
.user-info {
  color: #fff;
  cursor: pointer;
  display: flex;
  align-items: center;
}
.main-container {
  display: flex;
  flex: 1;
  overflow: hidden;
}
.aside {
  background-color: #304156;
}
.menu {
  border-right: none;
  height: 100%;
}
.main {
  background-color: #f0f2f5;
  padding: 20px;
  overflow-y: auto;
}
</style>
