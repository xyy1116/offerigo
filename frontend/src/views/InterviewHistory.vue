<template>
  <div class="history-panel w-full">
    <!-- 加载态：保持最终布局，用灰色块占位，不闪烁 -->
    <div v-if="loading" class="space-y-4" aria-busy="true" aria-label="正在加载面试记录">
      <div class="rounded-[var(--radius-md)] border border-[var(--color-border)] bg-[var(--color-surface)] p-4">
        <div class="h-2.5 w-20 rounded bg-[var(--color-surface-muted)]"></div>
        <div class="mt-3 h-7 w-24 rounded bg-[var(--color-surface-muted)]"></div>
        <div class="mt-3 h-10 w-full rounded bg-[var(--color-surface-muted)]"></div>
        <div class="mt-3 h-[164px] w-full rounded-[var(--radius-sm)] bg-[var(--color-surface-muted)]"></div>
      </div>
      <div class="space-y-3 pt-2">
        <div v-for="i in 3" :key="i" class="h-11 rounded bg-[var(--color-surface-muted)]"></div>
      </div>
    </div>

    <!-- 错误态：块内提示，不整屏弹窗 -->
    <div
      v-else-if="error"
      class="flex flex-col items-center gap-3 rounded-[var(--radius-md)] border border-[var(--color-border)] bg-[var(--color-surface)] px-6 py-12 text-center"
    >
      <AlertCircle :size="20" class="text-[var(--color-text-3)]" aria-hidden="true" />
      <div>
        <p class="m-0 text-[13px] font-medium text-[var(--color-text)]">面试记录加载失败</p>
        <p class="m-0 mt-1 text-[12px] leading-[1.6] text-[var(--color-text-3)]">请检查网络后重试。</p>
      </div>
      <button
        type="button"
        class="mt-1 inline-flex h-9 items-center justify-center rounded-[var(--radius-sm)] border border-[var(--color-border-strong)] bg-transparent px-4 font-[inherit] text-[13px] font-medium text-[var(--color-text)] transition-colors hover:bg-[var(--color-surface-muted)] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--color-primary-light)]"
        @click="loadRecords"
      >
        重试
      </button>
    </div>

    <template v-else-if="sortedRecords.length">
      <!-- 综合分趋势 -->
      <section
        class="rounded-[var(--radius-md)] border border-[var(--color-border)] bg-[var(--color-surface)] p-4"
        aria-labelledby="trend-title"
      >
        <div class="flex items-start justify-between gap-3">
          <div class="min-w-0">
            <h2 id="trend-title" class="m-0 text-[12px] font-medium text-[var(--color-text-3)]">综合分趋势</h2>
            <div class="mt-1.5 flex flex-wrap items-baseline gap-x-1.5 gap-y-1">
              <span
                v-if="latestScore !== null"
                class="font-[var(--font-display)] text-[30px] leading-none font-semibold tracking-[-0.02em] text-[var(--color-text)] tabular-nums"
              >{{ latestScore }}</span>
              <span v-else class="text-[20px] leading-none text-[var(--color-text-3)]">—</span>
              <span class="text-[12px] text-[var(--color-text-3)]">分</span>
              <span
                v-if="delta !== null && delta !== 0"
                class="ml-1.5 inline-flex items-center gap-1 text-[12px] font-medium"
                :class="delta > 0 ? 'text-[var(--color-success)]' : 'text-[var(--color-danger)]'"
                title="较上一次面试"
              >
                <component :is="delta > 0 ? TrendingUp : TrendingDown" :size="13" aria-hidden="true" />
                较上次 {{ delta > 0 ? '+' : '' }}{{ delta.toFixed(1) }}
              </span>
              <span v-else-if="delta === 0" class="ml-1.5 text-[12px] text-[var(--color-text-3)]">较上次持平</span>
            </div>
            <p class="m-0 mt-1.5 text-[11px] text-[var(--color-text-3)]">
              最近一次 · {{ formatTime(sortedRecords[0]?.createTime) }}
            </p>
          </div>
          <button
            type="button"
            class="inline-flex h-8 w-8 flex-none items-center justify-center rounded-[var(--radius-sm)] border-0 bg-transparent font-[inherit] text-[var(--color-text-3)] transition-colors hover:bg-[var(--color-surface-muted)] hover:text-[var(--color-text)] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--color-primary-light)] disabled:cursor-default disabled:opacity-60"
            :disabled="refreshing"
            aria-label="刷新面试记录"
            @click="loadRecords"
          >
            <RefreshCw :size="15" :class="{ 'animate-spin motion-reduce:animate-none': refreshing }" aria-hidden="true" />
          </button>
        </div>

        <dl class="m-0 mt-3 grid grid-cols-2 divide-x divide-[var(--color-border)] border-t border-[var(--color-border)] pt-3">
          <div class="pr-4">
            <dt class="text-[11px] text-[var(--color-text-3)]">平均分</dt>
            <dd class="m-0 mt-0.5 font-[var(--font-display)] text-[16px] font-medium text-[var(--color-text)] tabular-nums">
              {{ averageScore ?? '—' }}
            </dd>
          </div>
          <div class="pl-4">
            <dt class="text-[11px] text-[var(--color-text-3)]">面试场次</dt>
            <dd class="m-0 mt-0.5 font-[var(--font-display)] text-[16px] font-medium text-[var(--color-text)] tabular-nums">
              {{ sortedRecords.length }}
            </dd>
          </div>
        </dl>

        <div
          v-if="sortedRecords.length >= 2"
          ref="chartRef"
          class="mt-3 h-[164px] w-full"
          role="img"
          aria-label="综合分趋势折线图"
        ></div>
        <div
          v-else
          class="mt-3 flex h-[96px] items-center justify-center rounded-[var(--radius-sm)] bg-[var(--color-surface-muted)] text-[12px] text-[var(--color-text-3)]"
        >
          完成两次面试后展示趋势
        </div>
      </section>

      <!-- 面试记录 -->
      <section class="mt-6" aria-labelledby="records-title">
        <div class="mb-2 flex items-baseline justify-between">
          <h2 id="records-title" class="m-0 text-[14px] font-semibold text-[var(--color-text)]">面试记录</h2>
          <div class="flex items-baseline gap-3">
            <span class="text-[12px] text-[var(--color-text-3)]">{{ sortedRecords.length }} 场</span>
            <button
              type="button"
              class="border-0 bg-transparent p-0 font-[inherit] text-[12px] font-medium text-[var(--color-primary)] hover:underline focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--color-primary-light)]"
              @click="goProfile"
            >
              查看全部
            </button>
          </div>
        </div>

        <table class="w-full table-fixed border-collapse text-left">
          <colgroup>
            <col />
            <col class="w-[64px]" />
            <col class="w-[60px]" />
          </colgroup>
          <thead>
            <tr class="text-[12px] text-[var(--color-text-3)]">
              <th scope="col" class="pb-2 text-left font-normal">岗位</th>
              <th scope="col" class="pb-2 text-right font-normal">综合分</th>
              <th scope="col" class="pb-2">
                <span class="sr-only">操作</span>
              </th>
            </tr>
          </thead>
          <tbody>
            <tr
              v-for="row in sortedRecords"
              :key="row.id"
              class="border-t border-[var(--color-border)] transition-colors hover:bg-[var(--color-surface-muted)]"
            >
              <td class="py-3 pr-2">
                <div class="truncate text-[13px] font-medium text-[var(--color-text)]">{{ row.position }}</div>
                <div class="mt-0.5 text-[11px] text-[var(--color-text-3)]">{{ formatTime(row.createTime) }}</div>
              </td>
              <td class="py-3 text-right align-middle">
                <span
                  v-if="fmtScore(row.score) !== null"
                  class="font-[var(--font-display)] text-[17px] font-semibold text-[var(--color-text)] tabular-nums"
                >{{ fmtScore(row.score) }}</span>
                <span v-else class="text-[13px] text-[var(--color-text-3)]">—</span>
              </td>
              <td class="py-3 text-right align-middle">
                <button
                  type="button"
                  class="inline-flex h-9 items-center gap-0.5 rounded-[var(--radius-sm)] border-0 bg-transparent px-2 font-[inherit] text-[12px] font-medium text-[var(--color-text-2)] transition-colors hover:bg-[var(--color-surface-muted)] hover:text-[var(--color-text)] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--color-primary-light)]"
                  :aria-label="`查看 ${row.position} 的面试评估`"
                  @click="openDetail(row)"
                >
                  查看
                  <ChevronRight :size="14" aria-hidden="true" />
                </button>
              </td>
            </tr>
          </tbody>
        </table>
      </section>
    </template>

    <!-- 空态：保留边框和信息，不画假数据 -->
    <div
      v-else
      class="flex flex-col items-center gap-3 rounded-[var(--radius-md)] border border-[var(--color-border)] bg-[var(--color-surface)] px-6 py-12 text-center"
    >
      <History :size="20" class="text-[var(--color-text-3)]" aria-hidden="true" />
      <div>
        <p class="m-0 text-[13px] font-medium text-[var(--color-text)]">还没有面试记录</p>
        <p class="m-0 mt-1 text-[12px] leading-[1.6] text-[var(--color-text-3)]">
          完成一次 AI 面试后，这里会显示评估和趋势。
        </p>
      </div>
      <button
        type="button"
        class="mt-1 inline-flex h-9 items-center justify-center rounded-[var(--radius-sm)] border-0 bg-[var(--color-primary)] px-4 font-[inherit] text-[13px] font-medium text-white transition-colors hover:bg-[var(--color-primary-mid)] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--color-primary-light)]"
        @click="$emit('start')"
      >
        开始一场面试
      </button>
    </div>

    <!-- 单场面试评估详情 -->
    <el-dialog
      v-model="detailVisible"
      width="640px"
      align-center
      append-to-body
      :show-close="false"
      class="history-detail-dialog"
      aria-label="面试评估详情"
      @closed="detail = null"
    >
      <template #header>
        <div class="flex items-start justify-between gap-4">
          <div class="min-w-0">
            <h2 class="m-0 truncate text-[17px] font-semibold text-[var(--color-text)]">
              {{ detail?.position || '面试评估' }}
            </h2>
            <p class="m-0 mt-1 text-[12px] text-[var(--color-text-3)]">{{ formatTime(detail?.createTime) }}</p>
          </div>
          <button
            type="button"
            class="-mr-1 -mt-1 inline-flex h-8 w-8 flex-none items-center justify-center rounded-[var(--radius-sm)] border-0 bg-transparent font-[inherit] text-[var(--color-text-3)] transition-colors hover:bg-[var(--color-surface-muted)] hover:text-[var(--color-text)] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--color-primary-light)]"
            aria-label="关闭"
            @click="detailVisible = false"
          >
            <X :size="16" aria-hidden="true" />
          </button>
        </div>
      </template>

      <template v-if="detail">
        <InterviewReportDetail :report="detail.report" :score="detail.score" />
      </template>

      <template #footer>
        <button
          type="button"
          class="inline-flex h-9 items-center justify-center rounded-[var(--radius-sm)] border border-[var(--color-border-strong)] bg-[var(--color-surface)] px-4 font-[inherit] text-[13px] font-medium text-[var(--color-text)] transition-colors hover:bg-[var(--color-surface-muted)] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--color-primary-light)]"
          @click="detailVisible = false"
        >
          关闭
        </button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onBeforeUnmount, nextTick, watch } from 'vue'
