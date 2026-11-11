<template>
  <el-dialog
    :model-value="modelValue"
    @update:model-value="$emit('update:modelValue', $event)"
    title="求职小精灵"
    width="780px"
    align-center
    class="lib-dialog app-assistant"
    append-to-body
  >
    <div class="assistant-head">
      <span class="assistant-job">{{ job?.jobName || '目标岗位' }}</span>
      <span v-if="job?.companyName" class="assistant-company">{{ job.companyName }}</span>
    </div>

    <div v-if="!hasMaterials" class="assistant-hint">
      <span>我的简历素材中还没有内容，先补充素材后再生成评估、简历或求职信。</span>
      <button class="button button--small button--plain" @click="$emit('open-materials')">去补充素材</button>
    </div>

    <el-tabs v-model="activeTab" class="assistant-tabs">
      <!-- 匹配评估 -->
      <el-tab-pane label="匹配评估" name="evaluate">
        <div class="tab-body">
          <GenerationProgress
            :active="evaluateLoading"
            :stage="evaluateStage"
            :estimate="15"
            label="正在刷新匹配评估"
            kind="evaluate"
          />
          <div v-if="evaluateView">
            <div class="score-card">
              <div class="score-num">{{ evaluateView.overallScore ?? '—' }}</div>
              <div class="score-meta">
                <div class="score-label">综合匹配度</div>
                <div class="score-title">{{ evaluateView.jobTitle || job?.jobName || '' }}</div>
              </div>
            </div>
            <p v-if="evaluateView.summary" class="eval-summary">{{ evaluateView.summary }}</p>

            <div v-if="evaluateView.dimensions && evaluateView.dimensions.length" class="dim-list">
              <div v-for="d in evaluateView.dimensions" :key="d.name" class="dim-item">
                <div class="dim-head">
                  <span class="dim-name">{{ d.name }}</span>
                  <span class="dim-score">{{ d.score ?? '—' }}</span>
                </div>
                <el-progress :percentage="numScore(d.score)" :show-text="false" :stroke-width="6" />
                <p v-if="d.remark" class="dim-remark">{{ d.remark }}</p>
              </div>
            </div>

            <div v-if="evaluateView.matchedPoints.length" class="point-block">
              <div class="point-title hit">匹配优势</div>
              <div v-for="(p, i) in evaluateView.matchedPoints" :key="'m' + i" class="point-line hit">{{ p }}</div>
            </div>
            <div v-if="evaluateView.missingRequirements.length" class="point-block">
              <div class="point-title miss">待补足项</div>
              <div v-for="(p, i) in evaluateView.missingRequirements" :key="'x' + i" class="point-line miss">{{ p }}</div>
            </div>
          </div>
          <el-empty v-else-if="!evaluateLoading" description="还没有匹配评估，生成后可查看匹配度与待补足项" />
        </div>
      </el-tab-pane>

      <!-- 定制简历 -->
      <el-tab-pane label="定制简历" name="tailor">
        <div class="tab-body">
          <GenerationProgress
            :active="tailorLoading"
            :stage="tailorStage"
            :estimate="30"
            label="正在生成定制简历"
            kind="tailor"
          />
          <div class="template-block">
            <div class="template-head">
              <el-tooltip content="切换后 PDF 与 Word 会应用同一版式" placement="top">
                <span class="section-label">简历模板</span>
              </el-tooltip>
              <span class="template-current">{{ activeTemplate ? activeTemplate.name : '默认模板' }}</span>
            </div>
            <div v-if="templates.length" class="template-list">
              <button
                v-for="t in templates"
                :key="t.id"
                type="button"
                class="template-card"
                :class="{ 'is-active': t.id === activeTemplateId }"
                :title="t.name"
                @click="selectTemplate(t)"
              >
                <span
                  class="template-thumb"
                  :class="'tpl-' + (t.layout || 'single')"
                  :style="{ '--tpl-primary': t.primary, '--tpl-secondary': t.secondary }"
                >
                  <i class="tp-bar"></i>
                  <i class="tp-photo"></i>
                  <i class="tp-line l1"></i>
                  <i class="tp-line l2"></i>
                  <i class="tp-line l3"></i>
                </span>
                <span class="template-name">{{ t.name }}</span>
              </button>
            </div>
            <div v-else class="template-empty">
              {{ templatesLoading ? '模板加载中…' : '将使用默认极简版式' }}
            </div>
          </div>

          <div v-if="tailor">
            <div class="resume-preview-block">
              <div class="resume-preview-head">
                <span class="section-label">简历预览</span>
                <span class="preview-hint">预览效果与导出一致</span>
              </div>
              <ResumePaper :resume="tailor" :template="activeTemplate" :photo="userStore.photo" />
            </div>

            <div v-if="tailor.missingSkills && tailor.missingSkills.length" class="block">
              <div class="section-label">待补足技能（仅为建议，不进入简历）</div>
              <div v-for="(m, i) in tailor.missingSkills" :key="'ms' + i" class="preview-text line miss">
                {{ m.name }}<span v-if="m.suggestion"> — {{ m.suggestion }}</span>
              </div>
            </div>
          </div>
          <el-empty v-else-if="!tailorLoading" description="还没有定制简历，选择模板后生成一版针对该岗位的简历" />

          <div class="photo-block">
            <div class="section-label">证件照（可选）</div>
            <PhotoEditor :photo="userStore.photo" @change="onPhotoChange" />
          </div>

          <div v-if="tailor" class="export-actions">
            <button
              class="button"
              :disabled="tailorExporting"
              :class="{ 'is-loading': tailorExporting && tailorExportFormat === 'pdf' }"
              @click="downloadTailored('pdf')"
            >
              导出 PDF
            </button>
            <button
              class="button button--plain"
              :disabled="tailorExporting"
              :class="{ 'is-loading': tailorExporting && tailorExportFormat === 'docx' }"
              @click="downloadTailored('docx')"
            >
              导出 Word
            </button>
          </div>

        </div>
      </el-tab-pane>

      <!-- 求职信 -->
      <el-tab-pane label="求职信" name="cover">
        <div class="tab-body">
          <GenerationProgress
            :active="coverLoading"
            :stage="coverStage"
            :estimate="20"
            label="正在生成求职信"
            kind="cover"
          />
          <div v-if="cover">
            <div class="cover-letter">
              <div v-if="cover.subject" class="cover-subject">{{ cover.subject }}</div>
              <div v-if="cover.greeting" class="cover-greeting">{{ cover.greeting }}</div>
              <div class="cover-body">{{ cover.body }}</div>
              <div v-if="cover.applicantName" class="cover-sign">{{ cover.applicantName }}</div>
            </div>
            <!-- 引用回溯：这封信实际参考了哪些素材 -->
            <details v-if="cover.materials && cover.materials.length" class="material-refs">
              <summary>本次生成参考了 {{ cover.materials.length }} 条简历素材</summary>
              <ul>
                <li v-for="(m, i) in cover.materials" :key="i">
                  <span class="material-refs__type">{{ m.type }}</span>
                  <span class="material-refs__content">{{ m.content }}</span>
                </li>
              </ul>
            </details>
            <div class="export-actions">
              <button
                class="button"
                :disabled="coverExporting"
                :class="{ 'is-loading': coverExporting && coverExportFormat === 'pdf' }"
                @click="downloadCover('pdf')"
              >
                导出 PDF
              </button>
              <button
                class="button button--plain"
                :disabled="coverExporting"
                :class="{ 'is-loading': coverExporting && coverExportFormat === 'docx' }"
                @click="downloadCover('docx')"
              >
                导出 Word
              </button>
            </div>
          </div>
          <el-empty v-else-if="!coverLoading" description="还没有求职信，生成一封针对该岗位的求职信" />
        </div>
      </el-tab-pane>

      </el-tabs>

    <template #footer>
      <div class="assistant-footer">
        <button class="button button--plain" @click="$emit('update:modelValue', false)">关闭</button>
        <button class="button" :disabled="activeLoading || !hasMaterials" :class="{ 'is-loading': activeLoading }" @click="runActive">
          {{ activeLoading ? '生成中...' : activeActionLabel }}
        </button>
      </div>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import {
  evaluateApplication, tailorApplication, coverLetterApplication,
  getGenerationProgress, exportCoverLetterPdf, exportCoverLetterDocx
} from '@/api/application'
import { exportResumeDocx, exportResumePdf } from '@/api/jobmatch'
import { fetchResumeTemplates, readCachedResumeTemplates } from '@/utils/resumeTemplates'
import { recordGenerationDuration } from '@/utils/generationTiming'
import { useUserStore } from '@/stores/user'
import PhotoEditor from '@/components/PhotoEditor.vue'
import ResumePaper from '@/components/ResumePaper.vue'
import GenerationProgress from '@/components/GenerationProgress.vue'

