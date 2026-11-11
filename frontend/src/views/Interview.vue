<template>
  <div class="min-h-full">
    <!-- 岗位选择 / 设备检查 -->
    <InterviewPositionPage
      v-if="stage === 'position' || stage === 'permission'"
      :stage="stage"
      v-model:selected-position="selectedPosition"
      v-model:custom-position="customPosition"
      :positions="positions"
      :recommended-position="recommendedPosition"
      :active-interview="activeInterview"
      :incoming-jd-text="incomingJdText"
      :local-stream="localStream"
      :camera-granted="cameraGranted"
      :mic-granted="micGranted"
      :requesting="requesting"
      @use-custom="useCustomPosition"
      @next="stage = 'permission'"
      @back="stage = 'position'"
      @resume="resumeInterview"
      @open-history="openHistory"
      @request-permission="requestPermission"
      @start="startInterview"
    />

    <!-- 面试对话 -->
    <InterviewRoomPage
      v-else-if="stage === 'interview'"
      ref="roomPageRef"
      :position="selectedPosition"
      :messages="messages"
      :current-stage="currentStage"
      :stage-label="stageLabel"
      :question-in-stage="questionInStage"
      :stage-limit="stageLimit"
      :thinking="thinking"
      :is-recording="isRecording"
      :asr-ready="asrReady"
      :online="online"
      :disconnected="disconnected"
      :camera-granted="cameraGranted"
      :wave-active="waveActive"
      :asr-status-text="asrStatusText"
      :elapsed="elapsedText"
      :agent-steps="agentSteps"
      :local-stream="localStream"
      v-model:text-input="textInput"
      @toggle-mic="toggleMic"
      @submit-text="submitText"
      @end-interview="endInterview"
    />

    <!-- 面试评估报告弹窗（居中模态） -->
    <el-dialog
      v-model="reportDialogVisible"
      width="680px"
      align-center
      class="report-dialog"
      :close-on-click-modal="false"
      :close-on-press-escape="false"
      :show-close="false"
    >
      <template #header>
        <div class="rd-header">
          <span class="rd-title">面试评估报告</span>
          <span class="rd-position">{{ report?.position || selectedPosition }}</span>
        </div>
      </template>

      <div v-if="report" class="report-body">
        <div class="rd-top">
          <div class="rd-score-wrap">
            <el-progress type="dashboard" :percentage="score || 0" :width="150" :color="scoreColor">
              <template #default>
                <div class="rd-score-num">{{ displayScore }}</div>
                <div class="rd-score-label">综合得分</div>
              </template>
            </el-progress>
            <span class="rating-badge" :class="ratingClass">{{ report.level || ratingText }}</span>
          </div>
          <div class="rd-basic">
            <div class="rd-basic-item"><span class="k">面试岗位</span><span class="v">{{ report.position || selectedPosition }}</span></div>
            <div class="rd-basic-item"><span class="k">面试时长</span><span class="v">{{ report.durationMinutes }} 分钟</span></div>
            <div class="rd-basic-item"><span class="k">问题总数</span><span class="v">{{ report.questionCount }} 题</span></div>
          </div>
        </div>

        <p class="rd-summary">{{ report.summary }}</p>

        <h4 class="rd-section-title">分维度评分（每项 25 分）</h4>
        <div class="rd-dims">
          <div v-for="d in dimensions" :key="d.name" class="rd-dim">
            <div class="rd-dim-head">
              <span class="rd-dim-name">{{ d.name }}</span>
              <span class="rd-dim-score">{{ d.score }}<i>/25</i></span>
            </div>
            <el-progress
              :percentage="dimPercent(d.score)"
              :show-text="false"
              :stroke-width="8"
              :color="dimColor(d.score)"
            />
            <p v-if="d.comment" class="rd-dim-comment">{{ d.comment }}</p>
          </div>
        </div>

        <div class="rd-lists">
          <div class="rd-list rd-strength">
            <h4 class="rd-section-title"><Star :size="15" aria-hidden="true" /> 核心优势</h4>
            <ul>
              <li v-for="(s, i) in report.strengths || []" :key="i">{{ s }}</li>
            </ul>
          </div>
          <div class="rd-list rd-weak">
            <h4 class="rd-section-title"><TriangleAlert :size="15" aria-hidden="true" /> 待提升点</h4>
            <ul>
              <li v-for="(s, i) in report.weaknesses || []" :key="i">{{ s }}</li>
            </ul>
          </div>
        </div>
      </div>

      <template #footer>
        <div class="rd-footer">
          <button class="button button--default" @click="goExamFromReport">跳转笔试刷题</button>
          <button class="button button--success" @click="goJobMatchFromReport">岗位匹配</button>
          <button class="button button--plain" @click="reset">再来一次</button>
          <button class="button" @click="finishInterview">完成面试</button>
        </div>
      </template>
    </el-dialog>

    <!-- 历史记录抽屉：默认隐藏，点击「历史面试记录」按钮后从右侧滑出 -->
    <el-drawer v-model="historyVisible" direction="rtl" size="min(480px, 92vw)" :show-close="false">
      <template #header>
        <div class="flex w-full items-center justify-between gap-3">
          <h2 class="m-0 text-[16px] font-semibold text-[var(--color-text)]">历史面试记录</h2>
          <button
            type="button"
            class="inline-flex h-8 w-8 items-center justify-center rounded-[var(--radius-sm)] text-[var(--color-text-3)] transition-colors hover:bg-[var(--color-surface-muted)] hover:text-[var(--color-text)] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--color-primary-light)]"
            aria-label="关闭"
            @click="historyVisible = false"
          >
            <X :size="18" aria-hidden="true" />
          </button>
        </div>
      </template>
      <InterviewHistory v-if="historyVisible" @start="historyVisible = false" />
    </el-drawer>
  </div>
</template>

<script setup>
import { ref, computed, nextTick, onBeforeUnmount, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Star, TriangleAlert, X } from 'lucide-vue-next'
import { getToken } from '@/utils/token'
import { getResume } from '@/api/resume'
import { getActiveInterview } from '@/api/interview'
import { useLinkageStore } from '@/stores/linkage'
import { MicRecorder } from '@/utils/audioStream'
import InterviewHistory from '@/views/InterviewHistory.vue'
import InterviewPositionPage from '@/views/InterviewPositionPage.vue'
import InterviewRoomPage from '@/views/InterviewRoomPage.vue'

const router = useRouter()
const linkage = useLinkageStore()

const positions = [
  'Java 后端开发工程师',
  '前端开发工程师',
  '数据分析师',
  '产品经理',
  '软件测试工程师',
  '算法工程师',
  'UI/UX 设计师',
  '运维工程师',
  '新媒体运营'
]

