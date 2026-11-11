<template>
  <div class="flex h-[calc(100dvh_-_56px)] flex-col bg-[var(--color-bg)]">
    <!-- 顶部会话栏：身份、阶段、状态、计时 -->
    <header class="flex-none">
      <div class="mx-auto flex w-full max-w-[1200px] items-center gap-3 px-4 py-3 sm:px-6">
        <div class="flex min-w-0 items-center gap-2.5">
          <span
            class="flex h-9 w-9 flex-none items-center justify-center rounded-full bg-[var(--color-primary-bg)] text-[11px] font-semibold text-[var(--color-primary)]"
          >AI</span>
          <div class="min-w-0">
            <h1 class="page-title-anim truncate text-[14px] font-semibold text-[var(--color-text)]">AI 面试官</h1>
            <p class="truncate text-[11px] text-[var(--color-text-3)]">{{ position }}</p>
          </div>
        </div>

        <!-- 阶段进度：桌面端完整步骤条 -->
        <nav class="hidden flex-1 items-center justify-center gap-1 md:flex" aria-label="面试阶段">
          <template v-for="(s, i) in STAGES" :key="s.key">
            <span
              class="flex items-center gap-1.5 text-[11px] transition-colors"
              :class="
                i < stageIndex
                  ? 'text-[var(--color-success)]'
                  : i === stageIndex
                    ? 'font-medium text-[var(--color-primary)]'
                    : 'text-[var(--color-text-3)]'
              "
            >
              <span
                class="flex h-4 w-4 flex-none items-center justify-center rounded-full border text-[9px]"
                :class="
                  i < stageIndex
                    ? 'border-[var(--color-success)] bg-[var(--color-success)] text-[var(--color-bg)]'
                    : i === stageIndex
                      ? 'border-[var(--color-primary)] bg-[var(--color-primary)] text-[var(--color-bg)]'
                      : 'border-[var(--color-border-strong)] text-[var(--color-text-3)]'
                "
              >
                <Check v-if="i < stageIndex" :size="10" aria-hidden="true" />
                <span v-else>{{ i + 1 }}</span>
              </span>
              <span class="hidden lg:inline">{{ s.label }}</span>
            </span>
            <span v-if="i < STAGES.length - 1" class="h-px w-4 bg-[var(--color-border)] lg:w-6"></span>
          </template>
        </nav>

        <div class="ml-auto flex flex-none items-center gap-2">
          <span
            class="inline-flex items-center gap-1.5 rounded-full border border-[var(--color-border)] bg-[var(--color-surface)] px-2.5 py-1 text-[11px] text-[var(--color-text-2)]"
          >
            <span class="h-1.5 w-1.5 rounded-full" :class="statusDotClass"></span>
            {{ statusText }}
          </span>
          <span
            class="inline-flex items-center gap-1.5 rounded-full border border-[var(--color-border)] bg-[var(--color-surface)] px-2.5 py-1 text-[11px] tabular-nums text-[var(--color-text-2)]"
          >
            <Clock :size="12" class="text-[var(--color-text-3)]" aria-hidden="true" />
            {{ elapsed }}
          </span>
        </div>
      </div>

      <!-- 移动端：当前阶段 + 本题进度 -->
      <div class="flex items-center gap-3 px-4 py-2 md:hidden">
        <span class="flex-none text-[11px] text-[var(--color-text-3)]">{{ stageLabel || '准备中' }}</span>
        <span class="flex-none text-[11px] text-[var(--color-text-3)]">
          第 {{ questionInStage }}/{{ stageLimit }} 题 · 剩余 {{ remainingInStage }} 题
        </span>
        <div class="h-1 min-w-0 flex-1 overflow-hidden rounded-full bg-[var(--color-surface-muted)]">
          <div
            class="h-full rounded-full bg-[var(--color-primary)] transition-[width] duration-500"
            :style="{ width: stagePercent + '%' }"
          ></div>
        </div>
      </div>
    </header>

    <!-- 主区域：对话 + 自视图 -->
    <main class="mx-auto flex min-h-0 w-full max-w-[1200px] flex-1 flex-col gap-4 px-4 py-4 lg:flex-row lg:px-6">
      <!-- 对话区 -->
      <section
        class="page-card-anim order-2 flex min-h-0 flex-1 flex-col overflow-hidden rounded-[var(--radius-md)] border border-[var(--color-border)] bg-[var(--color-surface)] lg:order-1"
        style="animation-delay: 0.04s"
      >
        <!-- Agent 开场调研：可折叠，让「AI 自主检索资料」这件事可见 -->
        <details v-if="agentSteps.length" class="flex-none border-b border-[var(--color-border)] px-5 py-2.5">
          <summary
            class="cursor-pointer select-none text-[11px] text-[var(--color-text-3)] hover:text-[var(--color-text-2)]"
          >
            AI 面试官开场前检索了 {{ agentSteps.length }} 次资料，用于确定考察方向
          </summary>
          <ul class="mt-2 space-y-1.5">
            <li
              v-for="(step, i) in agentSteps"
              :key="i"
              class="flex flex-wrap items-center gap-1.5 text-[11px] text-[var(--color-text-2)]"
            >
              <span
                class="rounded-full bg-[var(--color-primary-bg)] px-2 py-0.5 text-[var(--color-primary)]"
              >{{ toolLabel(step.tool) }}</span>
              <span class="font-medium">「{{ step.keyword || '未指定' }}」</span>
              <span class="text-[var(--color-text-3)]">命中 {{ step.hits }} 条</span>
            </li>
          </ul>
        </details>

        <!-- 桌面端阶段信息 -->
        <div class="hidden flex-none items-center justify-between gap-4 border-b border-[var(--color-border)] px-5 py-3 md:flex">
          <div class="min-w-0">
            <p class="text-[11px] text-[var(--color-text-3)]">当前阶段</p>
            <p class="mt-0.5 truncate text-[14px] font-semibold text-[var(--color-text)]">{{ stageLabel || '准备中' }}</p>
          </div>
          <div class="flex-none text-right">
            <p class="text-[11px] text-[var(--color-text-3)]">本阶段进度</p>
            <p class="mt-0.5 text-[12px] font-medium text-[var(--color-text)]">
              第 {{ questionInStage }}/{{ stageLimit }} 题 · 剩余 {{ remainingInStage }} 题
            </p>
          </div>
        </div>
        <div class="hidden flex-none px-5 pt-3 md:block">
          <div class="h-1 overflow-hidden rounded-full bg-[var(--color-surface-muted)]">
            <div
              class="h-full rounded-full bg-[var(--color-primary)] transition-[width] duration-500"
              :style="{ width: stagePercent + '%' }"
            ></div>
          </div>
        </div>

        <!-- 对话 -->
        <div ref="chatEl" class="min-h-0 flex-1 overflow-y-auto px-4 py-4 sm:px-5">
          <div v-if="!messages.length" class="flex h-full min-h-[180px] flex-col items-center justify-center gap-2 text-center">
            <Loader2
              v-if="!online && !disconnected"
              :size="20"
              class="animate-spin text-[var(--color-text-3)] motion-reduce:animate-none"
              aria-hidden="true"
            />
            <TriangleAlert v-else-if="disconnected" :size="20" class="text-[var(--color-danger)]" aria-hidden="true" />
            <MessageSquare v-else :size="20" class="text-[var(--color-text-3)]" aria-hidden="true" />
            <p class="text-[12px] text-[var(--color-text-3)]">
              {{
                disconnected
                  ? '连接已断开，请刷新页面重新进入'
                  : online
                    ? 'AI 面试官正在准备第一个问题…'
                    : '正在连接面试房间…'
              }}
            </p>
          </div>

          <div v-else class="space-y-4">
            <div
              v-for="(m, i) in messages"
              :key="i"
              class="flex gap-2.5"
              :class="m.role === 'user' ? 'flex-row-reverse' : ''"
            >
              <span
                class="mt-0.5 flex h-7 w-7 flex-none items-center justify-center rounded-full bg-[var(--color-surface-muted)] text-[10px] font-semibold text-[var(--color-text-2)]"
              >
                {{ m.role === 'user' ? '我' : 'AI' }}
              </span>
              <div
                class="max-w-[82%] rounded-2xl px-3.5 py-2.5 text-[13px] leading-[1.7]"
                :class="
                  m.role === 'user'
                    ? 'rounded-tr-md bg-[var(--color-primary-bg)] text-[var(--color-text)]'
                    : 'rounded-tl-md border border-[var(--color-border)] bg-[var(--color-surface-muted)] text-[var(--color-text)]'
                "
              >
                {{ m.text }}
              </div>
            </div>

            <div v-if="thinking" class="flex gap-2.5">
              <span
                class="mt-0.5 flex h-7 w-7 flex-none items-center justify-center rounded-full bg-[var(--color-surface-muted)] text-[10px] font-semibold text-[var(--color-text-2)]"
              >AI</span>
              <div
                class="flex items-center gap-1.5 rounded-2xl rounded-tl-md bg-[var(--color-surface-muted)] px-3.5 py-3"
              >
                <span
                  v-for="n in 3"
                  :key="n"
                  class="h-1.5 w-1.5 animate-pulse rounded-full bg-[var(--color-text-3)] motion-reduce:animate-none"
                  :style="{ animationDelay: n * 0.15 + 's' }"
                ></span>
                <span class="ml-1 text-[11px] text-[var(--color-text-3)]">思考中</span>
              </div>
            </div>
          </div>
        </div>

        <!-- 输入区 -->
        <div class="flex-none border-t border-[var(--color-border)] px-3 py-3 sm:px-4">
          <div class="flex items-stretch gap-2">
              <div class="relative flex-none">
                <span
                v-if="isRecording"
                class="absolute inset-0 animate-ping rounded-full bg-[var(--color-danger)] opacity-25 motion-reduce:animate-none"
                aria-hidden="true"
              ></span>
              <button
                type="button"
                class="relative inline-flex h-11 items-center justify-center gap-1.5 rounded-full px-3 font-[inherit] text-[12px] font-medium transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--color-primary-light)] disabled:cursor-not-allowed disabled:opacity-40 sm:px-4"
                :class="isRecording
                  ? 'border border-[var(--color-danger-fill)] bg-[var(--color-danger-fill)] text-white'
                  : 'border border-[var(--color-border-strong)] bg-[var(--color-surface-muted)] text-[var(--color-text)]'"
                :disabled="micDisabled"
                :title="isRecording ? '结束录音' : '开始语音输入'"
                :aria-label="isRecording ? '结束录音' : '开始语音输入'"
                @click="onMic"
              >
                <component :is="isRecording ? Square : Mic" :size="16" :fill="isRecording ? 'currentColor' : 'none'" aria-hidden="true" />
                <span class="hidden leading-none sm:inline">{{ isRecording ? '结束' : '语音' }}</span>
              </button>
            </div>

            <input
              v-model="text"
              type="text"
              placeholder="输入回答，或点击语音按钮说话"
              class="h-11 min-w-0 flex-1 rounded-[var(--radius-sm)] border border-[var(--color-border-strong)] bg-[var(--color-surface)] px-3 font-[inherit] text-[13px] text-[var(--color-text)] placeholder:text-[var(--color-text-3)] focus:border-[var(--color-primary)] focus:outline-none focus:ring-2 focus:ring-[var(--color-primary-light)]"
              @keydown.enter="onEnter"
            />

            <BaseButton class="flex-none" :disabled="!text.trim()" @click="$emit('submitText')">
              <Send :size="15" aria-hidden="true" />
              <span class="hidden sm:inline">发送</span>
            </BaseButton>
          </div>

          <div class="mt-2 flex items-center justify-between gap-3">
            <span class="min-w-0 truncate text-[11px] text-[var(--color-text-3)]">{{ asrStatusText }}</span>
            <button
              type="button"
              class="button button--link button--danger flex-none text-[11px]"
              @click="$emit('endInterview')"
            >
              <PhoneOff :size="13" aria-hidden="true" />
              结束面试
            </button>
          </div>
        </div>
      </section>

      <!-- 自视图 / 语音状态 -->
      <aside class="order-1 flex-none lg:order-2 lg:w-[300px]">
        <div class="page-card-anim overflow-hidden rounded-[var(--radius-md)] border border-[var(--color-border)] bg-[var(--color-surface)]" style="animation-delay: 0.1s">
          <div class="relative h-[150px] bg-[#0f1b21] lg:h-auto lg:aspect-video">
            <video
              ref="selfViewEl"
              autoplay
              muted
              playsinline
              class="h-full w-full object-cover"
              :class="{ 'opacity-0': !cameraGranted }"
            ></video>

            <div
              v-if="!cameraGranted"
              class="absolute inset-0 flex flex-col items-center justify-center gap-2 text-white/60"
            >
              <VideoOff :size="24" aria-hidden="true" />
              <span class="text-[11px]">摄像头未开启</span>
            </div>

            <div class="absolute left-2.5 top-2.5 inline-flex items-center gap-1.5 rounded-full bg-black/50 px-2 py-0.5 text-[10px] text-white">
              <span class="h-1.5 w-1.5 rounded-full" :class="isRecording ? 'bg-[var(--color-success)]' : 'bg-white/40'"></span>
              你
            </div>

            <!-- 波形由父组件的音量循环写入 --amp / --level -->
            <div
              ref="waveEl"
              class="voice-wave absolute inset-x-0 bottom-0 flex h-10 items-end justify-center gap-[3px] px-2 pb-2"
              :class="{ active: waveActive }"
              aria-hidden="true"
            >
              <span
                v-for="n in WAVE_BARS"
                :key="n"
                class="bar"
                :style="{ animationDelay: (n - 1) * 0.05 + 's' }"
              ></span>
            </div>
          </div>

          <div class="flex items-center gap-2 border-t border-[var(--color-border)] px-3 py-2.5">
            <Mic
              :size="14"
              :class="isRecording ? 'text-[var(--color-danger)]' : 'text-[var(--color-text-3)]'"
              aria-hidden="true"
            />
            <span class="min-w-0 truncate text-[11px] text-[var(--color-text-3)]">{{ asrStatusText }}</span>
          </div>
        </div>

        <div class="page-card-anim mt-3 hidden rounded-[var(--radius-md)] border border-[var(--color-border)] bg-[var(--color-surface)] p-3 lg:block" style="animation-delay: 0.16s">
          <p class="text-[11px] text-[var(--color-text-3)]">面试提示</p>
          <p class="mt-1 text-[12px] leading-[1.6] text-[var(--color-text-2)]">
            回答时尽量说清背景、做法、取舍与结果；遇到追问可以停下来想一想。
          </p>
        </div>
      </aside>
    </main>
  </div>
