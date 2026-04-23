<template>
  <div>
    <div class="toolbar">
      <el-input v-model="keyword" placeholder="用户名/昵称" prefix-icon="Search" clearable style="width: 220px; margin-right: 12px" />
      <el-button type="primary" @click="handleSearch">查询</el-button>
      <el-button type="success" @click="handleAdd">新增用户</el-button>
    </div>

    <el-table :data="tableData" border v-loading="loading" style="margin-top: 16px">
      <el-table-column prop="username" label="用户名" />
      <el-table-column prop="nickname" label="昵称" />
      <el-table-column prop="email" label="邮箱" />
      <el-table-column prop="status" label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : 'danger'">{{ row.status === 1 ? '启用' : '禁用' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createTime" label="创建时间" width="180" />
      <el-table-column label="操作" width="220" fixed="right">
        <template #default="{ row }">
          <el-button size="small" @click="handleEdit(row)">编辑</el-button>
          <el-button size="small" type="primary" @click="handleAssignRoles(row)">分配角色</el-button>
          <el-button size="small" type="danger" @click="handleDelete(row)">删除</el-button>
        </template>
      </el-table-column>
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

    <!-- 新增/编辑弹窗 -->
    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑用户' : '新增用户'" width="500px">
      <el-form :model="form" :rules="rules" ref="formRef" label-width="80px">
        <el-form-item label="用户名" prop="username">
          <el-input v-model="form.username" :disabled="isEdit" />
        </el-form-item>
        <el-form-item label="密码" prop="password" v-if="!isEdit">
          <el-input v-model="form.password" type="password" />
        </el-form-item>
        <el-form-item label="密码" v-else>
          <el-input v-model="form.password" type="password" placeholder="不填则不修改" />
        </el-form-item>
        <el-form-item label="昵称">
          <el-input v-model="form.nickname" />
        </el-form-item>
        <el-form-item label="邮箱">
          <el-input v-model="form.email" />
        </el-form-item>
        <el-form-item label="状态" v-if="isEdit">
          <el-radio-group v-model="form.status">
            <el-radio :label="1">启用</el-radio>
            <el-radio :label="0">禁用</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSubmit" :loading="submitting">确定</el-button>
      </template>
    </el-dialog>

    <!-- 分配角色弹窗 -->
    <el-dialog v-model="roleDialogVisible" title="分配角色" width="400px">
      <el-checkbox-group v-model="selectedRoles">
        <el-checkbox v-for="role in allRoles" :key="role.id" :label="role.id">{{ role.roleName }}</el-checkbox>
      </el-checkbox-group>
      <template #footer>
        <el-button @click="roleDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitAssignRoles" :loading="roleSubmitting">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '../../utils/request.js'

const loading = ref(false)
const tableData = ref([])
const page = ref(1)
const size = ref(10)
const total = ref(0)
const keyword = ref('')
const isAdmin = ref(false)

const dialogVisible = ref(false)
const isEdit = ref(false)
const formRef = ref()
const submitting = ref(false)
const form = reactive({
  id: null,
  username: '',
  password: '',
  nickname: '',
  email: '',
  status: 1
})

const rules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

const roleDialogVisible = ref(false)
const roleSubmitting = ref(false)
const allRoles = ref([])
const selectedRoles = ref([])
const currentUserId = ref(null)
const loginUserId = ref(null)

const loadCurrentUser = async () => {
  try {
    const res = await request.get('/user/current')
    isAdmin.value = res.data.isAdmin || false
    loginUserId.value = res.data.id || null
  } catch (e) {
    // ignore
  }
}

const loadData = async () => {
  loading.value = true
  try {
    const res = await request.get('/user/page', {
      params: { page: page.value, size: size.value, keyword: keyword.value || undefined }
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

const handleAdd = () => {
  if (!isAdmin.value) {
    ElMessage.error('无权限，仅管理员可操作')
    return
  }
  isEdit.value = false
  Object.assign(form, { id: null, username: '', password: '', nickname: '', email: '', status: 1 })
  dialogVisible.value = true
}

const handleEdit = (row) => {
  if (!isAdmin.value && row.id !== loginUserId.value) {
    ElMessage.error('无权限，只能修改自己的信息')
    return
  }
  isEdit.value = true
  Object.assign(form, {
    id: row.id,
    username: row.username,
    password: '',
    nickname: row.nickname,
    email: row.email,
    status: row.status
  })
  dialogVisible.value = true
}

const handleSubmit = async () => {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  submitting.value = true
  try {
    if (isEdit.value) {
      await request.put(`/user/${form.id}`, form)
    } else {
      await request.post('/user', form)
    }
    ElMessage.success('保存成功')
    dialogVisible.value = false
    loadData()
  } catch (e) {
    ElMessage.error(e.message || '保存失败')
  } finally {
    submitting.value = false
  }
}

const handleDelete = async (row) => {
  if (!isAdmin.value) {
    ElMessage.error('无权限，仅管理员可操作')
    return
  }
  try {
    await ElMessageBox.confirm('确认删除该用户？', '提示', { type: 'warning' })
    await request.delete(`/user/${row.id}`)
    ElMessage.success('删除成功')
    loadData()
  } catch (e) {
    if (e !== 'cancel') {
      ElMessage.error(e.message || '删除失败')
    }
  }
}

const handleAssignRoles = async (row) => {
  currentUserId.value = row.id
  try {
    const [rolesRes, userRolesRes] = await Promise.all([
      request.get('/role/list'),
      request.get(`/user/${row.id}/roles`)
    ])
    allRoles.value = rolesRes.data
    selectedRoles.value = userRolesRes.data || []
    roleDialogVisible.value = true
  } catch (e) {
    ElMessage.error('加载失败')
  }
}

const submitAssignRoles = async () => {
  roleSubmitting.value = true
  try {
    await request.post(`/user/${currentUserId.value}/roles`, selectedRoles.value)
    ElMessage.success('分配成功')
    roleDialogVisible.value = false
  } catch (e) {
    ElMessage.error(e.message || '分配失败')
  } finally {
    roleSubmitting.value = false
  }
}

loadData()
loadCurrentUser()
</script>

<style scoped>
.toolbar {
  display: flex;
  align-items: center;
}
</style>