const stage = ref('position')
const selectedPosition = ref('')
const recommendedPosition = ref('')
const customPosition = ref('')
const historyVisible = ref(false)
const incomingJdText = ref('')
const incomingResumeText = ref('')
const incomingTechStack = ref('')
/** 进行中的面试记录（中断续接入口），null 表示无未完成面试 */
const activeInterview = ref(null)
/** 是否以续接模式接入 WebSocket（发 resume 而非 start） */
const pendingResume = ref(false)
/** 报告弹窗可见性 */
const reportDialogVisible = ref(false)

const localStream = ref(null)
const cameraGranted = ref(false)
const micGranted = ref(false)
const requesting = ref(false)
/** 面试对话页实例：用于取波形容器 */
const roomPageRef = ref(null)
/** 本场面试计时 */
const elapsedSeconds = ref(0)
let elapsedTimer = null

const ws = ref(null)
let reconnectAttempts = 0
let reconnectTimer = null
let stableTimer = null
let closingIntentionally = false
const recorder = ref(null)
const asrReady = ref(false)
/** ASR 失败时的降级提示（不弹阻塞 toast） */
const asrIssue = ref('')
/** 停止录音后延迟关闭 ASR，等待最后一句识别结果 */
let micStopTimer = null
const online = ref(false)
const disconnected = ref(false)
const thinking = ref(false)
const userSpeaking = ref(false)
/** 是否正在录音（用户点击麦克风按钮手动开启/关闭） */
const isRecording = ref(false)
/** Web Speech 本地识别器实例（双引擎之「本地引擎」，实时转写） */
let recognition = null
/** Web Speech 已确认的最终文本（累积），与 interim 拼接实时填充输入框 */
let finalSpeech = ''
/** 是否处于「语音识别进行中」状态：仅当语音识别就绪、且用户手动开启录音、且 AI 非思考期时为真 */
const listening = computed(() => asrReady.value && !thinking.value && isRecording.value)

const elapsedText = computed(() => {
  const m = Math.floor(elapsedSeconds.value / 60)
  const s = elapsedSeconds.value % 60
  return `${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`
})

const messages = ref([])
/** Agent 开场调研过程：每次工具调用一条，展示「查了什么、查到几条」 */
const agentSteps = ref([])
/** 当前面试阶段标识（overview/tech/project/scenario/reverse） */
const currentStage = ref('')
/** 当前阶段中文名（履历概览/专业深挖/项目拷问/场景题/反问环节） */
const stageLabel = ref('')
/** 当前阶段内已进行到第几题 */
const questionInStage = ref(1)
/** 当前阶段问题总数上限 */
const stageLimit = ref(1)

const report = ref(null)
const score = ref(null)
/** 综合得分 count-up 动画值（进入评分页时从 0 滚动至真实分数） */
const animatedScore = ref(0)

const textInput = ref('')

/** 报告各维度评分明细 */
const dimensions = computed(() => report.value?.dimensions || [])

/** 波形可视化循环句柄与平滑音量 */
let waveRaf = null
let smoothedLevel = 0

/** 回答超时：面试官提问后长时间未回答，温和提醒（最多两次） */
const ANSWER_TIMEOUT_MS = 30000
let answerTimer = null
let nudgeCount = 0

/** 服务端消息时间戳：用于乱序/过期状态消息的时序同步 */
let lastServerTs = 0

/** 综合得分 count-up 动画帧句柄 */
let scoreRaf = null

const scoreColor = computed(() => {
  const s = score.value || 0
  if (s >= 80) return 'var(--color-lilac)'
  if (s >= 60) return 'var(--color-warning)'
  return 'var(--color-danger)'
})

/** 评分页展示的分数：由 count-up 动画驱动 */
const displayScore = computed(() => {
  if (score.value == null) return '--'
  return animatedScore.value
})

/** 综合评级文案与等级配色 */
const ratingText = computed(() => {
  const s = score.value || 0
  if (s >= 85) return '优秀'
  if (s >= 70) return '良好'
  if (s >= 60) return '中等'
  return '待提升'
})
const ratingClass = computed(() => {
  const s = score.value || 0
  if (s >= 85) return 'rating-excellent'
  if (s >= 70) return 'rating-good'
  if (s >= 60) return 'rating-mid'
  return 'rating-low'
})

/** 维度得分归一化为 0-100（每项满分 25 分） */
function dimPercent(score) {
  return Math.max(0, Math.min(100, Math.round((Number(score) || 0) / 25 * 100)))
}

/** 维度进度条颜色：按得分高低分档着色 */
function dimColor(score) {
  const s = Number(score) || 0
  if (s >= 20) return 'var(--color-lilac)'
  if (s >= 12) return 'var(--color-warning)'
  return 'var(--color-danger)'
}

const asrStatusText = computed(() => {
  if (thinking.value) return 'AI 面试官思考中…'
  if (asrIssue.value) return asrIssue.value
  if (isRecording.value && !asrReady.value) return '语音识别连接中…'
  if (isRecording.value) return '正在聆听并实时转写…'
  return '点击麦克风开始语音输入，或直接打字'
})

/** 波形是否激活：仅用户正在说话且 AI 非思考期时激活（排除环境噪声与思考期） */
const waveActive = computed(() => userSpeaking.value && !thinking.value)

/** 综合得分 count-up：从 0 缓动滚动至目标分数 */
function animateScore(target) {
  if (scoreRaf) cancelAnimationFrame(scoreRaf)
  const t = Number(target) || 0
  const duration = 1200
  const start = performance.now()
  const tick = (now) => {
    const p = Math.min(1, (now - start) / duration)
    const eased = 1 - Math.pow(1 - p, 3)
    animatedScore.value = Math.round(t * eased)
    if (p < 1) scoreRaf = requestAnimationFrame(tick)
  }
  scoreRaf = requestAnimationFrame(tick)
}

async function requestPermission() {
  requesting.value = true
  // 重新授权时先释放旧设备，避免同时占用摄像头/麦克风
  if (localStream.value) {
    localStream.value.getTracks().forEach((t) => t.stop())
    localStream.value = null
  }
  try {
    const stream = await navigator.mediaDevices.getUserMedia({ video: true, audio: true })
    localStream.value = stream
    cameraGranted.value = true
    micGranted.value = true
  } catch (e) {
    cameraGranted.value = false
    try {
      const audioStream = await navigator.mediaDevices.getUserMedia({
        audio: { echoCancellation: true, noiseSuppression: true }
      })
      localStream.value = new MediaStream(audioStream.getAudioTracks())
      micGranted.value = true
      cameraGranted.value = false
      ElMessage.warning('摄像头不可用，已降级为纯语音面试')
    } catch (e2) {
      ElMessage.error('请授予麦克风权限以进行语音面试')
    }
  } finally {
    requesting.value = false
  }
}

