<template>
  <div class="min-h-[calc(100dvh_-_56px)] bg-[var(--color-bg)]">
    <div class="mx-auto w-full max-w-[1200px] px-6 py-6">
      <!-- 页头 -->
      <header class="page-header-band flex items-start justify-between gap-4">
        <div class="min-w-0">
          <h1 class="page-title page-title-anim text-[22px] font-semibold tracking-[-0.01em] text-[var(--color-text)]">
            {{ stage === 'permission' ? '面试前设备检查' : (incomingJdText ? '确认面试岗位' : '选择面试岗位') }}
          </h1>
          <p class="mt-2 max-w-2xl text-[13px] leading-[1.7] text-[var(--color-text-3)]">
            {{
              stage === 'permission'
                ? '确认摄像头与麦克风可用。面试画面仅本地预览，不会上传。'
                : incomingJdText
                  ? '已从「我的收藏」带入岗位与 JD，确认后进入设备检查。'
                  : 'AI 面试官会根据岗位方向与你的简历展开提问，优先考察工程取舍、踩坑复盘和方案对比。'
            }}
          </p>
        </div>
        <span
          class="hidden flex-none items-center gap-1.5 rounded-full border border-[var(--color-border)] bg-[var(--color-surface)] px-3 py-1.5 text-[11px] text-[var(--color-text-3)] sm:inline-flex"
        >
          <ShieldCheck :size="13" class="text-[var(--color-success)]" aria-hidden="true" />
          画面仅本地预览
        </span>
      </header>

      <!-- 岗位选择 -->
      <template v-if="stage === 'position'">
        <!-- 推荐岗位 -->
        <section
          v-if="!incomingJdText && recommendedPosition"
          class="page-card-anim mt-6 rounded-[var(--radius-md)] border border-[var(--color-primary-light)] bg-[var(--color-primary-bg)] px-4 py-3.5"
          style="animation-delay: 0.02s"
        >
          <div class="flex flex-wrap items-center gap-3">
            <span class="flex h-8 w-8 flex-none items-center justify-center rounded-full bg-[var(--color-surface)] text-[var(--color-primary)]">
              <Sparkles :size="15" aria-hidden="true" />
            </span>
            <div class="min-w-0">
              <p class="text-[11px] text-[var(--color-text-3)]">来自你的求职意向</p>
              <p class="truncate text-[14px] font-semibold text-[var(--color-text)]">{{ recommendedPosition }}</p>
            </div>
            <BaseButton variant="secondary" class="ml-auto flex-none" @click="select(recommendedPosition)">
              <Check v-if="selectedPosition === recommendedPosition" :size="13" aria-hidden="true" />
              {{ selectedPosition === recommendedPosition ? '已选择' : '选择' }}
            </BaseButton>
          </div>
        </section>

        <!-- 未完成面试 -->
        <section
          v-if="activeInterview"
          class="page-card-anim mt-4 flex flex-wrap items-center gap-3 rounded-[var(--radius-md)] border border-[var(--color-warning-border)] bg-[var(--color-warning-bg)] px-4 py-3"
          style="animation-delay: 0.06s"
        >
          <History :size="16" class="flex-none text-[var(--color-warning)]" aria-hidden="true" />
          <p class="min-w-0 flex-1 truncate text-[13px] text-[var(--color-text-2)]">
            上次面试未完成：<span class="font-medium text-[var(--color-text)]">{{ activeInterview.position }}</span>
          </p>
          <BaseButton variant="secondary" class="flex-none" @click="$emit('resume')">
            继续面试
            <ArrowRight :size="13" aria-hidden="true" />
          </BaseButton>
        </section>

        <!-- 热门职位 / 自定义岗位：从目标岗位带入时不再展示，避免重复选择 -->
        <template v-if="!incomingJdText">
        <section class="mt-8" aria-labelledby="positions-title">
          <div class="flex flex-wrap items-baseline justify-between gap-2">
            <h2 id="positions-title" class="text-[15px] font-semibold text-[var(--color-text)]">热门职位</h2>
            <span class="text-[12px] text-[var(--color-text-3)]">每个岗位对应不同的考察方向</span>
          </div>
          <div class="mt-3 grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
            <button
              v-for="(p, i) in positions"
              :key="p"
              type="button"
              class="page-card-anim group flex min-h-[124px] flex-col items-start rounded-[var(--radius-md)] border p-4 text-left transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--color-primary-light)]"
              :style="{ animationDelay: (0.04 + i * 0.06) + 's' }"
              :class="
                selectedPosition === p
                  ? 'border-[var(--color-primary)] bg-[var(--color-primary-bg)]'
                  : 'border-[var(--color-border)] bg-[var(--color-surface)] hover:border-[var(--color-border-strong)] hover:bg-[var(--color-surface-muted)]'
              "
              :aria-pressed="selectedPosition === p"
              @click="select(p)"
            >
              <span class="flex w-full items-start justify-between gap-3">
                <span class="text-[14px] font-semibold text-[var(--color-text)]">{{ p }}</span>
                <span
                  v-if="selectedPosition === p"
                  class="flex h-5 w-5 flex-none items-center justify-center rounded-full bg-[var(--color-primary)] text-[var(--color-bg)]"
                >
                  <Check :size="12" aria-hidden="true" />
                </span>
              </span>
              <span class="mt-1.5 text-[12px] leading-[1.5] text-[var(--color-text-3)]">{{ metaFor(p).focus }}</span>
              <span class="mt-auto flex flex-wrap gap-1.5 pt-3">
                <BaseTag v-for="t in metaFor(p).tags" :key="t">{{ t }}</BaseTag>
              </span>
            </button>
          </div>
        </section>

        <!-- 自定义职位 -->
        <section class="page-card-anim mt-5 rounded-[var(--radius-md)] border border-[var(--color-border)] bg-[var(--color-surface)] p-4" style="animation-delay: 0.28s">
          <h2 class="text-[14px] font-semibold text-[var(--color-text)]">没有合适的岗位？</h2>
          <p class="mt-1 text-[12px] leading-[1.6] text-[var(--color-text-3)]">
            输入目标岗位，AI 面试官会结合岗位名称、JD 与简历定向提问。
          </p>
          <div class="mt-3 flex flex-col gap-2 sm:flex-row">
            <input
              v-model="custom"
              type="text"
              placeholder="例如：Go 后端工程师"
              class="h-10 w-full min-w-0 rounded-[var(--radius-sm)] border border-[var(--color-border-strong)] bg-[var(--color-surface)] px-3 font-[inherit] text-[13px] text-[var(--color-text)] placeholder:text-[var(--color-text-3)] focus:border-[var(--color-primary)] focus:outline-none focus:ring-2 focus:ring-[var(--color-primary-light)]"
              @keydown.enter="onCustomEnter"
            />
            <BaseButton variant="secondary" class="flex-none" @click="$emit('useCustom')">
              <Plus :size="14" aria-hidden="true" />
              使用此岗位
            </BaseButton>
          </div>
        </section>
        </template>

        <!-- 已选与操作 -->
        <div class="mt-6 flex flex-col gap-4 border-t border-[var(--color-border)] pt-5 sm:flex-row sm:items-center sm:justify-between">
          <div class="min-w-0">
            <p class="text-[11px] text-[var(--color-text-3)]">{{ selectedPosition ? '已选择岗位' : '尚未选择岗位' }}</p>
            <p class="mt-1 truncate text-[14px] font-semibold text-[var(--color-text)]">
              {{ selectedPosition || '从上方选择一个岗位开始' }}
            </p>
            <p v-if="selectedPosition" class="mt-1 text-[12px] text-[var(--color-text-3)]">
              考察方向：{{ metaFor(selectedPosition).focus }}
            </p>
          </div>
          <div class="flex flex-none flex-col gap-2 sm:flex-row">
            <BaseButton variant="secondary" @click="$emit('openHistory')">
              <History :size="14" aria-hidden="true" />
              历史面试记录
            </BaseButton>
            <BaseButton :disabled="!selectedPosition" @click="$emit('next')">
              下一步
              <ArrowRight :size="14" aria-hidden="true" />
            </BaseButton>
          </div>
        </div>

        <p v-if="incomingJdText" class="mt-4 inline-flex items-center gap-1.5 text-[12px] text-[var(--color-success)]">
          <FileCheck2 :size="14" aria-hidden="true" />
          已带入该岗位 JD，AI 面试官将据此定向提问
        </p>
      </template>

      <!-- 设备检查 -->
      <template v-else>
        <div class="mt-6 grid gap-4 lg:grid-cols-[minmax(0,1fr)_300px]">
          <div class="page-card-anim relative aspect-video overflow-hidden rounded-[var(--radius-lg)] border border-[var(--color-border)] bg-[#0f1b21]" style="animation-delay: 0.02s">
            <video ref="videoEl" autoplay muted playsinline class="h-full w-full object-cover"></video>

            <div v-if="!cameraGranted" class="absolute inset-0 flex flex-col items-center justify-center gap-2 text-white/60">
              <VideoOff :size="28" aria-hidden="true" />
              <span class="text-[12px]">未开启摄像头</span>
            </div>
            <div
              v-else-if="!videoReady && !videoError"
              class="absolute inset-0 flex flex-col items-center justify-center gap-2 text-white/60"
            >
              <Loader2 :size="24" class="animate-spin motion-reduce:animate-none" aria-hidden="true" />
              <span class="text-[12px]">正在连接摄像头…</span>
            </div>
            <div v-if="videoError" class="absolute inset-x-0 bottom-0 bg-black/60 px-3 py-2 text-[12px] text-white">
              {{ videoError }}
            </div>

            <div class="absolute left-3 top-3 inline-flex items-center gap-1.5 rounded-full bg-black/50 px-2 py-1 text-[11px] text-white">
              <span class="h-1.5 w-1.5 rounded-full" :class="cameraGranted ? 'bg-[var(--color-success)]' : 'bg-white/40'"></span>
              本地预览
            </div>
          </div>

          <aside class="page-card-anim rounded-[var(--radius-md)] border border-[var(--color-border)] bg-[var(--color-surface)] p-4" style="animation-delay: 0.08s">
            <h2 class="text-[14px] font-semibold text-[var(--color-text)]">设备状态</h2>
            <ul class="mt-3 space-y-3">
              <li class="flex items-center gap-2.5 text-[13px]">
                <Camera
                  :size="16"
                  :class="cameraGranted ? 'text-[var(--color-success)]' : 'text-[var(--color-text-3)]'"
                  aria-hidden="true"
                />
                <span class="flex-1 text-[var(--color-text)]">摄像头</span>
                <span :class="cameraGranted ? 'text-[var(--color-success)]' : 'text-[var(--color-text-3)]'">
                  {{ cameraGranted ? '已授权' : '可选' }}
                </span>
              </li>
              <li class="flex items-center gap-2.5 text-[13px]">
                <Mic
                  :size="16"
                  :class="micGranted ? 'text-[var(--color-success)]' : 'text-[var(--color-text-3)]'"
                  aria-hidden="true"
                />
                <span class="flex-1 text-[var(--color-text)]">麦克风</span>
                <span :class="micGranted ? 'text-[var(--color-success)]' : 'text-[var(--color-text-3)]'">
                  {{ micUnavailable ? '不可用' : micGranted ? '已授权' : '必需' }}
                </span>
              </li>
            </ul>
            <p class="mt-3 text-[12px] leading-[1.6] text-[var(--color-text-3)]">
              <template v-if="micUnavailable">
                当前通过 HTTP 访问，浏览器禁止使用麦克风。可直接开始面试，用文字作答。
              </template>
              <template v-else>麦克风为必需项；摄像头可选，画面仅本地预览，不会上传。</template>
            </p>
            <BaseButton
              v-if="!micUnavailable"
              variant="secondary"
              class="mt-4 w-full"
              :disabled="requesting"
              @click="$emit('requestPermission')"
            >
              <RefreshCw :size="14" :class="{ 'animate-spin motion-reduce:animate-none': requesting }" aria-hidden="true" />
              {{ cameraGranted || micGranted ? '重新授权' : '请求授权' }}
            </BaseButton>
          </aside>
        </div>

        <div class="mt-6 flex flex-col gap-3 border-t border-[var(--color-border)] pt-5 sm:flex-row sm:items-center sm:justify-between">
          <BaseButton variant="secondary" @click="$emit('back')">
            <ArrowLeft :size="14" aria-hidden="true" />
            返回重选岗位
          </BaseButton>
          <BaseButton :disabled="(!micGranted && !micUnavailable) || requesting" @click="$emit('start')">
            开始面试
            <ArrowRight :size="14" aria-hidden="true" />
          </BaseButton>
        </div>
        <p v-if="!micGranted" class="mt-2 text-[12px] text-[var(--color-text-3)]">
          {{ micUnavailable ? '当前环境无法录音，进入后可打字作答。' : '授权麦克风后即可开始面试。' }}
        </p>
      </template>
    </div>
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue'
import {
  ArrowLeft,
  ArrowRight,
  Camera,
  Check,
  FileCheck2,
  History,
  Loader2,
  Mic,
  Plus,
  RefreshCw,
  ShieldCheck,
  Sparkles,
  VideoOff
} from 'lucide-vue-next'
import { attachVideo } from '@/utils/attachVideo'
import BaseTag from '@/components/BaseTag.vue'
import BaseButton from '@/components/BaseButton.vue'