import { useRouter } from 'vue-router'
import * as echarts from 'echarts'
import { RefreshCw, TrendingUp, TrendingDown, ChevronRight, History, AlertCircle, X } from 'lucide-vue-next'
import { getInterviewHistory, getInterviewDetail } from '@/api/interview'
import InterviewReportDetail from '@/components/InterviewReportDetail.vue'
import { resolvedTheme } from '@/utils/theme.ts'

defineEmits(['start'])

const records = ref([])
const loading = ref(true)
const error = ref(false)
const refreshing = ref(false)
const chartRef = ref(null)
let chart = null

const detailVisible = ref(false)
const detail = ref(null)
const router = useRouter()

function goProfile() {
  router.push('/profile')
}

/** 兼容字符串 / 数组 / 时间戳等多种 LocalDateTime 序列化结果 */
function toDate(t) {
  if (!t) return null
  if (Array.isArray(t)) {
    return new Date(t[0], (t[1] || 1) - 1, t[2] || 1, t[3] || 0, t[4] || 0, t[5] || 0)
  }
  const d = new Date(String(t).replace(' ', 'T'))
  return Number.isNaN(d.getTime()) ? null : d
}

function formatTime(t) {
  const d = toDate(t)
  if (!d) return '--'
  const p = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}`
}

function formatShortDate(t) {
  const d = toDate(t)
  if (!d) return '--'
  return `${d.getMonth() + 1}/${d.getDate()}`
}

function scoreNum(v) {
  const n = Number(v)
  return Number.isFinite(n) ? n : null
}

function fmtScore(v) {
  const n = scoreNum(v)
  return n === null ? null : n.toFixed(1)
}

/** 接口按时间倒序返回，这里再排一次，避免依赖后端顺序 */
const sortedRecords = computed(() =>
  [...records.value].sort((a, b) => {
    const ta = toDate(a.createTime)?.getTime() || 0
    const tb = toDate(b.createTime)?.getTime() || 0
    return tb - ta
  })
)

const latestScoreRaw = computed(() => scoreNum(sortedRecords.value[0]?.score))
const latestScore = computed(() => {
  const n = latestScoreRaw.value
  return n === null ? null : n.toFixed(1)
})

const delta = computed(() => {
  const latest = latestScoreRaw.value
  const previous = scoreNum(sortedRecords.value[1]?.score)
  if (latest === null || previous === null) return null
  return latest - previous
})

const averageScore = computed(() => {
  const nums = sortedRecords.value.map((r) => scoreNum(r.score)).filter((n) => n !== null)
  if (!nums.length) return null
  return (nums.reduce((sum, n) => sum + n, 0) / nums.length).toFixed(1)
})

function cssVar(name, fallback) {
  const value = getComputedStyle(document.documentElement).getPropertyValue(name).trim()
  return value || fallback
}

function renderChart() {
  if (!chart || sortedRecords.value.length < 2) return
  const rows = [...sortedRecords.value].reverse()
  const labels = rows.map((r) => formatShortDate(r.createTime))
  const fullLabels = rows.map((r) => formatTime(r.createTime))
  const values = rows.map((r) => scoreNum(r.score))
  const lastIndex = values.reduce((acc, v, i) => (v !== null ? i : acc), -1)

  const dark = resolvedTheme.value === 'dark'
  const lineColor = cssVar('--color-primary', '#17505c')
  const surface = cssVar('--color-surface', '#ffffff')
  const border = cssVar('--color-border', '#dfe7ec')
  const borderStrong = cssVar('--color-border-strong', '#c9d6df')
  const text = cssVar('--color-text', '#16202a')
  const text3 = cssVar('--color-text-3', '#5f6c77')
  const areaColor = dark ? 'rgba(94,200,214,0.14)' : 'rgba(31,95,107,0.08)'
  const tooltipShadow = dark ? '0 6px 18px rgba(0,0,0,.45)' : '0 6px 18px rgba(21,36,43,.08)'

  chart.setOption(
    {
      // 应用界面不做入场动画，数据直接呈现
      animation: false,
      grid: { left: 2, right: 8, top: 12, bottom: 4, containLabel: true },
      tooltip: {
        trigger: 'axis',
        backgroundColor: surface,
        borderColor: border,
        borderWidth: 1,
        padding: [8, 10],
        textStyle: { color: text, fontSize: 12 },
        extraCssText: `box-shadow: ${tooltipShadow}; border-radius: 8px;`,
        axisPointer: { type: 'line', lineStyle: { color: borderStrong, width: 1 } },
        formatter: (params) => {
          const p = Array.isArray(params) ? params.find((item) => item.seriesIndex === 0) : params
          if (!p) return ''
          const n = Number(p.value)
          const when = fullLabels[p.dataIndex] || p.axisValue
          return `<div style="font-size:11px;color:${text3};margin-bottom:2px">${when}</div>
                  <div style="font-size:13px;font-weight:600;color:${text}">${Number.isFinite(n) ? n.toFixed(1) : '—'} 分</div>`
        }
      },
      xAxis: {
        type: 'category',
        boundaryGap: false,
        data: labels,
        axisLine: { show: false },
        axisTick: { show: false },
        axisLabel: { color: text3, fontSize: 11, margin: 8, hideOverlap: true }
      },
      yAxis: {
        type: 'value',
        min: 0,
        max: 100,
        splitNumber: 2,
        axisLine: { show: false },
        axisTick: { show: false },
        axisLabel: { color: text3, fontSize: 11, margin: 6 },
        splitLine: { lineStyle: { color: border, width: 1 } }
      },
      series: [
        {
          name: '综合分',
          type: 'line',
          data: values,
          smooth: false,
          showSymbol: false,
          connectNulls: false,
          lineStyle: { width: 2, color: lineColor },
          itemStyle: { color: lineColor },
          areaStyle: { color: areaColor }
        },
        // 只标出最近一次，避免每个点都长一个标记
        {
          name: '最近一次',
          type: 'line',
          data: values.map((v, i) => (i === lastIndex ? v : null)),
          showSymbol: true,
          symbol: 'circle',
          symbolSize: 6,
          silent: true,
          tooltip: { show: false },
          lineStyle: { width: 0 },
          itemStyle: { color: lineColor, borderColor: surface, borderWidth: 2 }
        }
      ]
    },
    true
  )
}

function onResize() {
  chart && chart.resize()
}

async function loadRecords() {
  refreshing.value = true
  error.value = false
  try {
    const res = await getInterviewHistory()
    records.value = res.data || []
  } catch (e) {
    error.value = true
    records.value = []
  } finally {
    loading.value = false
    refreshing.value = false
  }

  await nextTick()
  if (sortedRecords.value.length >= 2 && chartRef.value) {
    if (!chart) chart = echarts.init(chartRef.value)
    renderChart()
    chart.resize()
  } else if (chart) {
    chart.dispose()
    chart = null
  }
}

async function openDetail(row) {
  try {
    const res = await getInterviewDetail(row.id)
    detail.value = res.data
    detailVisible.value = true
  } catch (e) {
    // 错误已由响应拦截器统一提示
  }
}

onMounted(async () => {
  window.addEventListener('resize', onResize)
  await loadRecords()
})

watch(resolvedTheme, () => {
  if (chart) renderChart()
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', onResize)
  if (chart) {
    chart.dispose()
    chart = null
  }
})
</script>

<style>
/* 项目关闭了 Tailwind preflight，这里补回组件内必需的盒模型。
   详情弹窗 teleport 到 body，样式用唯一类名限定，不做全局泛化。 */
.history-panel,
.history-panel *,
.history-panel *::before,
.history-panel *::after,
.history-detail-dialog,
.history-detail-dialog *,
.history-detail-dialog *::before,
.history-detail-dialog *::after {
  box-sizing: border-box;
}

/* Lucide 统一线宽，避免小尺寸图标显得过重 */
.history-panel svg.lucide,
.history-detail-dialog svg.lucide {
  stroke-width: 1.75;
}

.history-detail-dialog.el-dialog {
  max-width: calc(100vw - 32px);
  border-radius: var(--radius-lg);
}
.history-detail-dialog .el-dialog__header {
  margin-right: 0;
  padding: 20px 20px 0;
}
.history-detail-dialog .el-dialog__body {
  max-height: 70vh;
  overflow-y: auto;
  padding: 0 20px 4px;
}
.history-detail-dialog .el-dialog__footer {
  padding: 12px 20px 20px;
}
</style>