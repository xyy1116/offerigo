<template>
  <el-dialog
    :model-value="modelValue"
    title="头像"
    width="380px"
    align-center
    append-to-body
    @update:model-value="$emit('update:modelValue', $event)"
  >
    <div class="avatar-body">
      <img class="avatar-preview" :src="preview" alt="头像预览" />
      <div class="avatar-actions">
        <button
          class="button button--small"
          :class="{ 'is-loading': saving }"
          :disabled="saving"
          @click="fileInput.click()"
        >
          上传头像
        </button>
        <button
          v-if="hasCustom"
          class="button button--small button--plain"
          :disabled="saving"
          @click="removeAvatar"
        >
          移除头像
        </button>
      </div>
      <p class="avatar-tip">支持 JPG / PNG，自动居中裁成方形；不上传时显示默认头像。</p>
      <input ref="fileInput" type="file" accept="image/*" style="display: none" @change="onFileChange" />
    </div>
  </el-dialog>
</template>

<script setup>
import { computed, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/user'
import { cropAndCompressPhoto } from '@/utils/photo'

defineProps({ modelValue: { type: Boolean, default: false } })
defineEmits(['update:modelValue'])

const userStore = useUserStore()
const fileInput = ref(null)
const saving = ref(false)

const hasCustom = computed(() => !!userStore.userInfo?.avatar)
const preview = computed(() => userStore.userInfo?.avatar || '/default-avatar.svg')

async function onFileChange(e) {
  const file = e.target.files?.[0]
  e.target.value = ''
  if (!file) return
  saving.value = true
  try {
    const dataUrl = await cropAndCompressPhoto(file, { width: 256, height: 256, quality: 0.86 })
    await userStore.updateAvatar(dataUrl)
    ElMessage.success('头像已更新')
  } catch (err) {
    ElMessage.error(err?.message === 'not-image' ? '请选择图片文件' : '头像保存失败，请稍后重试')
  } finally {
    saving.value = false
  }
}

async function removeAvatar() {
  saving.value = true
  try {
    await userStore.updateAvatar('')
    ElMessage.success('已恢复默认头像')
  } catch (e) {
    ElMessage.error('操作失败，请稍后重试')
  } finally {
    saving.value = false
  }
}
</script>

<style scoped>
.avatar-body {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 16px;
  padding: 4px 0 2px;
}
.avatar-preview {
  width: 96px;
  height: 96px;
  border-radius: 50%;
  object-fit: cover;
  border: 1px solid var(--color-border);
  background: var(--color-surface-muted);
}
.avatar-actions {
  display: flex;
  gap: 10px;
}
.avatar-tip {
  margin: 0;
  font-size: 12px;
  line-height: 1.6;
  color: var(--color-text-3);
  text-align: center;
}
</style>