const props = defineProps({
  stage: { type: String, default: 'position' },
  positions: { type: Array, default: () => [] },
  selectedPosition: { type: String, default: '' },
  recommendedPosition: { type: String, default: '' },
  customPosition: { type: String, default: '' },
  activeInterview: { type: Object, default: null },
  incomingJdText: { type: String, default: '' },
  localStream: { type: Object, default: null },
  cameraGranted: { type: Boolean, default: false },
  micGranted: { type: Boolean, default: false },
  /** 非 HTTPS 环境下浏览器禁用麦克风，改为纯文字作答 */
  micUnavailable: { type: Boolean, default: false },
  requesting: { type: Boolean, default: false }
})

const emit = defineEmits([
  'update:selectedPosition',
  'update:customPosition',
  'useCustom',
  'next',
  'back',
  'resume',
  'openHistory',
  'requestPermission',
  'start'
])

/** 岗位考察方向与标签：让用户选之前就知道会被问什么 */
const POSITION_META = {
  'Java 后端开发工程师': { focus: '工程取舍与系统稳定性', tags: ['并发', 'JVM', '数据库'] },
  '前端开发工程师': { focus: '体验实现与方案取舍', tags: ['性能', '工程化', '框架'] },
  '数据分析师': { focus: '指标体系与归因分析', tags: ['指标', 'SQL', '归因'] },
  '产品经理': { focus: '用户价值与优先级判断', tags: ['需求', '指标', '协作'] },
  '软件测试工程师': { focus: '风险识别与质量度量', tags: ['用例', '自动化', '质量'] },
  '算法工程师': { focus: '模型效果与成本权衡', tags: ['模型', '评估', '数据'] },
  'UI/UX 设计师': { focus: '设计取舍与可用性验证', tags: ['交互', '可用性', '视觉'] },
  '运维工程师': { focus: '稳定性、故障与成本', tags: ['SLO', '故障', '成本'] },
  '新媒体运营': { focus: '内容增长与 ROI 复盘', tags: ['内容', '增长', '复盘'] }
}

