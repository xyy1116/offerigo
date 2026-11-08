<script setup>
defineProps({
  label: {
    type: String,
    default: '删除'
  },
  size: {
    type: String,
    default: 'default' // default | small
  }
})
</script>

<template>
  <button type="button" class="delete-btn" :class="`delete-btn--${size}`" :aria-label="label">
    <svg viewBox="0 0 24 24" class="icon" aria-hidden="true">
      <path d="M24 20.188l-8.315-8.209 8.2-8.282-3.697-3.697-8.212 8.318-8.31-8.203-3.666 3.666 8.321 8.24-8.206 8.313 3.666 3.666 8.237-8.318 8.285 8.203z"></path>
    </svg>
    <span class="text">{{ label }}</span>
  </button>
</template>

<style scoped>
/* 与表格内的文字操作保持一致：仅危险色文字 */
.delete-btn {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 2px 4px;
  background: transparent;
  border: none;
  border-radius: var(--radius-sm);
  color: var(--color-danger);
  font-family: inherit;
  font-size: 12px;
  cursor: pointer;
  transition: color 0.15s ease, background-color 0.15s ease;
}
.delete-btn:hover {
  color: var(--color-danger);
  background: var(--color-danger-bg);
}
.delete-btn:focus-visible {
  outline: 2px solid var(--color-text);
  outline-offset: 1px;
}
.delete-btn .icon {
  width: 10px;
  height: 10px;
  fill: currentColor;
  flex-shrink: 0;
}
.delete-btn .text {
  white-space: nowrap;
}
.delete-btn--default .text {
  font-size: 13px;
}
</style>
