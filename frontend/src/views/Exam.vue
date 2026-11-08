<template>
  <div class="exam-page">
    <div class="page-header">
      <h1 class="page-title page-title-anim">AI 笔试助手 · 岗位定向刷题</h1>
    </div>

    <!-- 岗位来源：从「我的收藏」带入，无需手动输入 -->
    <el-card id="exam-source" shadow="never" class="block page-card-anim" style="animation-delay: 0.02s">
      <template #header>当前岗位</template>
      <div v-if="!targetJobs.length && !jobTitle" class="job-empty">
        <p>还没有收藏岗位，先到「我的收藏」添加岗位后再开始笔试</p>
        <BaseButton variant="secondary" @click="router.push('/jobmatch')">去我的收藏</BaseButton>
      </div>
      <template v-else>
        <div v-if="targetJobs.length" class="job-picker">
          <el-select
            v-model="selectedJobId"
            filterable
            placeholder="从我的收藏选择"
            class="job-select"
            @change="onJobChange"
          >
            <el-option v-for="job in targetJobs" :key="job.id" :label="jobOptionLabel(job)" :value="job.id" />
          </el-select>
          <BaseTag v-if="sourceRecordId">已从匹配结果带入</BaseTag>
        </div>
        <div v-if="jobTitle" class="job-brief">
          <span class="job-brief-name">{{ jobTitle }}</span>
          <span v-if="selectedJob?.companyName" class="job-brief-meta">{{ selectedJob.companyName }}</span>
          <span v-if="selectedJob?.location" class="job-brief-meta">{{ selectedJob.location }}</span>
          <span v-if="selectedJob?.salary" class="job-brief-meta">{{ selectedJob.salary }}</span>
        </div>
        <p class="hint-text">岗位 JD 与技术栈自动从我的收藏带入，无需手动输入。</p>
      </template>
    </el-card>

    <!-- 训练设置 -->
    <el-card shadow="never" class="block page-card-anim training-card" style="animation-delay: 0.08s">
      <template #header>
        <div class="settings-head">
          <span>训练设置</span>
          <span v-if="bankStatus" class="bank-meta">
            真题目库 {{ bankStatus.total }} 题 · {{ bankStatus.companyCount }} 家公司 · 每日 08:00 自动更新
          </span>
          <span v-else-if="bankLoadFailed" class="bank-meta">题库信息加载失败，正在自动重试…</span>
          <span v-else class="bank-meta">题库加载中…</span>
        </div>
      </template>
      <el-form :disabled="generating">
        <div class="settings-grid">
          <div class="setting-line">
            <div class="setting-block">
              <el-tooltip content="自由练习不限时；限时模拟倒计时并自动交卷" placement="top">
                <span class="setting-label">训练模式</span>
              </el-tooltip>
              <el-radio-group v-model="paperType" @change="onModeChange">
                <el-radio-button value="practice">自由练习</el-radio-button>
                <el-radio-button value="mock">限时模拟</el-radio-button>
              </el-radio-group>
            </div>
            <div class="setting-block">
              <el-tooltip content="题量越大，组卷和补充解析耗时越长" placement="top">
                <span class="setting-label">题目数量</span>
              </el-tooltip>
              <el-input-number v-model="count" :min="1" :max="50" />
            </div>
            <div class="setting-block">
              <el-tooltip content="不限时会尽量混合不同难度" placement="top">
                <span class="setting-label">难度</span>
              </el-tooltip>
              <el-select v-model="difficulty" clearable placeholder="不限（自动梯度）" style="width: 180px">
                <el-option label="基础" value="basic" />
                <el-option label="中级" value="middle" />
                <el-option label="高级" value="advanced" />
              </el-select>
            </div>
            <div v-if="paperType === 'mock'" class="setting-block">
              <span class="setting-label">限时时长</span>
              <el-input-number v-model="durationMinutes" :min="10" :max="180" :step="5" />
              <span class="unit">分钟</span>
            </div>
          </div>

          <div v-if="paperType === 'mock'" class="mock-presets">
            <button
              v-for="preset in MOCK_PRESETS"
              :key="preset.label"
              type="button"
              class="preset-chip"
              :class="{ 'is-active': count === preset.count && durationMinutes === preset.minutes }"
              :disabled="generating"
              @click="applyMockPreset(preset)"
            >{{ preset.label }}</button>
          </div>

          <div class="setting-line setting-line--knowledge">
            <el-tooltip content="可多选岗位技术栈作为限定知识点；留空表示综合出题" placement="top">
              <span class="setting-label">知识点</span>
            </el-tooltip>
            <div class="knowledge-block">
              <div v-if="techStack.techStacks.length" class="knowledge-chips">
                <button
                  v-for="tag in techStack.techStacks"
                  :key="tag"
                  type="button"
                  class="knowledge-chip"
                  :class="{ 'is-active': knowledgeTags.includes(tag) }"
                  :disabled="generating"
                  @click="toggleKnowledgeTag(tag)"
                >{{ tag }}</button>
              </div>
              <el-input v-model="customKnowledge" clearable placeholder="也可手动输入知识点，留空表示综合出题" />
            </div>
          </div>
        </div>
      </el-form>

      <div class="training-summary">
        <span class="summary-text">{{ configSummary }}</span>
        <span v-if="hasSource" class="summary-bank" :class="{ 'is-empty': !bankMatchCount && !extracting }">
          <template v-if="extracting">正在匹配真题…</template>
          <template v-else-if="bankMatchCount">已匹配 {{ bankMatchCount >= 200 ? '200+' : bankMatchCount }} 道真题</template>
          <template v-else>暂无匹配真题，将由 AI 出题</template>
        </span>
      </div>

      <div class="training-actions">
        <BaseButton
          :disabled="!hasSource || extracting || techStack.itSupported === false || generating"
          :loading="generating"
          @click="doGenerate"
        >生成笔试试卷</BaseButton>
      </div>
      <GenerationProgress
        v-if="generating"
        :active="generating"
        :stage="generationStage"
        :steps="EXAM_STEPS"
        :ranges="EXAM_RANGES"
        kind="exam"
        label="正在生成笔试试卷"
        :estimate="45"
      />
      <p v-if="!hasSource" class="action-hint">请先从「我的收藏」选择岗位。</p>
      <p v-else-if="techStack.itSupported === false" class="action-hint">AI 笔试助手仅支持 IT 行业技术岗位。</p>
    </el-card>

    <el-card v-if="techStack.category || techStack.itSupported === false" shadow="never" class="block page-card-anim" style="animation-delay: 0.14s">
      <template #header>岗位画像识别</template>
      <el-alert
        v-if="techStack.itSupported === false"
        type="warning"
        :closable="false"
        show-icon
        :title="techStack.unsupportedReason || 'AI 笔试助手仅支持 IT 行业技术岗位'"
      />
      <template v-else>
        <div class="category-row">
          <span class="category-label">岗位分类</span>
          <BaseTag>{{ techStack.codingRequired ? '计算机技术岗（含编程题）' : '计算机技术岗（不含编程题）' }}</BaseTag>
        </div>
        <p v-if="techStack.codingRequired" class="coding-rule-hint">
          该岗位涉及编程能力，本卷将自动包含 1-2 道编程题。
        </p>
        <p v-else class="coding-rule-hint">
          该岗位技术栈未包含编程语言，本卷不生成编程题。
        </p>
        <div v-if="techStack.techStacks && techStack.techStacks.length">
          <span class="category-label">技术栈</span>
          <div class="mt-2 flex flex-wrap gap-2">
            <BaseTag v-for="s in techStack.techStacks" :key="s">{{ s }}</BaseTag>
          </div>
        </div>
      </template>
    </el-card>

    <!-- 历史记录 + 错题本 -->
    <el-tabs v-model="tab" class="records-tabs" v-reveal="100">
      <el-tab-pane label="历史记录" name="history">
        <el-table :data="history" row-key="id" @row-click="openHistory" class="clickable">
          <el-table-column prop="jobTitle" label="岗位" show-overflow-tooltip />
          <el-table-column prop="paperType" label="类型" width="110">
            <template #default="{ row }">{{ row.paperType === 'mock' ? '限时模拟' : '自由练习' }}</template>
          </el-table-column>
          <el-table-column prop="totalCount" label="题数" width="80" />
          <el-table-column label="得分" width="120">
            <template #default="{ row }">
              <span v-if="row.userScore != null">{{ row.userScore }} / {{ row.totalScore }}</span>
              <BaseTag v-else>未作答</BaseTag>
            </template>
          </el-table-column>
          <el-table-column prop="createTime" label="创建时间" width="180" />
          <template #empty>
            <div class="table-empty">
              <p>暂无笔试记录，先生成一份岗位试卷开始练习</p>
              <BaseButton variant="secondary" @click="scrollToSource">去生成试卷</BaseButton>
            </div>
          </template>
        </el-table>
      </el-tab-pane>
      <el-tab-pane label="错题本" name="wrong">
        <div class="wrong-filter">
          <el-select v-model="wrongJobTitle" clearable placeholder="按岗位筛选" style="width: 180px" @change="loadWrong">
            <el-option v-for="j in wrongJobs" :key="j.jobTitle" :label="j.jobTitle" :value="j.jobTitle" />
          </el-select>
          <el-select v-model="wrongType" clearable placeholder="题型" style="width: 130px" @change="loadWrong">
            <el-option label="单选题" value="single" />
            <el-option label="多选题" value="multiple" />
            <el-option label="简答题" value="short_answer" />
            <el-option label="编程题" value="coding" />
          </el-select>
          <el-select v-model="wrongDifficulty" clearable placeholder="难度" style="width: 130px" @change="loadWrong">
            <el-option label="基础" value="basic" />
            <el-option label="中级" value="middle" />
            <el-option label="高级" value="advanced" />
          </el-select>
          <el-input v-model="wrongKeyword" clearable placeholder="搜索知识点/题干" style="width: 180px"
            @keyup.enter="loadWrong" @clear="loadWrong" />
          <BaseButton variant="secondary" @click="loadWrong">查询</BaseButton>
        </div>
        <div class="job-chips">
          <BaseTag
            v-for="j in wrongJobs"
            :key="j.jobTitle"
            :active="wrongJobTitle === j.jobTitle"
            class="cursor-pointer"
            @click="toggleJob(j)"
          >
            {{ j.jobTitle }}（{{ j.count }}）
          </BaseTag>
        </div>
        <el-table :data="wrongList" row-key="id" @row-click="openWrong" class="clickable">
          <el-table-column prop="jobTitle" label="岗位名称" width="180" show-overflow-tooltip />
          <el-table-column prop="knowledgePoint" label="知识点" show-overflow-tooltip />
          <el-table-column prop="questionType" label="题型" width="100">
            <template #default="{ row }">{{ typeLabel(row.questionType) }}</template>
          </el-table-column>
          <el-table-column prop="difficulty" label="难度" width="80">
            <template #default="{ row }">{{ difficultyLabel(row.difficulty) }}</template>
          </el-table-column>
          <el-table-column prop="errorReason" label="错误原因" show-overflow-tooltip />
          <el-table-column prop="timesWrong" label="错次数" width="90" />
          <el-table-column prop="status" label="状态" width="90">
            <template #default="{ row }">
              <BaseTag>
                {{ row.status === 'mastered' ? '已掌握' : '待巩固' }}
              </BaseTag>
            </template>
          </el-table-column>
          <template #empty>
            <div class="table-empty">
              <p>暂无错题，完成一次笔试后错题会自动归档</p>
              <BaseButton variant="secondary" @click="scrollToSource">去生成试卷</BaseButton>
            </div>
          </template>
        </el-table>
      </el-tab-pane>
    </el-tabs>

    <!-- 错题详情 -->
    <el-dialog v-model="wrongDialogVisible" title="错题详情" width="720px" class="wrong-dialog">
      <template v-if="wrongDetail">
        <div class="wrong-question">
          <p class="q-stem">{{ wrongDetail.question?.stem }}</p>
          <div v-if="wrongDetail.question?.options?.length" class="q-options">
            <p v-for="opt in wrongDetail.question.options" :key="opt" class="q-option">{{ opt }}</p>
          </div>
          <el-divider content-position="left">我的答案</el-divider>
          <p class="q-answer wrong">{{ wrongDetail.userAnswer || '（未作答）' }}</p>
          <el-divider content-position="left">正确答案</el-divider>
          <p class="q-answer right">{{ wrongDetail.standardAnswer || '—' }}</p>
          <el-divider content-position="left">解析</el-divider>
          <p class="q-analysis">{{ wrongDetail.analysis || '—' }}</p>
          <el-divider content-position="left">延伸学习建议</el-divider>
          <p class="q-analysis">{{ wrongDetail.extension || '—' }}</p>
          <el-divider content-position="left">重新作答</el-divider>
          <el-input v-model="redoAnswer" type="textarea" :rows="3" placeholder="输入你的新答案后点击提交" />
          <div class="redo-actions">
            <BaseButton :loading="redoing" @click="doRedo">提交重做</BaseButton>
            <BaseButton variant="secondary" @click="doMastered">标记已掌握</BaseButton>
          </div>
          <el-alert v-if="redoResult" :type="redoResult.correct ? 'success' : 'warning'" :closable="false"
            :title="redoResult.correct ? '回答正确！已标记掌握' : '仍有偏差，请结合解析继续巩固'" class="redo-alert"
            :description="redoResult.analysis" />
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onUnmounted, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { useRoute, useRouter } from 'vue-router'
import {
  extractTechStack, generateExam, getExamHistory, getWrongList, getWrongDetail, redoWrong, markMastered,
  getWrongJobs, getBankStatus, getBankMatch, getExamProgress
} from '@/api/exam'
import { listJobs } from '@/api/jobmatch'
import { useLinkageStore } from '@/stores/linkage'
import BaseTag from '@/components/BaseTag.vue'
import BaseButton from '@/components/BaseButton.vue'
import GenerationProgress from '@/components/GenerationProgress.vue'
import { recordGenerationDuration } from '@/utils/generationTiming'