onMounted(async () => {
  // 监听前台/后台切换：切后台立即终止识别，避免后台继续采集
  document.addEventListener('visibilitychange', onVisibilityChange)

  // 跨模块联动：从匹配分析 / 简历编辑器 / 笔试报告带入岗位、JD、技术栈与简历上下文
  const ctx = linkage.consume()
  if (ctx.jobTitle) {
    selectedPosition.value = ctx.jobTitle
  }
  if (ctx.jdText) incomingJdText.value = ctx.jdText
  if (ctx.resumeText) incomingResumeText.value = ctx.resumeText
  if (ctx.techStack) incomingTechStack.value = ctx.techStack

  try {
    const res = await getActiveInterview()
    if (res.data) activeInterview.value = res.data
  } catch (e) {
    // 忽略：接口异常时仅不展示续接入口，不影响正常面试
  }

  try {
    const res = await getResume()
    const target = res.data?.careerGoal?.targetPosition
    if (target && target.trim()) {
      recommendedPosition.value = target.trim()
    }
  } catch (e) {
    // 未填写求职意向时忽略，仅展示热门职位
  }
})

function useCustomPosition() {
  const v = customPosition.value.trim()
  if (!v) {
    ElMessage.warning('请输入岗位名称')
    return
  }
  selectedPosition.value = v
}

function openHistory() {
  historyVisible.value = true
}

/** 继续上次未完成的面试：带入岗位并进入授权页，WebSocket 就绪后发 resume */
function resumeInterview() {
  if (!activeInterview.value) return
  selectedPosition.value = activeInterview.value.position
  pendingResume.value = true
  stage.value = 'permission'
}

function startElapsedTimer() {
  stopElapsedTimer()
  elapsedSeconds.value = 0
  elapsedTimer = window.setInterval(() => {
    elapsedSeconds.value += 1
  }, 1000)
}

function stopElapsedTimer() {
  if (elapsedTimer) {
    window.clearInterval(elapsedTimer)
    elapsedTimer = null
  }
}

function startInterview() {
  stage.value = 'interview'
  agentSteps.value = []
  startElapsedTimer()
  connectWs()
  nextTick(() => startWaveLoop())
}

function connectWs() {
  const token = getToken()
  if (!token) {
    ElMessage.error('登录已过期，请重新登录后再开始面试')
    stage.value = 'position'
    return
  }
  closingIntentionally = false
  if (reconnectTimer) {
    clearTimeout(reconnectTimer)
    reconnectTimer = null
  }
  const proto = location.protocol === 'https:' ? 'wss' : 'ws'
  const url = `${proto}://${location.host}/ws/interview?token=${encodeURIComponent(token)}`
  const socket = new WebSocket(url)
  socket.binaryType = 'arraybuffer'
  ws.value = socket

  socket.onopen = () => {
    if (ws.value !== socket) return
    const reconnecting = reconnectAttempts > 0 || pendingResume.value
    online.value = true
    disconnected.value = false
    if (stableTimer) clearTimeout(stableTimer)
    stableTimer = setTimeout(() => {
      stableTimer = null
      reconnectAttempts = 0
    }, 5000)
    if (reconnecting) {
      send({ type: 'resume', position: selectedPosition.value })
    } else {
      send({ type: 'start', position: selectedPosition.value, jdText: incomingJdText.value, resumeText: incomingResumeText.value, techStack: incomingTechStack.value })
    }
  }
  socket.onmessage = (e) => {
    if (ws.value !== socket) return
    handleMessage(e)
  }
  socket.onclose = () => {
    if (ws.value !== socket) return
    handleWsDisconnect()
  }
  socket.onerror = () => {
    if (ws.value !== socket) return
    handleWsDisconnect()
  }
}

/**
 * 网络抖动时自动重连；重连成功后发送 resume 恢复当前面试。
 * 注意：onopen 里必须先记录“这是重连”，因此把旧值保存在 schedule 前。
 */
function handleWsDisconnect() {
  online.value = false
  disconnected.value = true
  asrReady.value = false
  asrIssue.value = ''
  forceStopRecognition()
  if (stableTimer) {
    clearTimeout(stableTimer)
    stableTimer = null
  }
  if (closingIntentionally || stage.value !== 'interview') {
    return
  }
  if (reconnectAttempts >= 3) {
    ElMessage.error('面试连接已断开，请刷新页面后重新进入')
    return
  }
  if (reconnectTimer) {
    return
  }
  reconnectAttempts++
  const delay = Math.min(1500 * reconnectAttempts, 6000)
  ElMessage.warning(`面试连接断开，正在第 ${reconnectAttempts} 次重连…`)
  if (reconnectTimer) clearTimeout(reconnectTimer)
  reconnectTimer = setTimeout(() => {
    reconnectTimer = null
    if (stage.value === 'interview') {
      connectWs()
    }
  }, delay)
}

/** 初始化 Web Speech 本地识别器（双引擎之「本地引擎」）；不支持时返回 null，由后端 Paraformer 兜底 */
function initRecognition() {
  const SR = window.SpeechRecognition || window.webkitSpeechRecognition
  if (!SR) return null
  const r = new SR()
  r.lang = 'zh-CN'
  r.continuous = true
  r.interimResults = true
  return r
}

/** 开始录音：启动本地识别（Web Speech）与后端实时识别（Paraformer 双引擎校验），实时填充输入框 */
async function startRecording() {
  if (isRecording.value) return
  if (micStopTimer) {
    window.clearTimeout(micStopTimer)
    micStopTimer = null
  }
  asrIssue.value = ''
  asrReady.value = false
  // 按需建立 Paraformer 会话，避免长时间无音频触发 NO_INPUT_AUDIO_ERROR
  send({ type: 'mic_start' })
  // 仅复用仍存活的音频轨道；已被强制终止（track ended）则传 null，由 MicRecorder 重新请求麦克风
  let audioStream = null
  const tracks = (localStream.value?.getAudioTracks() || []).filter((t) => t.readyState === 'live')
  if (tracks.length) {
    audioStream = new MediaStream(tracks)
  }
  recorder.value = new MicRecorder({
    onPcm: (pcm) => {
      // 麦克风门控：仅录音中且非思考期时推送 PCM 到后端 Paraformer
      if (!listening.value) return
      if (ws.value && ws.value.readyState === WebSocket.OPEN) {
        ws.value.send(pcm.buffer)
      }
    },
    onError: () => {
      ElMessage.warning('麦克风不可用，可使用文字输入作答')
    },
    silenceHoldFrames: 3,
    onSpeechStart: () => {
      userSpeaking.value = true
      clearAnswerTimer()
      nudgeCount = 0
    },
    onSpeechEnd: () => {
      userSpeaking.value = false
    }
  })
  isRecording.value = true
  recorder.value.start(audioStream).catch(() => {})
  startRecognition()
}

