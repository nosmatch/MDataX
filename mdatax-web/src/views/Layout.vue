<template>
  <div class="layout">
    <!-- 顶部导航 -->
    <el-header class="header">
      <div class="logo">MDataX</div>
      <div class="user-info">
        <el-dropdown>
          <span class="el-dropdown-link">
            admin<el-icon class="el-icon--right"><arrow-down /></el-icon>
          </span>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item>退出登录</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>
    </el-header>

    <div class="main-container">
      <!-- 左侧菜单 -->
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
          <el-menu-item index="/development">
            <el-icon><Document /></el-icon>
            <span>数据开发</span>
          </el-menu-item>
          <el-menu-item index="/assets">
            <el-icon><Folder /></el-icon>
            <span>数据资产</span>
          </el-menu-item>
          <el-menu-item index="/query">
            <el-icon><Search /></el-icon>
            <span>数据查询</span>
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

      <!-- 主工作区 -->
      <el-main class="main">
        <router-view />
      </el-main>
    </div>
  </div>
</template>

<script setup>
import { HomeFilled, Connection, Document, Folder, Search, Lock, Setting, ArrowDown } from '@element-plus/icons-vue'
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