const DOCX_MIME = 'application/vnd.openxmlformats-officedocument.wordprocessingml.document'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  job: { type: Object, default: null },
  // 可选：与岗位卡片完全同源的匹配排名结果（JobRankVO），传入后匹配评估直接复用，无需二次请求
  match: { type: Object, default: null },
  // 我的简历素材是否已有内容：用于提前提示，不进入生成逻辑
  hasMaterials: { type: Boolean, default: true },
  // 素材版本：素材变化时失效该岗位的评估/定制简历/求职信缓存
  materialsVersion: { type: String, default: '' }
})
defineEmits(['update:modelValue', 'open-materials'])

const activeTab = ref('evaluate')

const evaluate = ref(null)
const tailor = ref(null)
const cover = ref(null)

// 每个岗位单独缓存，切换岗位时展示该岗位自己的内容
const evaluateCache = new Map()
const tailorCache = new Map()
const coverCache = new Map()

const evaluateLoading = ref(false)
const tailorLoading = ref(false)
const coverLoading = ref(false)
const evaluateStage = ref('')
const tailorStage = ref('')
const coverStage = ref('')
let evaluateStageTimer = null
let tailorStageTimer = null
let coverStageTimer = null
const tailorExporting = ref(false)
const coverExporting = ref(false)
const tailorExportFormat = ref('')
const coverExportFormat = ref('')