const route = useRoute()
const router = useRouter()
const linkage = useLinkageStore()

const sourceRecordId = ref(null)
const jobTitle = ref('')
const jdText = ref('')
const targetJobs = ref([])
const selectedJobId = ref(null)
const selectedJob = computed(() => targetJobs.value.find((j) => j.id === selectedJobId.value) || null)
const hasSource = computed(() => !!(sourceRecordId.value || jdText.value.trim()))

const paperType = ref('practice')
const count = ref(10)
const difficulty = ref('')
const knowledgeTags = ref([])
const customKnowledge = ref('')
const knowledgePoint = computed(() =>
  [...new Set([...knowledgeTags.value, customKnowledge.value.trim()].filter(Boolean))].join('、')
)
const durationMinutes = ref(60)

const extracting = ref(false)
const generating = ref(false)
const techStack = reactive({
  jobTitle: '',
  category: '',
  techStacks: [],
  itSupported: null,
  codingRequired: false,
  unsupportedReason: ''
})

const bankStatus = ref(null)
const bankLoadFailed = ref(false)
const bankMatchCount = ref(0)

const generationStage = ref('')
const generationStartedAt = ref(0)
let progressTimer = null
let extractSeq = 0
let bankMatchTimer = null

const EXAM_STEPS = [
  { key: 'PREPARING', label: '识别岗位' },
  { key: 'MATCHING', label: '匹配真题' },
  { key: 'ENRICHING', label: '补充答案与用例' },
  { key: 'GENERATING', label: 'AI 补题' },
  { key: 'SAVING', label: '保存试卷' }
]
const EXAM_RANGES = {
  PREPARING: [5, 15],
  MATCHING: [15, 30],
  ENRICHING: [30, 70],
  GENERATING: [70, 92],
  SAVING: [92, 99]
}
const EXAM_KIND = 'exam'
const MOCK_PRESETS = [
  { label: '30 分钟 · 15 题', minutes: 30, count: 15 },
  { label: '60 分钟 · 20 题', minutes: 60, count: 20 },
  { label: '90 分钟 · 30 题', minutes: 90, count: 30 }
]

