<template>
  <div v-if="active" class="generation-progress" role="status" aria-live="polite">
    <div class="gp-head">
      <span class="gp-spin" aria-hidden="true"></span>
      <span class="gp-label">{{ label }}</span>
      <span v-if="total > 0" class="gp-count">{{ current }}/{{ total }}</span>
      <span class="gp-percent">{{ percent }}%</span>
    </div>
    <div v-if="hasStage" class="gp-steps">
      <span
        v-for="(step, i) in stepList"
        :key="step.key"
        class="gp-step"
        :class="{ 'is-done': i < activeIndex, 'is-active': i === activeIndex }"
      >
        <i class="gp-dot" aria-hidden="true"></i>
        <span>{{ step.label }}</span>
      </span>
    </div>
    <div class="gp-track"><span class="gp-fill" :style="{ width: percent + '%' }"></span></div>
    <div class="gp-meta">{{ stageText ? stageText + ' · ' : '' }}{{ timeText }}</div>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { readGenerationStats } from '@/utils/generationTiming'

const DEFAULT_STEPS = [
  { key: 'PREPARING', label: '准备素材' },
  { key: 'GENERATING', label: 'AI 生成内容' },
  { key: 'PARSING', label: '整理结果' }
]

// 真实阶段划定百分比区间；区间内按已用时间 / 本机参考耗时长出进度
const DEFAULT_RANGES = {
  PREPARING: [5, 20],
  GENERATING: [25, 85],
  PARSING: [90, 99]
}

const props = defineProps({
  active: { type: Boolean, default: false },
  label: { type: String, default: '正在生成' },
  /** 后端真实阶段 key */
  stage: { type: String, default: '' },
  /** 阶段列表，默认是简历/求职信的三阶段 */
  steps: { type: Array, default: () => [] },
  /** 阶段对应的进度区间 */
  ranges: { type: Object, default: () => ({}) },
  /** 用于读取同类任务在本机的历史耗时 */
  kind: { type: String, default: '' },
  /** 首次生成时用于估算进度增长的参考秒数 */
  estimate: { type: Number, default: 30 },
  /** 逐题任务当前进度 */
  current: { type: Number, default: 0 },
  /** 逐题任务总题数 */
  total: { type: Number, default: 0 }
})

const stepList = computed(() => (Array.isArray(props.steps) && props.steps.length ? props.steps : DEFAULT_STEPS))
const rangeMap = computed(() => (props.ranges && Object.keys(props.ranges).length ? props.ranges : DEFAULT_RANGES))

const startedAt = ref(0)
const now = ref(Date.now())
const stats = ref(null)
let timer = null

watch(() => props.active, (active) => {
  clearInterval(timer)
  timer = null
  if (!active) return
  stats.value = readGenerationStats(props.kind)
  startedAt.value = Date.now()
  now.value = startedAt.value
  timer = setInterval(() => { now.value = Date.now() }, 500)
}, { immediate: true })

onBeforeUnmount(() => clearInterval(timer))

