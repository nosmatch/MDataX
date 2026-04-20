<template>
  <div>
    <div class="page-header">
      <h2>权限配置</h2>
      <p class="desc">为角色配置对数据表的读/写权限</p>
    </div>

    <el-row :gutter="20">
      <el-col :span="6">
        <el-card>
          <template #header>
            <span>选择角色</span>
          </template>
          <el-input
            v-model="roleKeyword"
            placeholder="搜索角色"
            prefix-icon="Search"
            clearable
            style="margin-bottom: 12px"
          />
          <el-menu
            :default-active="String(selectedRoleId)"
            @select="handleRoleSelect"
            style="border-right: none"
          >
            <el-menu-item
              v-for="role in filteredRoles"
              :key="role.id"
              :index="String(role.id)"
            >
              {{ role.roleName }}
            </el-menu-item>
          </el-menu>
        </el-card>
      </el-col>

      <el-col :span="18">
        <el-card v-if="selectedRole">
          <template #header>
            <div class="card-header">
              <span>{{ selectedRole.roleName }} — 权限配置</span>
              <el-button type="primary" @click="savePermissions" :loading="saving">保存权限</el-button>
            </div>
          </template>

          <el-table :data="tablePermissions" border style="width: 100%">
            <el-table-column prop="tableName" label="表名" min-width="200" />
            <el-table-column label="读权限" width="120" align="center">
              <template #default="{ row }">
                <el-checkbox v-model="row.read" />
              </template>
            </el-table-column>
            <el-table-column label="写权限" width="120" align="center">
              <template #default="{ row }">
                <el-checkbox v-model="row.write" />
              </template>
            </el-table-column>
          </el-table>
        </el-card>

        <el-empty v-else description="请选择左侧角色进行配置" />
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import { ElMessage } from 'element-plus'
import request from '../utils/request.js'

const roles = ref([])
const roleKeyword = ref('')
const selectedRoleId = ref(null)
const selectedRole = computed(() => roles.value.find(r => r.id === selectedRoleId.value))
const tablePermissions = ref([])
const saving = ref(false)

const filteredRoles = computed(() => {
  if (!roleKeyword.value) return roles.value
  return roles.value.filter(r => r.roleName.includes(roleKeyword.value))
})

const loadRoles = async () => {
  try {
    const res = await request.get('/role/list')
    roles.value = res.data
  } catch (e) {
    ElMessage.error('加载角色失败')
  }
}

const loadPermissions = async () => {
  if (!selectedRoleId.value) return
  try {
    const res = await request.get(`/role/${selectedRoleId.value}/permissions`)
    const permissions = res.data || []
    // 这里简化处理，实际应该从数据库获取所有表名
    // MVP阶段先展示已有的权限，后续从metadata_table获取全部表
    tablePermissions.value = permissions.map(p => ({
      tableName: p.tableName,
      read: p.permissionType === 'READ',
      write: p.permissionType === 'WRITE'
    }))
  } catch (e) {
    ElMessage.error('加载权限失败')
  }
}

const handleRoleSelect = (index) => {
  selectedRoleId.value = Number(index)
  loadPermissions()
}

const savePermissions = async () => {
  saving.value = true
  try {
    const permissions = []
    tablePermissions.value.forEach(row => {
      if (row.read) {
        permissions.push({ roleId: selectedRoleId.value, tableName: row.tableName, permissionType: 'READ' })
      }
      if (row.write) {
        permissions.push({ roleId: selectedRoleId.value, tableName: row.tableName, permissionType: 'WRITE' })
      }
    })
    await request.post(`/role/${selectedRoleId.value}/permissions`, permissions)
    ElMessage.success('保存成功')
  } catch (e) {
    ElMessage.error('保存失败')
  } finally {
    saving.value = false
  }
}

loadRoles()
</script>

<style scoped>
.page-header {
  margin-bottom: 20px;
}
.desc {
  color: #909399;
  font-size: 14px;
  margin-top: 4px;
}
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
</style>