const configSummary = computed(() => {
  const mode = paperType.value === 'mock' ? '限时模拟' : '自由练习'
  const parts = [
    mode,
    `${count.value} 题`,
    difficulty.value ? `难度：${difficultyLabel(difficulty.value)}` : '难度不限',
    knowledgePoint.value ? `知识点：${knowledgePoint.value}` : '知识点不限'
  ]
  if (paperType.value === 'mock') {
    parts.push(`${durationMinutes.value} 分钟`)
  }
  return parts.join(' · ')
})

function toggleKnowledgeTag(tag) {
  if (knowledgeTags.value.includes(tag)) {
    knowledgeTags.value = knowledgeTags.value.filter((t) => t !== tag)
  } else {
    knowledgeTags.value = [...knowledgeTags.value, tag]
  }
}

function applyMockPreset(preset) {
  count.value = preset.count
  durationMinutes.value = preset.minutes
}

const tab = ref('history')
const history = ref([])
const wrongList = ref([])
const wrongType = ref('')
const wrongDifficulty = ref('')
const wrongJobTitle = ref('')
const wrongKeyword = ref('')
const wrongJobs = ref([])

const wrongDialogVisible = ref(false)
const wrongDetail = ref(null)
const redoAnswer = ref('')
const redoing = ref(false)
const redoResult = ref(null)
const currentWrongId = ref(null)