</template>

<script setup>
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import { Check, Clock, Loader2, MessageSquare, Mic, PhoneOff, Send, Square, TriangleAlert, VideoOff } from 'lucide-vue-next'
import { attachVideo } from '@/utils/attachVideo'
import BaseButton from '@/components/BaseButton.vue'

const props = defineProps({
  position: { type: String, default: '' },
  messages: { type: Array, default: () => [] },
  currentStage: { type: String, default: '' },
  stageLabel: { type: String, default: '' },
  questionInStage: { type: Number, default: 1 },
  stageLimit: { type: Number, default: 1 },
  thinking: { type: Boolean, default: false },
  isRecording: { type: Boolean, default: false },
  asrReady: { type: Boolean, default: false },
  online: { type: Boolean, default: false },
  disconnected: { type: Boolean, default: false },
  cameraGranted: { type: Boolean, default: false },
  waveActive: { type: Boolean, default: false },
  asrStatusText: { type: String, default: '' },
  elapsed: { type: String, default: '00:00' },
  agentSteps: { type: Array, default: () => [] },
  localStream: { type: Object, default: null },
  textInput: { type: String, default: '' }
})

const emit = defineEmits(['update:textInput', 'toggleMic', 'submitText', 'endInterview'])

const WAVE_BARS = 20

