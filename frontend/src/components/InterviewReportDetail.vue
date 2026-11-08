<template>
  <div class="interview-report">
    <template v-if="report">
      <div class="flex flex-wrap items-end gap-x-4 gap-y-2 border-b border-[var(--color-border)] pb-4 pt-1">
        <div class="flex items-baseline gap-1">
          <span
            class="font-[var(--font-display)] text-[28px] font-semibold leading-none text-[var(--color-text)] tabular-nums"
          >{{ displayScore }}</span>
          <span class="text-[12px] text-[var(--color-text-3)]">分</span>
        </div>
        <span
          v-if="report.level"
          class="inline-flex h-5 items-center rounded-full px-2 text-[11px] font-medium"
          :class="levelClass(report.level)"
        >{{ report.level }}</span>
        <div class="ml-auto flex flex-wrap gap-x-3 gap-y-1 text-[12px] text-[var(--color-text-3)]">
          <span v-if="report.durationMinutes != null">面试 {{ report.durationMinutes }} 分钟</span>
          <span v-if="report.questionCount != null">{{ report.questionCount }} 题</span>
        </div>
      </div>

      <p class="m-0 mt-4 text-[13px] leading-[1.7] text-[var(--color-text-2)]">
        {{ report.summary || '暂无评估摘要' }}
      </p>

      <div v-if="report.dimensions && report.dimensions.length" class="mt-5">
        <h3 class="m-0 text-[12px] font-medium text-[var(--color-text-3)]">分维度评分</h3>
        <ul class="m-0 mt-1 list-none divide-y divide-[var(--color-border)] p-0">
          <li v-for="d in report.dimensions" :key="d.name" class="py-3">
            <div class="flex items-baseline justify-between gap-3">
              <span class="text-[13px] font-medium text-[var(--color-text)]">{{ d.name }}</span>
              <span
                class="font-[var(--font-display)] text-[14px] font-semibold text-[var(--color-text)] tabular-nums"
              >{{ d.score }}<span class="ml-0.5 text-[11px] font-normal text-[var(--color-text-3)]">/25</span></span>
            </div>
            <div
              class="mt-2 h-1.5 w-full overflow-hidden rounded-full bg-[var(--color-surface-muted)]"
              role="progressbar"
              :aria-valuenow="d.score"
              aria-valuemin="0"
              aria-valuemax="25"
              :aria-label="d.name"
            >
              <div
                class="h-full rounded-full"
                :style="{ width: dimPercent(d.score) + '%', backgroundColor: dimColor(d.score) }"
              ></div>
            </div>
            <p v-if="d.comment" class="m-0 mt-1.5 text-[12px] leading-[1.6] text-[var(--color-text-3)]">
              {{ d.comment }}
            </p>
            <p v-if="d.evidence" class="m-0 mt-1 text-[12px] leading-[1.6] text-[var(--color-text-3)]">
              依据：{{ d.evidence }}
            </p>
          </li>
        </ul>
      </div>

      <div class="mt-5 grid grid-cols-1 gap-5 sm:grid-cols-2">
        <div v-if="report.strengths && report.strengths.length">
          <h3 class="m-0 text-[12px] font-medium text-[var(--color-text-3)]">核心优势</h3>
          <ul class="m-0 mt-2 list-none space-y-1.5 p-0">
            <li
              v-for="(s, i) in report.strengths"
              :key="i"
              class="flex gap-2 text-[13px] leading-[1.6] text-[var(--color-text-2)]"
            >
              <span class="mt-[7px] h-1 w-1 flex-none rounded-full bg-[var(--color-success)]" aria-hidden="true"></span>
              <span>{{ s }}</span>
            </li>
          </ul>
        </div>
        <div v-if="report.weaknesses && report.weaknesses.length">
          <h3 class="m-0 text-[12px] font-medium text-[var(--color-text-3)]">待提升点</h3>
          <ul class="m-0 mt-2 list-none space-y-1.5 p-0">
            <li
              v-for="(s, i) in report.weaknesses"
              :key="i"
              class="flex gap-2 text-[13px] leading-[1.6] text-[var(--color-text-2)]"
            >
              <span class="mt-[7px] h-1 w-1 flex-none rounded-full bg-[var(--color-warning)]" aria-hidden="true"></span>
              <span>{{ s }}</span>
            </li>
          </ul>
        </div>
      </div>
    </template>
    <p v-else class="m-0 text-[13px] text-[var(--color-text-3)]">暂无评估摘要</p>
  </div>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  report: { type: Object, default: null },
  score: { type: [Number, String], default: null }
})

const displayScore = computed(() => {
  const value = props.score ?? props.report?.score
  if (value === null || value === undefined || value === '') return '—'
  const n = Number(value)
  return Number.isFinite(n) ? n : '—'
})

function dimPercent(score) {
  return Math.max(0, Math.min(100, Math.round(((Number(score) || 0) / 25) * 100)))
}

function dimColor(score) {
  const s = Number(score) || 0
  if (s >= 20) return 'var(--color-success)'
  if (s >= 12) return 'var(--color-warning)'
  return 'var(--color-danger)'
}

function levelClass(level) {
  return {
    优秀: 'bg-[var(--color-success-bg)] text-[var(--color-success)]',
    良好: 'bg-[var(--color-primary-bg)] text-[var(--color-primary)]',
    中等: 'bg-[var(--color-warning-bg)] text-[var(--color-warning)]',
    待提升: 'bg-[var(--color-danger-bg)] text-[var(--color-danger)]'
  }[level] || 'bg-[var(--color-surface-muted)] text-[var(--color-text-3)]'
}
</script>