/** 结束录音：停止采集与本地识别，随后后端 Paraformer 的最终识别文本经 asr_result 回填输入框 */
function stopRecording() {
  isRecording.value = false
  userSpeaking.value = false
  asrReady.value = false
  stopRecognition()
  if (recorder.value) {
    recorder.value.stop()
    recorder.value = null
  }
  // 延迟关闭 ASR，给最后一句识别结果留出返回时间
  if (micStopTimer) window.clearTimeout(micStopTimer)
  micStopTimer = window.setTimeout(() => {
    micStopTimer = null
    send({ type: 'mic_stop' })
  }, 1200)
}

/** 麦克风按钮：手动切换录音开关 */
async function toggleMic() {
  if (isRecording.value) {
    stopRecording()
  } else {
    await startRecording()
  }
}

/** 启动本地 Web Speech 实时转写，interim + final 拼接实时填充输入框（弱网络下仍可用） */
function startRecognition() {
  finalSpeech = ''
  const r = initRecognition()
  if (!r) return
  r.onresult = (e) => {
    let interim = ''
    for (let i = e.resultIndex; i < e.results.length; i++) {
      const res = e.results[i]
      if (res.isFinal) {
        finalSpeech += res[0].transcript
      } else {
        interim += res[0].transcript
      }
    }
    // 语音转文字实时填充输入框（识别完成后用户仍可在发送前编辑）
    textInput.value = (finalSpeech + interim).trim()
  }
  r.onerror = () => {
    // 本地引擎异常时静默降级，交由后端 Paraformer 兜底，不打断录音
  }
  r.onend = () => {
    // 本地引擎自动结束后，若仍在录音则重启以持续转写
    if (isRecording.value && recognition) {
      try { recognition.start() } catch (err) { /* 忽略 */ }
    }
  }
  recognition = r
  try {
    r.start()
  } catch (err) {
    recognition = null
  }
}

/** 停止本地 Web Speech 识别 */
function stopRecognition() {
  if (recognition) {
    const r = recognition
    recognition = null
    r.onresult = null
    r.onend = null
    r.onerror = null
    try { r.stop() } catch (err) { /* 忽略 */ }
  }
}

/** 高频波形渲染循环：通过 AnalyserNode 以 ~60fps 读取实时音量并直接写 CSS 变量 */
function startWaveLoop() {
  if (waveRaf) return
  const tick = () => {
    const rec = recorder.value
    const el = roomPageRef.value?.waveEl
    if (rec && el) {
      const raw = rec.getLevel ? rec.getLevel() : 0
      const target = Math.min(1, raw / 0.15)
      // 快上升、慢回落，避免波形跳变
      smoothedLevel = target >= smoothedLevel
        ? target
        : smoothedLevel + (target - smoothedLevel) * 0.35

      if (waveActive.value) {
        const amp = Math.round(5 + smoothedLevel * 30)
        const intensity = 0.4 + 0.6 * smoothedLevel
        el.style.setProperty('--amp', amp + 'px')
        el.style.setProperty('--level', intensity.toFixed(3))
      } else {
        el.style.setProperty('--amp', '5px')
        el.style.setProperty('--level', '0.4')
      }
    }
    waveRaf = requestAnimationFrame(tick)
  }
  waveRaf = requestAnimationFrame(tick)
}

function handleMessage(e) {
  let data
  try {
    data = JSON.parse(e.data)
  } catch (err) {
    return
  }
  // 统一时间戳同步：丢弃乱序/过期到达的状态类消息，防止视觉提示回退
  const ts = Number(data.ts) || 0
  if (ts && lastServerTs && ts < lastServerTs && (data.type === 'thinking' || data.type === 'stage')) {
    return
  }
  if (ts) lastServerTs = ts
  switch (data.type) {
    case 'ready':
      ElMessage.success('AI 面试官已就绪')
      break
    case 'asr_ready':
      asrReady.value = true
      asrIssue.value = ''
      break
    case 'asr_status':
      // ASR 失败降级：只更新状态文案，不弹阻塞错误
      asrReady.value = !!data.ready
      asrIssue.value = data.message || ''
      break
    case 'stage':
      currentStage.value = data.stage || ''
      stageLabel.value = data.stageLabel || ''
      questionInStage.value = data.question || 1
      stageLimit.value = data.stageLimit || 1
      break
    case 'resume_history':
      // 中断续接：恢复历史对话气泡
      if (Array.isArray(data.transcript)) {
        messages.value = data.transcript.map(parseTranscriptLine).filter(Boolean)
      }
      break
    case 'agent_trace':
      // Agent 开场调研过程：让「AI 自主检索资料」这件事在界面上可见
      if (Array.isArray(data.steps)) agentSteps.value = data.steps
      break
    case 'assistant_text':
      // 面试官文字回复（已无语音播报）：直接闭合气泡，并转入等待用户作答
      thinking.value = false
      messages.value.push({ role: 'interviewer', text: data.text })
      beginAwaitAnswer()
      break
    case 'asr_result':
      // 后端 Paraformer 权威识别文本：回填输入框（可编辑后再发送）
      if (data.text) {
        textInput.value = data.text.trim()
      }
      break
    case 'user_final':
      messages.value.push({ role: 'user', text: data.text })
      break
    case 'thinking':
      thinking.value = !!data.value
      if (data.value) {
        // 进入思考阶段：停止用户录音，防止思考期音频被误处理
        if (isRecording.value) stopRecording()
      }
      break
    case 'report':
      thinking.value = false
      report.value = data.report
      score.value = data.score
      animatedScore.value = 0
      reportDialogVisible.value = true
      // 面试结束：彻底停止麦克风/语音识别，报告以弹窗模态展示
      cleanup()
      nextTick(() => animateScore(score.value))
      break
    case 'error':
      thinking.value = false
      ElMessage.error(data.message || '操作失败')
      break
  }
}

/** 将服务端 transcript 行（"面试官：…"/"候选人：…"）还原为对话气泡 */
function parseTranscriptLine(line) {
  if (!line) return null
  const l = String(line)
  if (l.startsWith('面试官：')) return { role: 'interviewer', text: l.slice(4) }
  if (l.startsWith('候选人：')) return { role: 'user', text: l.slice(4) }
  return { role: 'interviewer', text: l }
}

function send(obj) {
  if (ws.value && ws.value.readyState === WebSocket.OPEN) {
    ws.value.send(JSON.stringify(obj))
  }
}

function submitText() {
  const t = textInput.value.trim()
  if (!t) return
  if (isRecording.value) stopRecording()
  clearAnswerTimer()
  nudgeCount = 0
  // 不在此处本地追加消息：统一等待后端回传 user_final 渲染，避免与本地乐观更新重复显示
  textInput.value = ''
  send({ type: 'user_text', text: t })
}

function endInterview() {
  // 结束本次面试：停止语音/识别、清理会话并返回职业选择页（不生成评估报告）
  ElMessage.info('已结束本次面试')
  reset()
}

