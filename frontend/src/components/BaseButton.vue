<template>
  <button
    :type="type"
    class="base-button"
    :class="variant === 'secondary' ? 'base-button--secondary' : 'base-button--primary'"
    :disabled="disabled || loading"
    @click="$emit('click', $event)"
  >
    <Loader2 v-if="loading" :size="14" class="animate-spin motion-reduce:animate-none" aria-hidden="true" />
    <slot />
  </button>
</template>

<script setup>
import { Loader2 } from 'lucide-vue-next'

defineProps({
  type: { type: String, default: 'button' },
  disabled: { type: Boolean, default: false },
  loading: { type: Boolean, default: false },
  variant: { type: String, default: 'primary' }
})

defineEmits(['click'])
</script>

<style scoped>
.base-button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  padding: 6px 16px;
  border: 1px solid transparent;
  border-radius: var(--radius-sm);
  background: var(--color-action);
  color: var(--color-action-text);
  font-family: inherit;
  font-size: 13px;
  font-weight: 500;
  line-height: 1;
  white-space: nowrap;
  cursor: pointer;
  transition:
    background-color 0.18s ease,
    transform 0.18s var(--ease-out-expo),
    box-shadow 0.18s ease;
}
.base-button:hover {
  background: var(--color-action-hover);
  transform: translateY(-1px);
  box-shadow: var(--shadow-teal);
}
.base-button:hover svg {
  transform: translateX(1px);
}
.base-button svg {
  transition: transform 0.18s var(--ease-out-expo);
}
.base-button:active {
  background: var(--color-action-active);
  transform: translateY(0) scale(0.98);
  box-shadow: none;
}
.base-button--secondary {
  background: var(--color-surface);
  border-color: var(--color-border-strong);
  color: var(--color-text);
}
.base-button--secondary:hover {
  background: var(--color-surface-muted);
  border-color: var(--color-border-hover);
}
.base-button--secondary:active {
  background: var(--color-surface-muted);
  border-color: var(--color-border-hover);
}
.base-button:disabled {
  opacity: 0.5;
  cursor: not-allowed;
  pointer-events: none;
}
.base-button:focus-visible {
  outline: 2px solid var(--color-action-light);
  outline-offset: 2px;
}
</style>