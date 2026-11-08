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
  box-sizing: border-box;
  height: 34px;
  padding: 0 14px;
  gap: 6px;
  border: 1px solid transparent;
  border-radius: var(--radius-sm);
  background: var(--gradient-action);
  color: var(--color-action-text);
  box-shadow: var(--shadow-teal);
  font-family: inherit;
  font-size: 13px;
  font-weight: 500;
  line-height: 1;
  white-space: nowrap;
  cursor: pointer;
  transition:
    filter 0.18s ease,
    transform 0.18s var(--ease-out-expo),
    box-shadow 0.18s ease;
}
.base-button:hover {
  background: var(--gradient-action);
  filter: brightness(1.06);
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
  background: var(--gradient-action);
  filter: brightness(0.96);
  transform: translateY(0) scale(0.98);
  box-shadow: none;
}
.base-button--secondary {
  background: var(--color-surface);
  border-color: var(--color-border-strong);
  color: var(--color-text);
  box-shadow: none;
}
.base-button--secondary:hover {
  background: var(--color-surface-muted);
  border-color: var(--color-border-hover);
  filter: none;
}
.base-button--secondary:active {
  background: var(--color-surface-muted);
  filter: none;
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