<template>
  <span class="base-tag" :class="{ 'is-active': active }"><slot /></span>
</template>

<script setup>
defineProps({
  active: { type: Boolean, default: false }
})
</script>

<style scoped>
.base-tag {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 4px 12px;
  border-radius: 9999px;
  background: var(--color-surface-muted);
  color: var(--color-text-2);
  font-size: 12px;
  font-weight: 500;
  line-height: 1;
  white-space: nowrap;
}
.base-tag.is-active {
  background: var(--color-text);
  color: var(--color-bg);
}
</style>