/** 强制终止识别：立即停止采集与处理，确保任何终止条件（后台/断网/结束）下都不再继续识别 */
function forceStopRecognition() {
  userSpeaking.value = false
  if (isRecording.value) stopRecording()
}

/** 应用前台/后台切换：后台保持语音识别/音频/WebSocket 持续运行，仅暂停高频波形渲染以省资源；
 *  回前台恢复波形渲染，并恢复可能被浏览器后台挂起的 AudioContext，保证采集与识别不中断。 */
function onVisibilityChange() {
  if (document.hidden) {
    if (waveRaf) {
      cancelAnimationFrame(waveRaf)
      waveRaf = null
    }
  } else if (stage.value === 'interview') {
    if (!waveRaf) startWaveLoop()
    resumeAudioContext()
  }
}

/** 恢复被浏览器在后台挂起的 AudioContext，确保麦克风采集与语音识别持续可用 */
function resumeAudioContext() {
  const rec = recorder.value
  if (rec && typeof rec.resume === 'function') {
    rec.resume()
  }
}

/** 面试官文字回复下发后开始计时，等待用户作答（语音或文字） */
function beginAwaitAnswer() {
  // 不在这里重置 nudgeCount：超时提醒本身也会回 assistant_text，
  // 重置会导致提醒次数上限永远达不到，形成无限提醒循环。
  clearAnswerTimer()
  answerTimer = window.setTimeout(onAnswerTimeout, ANSWER_TIMEOUT_MS)
}

function clearAnswerTimer() {
  if (answerTimer) {
    window.clearTimeout(answerTimer)
    answerTimer = null
  }
}

function onAnswerTimeout() {
  answerTimer = null
  // 最多提醒两次，避免反复打扰
  if (nudgeCount >= 2) return
  nudgeCount++
  send({ type: 'no_answer' })
  // 若仍无回应，重新计时以再次提醒
  answerTimer = window.setTimeout(onAnswerTimeout, ANSWER_TIMEOUT_MS)
}

function cleanup() {
  stopElapsedTimer()
  if (isRecording.value) stopRecording()
  // stopRecording 会安排延迟 mic_stop；清理时直接取消，避免关闭后还发消息
  if (micStopTimer) {
    window.clearTimeout(micStopTimer)
    micStopTimer = null
  }
  clearAnswerTimer()
  document.removeEventListener('visibilitychange', onVisibilityChange)
  if (waveRaf) {
    cancelAnimationFrame(waveRaf)
    waveRaf = null
  }
  if (scoreRaf) {
    cancelAnimationFrame(scoreRaf)
    scoreRaf = null
  }
  smoothedLevel = 0
  if (recorder.value) {
    recorder.value.stop()
    recorder.value = null
  }
  if (localStream.value) {
    localStream.value.getTracks().forEach((t) => t.stop())
    localStream.value = null
  }
  if (ws.value) {
    closingIntentionally = true
    if (reconnectTimer) {
      clearTimeout(reconnectTimer)
      reconnectTimer = null
    }
    if (stableTimer) {
      clearTimeout(stableTimer)
      stableTimer = null
    }
    reconnectAttempts = 0
    try {
      ws.value.close()
    } catch (e) {
      // 忽略
    }
    ws.value = null
  }
  online.value = false
  asrReady.value = false
  asrIssue.value = ''
}

function reset() {
  cleanup()
  stage.value = 'position'
  selectedPosition.value = ''
  customPosition.value = ''
  recommendedPosition.value = ''
  incomingJdText.value = ''
  incomingResumeText.value = ''
  incomingTechStack.value = ''
  activeInterview.value = null
  pendingResume.value = false
  reportDialogVisible.value = false
  cameraGranted.value = false
  micGranted.value = false
  elapsedSeconds.value = 0
  disconnected.value = false
  asrIssue.value = ''
  asrReady.value = false
  asrReady.value = false
  messages.value = []
  currentStage.value = ''
  stageLabel.value = ''
  questionInStage.value = 1
  stageLimit.value = 1
  report.value = null
  score.value = null
  thinking.value = false
  userSpeaking.value = false
  isRecording.value = false
  textInput.value = ''
}

/** 完成面试：报告已入库，收尾后去「面试记录」查看本次评估 */
function finishInterview() {
  reset()
  ElMessage.success('面试已完成，评估报告已保存')
  router.push('/profile')
}

// ===== 跨模块联动（模块三 → 模块四 / 模块二）=====

/** 复盘报告中识别的薄弱知识点：得分最低的维度名称 + 不足项 */
const weakKnowledgePoints = computed(() => {
  const dims = [...(report.value?.dimensions || [])]
    .sort((a, b) => (Number(a.score) || 0) - (Number(b.score) || 0))
  const names = dims.slice(0, 3).map(d => d.name)
  const weak = report.value?.weaknesses || []
  return [...new Set([...names, ...weak.slice(0, 3)])].join('、')
})

function goExamFromReport() {
  linkage.setContext({
    from: 'interview',
    jobTitle: selectedPosition.value,
    jdText: incomingJdText.value || `岗位名称：${selectedPosition.value}`,
    techStack: incomingTechStack.value,
    resumeText: incomingResumeText.value,
    knowledgePoints: weakKnowledgePoints.value
  })
  router.push('/exam')
}

function goJobMatchFromReport() {
  router.push('/jobmatch')
}

onBeforeUnmount(cleanup)
</script>

<style scoped>
.interview-layout {
  height: 100%;
}
.main {
  background: transparent;
  display: flex;
  justify-content: center;
}
.panel {
  width: 100%;
  max-width: 860px;
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  padding: 40px;
}
/* 职位选择阶段：不再有任何卡片/包裹盒，内容直接平铺在页面背景上。
   el-main 自身即承载内容并居中（max-width 仅用于避免超宽屏拉伸），
   输入框与按钮作为直接子项通过 grid 同行排列、窄屏自动换行 */