const userStore = useUserStore()
const templates = ref([])
const activeTemplateId = ref('')
const templatesLoading = ref(false)
const activeTemplate = computed(
  () => templates.value.find((t) => t.id === activeTemplateId.value) || null
)

const loadingMap = {
  evaluate: () => evaluateLoading.value,
  tailor: () => tailorLoading.value,
  cover: () => coverLoading.value
}
const actionLabelMap = {
  evaluate: '刷新评估',
  tailor: '生成定制简历',
  cover: '生成求职信'
}

const activeLoading = computed(() => loadingMap[activeTab.value]?.() || false)
const activeActionLabel = computed(() => actionLabelMap[activeTab.value] || '生成')

// 与岗位卡片同源的匹配评估数据：直接复用传入的 match（JobRankVO），保证与卡片详情完全一致
const evaluateFromMatch = computed(() => {
  const m = props.match
  if (!m) return null
  const dims = [
    { name: '技能', score: m.skillScore, remark: '' },
    { name: '经历', score: m.experienceScore, remark: '' },
    { name: '学历', score: m.educationScore, remark: '' },
    { name: '契合', score: m.fitScore, remark: '' }
  ].filter((d) => d.score != null)
  const matched = (m.details || [])
    .filter((d) => d.matched !== false)
    .map((d) => (d.resumeContent || d.requirement || '').trim())
    .filter(Boolean)
  const missing = (m.details || [])
    .filter((d) => d.matched === false)
    .map((d) => {
      const req = d.requirement || ''
      const reason = d.missReason || ''
      return req && reason && req !== reason ? `${req} · ${reason}` : (req || reason || '未满足')
    })
  if (!missing.length && Array.isArray(m.gaps)) missing.push(...m.gaps)
  return {
    overallScore: m.score,
    jobTitle: props.job?.jobName || m.jobName || '',
    summary: m.summary || '',
    dimensions: dims,
    matchedPoints: matched,
    missingRequirements: missing
  }
})