function metaFor(position) {
  return POSITION_META[position] || { focus: '结合 JD 与简历定向提问', tags: ['结合简历', '定向追问'] }
}

const custom = computed({
  get: () => props.customPosition,
  set: (value) => emit('update:customPosition', value)
})

function select(position) {
  emit('update:selectedPosition', position)
}

function onCustomEnter(event) {
  if (event.isComposing) return
  emit('useCustom')
}

// 设备检查页的视频预览：流由父组件授权后传入，这里只负责绑定与状态提示
const videoEl = ref(null)
const videoReady = ref(false)
const videoError = ref('')
let videoCheckTimer = null

watch(
  [() => props.localStream, () => props.stage],
  async ([stream, stage]) => {
    if (stage !== 'permission' || !stream) return
    await nextTick()
    const el = videoEl.value
    if (!el) return

    videoReady.value = false
    videoError.value = ''
    attachVideo(el, stream)

    el.addEventListener('playing', () => {
      videoReady.value = true
      videoError.value = ''
    }, { once: true })
    el.addEventListener('error', () => {
      videoReady.value = false
      videoError.value = '摄像头画面加载失败，可能被其他应用占用'
    }, { once: true })

    if (videoCheckTimer) window.clearTimeout(videoCheckTimer)
    videoCheckTimer = window.setTimeout(() => {
      if (props.cameraGranted && !videoReady.value && !videoError.value) {
        videoError.value = '摄像头无画面，请检查是否被其他应用占用或系统隐私设置'
      }
    }, 3000)
  },
  { immediate: true }
)

onBeforeUnmount(() => {
  if (videoCheckTimer) window.clearTimeout(videoCheckTimer)
})
</script>