.main.is-position {
  display: grid;
  grid-template-columns: minmax(220px, 1fr) auto;
  column-gap: 10px;
  align-items: center;
  align-content: start;
  width: 100%;
  max-width: 860px;
  margin: 0 auto;
  padding: 40px;
}
/* 除输入框/按钮外的直接子元素各占满整行，保持原有纵向排版 */
.main.is-position > * {
  min-width: 0;
  max-width: 100%;
  box-sizing: border-box;
}
.main.is-position > :not(.custom-input):not(.custom-position-btn) {
  grid-column: 1 / -1;
}
.main.is-position > .custom-input {
  grid-column: 1;
  min-width: 220px;
}
.main.is-position > .custom-position-btn {
  grid-column: 2;
  white-space: nowrap;
}
@media (max-width: 560px) {
  .main.is-position {
    grid-template-columns: 1fr;
  }
  .main.is-position > .custom-input,
  .main.is-position > .custom-position-btn {
    grid-column: 1;
  }
}
.panel-title {
  font-size: 22px;
  font-weight: 600;
  letter-spacing: -0.01em;
  color: var(--color-text);
  margin-bottom: 8px;
}
.panel-sub {
  color: var(--color-text-2);
  margin-bottom: 32px;
}
.position-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 16px;
}
@media (max-width: 640px) {
  .position-grid {
    grid-template-columns: repeat(2, 1fr);
  }
  /* 奇数个岗位时，最后一个占满整行，避免落单 */
  .position-card:last-child:nth-child(odd) {
    grid-column: 1 / -1;
  }
}
.position-card {
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
  padding: 18px 12px;
  text-align: center;
  font-size: 14px;
  color: var(--color-text-2);
  background: var(--color-surface);
  cursor: pointer;
  transition: border-color 0.15s ease, background-color 0.15s ease, color 0.15s ease;
}
.position-card:hover {
  border-color: var(--color-border-strong);
  color: var(--color-text);
}
.position-card.active {
  border-color: var(--color-accent);
  background: var(--color-accent-soft);
  color: var(--color-accent);
  font-weight: 600;
}
.recommend-block {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
  padding: 14px 16px;
  background: var(--color-warning-bg);
  border: 1px solid var(--color-warning-border);
  border-radius: var(--radius-sm);
  margin-bottom: 20px;
}
.recommend-label {
  color: var(--color-warning);
  font-size: 13px;
  flex-shrink: 0;
}
.recommend-tag {
  padding: 6px 14px;
  border: 1px solid var(--color-warning-border);
  border-radius: 4px;
  font-size: 13px;
  color: var(--color-warning);
  cursor: pointer;
  transition: border-color 0.15s ease, background-color 0.15s ease;
  background: var(--color-surface);
}
.recommend-tag.active {
  border-color: var(--color-warning);
  background: var(--color-warning-bg);
  color: var(--color-warning);
  font-weight: 600;
}
.group-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--color-text);
  margin: 20px 0 12px;
}
.selected-tip {
  margin-top: 14px;
  color: var(--color-accent);
  font-size: 14px;
}
.jd-context-tip {
  margin-top: 12px;
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  color: var(--color-success);
  background: var(--color-success-bg);
  border: 1px solid #c9e8d7;
  border-radius: var(--radius-sm);
  padding: 8px 12px;
}
.resume-banner {
  margin-top: 12px;
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 13px;
  color: var(--color-text-2);
  background: var(--color-surface-muted);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
  padding: 10px 14px;
}
.resume-text {
  flex: 1;
  color: var(--color-text);
}
.panel-actions {
  margin-top: 32px;
  display: flex;
  gap: 12px;
  justify-content: center;
}
.panel-hint {
  margin: 10px 0 0;
  text-align: center;
  font-size: 12px;
  color: var(--color-text-3);
}
.permission-body {
  display: flex;
  gap: 24px;
  align-items: center;
}
.video-box {
  position: relative;
  width: 380px;
  height: 260px;
  background: #15242b;
  border-radius: var(--radius-md);
  overflow: hidden;
}
.video-box video {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.video-placeholder {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  color: #fff;
  background: #15242b;
}
.video-placeholder.loading {
  background: #1e3038;
}
.video-placeholder.error {
  background: #1e3038;
  color: #e7aca5;
  font-size: 13px;
  padding: 0 16px;
  text-align: center;
}
.perm-status {
  display: flex;
  flex-direction: column;
  gap: 20px;
  font-size: 15px;
}
.perm-hint {
  margin: 0;
  font-size: 12px;
  line-height: 1.6;
  color: var(--color-text-3);
}
.perm-row {
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--color-text-2);
}

/* 面试中 */
.interview-body {
  width: 100%;
  max-width: 1100px;
  display: flex;
  gap: 20px;
}
.interview-left {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  overflow: hidden;
}
.interviewer-box {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 16px 20px;
  border-bottom: 1px solid var(--color-border);
}
.avatar {
  width: 48px;
  height: 48px;
  border-radius: 50%;
  background: var(--color-accent);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 600;
  flex-shrink: 0;
}
.avatar.speaking {
  animation: pulse 1s infinite;
}
@keyframes pulse {
  0% {
    box-shadow: 0 0 0 0 rgba(31, 95, 107, 0.35);
  }
  70% {
    box-shadow: 0 0 0 12px rgba(31, 95, 107, 0);
  }
  100% {
    box-shadow: 0 0 0 0 rgba(31, 95, 107, 0);
  }
}
.interviewer-meta {
  flex: 1;
}
.interviewer-meta .name {
  font-weight: 600;
  color: var(--color-text);
}
.interviewer-meta .tag {
  font-size: 12px;
  color: var(--color-text-3);
}
.phase-tag {
  font-size: 12px;
  color: var(--color-accent);
  background: var(--color-accent-soft);
  padding: 4px 10px;
  border-radius: 4px;
}
.chat-box {
  flex: 1;
  min-height: 300px;
  max-height: 420px;
  overflow-y: auto;
  padding: 20px;
  background: var(--color-bg);
}
.msg-row {
  display: flex;
  margin-bottom: 14px;
}
.msg-row.interviewer {
  justify-content: flex-start;
}
.msg-row.user {
  justify-content: flex-end;
}
.bubble {
  max-width: 70%;
  padding: 10px 14px;
  border-radius: var(--radius-md);
  line-height: 1.6;
  word-break: break-word;
  white-space: pre-wrap;
  animation: page-enter 0.3s var(--ease-out-expo) backwards;
}
.bubble.streaming::after {
  content: "▍";
  margin-left: 2px;
  color: var(--color-accent);
  animation: cursorBlink 1s steps(2, start) infinite;
}
.bubble.has-audio {
  position: relative;
  padding-right: 34px;
  cursor: pointer;
  user-select: none;
}
.speech-toggle {
  position: absolute;
  right: 8px;
  top: 50%;
  transform: translateY(-50%);
  border: none;
  background: transparent;
  color: var(--color-text-3);
  cursor: pointer;
  padding: 2px;
  display: flex;
  align-items: center;
  justify-content: center;
}
.speech-toggle:hover {
  color: var(--color-accent);
}
@keyframes cursorBlink {
  0%, 100% { opacity: 1; }
  50% { opacity: 0; }
}
.msg-row.interviewer .bubble {
  background: var(--color-surface-muted);
  color: var(--color-text);
  border-top-left-radius: 4px;
}
.msg-row.user .bubble {
  background: var(--color-primary);
  color: #fff;
  border-top-right-radius: 4px;
}
.typing {
  color: var(--color-text-3);
}
.input-bar {
  display: flex;
  gap: 8px;
  padding: 14px 16px;
  background: var(--color-surface);
  border-top: 1px solid var(--color-border);
}
.input-bar .mic-btn {
  flex-shrink: 0;
}
.input-bar :deep(.el-input) {
  flex: 1;
}