// 展示层去重：同源 match 与后端评估都可能返回重复条目，按内容去重后再渲染
function uniqText(list) {
  const seen = new Set()
  return (Array.isArray(list) ? list : []).filter((x) => {
    const key = String(x ?? '').replace(/\s+/g, ' ').trim()
    if (!key || seen.has(key)) return false
    seen.add(key)
    return true
  })
}

const evaluateView = computed(() => {
  const e = evaluate.value
  if (!e) return null
  const seenDims = new Set()
  const dimensions = (Array.isArray(e.dimensions) ? e.dimensions : []).filter((d) => {
    const name = String(d?.name ?? '').trim()
    if (!name || seenDims.has(name)) return false
    seenDims.add(name)
    return true
  })
  return {
    ...e,
    dimensions,
    matchedPoints: uniqText(e.matchedPoints),
    missingRequirements: uniqText(e.missingRequirements)
  }
})

// 传入 match 时立即渲染同源匹配评估；切换岗位时优先使用该岗位自己的缓存
function evaluationKey(v) {
  if (!v) return ''
  return JSON.stringify([
    v.overallScore,
    v.summary || '',
    v.matchedPoints || [],
    v.missingRequirements || []
  ])
}

watch(() => props.match, (m) => {
  const jobId = props.job?.id
  const next = m ? evaluateFromMatch.value : null
  const cached = jobId != null ? evaluateCache.get(jobId) : null
  // 分数相同但素材内容变了（优势/缺口不同）时不能用旧缓存
  if (cached && next && evaluationKey(cached) === evaluationKey(next)) {
    evaluate.value = cached
    return
  }
  if (jobId != null) evaluateCache.delete(jobId)
  evaluate.value = next
}, { immediate: true })

// 素材变化后，旧岗位的评估/定制简历/求职信全部失效
watch(() => props.materialsVersion, () => {
  evaluateCache.clear()
  tailorCache.clear()
  coverCache.clear()
  evaluate.value = props.match ? evaluateFromMatch.value : null
  tailor.value = null
  cover.value = null
})

// 切换岗位：清掉上一个岗位的生成态，读取新岗位自己的缓存
watch(() => props.job?.id, (jobId, oldJobId) => {
  if (jobId == null || jobId === oldJobId) return
  stopTailorStagePoll()
  stopCoverStagePoll()
  evaluateLoading.value = false
  tailorLoading.value = false
  coverLoading.value = false
  evaluate.value = evaluateCache.get(jobId) || null
  tailor.value = tailorCache.get(jobId) || null
  cover.value = coverCache.get(jobId) || null
  if (!evaluate.value && props.match) evaluate.value = evaluateFromMatch.value
}, { immediate: true })

// 打开弹窗时加载模板与全局证件照（两者都只在首次打开时请求一次）
watch(() => props.modelValue, (open) => {
  if (!open) return
  loadTemplates()
  userStore.fetchPhoto().catch(() => {})
}, { immediate: true })

function selectTemplate(t) {
  if (!t || t.id === activeTemplateId.value) return
  activeTemplateId.value = t.id
  ElMessage({ type: 'success', message: `已切换模板：${t.name}`, duration: 1500 })
}

function applyTemplates(list) {
  templates.value = list
  if (!list.some((t) => t.id === activeTemplateId.value)) {
    activeTemplateId.value = list[0]?.id || ''
  }
}

async function loadTemplates() {
  if (templatesLoading.value) return
  const cached = readCachedResumeTemplates()
  if (cached) applyTemplates(cached)
  // 有缓存先立即展示；同时后台拉取最新列表，避免旧缓存一直不更新
  templatesLoading.value = !cached
  try {
    applyTemplates(await fetchResumeTemplates())
  } catch (e) {
    if (!cached) templates.value = []
  } finally {
    templatesLoading.value = false
  }
}

async function onPhotoChange(dataUrl) {
  try {
    await userStore.updatePhoto(dataUrl)
    ElMessage.success(dataUrl ? '证件照已更新' : '已删除证件照')
  } catch (e) {
    ElMessage.error('证件照保存失败，请稍后重试')
  }
}

