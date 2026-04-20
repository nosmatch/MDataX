<template>
  <div>
    <el-tabs v-model="activeTab" type="border-card">
      <el-tab-pane label="用户管理" name="user">
        <UserManage />
      </el-tab-pane>
      <el-tab-pane label="角色管理" name="role">
        <RoleManage />
      </el-tab-pane>
      <el-tab-pane label="操作日志" name="log">
        <LogManage />
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import UserManage from '../components/system/UserManage.vue'
import RoleManage from '../components/system/RoleManage.vue'
import LogManage from '../components/system/LogManage.vue'

const activeTab = ref('user')
</script>
