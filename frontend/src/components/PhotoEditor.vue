<template>
  <div class="photo-editor">
    <div class="photo-preview" :class="{ 'is-empty': !photo }">
      <img v-if="photo" :src="photo" alt="证件照" />
      <span v-else>3:4</span>
    </div>
    <div class="photo-side">
      <div class="photo-actions">
        <button
          type="button"
          class="button button--plain button--small"
          :disabled="uploading"
          @click="pick"
        >
          {{ uploading ? '处理中…' : photo ? '更换证件照' : '上传证件照' }}
        </button>
        <button
          v-if="photo"
          type="button"
          class="button button--plain button--small photo-delete"
          @click="$emit('change', '')"
        >
          删除
        </button>
      </div>
      <p class="photo-tip">自动裁剪为 3:4 一寸照，仅用于简历导出</p>
    </div>
    <input
      ref="fileInput"
      type="file"
      accept="image/jpeg,image/png,image/webp"
      class="photo-input"
      @change="onFileChange"
    />
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { cropAndCompressPhoto } from '@/utils/photo'

defineProps({
  photo: { type: String, default: '' }
})
const emit = defineEmits(['change'])

const fileInput = ref(null)
const uploading = ref(false)

function pick() {
  if (uploading.value) return
  fileInput.value?.click()
}

async function onFileChange(e) {
  const file = e.target.files && e.target.files[0]
  e.target.value = ''
  if (!file) return
  if (!file.type.startsWith('image/')) {
    ElMessage.warning('请上传图片格式的证件照')
    return
  }
  if (file.size > 15 * 1024 * 1024) {
    ElMessage.warning('图片不能超过 15MB')
    return
  }
  uploading.value = true
  try {
    const dataUrl = await cropAndCompressPhoto(file)
    emit('change', dataUrl)
  } catch (err) {
    ElMessage.error('证件照处理失败，请重试')
  } finally {
    uploading.value = false
  }
}
</script>

<style scoped>
.photo-editor {
  display: flex;
  align-items: flex-start;
  gap: 16px;
}
.photo-preview {
  width: 78px;
  height: 104px;
  flex: none;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
  background: var(--color-surface-muted);
  overflow: hidden;
  display: grid;
  place-items: center;
  color: var(--color-text-3);
  font-size: 12px;
}
.photo-preview img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}
.photo-side {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding-top: 2px;
}
.photo-actions {
  display: flex;
  align-items: center;
  gap: 6px;
}
.photo-delete {
  color: var(--color-danger);
  border-color: var(--color-danger-border);
}
.photo-delete:hover {
  color: var(--color-danger);
  background: var(--color-danger-bg);
  border-color: var(--color-danger-border-hover);
}
.photo-tip {
  margin: 0;
  font-size: 12px;
  line-height: 1.5;
  color: var(--color-text-3);
}
.photo-input {
  display: none;
}
</style>