// 仅在用户点击「生成」按钮时请求，避免打开弹窗/切换 tab 时重复触发请求与重复报错
async function runActive() {
  if (!props.hasMaterials) {
    ElMessage.warning('请先在我的简历素材中补充内容')
    return
  }
  if (activeTab.value === 'evaluate') {
    await loadEvaluate(true)
    return
  }
  const fns = { tailor: loadTailor, cover: loadCover }
  await (fns[activeTab.value]?.() || Promise.resolve())
}

async function loadEvaluate(force = false) {
  if (!checkJob()) return
  const jobId = props.job.id
  // 与岗位卡片同源的本地匹配结果始终优先：分数稳定，不会因刷新与卡片不一致
  if (props.match) {
    const data = evaluateFromMatch.value
    evaluateCache.set(jobId, data)
    evaluate.value = data
    ElMessage.success(force ? '已按最新素材更新评估' : '已同步卡片匹配结果')
    return
  }
  evaluateLoading.value = true
  startEvaluateStagePoll(jobId)
  try {
    const res = await evaluateApplication(jobId)
    const data = res.data || null
    evaluateCache.set(jobId, data)
    if (props.job?.id === jobId) {
      evaluate.value = data
      if (data) ElMessage.success('匹配评估已更新')
    }
  } finally {
    stopEvaluateStagePoll()
    if (props.job?.id === jobId) evaluateLoading.value = false
  }
}

function startEvaluateStagePoll(jobId) {
  stopEvaluateStagePoll()
  evaluateStageTimer = setInterval(async () => {
    try {
      const res = await getGenerationProgress(jobId, 'evaluate')
      const stage = res.data?.data?.stage
      if (stage && stage !== 'IDLE') evaluateStage.value = stage
    } catch (e) {
      // 进度轮询失败不打断评估请求
    }
  }, 800)
}

function stopEvaluateStagePoll() {
  if (evaluateStageTimer) {
    clearInterval(evaluateStageTimer)
    evaluateStageTimer = null
  }
  evaluateStage.value = ''
}

function startTailorStagePoll(jobId) {
  stopTailorStagePoll()
  tailorStageTimer = setInterval(async () => {
    try {
      const res = await getGenerationProgress(jobId, 'resume')
      const stage = res.data?.data?.stage
      if (stage && stage !== 'IDLE') tailorStage.value = stage
    } catch (e) {
      // 进度轮询失败不打断主生成请求
    }
  }, 800)
}

function stopTailorStagePoll() {
  if (tailorStageTimer) {
    clearInterval(tailorStageTimer)
    tailorStageTimer = null
  }
  tailorStage.value = ''
}

function startCoverStagePoll(jobId) {
  stopCoverStagePoll()
  coverStageTimer = setInterval(async () => {
    try {
      const res = await getGenerationProgress(jobId, 'cover')
      const stage = res.data?.data?.stage
      if (stage && stage !== 'IDLE') coverStage.value = stage
    } catch (e) {
      // 进度轮询失败不打断主生成请求
    }
  }, 800)
}

function stopCoverStagePoll() {
  if (coverStageTimer) {
    clearInterval(coverStageTimer)
    coverStageTimer = null
  }
  coverStage.value = ''
}

onBeforeUnmount(() => {
  stopEvaluateStagePoll()
  stopTailorStagePoll()
  stopCoverStagePoll()
})

async function loadTailor() {
  if (!checkJob()) return
  const jobId = props.job.id
  const started = performance.now()
  tailorLoading.value = true
  startTailorStagePoll(jobId)
  try {
    const res = await tailorApplication(jobId)
    const data = res.data || null
    tailorCache.set(jobId, data)
    if (data) recordGenerationDuration('tailor', (performance.now() - started) / 1000)
    if (props.job?.id === jobId) {
      tailor.value = data
      if (data) ElMessage.success('定制简历已生成')
    }
  } finally {
    if (props.job?.id === jobId) {
      tailorLoading.value = false
      stopTailorStagePoll()
    }
  }
}

async function loadCover() {
  if (!checkJob()) return
  const jobId = props.job.id
  const started = performance.now()
  coverLoading.value = true
  startCoverStagePoll(jobId)
  try {
    const res = await coverLetterApplication(jobId)
    const data = res.data || null
    coverCache.set(jobId, data)
    if (data) recordGenerationDuration('cover', (performance.now() - started) / 1000)
    if (props.job?.id === jobId) {
      cover.value = data
      if (data) ElMessage.success('求职信已生成')
    }
  } finally {
    if (props.job?.id === jobId) {
      coverLoading.value = false
      stopCoverStagePoll()
    }
  }
}

