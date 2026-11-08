<template>
  <div ref="root" class="relative">
    <button
      type="button"
      class="inline-flex h-8 w-8 items-center justify-center rounded-[var(--radius-sm)] border-0 bg-transparent transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--color-primary-light)]"
      :class="
        overlay
          ? 'text-white hover:bg-white/10'
          : 'text-[var(--color-text-2)] hover:bg-[var(--color-surface-muted)] hover:text-[var(--color-text)]'
      "
      :aria-label="`切换主题（当前：${currentLabel}）`"
      aria-haspopup="menu"
      :aria-expanded="open"
      @click="open = !open"
    >
      <component :is="currentIcon" :size="17" aria-hidden="true" />
    </button>

    <div
      v-if="open"
      class="absolute right-0 top-full z-[1001] mt-2 w-40 overflow-hidden rounded-[var(--radius-md)] border border-[var(--color-border)] bg-[var(--color-surface)] p-1 shadow-[var(--shadow-lg)]"
      role="menu"
      aria-label="主题模式"
    >
      <button
        v-for="option in options"
        :key="option.value"
        type="button"
        class="flex w-full items-center gap-2 rounded-[var(--radius-sm)] border-0 bg-transparent px-2 py-1.5 text-left font-[inherit] text-[13px] transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--color-primary-light)]"
        :class="
          themeMode === option.value
            ? 'bg-[var(--color-surface-muted)] text-[var(--color-text)]'
            : 'text-[var(--color-text-2)] hover:bg-[var(--color-surface-muted)]'
        "
        role="menuitemradio"
        :aria-checked="themeMode === option.value"
        @click="choose(option.value)"
      >
        <component :is="option.icon" :size="15" aria-hidden="true" />
        <span class="flex-1">{{ option.label }}</span>
        <Check v-if="themeMode === option.value" :size="14" class="text-[var(--color-primary)]" aria-hidden="true" />
      </button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, type Component } from 'vue'
import { Check, Monitor, Moon, Sun } from 'lucide-vue-next'
import { setThemeMode, themeMode, type ThemeMode } from '@/utils/theme.ts'

interface ThemeOption {
  value: ThemeMode
  label: string
  icon: Component
}

defineProps({
  overlay: { type: Boolean, default: false }
})

const open = ref<boolean>(false)
const root = ref<HTMLElement | null>(null)

const options: ThemeOption[] = [
  { value: 'system', label: '跟随系统', icon: Monitor },
  { value: 'light', label: '亮色', icon: Sun },
  { value: 'dark', label: '暗色', icon: Moon }
]

const current = computed<ThemeOption>(
  () => options.find((item) => item.value === themeMode.value) || options[0]
)
const currentIcon = computed<Component>(() => current.value.icon)
const currentLabel = computed<string>(() => current.value.label)

function choose(value: ThemeMode): void {
  setThemeMode(value)
  open.value = false
}

function onPointerDown(event: PointerEvent): void {
  const target = event.target as Node | null
  if (root.value && target && !root.value.contains(target)) open.value = false
}

function onKeydown(event: KeyboardEvent): void {
  if (event.key === 'Escape') open.value = false
}

onMounted(() => {
  document.addEventListener('pointerdown', onPointerDown)
  document.addEventListener('keydown', onKeydown)
})

onBeforeUnmount(() => {
  document.removeEventListener('pointerdown', onPointerDown)
  document.removeEventListener('keydown', onKeydown)
})
</script>