const elapsed = computed(() => {
  if (!startedAt.value) return 0
  return Math.max(0, Math.floor((now.value - startedAt.value) / 1000))
})
const activeIndex = computed(() => stepList.value.findIndex((step) => step.key === props.stage))
const hasStage = computed(() => activeIndex.value >= 0)
const stageText = computed(() => {
  if (!hasStage.value) return ''
  const count = props.total > 0 ? `（${props.current}/${props.total}）` : ''
  return `当前阶段：${stepList.value[activeIndex.value].label}${count}`
})
const reference = computed(() => {
  const s = stats.value
  if (s && s.max > 0) return s.max
  if (s && s.last > 0) return s.last
  return Math.max(10, props.estimate)
})
const percent = computed(() => {
  if (!props.active) return 0
  const range = rangeMap.value[props.stage]
  if (!range) {
    // 后端阶段尚未返回（或轮询失败）时，按参考耗时保守增长，封顶 60%，不假装接近完成
    const ratio = Math.min(1, elapsed.value / Math.max(1, reference.value))
    return Math.min(60, Math.max(5, Math.round(5 + 55 * ratio)))
  }
  if (props.total > 0) {
    const ratio = Math.min(1, Math.max(0, props.current / props.total))
    return Math.round(range[0] + (range[1] - range[0]) * ratio)
  }
  const ratio = Math.min(1, elapsed.value / Math.max(1, reference.value))
  return Math.round(range[0] + (range[1] - range[0]) * ratio)
})
const timeText = computed(() => {
  const s = stats.value
  const sec = elapsed.value
  if (s && sec > s.max + 10) {
    return `已等待 ${formatSec(sec)} · 已超过最近参考耗时，仍在生成…`
  }
  if (s && s.count >= 2) {
    return `已等待 ${formatSec(sec)} · 最近同类生成约 ${formatSec(s.min)}–${formatSec(s.max)}`
  }
  if (s && s.count === 1) {
    return `已等待 ${formatSec(sec)} · 上次同类生成约 ${formatSec(s.last)}`
  }
  if (sec > props.estimate + 10) {
    return `已等待 ${formatSec(sec)} · 已超过参考时长，仍在生成…`
  }
  return `已等待 ${formatSec(sec)} · 首次生成，完成后会记录实际耗时（通常几十秒）`
})

function formatSec(seconds) {
  const sec = Math.max(0, Math.round(seconds))
  if (sec < 60) return `${sec}s`
  return `${Math.floor(sec / 60)}m${sec % 60 ? ` ${sec % 60}s` : ''}`
}
</script>

<style scoped>
.generation-progress {
  position: relative;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin: 0 0 14px;
  padding: 12px 14px;
  border: 1px solid var(--color-border);
  border-radius: 10px;
  background: var(--gradient-soft);
}
.generation-progress::after {
  content: '';
  position: absolute;
  top: 0;
  left: -40%;
  width: 40%;
  height: 2px;
  background: linear-gradient(90deg, transparent, var(--color-accent), transparent);
  animation: gp-sweep 1.6s linear infinite;
}
@keyframes gp-sweep {
  to { left: 100%; }
}
.gp-head {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  color: var(--color-accent);
}
.gp-label {
  flex: 1;
  min-width: 0;
}
.gp-percent {
  flex: none;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}
.gp-count {
  flex: none;
  padding: 0 6px;
  border-radius: 999px;
  background: var(--color-surface);
  color: var(--color-text-2);
  font-size: 11px;
  font-variant-numeric: tabular-nums;
}
.gp-steps {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px 14px;
  font-size: 12px;
}
.gp-step {
  display: flex;
  align-items: center;
  gap: 6px;
  color: var(--color-text-3);
}
.gp-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--color-border);
}
.gp-step.is-done {
  color: var(--color-text-2);
}
.gp-step.is-done .gp-dot {
  background: var(--color-accent);
}
.gp-step.is-active {
  color: var(--color-accent);
  font-weight: 600;
}
.gp-step.is-active .gp-dot {
  background: var(--color-accent);
  box-shadow: 0 0 0 3px var(--color-accent-soft);
}
.gp-track {
  height: 8px;
  border-radius: 999px;
  background: var(--color-surface-muted);
  overflow: hidden;
}
.gp-fill {
  display: block;
  height: 100%;
  border-radius: 999px;
  background: var(--color-accent);
  transition: width 0.5s ease;
}
.gp-meta {
  font-size: 12px;
  color: var(--color-text-3);
  font-variant-numeric: tabular-nums;
}
.gp-spin {
  flex: none;
  width: 14px;
  height: 14px;
  border-radius: 50%;
  border: 2px solid var(--color-border);
  border-top-color: var(--color-accent);
  animation: gp-rotate 0.8s linear infinite;
}
@keyframes gp-rotate {
  to { transform: rotate(360deg); }
}
@media (prefers-reduced-motion: reduce) {
  .generation-progress::after { animation: none; opacity: 0; }
}
</style>