// 导出定制简历：模板 + 全局证件照随请求提交，PDF/Word 使用同一版式
async function downloadTailored(format) {
  if (!tailor.value) return
  tailorExporting.value = true
  tailorExportFormat.value = format
  try {
    await userStore.fetchPhoto().catch(() => {})
    const payload = buildTailorPayload()
    const base = tailorFilename()
    if (format === 'pdf') {
      const res = await exportResumePdf(payload, activeTemplateId.value, base)
      saveBlob(res.data, `${base}.pdf`, 'application/pdf')
    } else {
      const res = await exportResumeDocx(payload, activeTemplateId.value, base)
      saveBlob(res.data, `${base}.docx`, DOCX_MIME)
    }
  } catch (e) {
    ElMessage.error(`${format === 'pdf' ? 'PDF' : 'Word'} 导出失败，请稍后重试`)
  } finally {
    tailorExporting.value = false
    tailorExportFormat.value = ''
  }
}

async function downloadCover(format) {
  if (!cover.value) return
  coverExporting.value = true
  coverExportFormat.value = format
  try {
    const res = format === 'pdf'
      ? await exportCoverLetterPdf(cover.value)
      : await exportCoverLetterDocx(cover.value)
    const base = coverFilename()
    saveBlob(res.data, `${base}.${format}`, format === 'pdf' ? 'application/pdf' : DOCX_MIME)
  } catch (e) {
    ElMessage.error(`${format === 'pdf' ? 'PDF' : 'Word'} 导出失败，请稍后重试`)
  } finally {
    coverExporting.value = false
    coverExportFormat.value = ''
  }
}

function buildTailorPayload() {
  const t = tailor.value || {}
  const clean = (arr) => (arr || []).filter((x) => x && String(x).trim())
  return {
    jobName: t.jobName || props.job?.jobName || '',
    name: t.name || '',
    photo: userStore.photo || '',
    basicInfo: t.basicInfo || '',
    selfEvaluation: t.selfEvaluation || '',
    skills: (t.skills || []).filter((s) => s && s.name && String(s.name).trim()),
    experiences: clean(t.experiences),
    projectHighlights: clean(t.projectHighlights),
    education: clean(t.education),
    certificates: clean(t.certificates)
  }
}

function tailorFilename() {
  const t = tailor.value || {}
  const who = String(t.name || '候选人').trim() || '候选人'
  const job = String(t.jobName || props.job?.jobName || '岗位').trim() || '岗位'
  return `${job}-${who}-简历`
}

