<template>
  <el-select
    v-model="selectedUsername"
    filterable
    remote
    reserve-keyword
    placeholder="请输入用户名搜索"
    :remote-method="searchUsers"
    :loading="loading"
    @change="handleUserChange"
    style="width: 100%"
  >
    <el-option
      v-for="user in userList"
      :key="user.id"
      :label="user.label"
      :value="user.username"
    >
      <div class="user-option">
        <span class="user-name">{{ user.nickname || user.username }}</span>
        <span class="user-username">@{{ user.username }}</span>
      </div>
    </el-option>
  </el-select>
</template>

<script setup>
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import request from '../utils/request.js'

const emit = defineEmits(['update:modelValue', 'select'])

const props = defineProps({
  modelValue: {
    type: String,
    default: ''
  }
})

const selectedUsername = ref(props.modelValue)
const userList = ref([])
const loading = ref(false)

let searchTimer = null

async function searchUsers(query) {
  if (!query || query.trim().length === 0) {
    userList.value = []
    return
  }

  // 防抖：500ms后才执行搜索
  clearTimeout(searchTimer)
  searchTimer = setTimeout(async () => {
    loading.value = true
    try {
      const res = await request.get('/user/search', {
        params: { keyword: query.trim() }
      })

      if (res.data && res.data.length > 0) {
        userList.value = res.data.map(user => ({
          id: user.id,
          username: user.username,
          nickname: user.nickname,
          label: `${user.nickname || user.username} (@${user.username})`
        }))
      } else {
        userList.value = []
        ElMessage.warning('未找到匹配的用户')
      }
    } catch (err) {
      console.error('搜索用户失败', err)
      ElMessage.error(err.message || '搜索用户失败')
    } finally {
      loading.value = false
    }
  }, 500)
}

function handleUserChange(username) {
  emit('update:modelValue', username)

  // 查找选中的用户信息
  const selectedUser = userList.value.find(u => u.username === username)
  if (selectedUser) {
    emit('select', selectedUser)
  }
}

// 暴露方法供父组件调用
defineExpose({
  clear() {
    selectedUsername.value = ''
    userList.value = []
  }
})
</script>

<style scoped>
.user-option {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.user-name {
  font-weight: 500;
  color: #333;
}

.user-username {
  font-size: 12px;
  color: #999;
}
</style>
