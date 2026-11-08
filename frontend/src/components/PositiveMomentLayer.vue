<template>
  <Teleport to="body">
    <div class="positive-moment-layer" aria-live="polite" aria-atomic="false">
      <TransitionGroup name="positive-moment">
        <div v-for="m in store.moments" :key="m.id" class="positive-moment" :data-type="m.type">
          <strong v-if="m.title" class="positive-moment-title">{{ m.title }}</strong>
          <span v-if="m.message" class="positive-moment-message">{{ m.message }}</span>
        </div>
      </TransitionGroup>
    </div>
  </Teleport>
</template>

<script setup>
import { usePositiveMomentStore } from '@/stores/positiveMoment'

const store = usePositiveMomentStore()
</script>

<style scoped>
/* 占位样式：只保证浮层脱离文档流、不拦截操作、自动淡入淡出，外观可按项目设计替换 */
.positive-moment-layer {
  position: fixed;
  right: 24px;
  bottom: 24px;
  z-index: 3000;
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 10px;
  pointer-events: none;
}
.positive-moment {
  display: flex;
  flex-direction: column;
  gap: 2px;
  max-width: min(340px, calc(100vw - 48px));
  padding: 12px 16px;
  border-radius: 12px;
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  box-shadow: var(--shadow-md);
  color: var(--color-text);
}
.positive-moment-title {
  font-size: 13.5px;
  font-weight: 600;
}
.positive-moment-message {
  font-size: 12.5px;
  line-height: 1.5;
  color: var(--color-text-2);
}
.positive-moment-enter-active,
.positive-moment-leave-active {
  transition: opacity 0.25s ease, transform 0.25s ease;
}
.positive-moment-enter-from,
.positive-moment-leave-to {
  opacity: 0;
  transform: translateY(8px);
}
</style>