const STAGES = [
  { key: 'overview', label: '履历概览' },
  { key: 'tech', label: '专业深挖' },
  { key: 'project', label: '项目拷问' },
  { key: 'scenario', label: '场景题' },
  { key: 'reverse', label: '反问' }
]

const text = computed({
  get: () => props.textInput,
  set: (value) => emit('update:textInput', value)
})

/** Agent 工具名 → 中文展示名 */
const TOOL_LABELS = {
  search_resume_materials: '检索简历素材',
  search_question_bank: '检索面试题库'
}
const toolLabel = (tool) => TOOL_LABELS[tool] || tool

const stageIndex = computed(() => STAGES.findIndex((s) => s.key === props.currentStage))

const stagePercent = computed(() => {
  if (!props.currentStage || props.stageLimit <= 0) return 0
  return Math.min(100, Math.max(0, Math.round((props.questionInStage / props.stageLimit) * 100)))
})

const remainingInStage = computed(() => {
  if (!props.currentStage) return 0
  return Math.max(0, props.stageLimit - props.questionInStage)
})

// 麦克风按需建立 ASR 会话，因此不依赖 asrReady；只要求连接正常且 AI 不在思考
const micDisabled = computed(() => props.thinking || !props.online)

const statusText = computed(() => {
  if (!props.online) return props.disconnected ? '连接已断开' : '正在连接'
  if (props.thinking) return 'AI 思考中'
  if (props.isRecording && !props.asrReady) return '语音连接中'
  if (props.isRecording) return '正在聆听'
  return '等待回答'
})