function coverFilename() {
  const base = `求职信-${props.job?.companyName || props.job?.jobName || ''}`
    .replace(/[\\/:*?"<>|]/g, '')
    .trim()
  return base || '求职信'
}

function saveBlob(data, filename, mime) {
  const blob = new Blob([data], { type: mime })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = filename
  document.body.appendChild(a)
  a.click()
  document.body.removeChild(a)
  // 部分浏览器在 click 后立即 revoke 会中断下载，延迟释放
  setTimeout(() => URL.revokeObjectURL(url), 1000)
  ElMessage.success(`已导出：${filename}`)
}

function checkJob() {
  if (!props.job?.id) {
    ElMessage.warning('未找到对应岗位，请先在我的收藏中添加该岗位')
    return false
  }
  return true
}

function numScore(s) {
  const n = Number(s)
  return Number.isFinite(n) ? Math.min(100, Math.max(0, n)) : 0
}
</script>

<style scoped>
.assistant-head {
  display: flex;
  align-items: baseline;
  gap: 10px;
  margin-bottom: 8px;
}
.assistant-job {
  font-size: 16px;
  font-weight: 600;
  color: var(--color-text);
}
.assistant-company {
  font-size: 12px;
  color: var(--color-text-3);
}
.assistant-hint {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 10px;
  margin-bottom: 12px;
  padding: 10px 12px;
  background: var(--color-surface-muted);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
  font-size: 12px;
  line-height: 1.6;
  color: var(--color-text-2);
}
.assistant-hint .button {
  flex: none;
}
.tab-body {
  min-height: 300px;
  max-height: 56vh;
  overflow-y: auto;
  padding: 4px 2px;
}
.assistant-tabs :deep(.el-tabs__content) {
  overflow: visible;
}

/* 匹配评估 */
.score-card {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 16px 18px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-surface);
  margin-bottom: 12px;
}
.score-num {
  font-size: 40px;
  font-weight: 700;
  color: var(--color-accent);
  line-height: 1;
}
.score-label {
  font-size: 12px;
  color: var(--color-text-3);
}
.score-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--color-text);
}
.eval-summary {
  color: var(--color-text-2);
  line-height: 1.7;
  font-size: 13px;
  margin: 0 0 14px;
}
.dim-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
  margin-bottom: 14px;
}
.dim-item {
  padding: 10px 12px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
  background: var(--color-surface);
}
.dim-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 4px;
}
.dim-name {
  font-size: 13px;
  font-weight: 600;
  color: var(--color-text-2);
}
.dim-score {
  font-size: 13px;
  font-weight: 600;
  color: var(--color-accent);
}
.dim-remark {
  margin: 6px 0 0;
  font-size: 12px;
  color: var(--color-text-3);
  line-height: 1.5;
}
.point-block {
  margin-bottom: 12px;
}
.point-title {
  font-size: 13px;
  font-weight: 600;
  margin-bottom: 6px;
}
.point-title.hit {
  color: var(--color-success);
}
.point-title.miss {
  color: var(--color-danger);
}
.point-line {
  font-size: 13px;
  line-height: 1.6;
  padding: 4px 0 4px 14px;
  position: relative;
}
.point-line::before {
  content: '';
  position: absolute;
  left: 0;
  top: 11px;
  width: 6px;
  height: 6px;
  border-radius: 50%;
}
.point-line.hit {
  color: var(--color-text-2);
}
.point-line.hit::before {
  background: var(--color-success);
}
.point-line.miss {
  color: var(--color-text-2);
}
.point-line.miss::before {
  background: var(--color-danger);
}