.interview-right {
  width: 260px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.self-box {
  position: relative;
  height: 200px;
  background: #15242b;
  border-radius: var(--radius-md);
  overflow: hidden;
}
.self-box video {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.self-placeholder {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  background: #15242b;
}

.asr-status {
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
  padding: 12px 16px;
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  color: var(--color-text-2);
}
.dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--color-border-strong);
}
.dot.on {
  background: var(--color-success);
}
.dot.speaking {
  background: var(--color-success);
  animation: dotPulse 1s infinite;
}
@keyframes dotPulse {
  0%, 100% { box-shadow: 0 0 0 0 rgba(21, 128, 61, 0.4); }
  50% { box-shadow: 0 0 0 6px rgba(21, 128, 61, 0); }
}
.asr-line {
  display: flex;
  align-items: center;
  gap: 8px;
}
.asr-status.thinking {
  background: var(--color-warning-bg);
  color: var(--color-warning);
}
.asr-status.listening {
  background: var(--color-success-bg);
  color: var(--color-success);
}
.asr-status.ending {
  background: var(--color-surface-muted);
  color: var(--color-text-3);
}

/* 用户说话波纹：动态随音量起伏 */
.voice-wave {
  position: absolute;
  left: 50%;
  bottom: 12px;
  transform: translateX(-50%);
  display: flex;
  align-items: center;
  gap: 3px;
  height: 40px;
  pointer-events: none;
}
.voice-wave .bar {
  width: 4px;
  height: var(--amp, 5px);
  border-radius: 2px;
  background: rgba(255, 255, 255, 0.45);
  transform-origin: center bottom;
  transition: height 0.12s cubic-bezier(0.4, 0, 0.2, 1);
}
.voice-wave.active .bar {
  background: #fff;
  opacity: var(--level, 0.5);
  box-shadow: 0 0 8px rgba(255, 255, 255, 0.35);
  animation: waveLive 0.8s cubic-bezier(0.45, 0, 0.55, 1) infinite;
}
@keyframes waveLive {
  0%, 100% { transform: scaleY(0.3); }
  50% { transform: scaleY(1); }
}

/* AI 思考指示 */
.thinking-chip {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  font-size: 12px;
  color: var(--color-warning);
  background: var(--color-warning-bg);
  padding: 4px 10px;
  border-radius: 4px;
  flex-shrink: 0;
}
.thinking-chip .t-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--color-warning);
  animation: blink 1.2s ease-in-out infinite;
}
.thinking-chip .t-dot:nth-child(2) { animation-delay: 0.2s; }
.thinking-chip .t-dot:nth-child(3) { animation-delay: 0.4s; }
@keyframes blink {
  0%, 80%, 100% { opacity: 0.25; transform: scale(0.7); }
  40% { opacity: 1; transform: scale(1); }
}

/* 报告 */
.report-panel {
  text-align: left;
}
.score-card {
  text-align: center;
  margin-bottom: 28px;
  padding: 28px 20px 26px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-surface);
  animation: scoreIn 0.6s ease both;
}
@keyframes scoreIn {
  from { opacity: 0; transform: translateY(16px) scale(0.98); }
  to { opacity: 1; transform: translateY(0) scale(1); }
}
.score-num {
  font-size: 34px;
  font-weight: 700;
  color: var(--color-accent);
}
.score-label {
  font-size: 13px;
  color: var(--color-text-3);
}
.summary {
  margin-top: 16px;
  color: var(--color-text-2);
  font-size: 15px;
}
.report-lists ul {
  padding-left: 18px;
  color: var(--color-text-2);
  line-height: 1.9;
  font-size: 14px;
}

/* 维度可视化与明细 */
.section-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--color-text);
  margin: 28px 0 14px;
}
.chart-card {
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  padding: 12px 14px;
  margin-bottom: 16px;
  transition: border-color 0.15s ease;
}
.chart-card:hover {
  border-color: var(--color-border-strong);
}
.chart-title {
  font-size: 14px;
  color: var(--color-text-2);
  font-weight: 600;
  margin-bottom: 6px;
}
.dim-section {
  margin-bottom: 8px;
}
.dim-card {
  border: 1px solid var(--color-border);
  border-left: 3px solid var(--color-border-strong);
  border-radius: var(--radius-md);
  padding: 14px 18px;
  margin-bottom: 14px;
  background: var(--color-surface);
  transition: border-color 0.15s ease;
  /* 错峰淡入：animationDelay 由内联样式按索引递增 */
  animation: dimIn 0.5s ease backwards;
}
.dim-card:hover {
  border-left-color: var(--color-accent);
  border-color: var(--color-border-strong);
}
@keyframes dimIn {
  from { opacity: 0; transform: translateY(12px); }
  to { opacity: 1; transform: translateY(0); }
}
.dim-card.active {
  border-left-color: var(--color-accent);
  border-color: var(--color-border);
}
.dim-head {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 10px;
}
.dim-name {
  font-size: 15px;
  font-weight: 600;
  color: var(--color-text);
}
.dim-score {
  font-size: 22px;
  font-weight: 600;
  color: var(--color-accent);
}
.dim-avg {
  font-size: 12px;
  color: var(--color-text-3);
  margin-left: auto;
}
.dim-field {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  margin-bottom: 8px;
  font-size: 14px;
}
.dim-label {
  flex-shrink: 0;
  width: 76px;
  color: var(--color-text-3);
  font-size: 13px;
  padding-top: 2px;
}
.dim-label.bonus {
  color: var(--color-success);
}
.dim-label.deduct {
  color: var(--color-danger);
}
.dim-text {
  flex: 1;
  color: var(--color-text);
  line-height: 1.7;
}
.dim-tag {
  margin-right: 6px;
  margin-bottom: 4px;
}

/* 综合评级与差值 */
.score-meta {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12px;
  margin-top: 14px;
}
.rating-badge {
  display: inline-block;
  padding: 4px 14px;
  border-radius: 999px;
  font-size: 13px;
  font-weight: 500;
}
.rating-excellent { background: var(--color-lilac-bg); color: var(--color-lilac-text); }
.rating-good { background: var(--color-accent-soft); color: var(--color-accent); }
.rating-mid { background: var(--color-warning-bg); color: var(--color-warning); }
.rating-low { background: var(--color-danger-bg); color: var(--color-danger); }
.score-delta {
  font-size: 13px;
  color: var(--color-text-2);
}