const statusDotClass = computed(() => {
  if (!props.online) return props.disconnected ? 'bg-[var(--color-danger)]' : 'bg-[var(--color-text-3)]'
  if (props.thinking) return 'bg-[var(--color-warning)]'
  if (props.isRecording) return 'bg-[var(--color-success)]'
  return 'bg-[var(--color-primary)]'
})

function onMic() {
  if (micDisabled.value) return
  emit('toggleMic')
}

function onEnter(event) {
  if (event.isComposing) return
  emit('submitText')
}

const chatEl = ref(null)
const selfViewEl = ref(null)
const waveEl = ref(null)

function scrollToBottom() {
  if (chatEl.value) chatEl.value.scrollTop = chatEl.value.scrollHeight
}

watch(
  () => [props.messages.length, props.thinking],
  async () => {
    await nextTick()
    scrollToBottom()
  }
)

watch(
  [() => props.localStream, () => props.cameraGranted],
  async ([stream, granted]) => {
    if (!stream || !granted) return
    await nextTick()
    attachVideo(selfViewEl.value, stream)
  },
  { immediate: true }
)

onMounted(async () => {
  await nextTick()
  scrollToBottom()
})

defineExpose({ waveEl })
</script>

<style scoped>
.voice-wave .bar {
  width: 3px;
  height: var(--amp, 5px);
  border-radius: 2px;
  background: rgba(255, 255, 255, 0.45);
  transform-origin: center bottom;
  transition: height 0.12s cubic-bezier(0.4, 0, 0.2, 1);
}

.voice-wave.active .bar {
  background: #fff;
  opacity: var(--level, 0.5);
  animation: waveLive 0.8s cubic-bezier(0.45, 0, 0.55, 1) infinite;
}

@keyframes waveLive {
  0%,
  100% {
    transform: scaleY(0.3);
  }
  50% {
    transform: scaleY(1);
  }
}

@media (prefers-reduced-motion: reduce) {
  .voice-wave.active .bar {
    animation: none;
  }
}
</style>