onMounted(async () => {
  await loadTargetJobs()
  const recordId = route.query.recordId
  if (recordId) sourceRecordId.value = Number(recordId)

  // 跨模块联动：从目标岗位 / 面试复盘带入岗位，不再要求手动输入
  const ctx = linkage.consume()
  const ctxKnowledge = ctx.knowledgePoints || ''
  const matched = ctx.jobTitle ? targetJobs.value.find((j) => j.jobName === ctx.jobTitle) : null
  // 进入页面时的自动识别静默处理，不再弹「岗位信息已识别」
  if (matched) {
    applyJob(matched, true)
  } else if (ctx.jobTitle || ctx.jdText) {
    jobTitle.value = ctx.jobTitle || ''
    jdText.value = ctx.jdText || ''
    techStack.codingRequired = inferCodingRequired(jobTitle.value, [])
    doExtract(true)
  } else if (recordId) {
    doExtract(true)
  } else if (targetJobs.value.length) {
    applyJob(targetJobs.value[0], true)
  }
  if (ctxKnowledge) {
    customKnowledge.value = ctxKnowledge
    paperType.value = 'practice'
  }
  loadHistory()
  loadWrong()
  loadWrongJobs()
  loadBankStatus()
})

onUnmounted(() => {
  stopProgressPolling()
  if (bankMatchTimer) clearTimeout(bankMatchTimer)
})

