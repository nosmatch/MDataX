<template>
  <el-dialog
    v-model="visible"
    :title="dialogTitle"
    width="480px"
    :close-on-click-modal="false"
  >
    <el-form :model="form" :rules="rules" ref="formRef" label-width="80px">
      <el-form-item label="目标表">
        <span>{{ form.databaseName }}.{{ form.tableName }}</span>
      </el-form-item>
      <el-form-item label="权限类型">
        <el-tag :type="form.applyType === 'READ' ? 'success' : 'warning'" size="small">
          {{ form.applyType === 'READ' ? '读权限' : '写权限' }}
        </el-tag>
      </el-form-item>
      <el-form-item label="申请理由" prop="applyReason">
        <el-input
          v-model="form.applyReason"
          type="textarea"
          :rows="3"
          placeholder="请填写申请理由，方便责任人审批"
          maxlength="500"
          show-word-limit
        />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" @click="handleSubmit" :loading="submitting">提交申请</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, computed } from 'vue'
import { ElMessage } from 'element-plus'
import request from '../utils/request.js'

const props = defineProps({
  databaseName: String,
  tableName: String,
  applyType: String
})

const emit = defineEmits(['success'])

const visible = ref(false)
const submitting = ref(false)
const formRef = ref(null)

const form = ref({
  databaseName: '',
  tableName: '',
  applyType: 'READ',
  applyReason: ''
})

const dialogTitle = computed(() => {
  const typeText = form.value.applyType === 'READ' ? '读权限' : '写权限'
  return `申请${typeText}`
})

const rules = {
  applyReason: [
    { required: true, message: '请填写申请理由', trigger: 'blur' },
    { min: 2, max: 500, message: '申请理由长度在 2 到 500 个字符', trigger: 'blur' }
  ]
}

const open = (databaseName, tableName, applyType) => {
  form.value = {
    databaseName,
    tableName,
    applyType,
    applyReason: ''
  }
  visible.value = true
  if (formRef.value) {
    formRef.value.resetFields()
  }
}

const handleSubmit = async () => {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    submitting.value = true
    try {
      await request.post('/permission/apply', {
        databaseName: form.value.databaseName,
        tableName: form.value.tableName,
        applyType: form.value.applyType,
        applyReason: form.value.applyReason
      })
      ElMessage.success('申请已提交，请等待责任人审批')
      visible.value = false
      emit('success', form.value.databaseName, form.value.tableName, form.value.applyType)
    } catch (error) {
      ElMessage.error(error.response?.data?.message || '提交申请失败')
    } finally {
      submitting.value = false
    }
  })
}

defineExpose({ open })
</script>