.section-label {
  font-size: 13px;
  font-weight: 600;
  color: var(--color-text);
  margin-bottom: 8px;
}
.block {
  margin-bottom: 14px;
}
.preview-text {
  white-space: pre-wrap;
  color: var(--color-text-2);
  line-height: 1.7;
  background: var(--color-surface-muted);
  border-radius: var(--radius-sm);
  padding: 12px 14px;
  font-size: 13px;
}
.preview-text.line {
  background: transparent;
  border-left: 3px solid var(--color-accent);
  border-radius: 0;
  padding: 5px 0 5px 12px;
  margin-bottom: 6px;
}
.preview-text.line.miss {
  border-left-color: var(--color-danger);
  color: var(--color-danger);
}
/* 定制简历：模板选择 + 证件照 */
.template-block {
  margin-bottom: 16px;
}
.template-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 8px;
}
.template-current {
  font-size: 12px;
  color: var(--color-text-3);
}
.template-list {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 8px;
}
.template-card {
  position: relative;
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 8px;
  font-family: inherit;
  text-align: left;
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: 8px;
  cursor: pointer;
  transition: border-color 0.2s ease, box-shadow 0.2s ease, transform 0.2s var(--ease-out-expo);
}
.template-card:hover {
  border-color: #b7cbd5;
  box-shadow: var(--shadow-sm);
  transform: translateY(-1px);
}
.template-card.is-active {
  border-color: var(--color-accent);
  box-shadow: 0 0 0 1px var(--color-accent) inset;
}
.template-card.is-active:hover {
  transform: none;
}
.template-thumb {
  position: relative;
  display: block;
  height: 52px;
  border: 1px solid var(--color-border);
  border-radius: 4px;
  background: var(--color-surface);
  overflow: hidden;
}
.tp-bar {
  position: absolute;
  left: 0;
  right: 0;
  top: 0;
  height: 12px;
  background: var(--tpl-primary, var(--color-primary));
}
.tp-photo {
  position: absolute;
  right: 6px;
  top: 17px;
  width: 12px;
  height: 15px;
  background: var(--tpl-secondary, #c9d6df);
  border-radius: 1px;
}
.tp-line {
  position: absolute;
  left: 6px;
  height: 2px;
  background: var(--color-border);
  border-radius: 1px;
}
.tp-line.l1 {
  top: 20px;
  width: 55%;
}
.tp-line.l2 {
  top: 26px;
  width: 70%;
}
.tp-line.l3 {
  top: 32px;
  width: 62%;
}
.tpl-sidebar .tp-bar {
  top: 0;
  bottom: 0;
  right: auto;
  width: 30%;
  height: auto;
}
.tpl-sidebar .tp-photo {
  left: 7px;
  right: auto;
  top: 6px;
}
.tpl-sidebar .tp-line {
  left: 36%;
}
.tpl-banner .tp-bar {
  height: 16px;
}
.tpl-banner .tp-photo {
  top: 21px;
}
.tpl-serif .tp-bar {
  top: 6px;
  left: 12%;
  right: 12%;
  height: 2px;
}
.tpl-serif .tp-photo {
  top: 14px;
}
.template-name {
  font-size: 11px;
  color: var(--color-text-2);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.template-empty {
  padding: 10px 0;
  font-size: 12px;
  color: var(--color-text-3);
}
.photo-block {
  margin-bottom: 16px;
}
.resume-preview-block {
  margin-bottom: 16px;
}
.resume-preview-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 8px;
}
.preview-hint {
  font-size: 12px;
  color: var(--color-text-3);
}
.export-actions {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 6px;
}
@media (max-width: 640px) {
  .template-list {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

/* 求职信 */
.material-refs {
  margin: -4px 0 14px;
  border: 1px dashed var(--color-border);
  border-radius: var(--radius-md);
  padding: 8px 12px;
  background: var(--color-surface-muted);
}
.material-refs summary {
  cursor: pointer;
  font-size: 12px;
  color: var(--color-text-3);
}
.material-refs ul {
  margin: 8px 0 0;
  padding: 0;
  list-style: none;
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.material-refs li {
  display: flex;
  gap: 8px;
  font-size: 12px;
  line-height: 1.5;
  color: var(--color-text-2);
}
.material-refs__type {
  flex: none;
  padding: 0 6px;
  border-radius: 999px;
  background: var(--color-primary-bg);
  color: var(--color-primary);
  font-size: 11px;
  line-height: 18px;
}
.material-refs__content {
  min-width: 0;
}
.cover-letter {
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  padding: 20px 22px;
  background: var(--color-surface);
  margin-bottom: 14px;
}
.cover-subject {
  font-size: 15px;
  font-weight: 600;
  text-align: center;
  margin-bottom: 16px;
  color: var(--color-text);
}
.cover-greeting {
  margin-bottom: 10px;
  color: var(--color-text-2);
}
.cover-body {
  white-space: pre-wrap;
  line-height: 2;
  color: var(--color-text-2);
  font-size: 14px;
  text-align: justify;
}
.cover-sign {
  text-align: right;
  margin-top: 24px;
  color: var(--color-text-2);
}

/* ATS */
.ats-checks {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-bottom: 12px;
}
.ats-chip {
  padding: 6px 12px;
  border-radius: 999px;
  font-size: 13px;
  font-weight: 500;
}
.ats-chip.ok {
  background: var(--color-success-bg);
  color: var(--color-success);
}
.ats-chip.warn {
  background: var(--color-danger-bg);
  color: var(--color-danger);
}
.ats-contact {
  font-size: 13px;
  color: var(--color-text-3);
  margin-bottom: 12px;
}
.keyword-block {
  margin-bottom: 14px;
}
.suggest-item {
  font-size: 13px;
  line-height: 1.6;
  color: var(--color-text-2);
  padding: 6px 0 6px 14px;
  position: relative;
}
.suggest-item::before {
  content: '•';
  position: absolute;
  left: 0;
  color: var(--color-accent);
}
.assistant-footer {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}
</style>