watch([difficulty, knowledgePoint], () => {
  if (bankMatchTimer) clearTimeout(bankMatchTimer)
  bankMatchTimer = setTimeout(() => {
    bankMatchTimer = null
    loadBankMatch()
  }, 300)
})

function splitStacks(text) {
  return String(text || '').split(/[,，、/|]+/).map((s) => s.trim()).filter(Boolean)
}

/** 与后端一致的兜底判断，保证页面永远明确显示含 / 不含编程题 */
function inferCodingRequired(titleText, stacks) {
  const title = String(titleText || '').toLowerCase()
  const stackText = (stacks || []).join(' ').toLowerCase()
  const languageStack = /(java|python|golang|go语言|c\+\+|c#|javascript|typescript|vue|react|node|spring|django|flask|kotlin|swift|rust|php|scala|ruby|\.net)/
  if (languageStack.test(stackText)) return true
  const codingTitle = /(测试开发|自动化测试|开发|研发|程序员|前端|后端|服务端|全栈|算法|客户端|移动端|android|ios|嵌入式|大模型|机器学习|深度学习|ai应用|数据开发|大数据|数仓|数据仓库|平台|基础设施|sre|devops|安全研发|渗透|架构师|java|python|golang|go语言|c\+\+|c#|php|node|rust|kotlin|swift|javascript|typescript)/
  return codingTitle.test(title)
}

function jobOptionLabel(job) {
  return job.companyName ? `${job.jobName} · ${job.companyName}` : job.jobName
}

function applyJob(job, silent = false) {
  if (!job) return
  selectedJobId.value = job.id
  sourceRecordId.value = null
  jobTitle.value = job.jobName || ''
  jdText.value = job.jobRequirement || ''
  knowledgeTags.value = []
  customKnowledge.value = ''
  techStack.jobTitle = job.jobName || ''
  techStack.category = ''
  const stacks = splitStacks(job.techStack)
  techStack.techStacks = stacks
  techStack.codingRequired = inferCodingRequired(job.jobName, stacks)
  techStack.itSupported = null
  techStack.unsupportedReason = ''
  doExtract(silent)
}

function onJobChange(id) {
  const job = targetJobs.value.find((j) => j.id === id)
  if (job) applyJob(job)
}

async function loadTargetJobs() {
  try {
    const res = await listJobs()
    targetJobs.value = res.data || []
  } catch (e) {
    targetJobs.value = []
  }
}

async function loadBankStatus(retry = 0) {
  try {
    const res = await getBankStatus()
    bankStatus.value = res.data || null
    bankLoadFailed.value = !bankStatus.value
    if (!bankStatus.value && retry < 3) {
      setTimeout(() => loadBankStatus(retry + 1), 2000)
    }
  } catch (e) {
    bankStatus.value = null
    bankLoadFailed.value = true
    // 后端重启/短暂抖动时自动重试，避免一直停在"加载中"
    if (retry < 5) {
      setTimeout(() => loadBankStatus(retry + 1), 2500)
    }
  }
}

async function loadBankMatch() {
  if (!jobTitle.value || !techStack.techStacks.length) {
    bankMatchCount.value = 0
    return
  }
  try {
    const res = await getBankMatch({
      jobTitle: jobTitle.value,
      techStack: techStack.techStacks.join(','),
      knowledgePoint: knowledgePoint.value || undefined,
      difficulty: difficulty.value || undefined
    })
    bankMatchCount.value = Number(res.data) || 0
  } catch (e) {
    bankMatchCount.value = 0
  }
}

function onModeChange() {
  if (paperType.value === 'mock') {
    count.value = 20
    durationMinutes.value = 60
  } else {
    count.value = 10
  }
}

function scrollToSource() {
  document.getElementById('exam-source')?.scrollIntoView({ behavior: 'smooth', block: 'start' })
}

function jdParams() {
  return sourceRecordId.value ? { sourceRecordId: sourceRecordId.value } : { jobTitle: jobTitle.value, jdText: jdText.value }
}

async function doExtract(silent = false) {
  if (!sourceRecordId.value && !jdText.value.trim()) {
    ElMessage.warning('请先从「我的收藏」选择岗位')
    return
  }
  const seq = ++extractSeq
  extracting.value = true
  try {
    const res = await extractTechStack(jdParams())
    if (seq !== extractSeq) return
    techStack.jobTitle = res.data?.jobTitle || ''
    techStack.category = res.data?.category || ''
    techStack.techStacks = res.data?.techStacks || []
    techStack.itSupported = res.data?.itSupported !== false
    techStack.codingRequired = typeof res.data?.codingRequired === 'boolean'
      ? res.data.codingRequired
      : inferCodingRequired(jobTitle.value, techStack.techStacks)
    techStack.unsupportedReason = res.data?.unsupportedReason || ''
    if (techStack.jobTitle && !jobTitle.value) {
      jobTitle.value = techStack.jobTitle
    }
    if (techStack.itSupported === false) {
      ElMessage.warning(techStack.unsupportedReason || 'AI 笔试助手仅支持 IT 行业技术岗位')
    } else if (techStack.category === 'technical' && !techStack.techStacks.length) {
      ElMessage.warning('未提取到技术栈，可尝试重新选择岗位')
    } else if (!silent && techStack.category) {
      ElMessage.success('岗位信息已识别')
    }
    loadBankMatch()
  } finally {
    if (seq === extractSeq) extracting.value = false
  }
}

async function doGenerate() {
  if (!hasSource.value) {
    ElMessage.warning('请先从「我的收藏」选择岗位')
    return
  }
  if (techStack.itSupported === false) {
    ElMessage.warning(techStack.unsupportedReason || 'AI 笔试助手仅支持 IT 行业技术岗位')
    return
  }
  const requestId = Date.now()
  const data = {
    paperType: paperType.value,
    count: count.value,
    difficulty: difficulty.value || null,
    durationSeconds: paperType.value === 'mock' ? durationMinutes.value * 60 : null,
    requestId,
    ...jdParams()
  }
  if (knowledgePoint.value.trim()) {
    data.mode = 'knowledge'
    data.knowledgePoint = knowledgePoint.value.trim()
  }
  generating.value = true
  generationStartedAt.value = Date.now()
  generationStage.value = 'PREPARING'
  startProgressPolling(requestId)
  try {
    const res = await generateExam(data)
    const paperId = res.data?.id
    if (paperId) {
      const realCount = Number(res.data?.realCount) || 0
      recordGenerationDuration(EXAM_KIND, (Date.now() - generationStartedAt.value) / 1000)
      ElMessage.success(realCount > 0 ? `试卷生成成功，含 ${realCount} 道大厂真题` : '试卷生成成功')
      router.push(`/exam/paper/${paperId}`)
    }
  } finally {
    stopProgressPolling()
    generationStage.value = ''
    generating.value = false
  }
}

function startProgressPolling(requestId) {
  stopProgressPolling()
  const poll = async () => {
    try {
      const res = await getExamProgress({ requestId })
      const data = res.data?.data || {}
      if (data.active && data.stage) {
        generationStage.value = data.stage
      }
    } catch (e) {
      // 轮询失败不打扰用户，按钮 loading 仍在
    }
  }
  poll()
  progressTimer = setInterval(poll, 800)
}

function stopProgressPolling() {
  if (progressTimer) {
    clearInterval(progressTimer)
    progressTimer = null
  }
}

async function loadHistory() {
  const res = await getExamHistory()
  history.value = res.data || []
}

async function loadWrong() {
  const params = {}
  if (wrongJobTitle.value) params.jobTitle = wrongJobTitle.value
  if (wrongType.value) params.type = wrongType.value
  if (wrongDifficulty.value) params.difficulty = wrongDifficulty.value
  if (wrongKeyword.value) params.keyword = wrongKeyword.value
  const res = await getWrongList(params)
  wrongList.value = res.data || []
}

async function loadWrongJobs() {
  const res = await getWrongJobs()
  wrongJobs.value = res.data || []
}

function toggleJob(j) {
  wrongJobTitle.value = wrongJobTitle.value === j.jobTitle ? '' : j.jobTitle
  loadWrong()
}

function openHistory(row) {
  if (row.userScore != null || row.status === 'graded') {
    router.push(`/exam/report/${row.id}`)
  } else {
    router.push(`/exam/paper/${row.id}`)
  }
}

async function openWrong(row) {
  currentWrongId.value = row.id
  redoAnswer.value = ''
  redoResult.value = null
  const res = await getWrongDetail(row.id)
  wrongDetail.value = res.data
  wrongDialogVisible.value = true
}

async function doRedo() {
  if (!redoAnswer.value.trim()) {
    ElMessage.warning('请输入你的答案')
    return
  }
  redoing.value = true
  try {
    const res = await redoWrong(currentWrongId.value, { answer: redoAnswer.value.trim() })
    redoResult.value = res.data
    loadWrong()
  } finally {
    redoing.value = false
  }
}

async function doMastered() {
  await markMastered(currentWrongId.value)
  ElMessage.success('已标记掌握')
  loadWrong()
  wrongDialogVisible.value = false
}

function typeLabel(t) {
  return { single: '单选题', multiple: '多选题', short_answer: '简答题', coding: '编程题' }[t] || t
}
function difficultyLabel(d) {
  return { basic: '基础', middle: '中级', advanced: '高级' }[d] || d
}
</script>

<style scoped>
.exam-page {
  max-width: 1200px;
  margin: 0 auto;
  padding: 24px;
  box-sizing: border-box;
}
.page-header {
  align-items: flex-end;
  margin-bottom: 20px;
}
.page-title {
  margin: 0;
  font-size: 22px;
  font-weight: 600;
  letter-spacing: -0.01em;
  color: var(--color-text);
}
.block {
  margin-bottom: 16px;
}
.job-picker {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
  margin-bottom: 12px;
}
.job-select {
  width: 380px;
  max-width: 100%;
}
.job-brief {
  display: flex;
  align-items: baseline;
  gap: 12px;
  flex-wrap: wrap;
  margin-bottom: 8px;
}
.job-brief-name {
  font-size: 15px;
  font-weight: 600;
  color: var(--color-text);
}
.job-brief-meta {
  font-size: 12px;
  color: var(--color-text-3);
}
.job-empty {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
}
.job-empty p {
  margin: 0;
  font-size: 13px;
  color: var(--color-text-2);
}
.settings-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
}
.bank-meta {
  font-size: 12px;
  color: var(--color-text-3);
  font-variant-numeric: tabular-nums;
}
.mock-presets,
.knowledge-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
.preset-chip,
.knowledge-chip {
  padding: 5px 10px;
  border: 1px solid var(--color-border);
  border-radius: 999px;
  background: var(--color-surface);
  color: var(--color-text-2);
  font-family: inherit;
  font-size: 12px;
  cursor: pointer;
  transition: color 0.18s ease, background-color 0.18s ease, border-color 0.18s ease;
}
.preset-chip:hover,
.knowledge-chip:hover {
  border-color: var(--color-border-strong);
  background: var(--color-surface-muted);
}
.preset-chip.is-active,
.knowledge-chip.is-active {
  border-color: var(--color-primary);
  background: var(--color-primary-bg);
  color: var(--color-primary);
}
.preset-chip:disabled,
.knowledge-chip:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}
.field-hint {
  width: 100%;
  margin-top: 4px;
  font-size: 12px;
  color: var(--color-text-3);
}
.knowledge-block {
  width: 100%;
}
.knowledge-chips {
  margin-bottom: 8px;
}
.training-summary {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
  margin-top: 4px;
  padding: 10px 12px;
  border-radius: var(--radius-sm);
  background: var(--color-surface-muted);
  font-size: 12px;
  color: var(--color-text-2);
}
.summary-text {
  min-width: 0;
}
.summary-bank {
  flex: none;
  color: var(--color-accent);
}
.summary-bank.is-empty {
  color: var(--color-text-3);
}
.training-actions {
  display: flex;
  justify-content: center;
  margin: 14px 0 4px;
}
.training-card :deep(.el-form-item__content) {
  flex-wrap: wrap;
  gap: 8px;
}
.hint-text {
  color: var(--color-text-2);
  font-size: 13px;
}
.unit {
  margin-left: 8px;
  color: var(--color-text-3);
  font-size: 12px;
}
.action-hint {
  margin: 8px 0 0;
  text-align: center;
  font-size: 12px;
  color: var(--color-text-3);
}
.category-row {
  margin-bottom: 12px;
}
.coding-rule-hint {
  margin: 8px 0 0;
  font-size: 12px;
  color: var(--color-text-3);
}
.category-label {
  display: inline-block;
  width: 60px;
  color: var(--color-text-3);
  font-size: 12px;
}
.records-tabs {
  background: transparent;
  padding: 0;
}
.clickable :deep(.el-table__row) {
  cursor: pointer;
}
.wrong-filter {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 12px;
  align-items: center;
}
.job-chips {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
}
.q-stem {
  color: var(--color-text);
  font-weight: 500;
  margin: 0 0 12px;
  line-height: 1.7;
}
.q-options {
  margin: 0 0 8px;
}
.q-option {
  margin: 4px 0;
  color: var(--color-text-2);
}
.q-answer {
  margin: 0 0 8px;
  padding: 8px 12px;
  border-radius: var(--radius-sm);
  white-space: pre-wrap;
}
.q-answer.wrong {
  background: var(--color-danger-bg);
  color: var(--color-danger);
}
.q-answer.right {
  background: var(--color-success-bg);
  color: var(--color-success);
}
.q-analysis {
  margin: 0;
  color: var(--color-text-2);
  line-height: 1.7;
  white-space: pre-wrap;
}
.redo-actions {
  margin-top: 12px;
  display: flex;
  gap: 8px;
}
.redo-alert {
  margin-top: 12px;
}
.wrong-dialog :deep(.el-dialog__body) {
  max-height: 70vh;
  overflow-y: auto;
}
/* 训练设置：两行布局 + 高级折叠 */
.settings-grid {
  display: flex;
  flex-direction: column;
  gap: 14px;
}
.setting-line {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 12px 20px;
}
.setting-block {
  display: flex;
  align-items: center;
  gap: 8px;
}
.setting-label {
  flex: none;
  font-size: 13px;
  color: var(--color-text-2);
  white-space: nowrap;
}
.setting-line--knowledge {
  align-items: flex-start;
}
.setting-line--knowledge .setting-label {
  line-height: 32px;
}
.setting-line--knowledge .knowledge-block {
  flex: 1;
  min-width: 240px;
}
@media (max-width: 768px) {
  .exam-page {
    padding: 16px;
  }
  .settings-head {
    align-items: flex-start;
  }
  .setting-line {
    gap: 10px 12px;
  }
  .setting-block {
    flex-wrap: wrap;
  }
  .setting-line--knowledge .knowledge-block {
    min-width: 0;
  }
  :deep(.el-dialog) {
    width: 92vw !important;
  }
}
</style>