/* 核心能力速览：最突出 / 待提升 */
.insight-section {
  display: flex;
  gap: 16px;
  margin-bottom: 24px;
}
.insight-card {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 16px 18px;
  border: 1px solid var(--color-border);
  border-left: 3px solid var(--color-border-strong);
  border-radius: var(--radius-md);
  background: var(--color-surface);
  transition: border-color 0.15s ease;
}
.insight-card:hover {
  border-color: var(--color-border-strong);
}
.insight-top {
  border-left-color: var(--color-success);
}
.insight-low {
  border-left-color: var(--color-danger);
}
.insight-tag {
  font-size: 12px;
  color: var(--color-text-3);
}
.insight-top .insight-tag { color: var(--color-success); }
.insight-low .insight-tag { color: var(--color-danger); }
.insight-name {
  font-size: 16px;
  font-weight: 600;
  color: var(--color-text);
}
.insight-score {
  font-size: 26px;
  font-weight: 700;
  line-height: 1;
}
.insight-top .insight-score { color: var(--color-success); }
.insight-low .insight-score { color: var(--color-danger); }

/* 维度得分对比条：悬停时该条从 0 平滑铺展到实际得分 */
.dim-meter {
  position: relative;
  height: 8px;
  border-radius: 6px;
  background: var(--color-surface-muted);
  margin: 4px 0 6px;
}
.dim-meter-track {
  position: absolute;
  inset: 0;
  border-radius: 6px;
  background: var(--color-surface-muted);
}
.dim-meter-fill {
  position: absolute;
  left: 0;
  top: 0;
  bottom: 0;
  background: var(--color-accent);
  border-radius: 6px;
  transform: scaleX(0);
  transform-origin: left;
  transition: transform 0.6s ease;
}
.dim-card:hover .dim-meter-fill {
  transform: scaleX(1);
}
.dim-meter-avg {
  position: absolute;
  top: -3px;
  bottom: -3px;
  width: 2px;
  background: var(--color-danger);
  border-radius: 2px;
}
.dim-meter-legend {
  display: flex;
  gap: 16px;
  font-size: 12px;
  color: var(--color-text-3);
  margin-bottom: 8px;
}
.legend-item {
  display: inline-flex;
  align-items: center;
}
.dot {
  display: inline-block;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  margin-right: 4px;
}
.dot.you { background: var(--color-accent); }
.dot.avg { background: var(--color-danger); }

/* 优势 / 待改进 / 改进方向：分组卡片 */
.report-lists .list-card {
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-surface);
  margin-bottom: 16px;
  overflow: hidden;
  transition: border-color 0.15s ease;
}
.report-lists .list-card:hover {
  border-color: var(--color-border-strong);
}
.list-header {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 12px 16px;
  font-size: 15px;
  font-weight: 600;
}
.list-strength .list-header { background: var(--color-success-bg); color: var(--color-success); }
.list-weakness .list-header { background: var(--color-warning-bg); color: var(--color-warning); }
.list-suggestion .list-header { background: var(--color-accent-soft); color: var(--color-accent); }
.list-card ul {
  margin: 0;
  padding: 14px 18px 14px 32px;
  color: var(--color-text-2);
  line-height: 1.9;
  font-size: 14px;
}

/* 面试评估报告弹窗（紫白冷调企业级风格） */
.report-dialog :deep(.el-dialog) {
  border-radius: 14px;
  overflow: hidden;
}
.report-dialog :deep(.el-dialog__header) {
  padding: 18px 24px;
  border-bottom: 1px solid var(--color-border);
}
.report-dialog :deep(.el-dialog__body) {
  padding: 24px;
  max-height: 70vh;
  overflow-y: auto;
}
.report-dialog :deep(.el-dialog__footer) {
  padding: 16px 24px 20px;
  border-top: 1px solid var(--color-border);
}
.rd-header {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
  padding-right: 36px;
}
.rd-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--color-text);
}
.rd-position {
  font-size: 13px;
  color: var(--color-accent);
  background: var(--color-accent-soft);
  padding: 2px 10px;
  border-radius: 4px;
}
.report-body {
  color: var(--color-text);
}
.rd-top {
  display: flex;
  align-items: center;
  gap: 28px;
  margin-bottom: 18px;
}
.rd-score-wrap {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
}
.rd-score-num {
  font-size: 40px;
  font-weight: 700;
  color: var(--color-text);
  line-height: 1;
  font-variant-numeric: tabular-nums;
  letter-spacing: -0.02em;
}
.rd-score-label {
  font-size: 12px;
  color: var(--color-text-3);
}
.rd-basic {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.rd-basic-item {
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 14px;
}
.rd-basic-item .k {
  flex-shrink: 0;
  width: 76px;
  color: var(--color-text-3);
}
.rd-basic-item .v {
  color: var(--color-text);
  font-weight: 600;
}
.rd-summary {
  margin: 0 0 18px;
  padding: 12px 16px;
  font-size: 14px;
  line-height: 1.8;
  color: var(--color-text-2);
  background: var(--color-bg);
  border-left: 3px solid var(--color-accent);
  border-radius: var(--radius-sm);
}
.rd-section-title {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 15px;
  font-weight: 600;
  color: var(--color-text);
  margin: 0 0 12px;
}
.rd-dims {
  margin-bottom: 20px;
}
.rd-dim {
  margin-bottom: 14px;
  animation: page-enter 0.4s var(--ease-out-expo) backwards;
}
.rd-dims .rd-dim:nth-child(2) { animation-delay: 60ms; }
.rd-dims .rd-dim:nth-child(3) { animation-delay: 120ms; }
.rd-dims .rd-dim:nth-child(4) { animation-delay: 180ms; }
.rd-dim-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  margin-bottom: 6px;
}
.rd-dim-name {
  font-size: 14px;
  font-weight: 600;
  color: var(--color-text);
}
.rd-dim-score {
  font-size: 18px;
  font-weight: 600;
  color: var(--color-accent);
  font-variant-numeric: tabular-nums;
}
.rd-dim-score i {
  font-style: normal;
  font-size: 12px;
  color: var(--color-text-3);
  font-weight: 400;
}
.rd-dim-comment {
  margin: 6px 0 0;
  font-size: 13px;
  color: var(--color-text-3);
  line-height: 1.6;
}
.rd-lists {
  display: flex;
  gap: 16px;
}
.rd-list {
  flex: 1;
  border: 1px solid var(--color-border);
  border-radius: 10px;
  padding: 14px 16px;
  background: var(--color-surface);
}
.rd-list ul {
  margin: 0;
  padding-left: 18px;
  color: var(--color-text-2);
  line-height: 1.9;
  font-size: 13px;
}
.rd-strength .rd-section-title {
  color: #177a4c;
}
.rd-weak .rd-section-title {
  color: #b45309;
}
.rd-footer {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 12px;
}

/* 响应式：小屏下面试区改为纵向布局 */
@media (max-width: 768px) {
  .interview-body {
    flex-direction: column;
  }
  .interview-right {
    width: 100%;
    flex-direction: row;
    flex-wrap: wrap;
  }
  .self-box {
    height: 160px;
    flex: 1;
    min-width: 200px;
  }
  .asr-status {
    flex: 1;
    min-width: 200px;
  }
  .insight-section {
    flex-direction: column;
  }
  .score-meta {
    flex-wrap: wrap;
  }
}
</style>