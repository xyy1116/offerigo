<template>
  <div class="paper-page">
    <div class="paper-header" v-if="paper" v-reveal>
      <div class="header-left">
        <button class="button button--link back-btn" @click="goBack"><el-icon><Back /></el-icon>返回上一页</button>
        <h1 class="paper-title">{{ paper.jobTitle || '岗位笔试' }}</h1>
        <div class="paper-meta">
          <el-tag size="small">{{ paper.paperType === 'mock' ? '限时模拟' : '自由练习' }}</el-tag>
          <span class="meta-text">共 {{ paper.questions?.length || 0 }} 题</span>
          <span v-if="paper.realCount" class="meta-text">大厂真题 {{ paper.realCount }} 道</span>
          <span v-if="paper.aiCount" class="meta-text">AI 题 {{ paper.aiCount }} 道</span>
          <span v-if="typeSummary" class="meta-text">题型：{{ typeSummary }}</span>
          <span v-if="paper.techStack" class="meta-text">技术栈：{{ paper.techStack }}</span>
        </div>
      </div>
      <div class="header-right" v-if="remaining > 0">
        <span class="countdown" :class="{ urgent: remaining < 600 }">{{ formatTime(remaining) }}</span>
        <button class="button" :disabled="submitting" :class="{ 'is-loading': submitting }" @click="submit">交卷</button>
      </div>
      <div class="header-right" v-else>
        <button class="button" :disabled="submitting" :class="{ 'is-loading': submitting }" @click="submit">交卷</button>
      </div>
    </div>

    <div class="paper-source-note" v-if="paper && paper.realCount">
      本卷包含 {{ paper.realCount }} 道公开大厂面经真题，参考答案由 AI 整理；其余为 AI 生成题。
    </div>

    <div class="question-list" v-if="paper">
      <el-card v-for="(q, qi) in paper.questions" :key="q.questionId" shadow="never" class="question" v-reveal="qi < 3 ? qi * 40 : 120">
        <div class="q-head">
          <span class="q-num">{{ qi + 1 }}.</span>
          <el-tag v-if="q.sourceType === 'real'" size="small" type="success">大厂真题 · {{ companyLabel(q) }}</el-tag>
          <el-tag v-else size="small" type="info">AI 生成</el-tag>
          <el-tag size="small" type="info">{{ typeLabel(q.type) }}</el-tag>
          <el-tag size="small" type="warning">{{ difficultyLabel(q.difficulty) }}</el-tag>
          <span class="q-kp">{{ q.knowledgePoint }}</span>
        </div>
        <p class="q-stem">{{ q.stem }}</p>
        <div v-if="q.sourceType === 'real' && (q.sourceName || q.sourceUrl)" class="q-source-line">
          <span class="q-source-label">来源</span>
          <span class="q-source-name">{{ q.sourceName || '公开面经' }}</span>
          <span v-if="q.sourceUrl" class="q-source-url">{{ prettyUrl(q.sourceUrl) }}</span>
        </div>

        <div v-if="q.options && q.options.length" class="q-options">
          <el-radio-group v-if="q.type === 'single'" v-model="answers[qi].answer">
            <div v-for="(opt, oi) in q.options" :key="oi" class="opt-row">
              <el-radio :value="letter(oi)" class="opt-item">
                <span class="opt-letter">{{ letter(oi) }}.</span>
                <span class="opt-text">{{ stripLetter(opt) }}</span>
              </el-radio>
            </div>
          </el-radio-group>
          <el-checkbox-group v-else v-model="answers[qi].multi">
            <div v-for="(opt, oi) in q.options" :key="oi" class="opt-row">
              <el-checkbox :value="letter(oi)" class="opt-item">
                <span class="opt-letter">{{ letter(oi) }}.</span>
                <span class="opt-text">{{ stripLetter(opt) }}</span>
              </el-checkbox>
            </div>
          </el-checkbox-group>
        </div>

        <el-input v-else-if="q.type === 'short_answer'" v-model="answers[qi].answer" type="textarea"
          :rows="4" placeholder="请输入你的答案" />
        <div v-else class="coding-block">
          <div class="coding-toolbar">
            <el-select v-model="answers[qi].language" size="small" class="code-lang">
              <el-option label="JavaScript" value="javascript" />
              <el-option label="Java" value="java" />
            </el-select>
            <span class="coding-hint">
              {{ answers[qi].language === 'java'
                ? '编写 public class Main，从标准输入读取，向标准输出打印'
                : '使用 process.stdin 读取标准输入，用 console.log 输出' }}
            </span>
            <div class="editor-actions">
              <button
                class="ide-btn ide-btn--run"
                :disabled="answers[qi].running || !answers[qi].answer.trim()"
                :title="q.codeTestCases && q.codeTestCases.length ? '运行测试用例' : '本题暂无测试用例，将使用自定义输入运行'"
                @click.stop="handleRun(qi)"
              >
                <Play :size="14" aria-hidden="true" />
                <span>{{ answers[qi].running ? '运行中' : '运行' }}</span>
              </button>
              <button
                class="ide-btn ide-btn--debug"
                :disabled="answers[qi].debugRunning || !answers[qi].answer.trim()"
                title="输入自定义 stdin 调试运行"
                @click.stop="openStdinDialog(qi, 'debug')"
              >
                <Bug :size="14" aria-hidden="true" />
                <span>{{ answers[qi].debugRunning ? '调试中' : '调试' }}</span>
              </button>
            </div>
          </div>

          <CodeEditor v-model="answers[qi].answer" :language="answers[qi].language" height="340px" />

          <div class="console-panel">
            <div class="console-tabs">
              <button
                type="button"
                class="console-tab"
                title="按题目测试用例执行的结果"
                :class="{ 'is-active': answers[qi].consoleTab === 'test' }"
                @click="answers[qi].consoleTab = 'test'"
              >运行结果（测试用例）</button>
              <button
                type="button"
                class="console-tab"
                title="使用自定义 stdin 调试的结果"
                :class="{ 'is-active': answers[qi].consoleTab === 'debug' }"
                @click="answers[qi].consoleTab = 'debug'"
              >调试结果（自定义输入）</button>
            </div>

            <div v-show="answers[qi].consoleTab === 'test'" class="console-body">
              <div v-if="answers[qi].runResult" class="run-result">
                <template v-if="!answers[qi].runResult.supported">
                  <p class="run-message">{{ answers[qi].runResult.message }}</p>
                </template>
                <template v-else>
                  <p
                    class="run-summary"
                    :class="{ 'is-pass': answers[qi].runResult.compiled && answers[qi].runResult.passed === answers[qi].runResult.total }"
                  >
                    <template v-if="!answers[qi].runResult.compiled">编译失败</template>
                    <template v-else>
                      运行通过 {{ answers[qi].runResult.passed }} / {{ answers[qi].runResult.total }} 个用例 · {{ answers[qi].runResult.totalMs }}ms
                    </template>
                  </p>
                  <pre v-if="!answers[qi].runResult.compiled && answers[qi].runResult.compileError" class="run-error">{{ answers[qi].runResult.compileError }}</pre>
                  <div
                    v-for="c in (answers[qi].runResult.cases || [])"
                    :key="c.index"
                    class="run-case"
                    :class="{ 'is-pass': c.passed }"
                  >
                    <div class="run-case-head">用例 {{ c.index }} · {{ c.passed ? '通过' : '未通过' }} · {{ c.runtimeMs }}ms</div>
                    <div class="run-case-grid">
                      <div><span class="tc-label">期望</span><pre>{{ c.expected || '（空）' }}</pre></div>
                      <div><span class="tc-label">实际</span><pre>{{ c.actual || '（无输出）' }}</pre></div>
                    </div>
                    <pre v-if="c.error" class="run-error">{{ c.error }}</pre>
                  </div>
                </template>
              </div>
              <p v-else class="run-message">点击「运行」按题目测试用例执行，结果会显示在这里。</p>
            </div>

            <div v-show="answers[qi].consoleTab === 'debug'" class="console-body">
              <template v-if="answers[qi].debugResult">
                <template v-if="!answers[qi].debugResult.supported">
                  <p class="run-message">{{ answers[qi].debugResult.message }}</p>
                </template>
                <template v-else>
                  <p class="run-summary" :class="{ 'is-pass': debugSuccess(qi) }">
                    <template v-if="!answers[qi].debugResult.compiled">编译失败</template>
                    <template v-else-if="debugCase(qi).timedOut">运行超时</template>
                    <template v-else-if="debugCase(qi).error">运行结束（有错误）</template>
                    <template v-else>运行完成 · {{ answers[qi].debugResult.totalMs }}ms</template>
                  </p>
                  <pre v-if="!answers[qi].debugResult.compiled && answers[qi].debugResult.compileError" class="run-error">{{ answers[qi].debugResult.compileError }}</pre>
                  <template v-else>
                    <div class="console-section">
                      <span class="tc-label">标准输出</span>
                      <pre class="console-pre">{{ debugCase(qi).actual || '（无输出）' }}</pre>
                    </div>
                    <div v-if="debugCase(qi).error" class="console-section">
                      <span class="tc-label">标准错误</span>
                      <pre class="console-pre console-pre--error">{{ debugCase(qi).error }}</pre>
                    </div>
                    <p class="debug-note">自定义输入运行结果不参与正式判分；交卷仍按测试用例真实执行。</p>
                  </template>
                </template>
              </template>
              <p v-else class="run-message">点击「调试」输入 stdin 后运行，结果会显示在这里。</p>
            </div>
          </div>

          <div v-if="q.codeTestCases && q.codeTestCases.length" class="test-cases">
            <p class="tc-title">可执行测试用例（交卷时按此真实判分）</p>
            <div v-for="(tc, ti) in q.codeTestCases" :key="ti" class="tc-case">
              <div class="tc-line">
                <span class="tc-label">输入</span>
                <pre class="tc-item">{{ tc.input || '（无输入）' }}</pre>
              </div>
              <div class="tc-line">
                <span class="tc-label">期望输出</span>
                <pre class="tc-item">{{ tc.expectedOutput }}</pre>
              </div>
              <p v-if="tc.explanation" class="tc-note">{{ tc.explanation }}</p>
            </div>
          </div>
          <div v-else-if="q.testCases && q.testCases.length" class="test-cases">
            <p class="tc-title">测试用例参考</p>
            <pre class="tc-item" v-for="(tc, ti) in q.testCases" :key="ti">{{ tc }}</pre>
          </div>
          <p v-if="!(q.codeTestCases && q.codeTestCases.length)" class="tc-note">
            本题暂无可执行测试用例，交卷后由 AI 按参考答案批改；重新生成试卷后可能获得可运行用例。
          </p>
        </div>

        <div v-if="q.type !== 'coding' && q.testCases && q.testCases.length" class="test-cases">
          <p class="tc-title">测试用例参考</p>
          <pre class="tc-item" v-for="(tc, ti) in q.testCases" :key="ti">{{ tc }}</pre>
        </div>
      </el-card>
    </div>

    <div class="submit-bar" v-if="paper">
      <button class="button button--large" :disabled="submitting" :class="{ 'is-loading': submitting }" @click="submit">提交答卷</button>
    </div>

    <el-dialog
      v-model="stdinDialog.visible"
      :title="stdinDialog.mode === 'debug' ? '调试输入（自定义 stdin）' : '运行输入（自定义 stdin）'"
      width="min(560px, 92vw)"
      align-center
      append-to-body
      class="stdin-dialog"
    >
      <p class="stdin-tip">输入内容将作为程序的标准输入（stdin）；留空表示空输入。</p>
      <el-input v-model="stdinDialog.value" type="textarea" :rows="6" :placeholder="stdinPlaceholder" />
      <template #footer>
        <div class="stdin-footer">
          <button class="button button--plain" @click="stdinDialog.visible = false">取消</button>
          <button class="button" @click="confirmStdinDialog">运行</button>
        </div>
      </template>
    </el-dialog>

    <Teleport to="body">
      <div v-if="submitting" class="grading-mask" role="dialog" aria-modal="true" aria-label="正在批改答卷">
        <div class="grading-panel">
          <GenerationProgress
            :active="submitting"
            :stage="gradingStage"
            :steps="GRADING_STEPS"
            :ranges="GRADING_RANGES"
            :current="gradingCurrent"
            :total="gradingTotal"
            kind="exam-grade"
            label="正在批改答卷"
            :estimate="45"
          />
        </div>
      </div>
    </Teleport>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onUnmounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRoute, useRouter } from 'vue-router'
import { Back } from '@element-plus/icons-vue'
import { Play, Bug } from 'lucide-vue-next'
import { getExamPaper, submitExam, runExamCode, getExamGradeProgress } from '@/api/exam'
import GenerationProgress from '@/components/GenerationProgress.vue'
import CodeEditor from '@/components/CodeEditor.vue'
import { recordGenerationDuration } from '@/utils/generationTiming'

const route = useRoute()
const router = useRouter()

const paper = ref(null)
const answers = ref([])
const submitting = ref(false)
const remaining = ref(0)
let timer = null

const gradingStage = ref('')
const gradingCurrent = ref(0)
const gradingTotal = ref(0)
let gradeTimer = null
const stdinDialog = reactive({ visible: false, mode: 'debug', value: '', index: null })

const stdinPlaceholder = computed(() => {
  const question = paper.value?.questions?.[stdinDialog.index]
  const firstInput = question?.codeTestCases?.[0]?.input
  if (!firstInput || !String(firstInput).trim()) {
    return '输入自定义测试数据'
  }
  const text = String(firstInput).trim()
  const example = text.length > 180 ? `${text.slice(0, 180)}…` : text
  return `输入自定义测试数据，例如：\n${example}`
})
const GRADING_STEPS = [
  { key: 'PREPARING', label: '整理答卷' },
  { key: 'GRADING', label: '逐题批改' },
  { key: 'SUMMARIZING', label: '汇总报告' }
]
const GRADING_RANGES = {
  PREPARING: [5, 15],
  GRADING: [15, 90],
  SUMMARIZING: [90, 99]
}

const typeSummary = computed(() => {
  const p = paper.value
  if (!p) return ''
  const parts = []
  if (p.singleCount) parts.push(`单选 ${p.singleCount}`)
  if (p.multipleCount) parts.push(`多选 ${p.multipleCount}`)
  if (p.shortAnswerCount) parts.push(`简答 ${p.shortAnswerCount}`)
  if (p.codingCount) parts.push(`编程 ${p.codingCount}`)
  return parts.join(' · ')
})

onMounted(async () => {
  const res = await getExamPaper(route.params.id)
  paper.value = res.data
  answers.value = (paper.value.questions || []).map((q) => ({
    answer: '',
    multi: [],
    language: defaultLanguage(),
    running: false,
    runResult: null,
    debugRunning: false,
    debugResult: null,
    consoleTab: 'test'
  }))
  if (paper.value.durationSeconds > 0) {
    startCountdown()
  }
})

onUnmounted(() => {
  if (timer) clearInterval(timer)
  stopGradePolling()
})

function goBack() {
  if (window.history.length > 1) {
    router.back()
  } else {
    router.push('/exam')
  }
}

function startCountdown() {
  remaining.value = paper.value.durationSeconds
  timer = setInterval(() => {
    remaining.value--
    if (remaining.value <= 0) {
      clearInterval(timer)
      submit()
    }
  }, 1000)
}

async function submit() {
  if (submitting.value) return
  const questions = paper.value.questions || []
  const requestId = Date.now()
  const startedAt = Date.now()
  submitting.value = true
  gradingStage.value = 'PREPARING'
  gradingCurrent.value = 0
  gradingTotal.value = questions.length
  startGradePolling(requestId)
  try {
    const payload = {
      paperId: paper.value.id,
      requestId,
      answers: questions.map((q, i) => ({
        index: i,
        answer: q.type === 'multiple' ? (answers.value[i].multi || []).slice().sort().join(',') : (answers.value[i].answer || ''),
        language: q.type === 'coding' ? answers.value[i].language : null
      }))
    }
    const res = await submitExam(payload)
    recordGenerationDuration('exam-grade', (Date.now() - startedAt) / 1000)
    ElMessage.success('批改完成')
    router.replace(`/exam/report/${paper.value.id}`)
  } finally {
    stopGradePolling()
    gradingStage.value = ''
    submitting.value = false
  }
}

function startGradePolling(requestId) {
  stopGradePolling()
  const poll = async () => {
    try {
      const res = await getExamGradeProgress({ requestId })
      const data = res.data?.data || {}
      if (!data.active) return
      if (data.stage) gradingStage.value = data.stage
      gradingCurrent.value = Number(data.current) || 0
      if (Number(data.total) > 0) gradingTotal.value = Number(data.total)
    } catch (e) {
      // 轮询失败不打扰用户，提交按钮仍在 loading
    }
  }
  poll()
  gradeTimer = setInterval(poll, 800)
}

function stopGradePolling() {
  if (gradeTimer) {
    clearInterval(gradeTimer)
    gradeTimer = null
  }
}

function letter(i) {
  return String.fromCharCode(65 + i)
}
function stripLetter(opt) {
  return (opt || '').replace(/^[A-Za-z][\s)）．.、:：]/, '').trim()
}
function formatTime(s) {
  const m = Math.floor(s / 60)
  const sec = s % 60
  return `${String(m).padStart(2, '0')}:${String(sec).padStart(2, '0')}`
}
function typeLabel(t) {
  return { single: '单选题', multiple: '多选题', short_answer: '简答题', coding: '编程题' }[t] || t
}
function difficultyLabel(d) {
  return { basic: '基础', middle: '中级', advanced: '高级' }[d] || d
}

function companyLabel(q) {
  const company = String(q?.sourceCompany || '').trim()
  return !company || company === '多家大厂' ? '公开面经（未标注公司）' : company
}

function prettyUrl(url) {
  try {
    return decodeURI(String(url))
  } catch (e) {
    return String(url || '')
  }
}

function defaultLanguage() {
  const tech = String(paper.value?.techStack || '').toLowerCase()
  if (tech.includes('java') && !tech.includes('javascript') && !tech.includes('vue') && !tech.includes('react')) {
    return 'java'
  }
  return 'javascript'
}

async function runCode(qi) {
  const answer = answers.value[qi]
  if (!answer || !String(answer.answer || '').trim()) {
    ElMessage.warning('请先编写代码')
    return
  }
  answer.running = true
  answer.consoleTab = 'test'
  try {
    const res = await runExamCode({
      paperId: paper.value.id,
      index: qi,
      language: answer.language,
      code: answer.answer
    })
    answer.runResult = res.data
  } finally {
    answer.running = false
  }
}

function handleRun(qi) {
  const question = paper.value?.questions?.[qi]
  if (question?.codeTestCases?.length) {
    runCode(qi)
  } else {
    openStdinDialog(qi, 'run')
  }
}

function openStdinDialog(qi, mode) {
  stdinDialog.index = qi
  stdinDialog.mode = mode
  stdinDialog.value = ''
  stdinDialog.visible = true
}

function confirmStdinDialog() {
  const qi = stdinDialog.index
  const value = stdinDialog.value
  stdinDialog.visible = false
  if (qi != null) {
    runCustom(qi, value)
  }
}

async function runCustom(qi, stdin) {
  const answer = answers.value[qi]
  if (!answer || !String(answer.answer || '').trim()) {
    ElMessage.warning('请先编写代码')
    return
  }
  answer.debugRunning = true
  answer.consoleTab = 'debug'
  try {
    const res = await runExamCode({
      paperId: paper.value.id,
      index: qi,
      language: answer.language,
      code: answer.answer,
      mode: 'debug',
      stdin: stdin || ''
    })
    answer.debugResult = res.data
  } finally {
    answer.debugRunning = false
  }
}

function debugCase(qi) {
  return answers.value[qi]?.debugResult?.cases?.[0] || {}
}

function debugSuccess(qi) {
  const result = answers.value[qi]?.debugResult
  const testCase = result?.cases?.[0]
  return !!(result?.compiled && testCase && !testCase.timedOut && !testCase.error)
}
</script>

<style scoped>
.paper-page {
  max-width: 900px;
  margin: 0 auto;
  padding: 24px;
  box-sizing: border-box;
}
.paper-header {
  position: sticky;
  top: 0;
  z-index: 10;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  padding: 16px 20px;
  border-radius: var(--radius-md);
  margin-bottom: 16px;
}
.paper-title {
  margin: 0 0 6px;
  font-size: 20px;
  font-weight: 600;
  letter-spacing: -0.01em;
  color: var(--color-text);
}
.q-source-line {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  gap: 6px;
  margin: 2px 0 10px;
  font-size: 12px;
  line-height: 1.6;
  color: var(--color-text-3);
}
.q-source-label {
  flex: none;
  padding: 0 6px;
  border-radius: 999px;
  background: var(--color-surface-muted);
  color: var(--color-text-2);
}
.q-source-name {
  color: var(--color-text-2);
}
.q-source-url {
  color: var(--color-text-3);
  word-break: break-all;
  user-select: text;
}
.paper-source-note {
  margin-bottom: 12px;
  padding: 10px 14px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-surface-muted);
  color: var(--color-text-2);
  font-size: 12px;
  line-height: 1.6;
}
.back-btn {
  align-self: flex-start;
  padding: 0;
  margin-bottom: 6px;
  font-size: 12px;
  color: var(--color-text-3);
}
.back-btn:hover {
  color: var(--color-text);
}
.paper-meta {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}
.meta-text {
  color: var(--color-text-3);
  font-size: 12px;
}
.header-right {
  display: flex;
  align-items: center;
  gap: 16px;
  flex-shrink: 0;
}
.countdown {
  font-size: 20px;
  font-weight: 600;
  color: var(--color-text);
  font-family: var(--font-display);
  font-variant-numeric: tabular-nums;
}
.countdown.urgent {
  color: var(--color-danger);
}
.question-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.q-head {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
  flex-wrap: wrap;
}
.q-num {
  font-size: 13px;
  font-weight: 600;
  color: var(--color-accent);
}
.q-kp {
  color: var(--color-text-3);
  font-size: 12px;
}
.q-stem {
  color: var(--color-text);
  margin: 0 0 16px;
  font-size: 14px;
  line-height: 1.7;
  white-space: pre-wrap;
}
.q-options {
  margin-bottom: 4px;
  text-align: left;
}
.opt-row {
  display: flex;
  align-items: flex-start;
  margin-bottom: 8px;
}
.opt-item {
  display: flex;
  align-items: flex-start;
  height: auto;
  margin-right: 0;
  white-space: normal;
}
.opt-letter {
  flex-shrink: 0;
  font-weight: 500;
  color: var(--color-text);
  line-height: 1.6;
}
.opt-text {
  white-space: normal;
  word-break: break-word;
  line-height: 1.6;
  color: var(--color-text-2);
}
.coding-toolbar {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
  margin-bottom: 8px;
}
.code-lang {
  width: 132px;
  flex: none;
}
.coding-hint {
  flex: 1;
  min-width: 200px;
  font-size: 12px;
  color: var(--color-text-3);
}
.coding-toolbar .button {
  flex: none;
}
.editor-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-left: auto;
}
.ide-btn {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  height: 28px;
  padding: 0 11px;
  border-radius: 7px;
  font-family: inherit;
  font-size: 12px;
  font-weight: 600;
  line-height: 1;
  cursor: pointer;
  transition: background-color 0.18s ease, color 0.18s ease, border-color 0.18s ease, opacity 0.18s ease;
}
.ide-btn--run {
  border: 1px solid #1f7a4d;
  background: #1f7a4d;
  color: #ffffff;
}
.ide-btn--run:hover {
  background: #17603c;
  border-color: #17603c;
}
.ide-btn--debug {
  border: 1px solid #2e8b57;
  background: transparent;
  color: #2e8b57;
}
.ide-btn--debug:hover {
  background: var(--color-success-bg);
}
.ide-btn:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}
.stdin-tip {
  margin: 0 0 10px;
  font-size: 13px;
  color: var(--color-text-2);
}
.stdin-footer {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}
.console-panel {
  margin-top: 14px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
  overflow: hidden;
}
.console-tabs {
  display: flex;
  gap: 2px;
  background: var(--color-surface-muted);
  border-bottom: 1px solid var(--color-border);
}
.console-tab {
  padding: 8px 14px;
  border: none;
  border-bottom: 2px solid transparent;
  background: transparent;
  color: var(--color-text-3);
  font-family: inherit;
  font-size: 12px;
  cursor: pointer;
}
.console-tab.is-active {
  color: var(--color-text);
  border-bottom-color: var(--color-accent);
  background: var(--color-surface);
}
.console-body {
  padding: 10px 12px;
}
.console-body .run-result {
  margin-top: 0;
  border: 0;
}
.console-section {
  margin-top: 10px;
}
.console-pre {
  margin: 4px 0 0;
  padding: 10px;
  border-radius: var(--radius-sm);
  background: #0f1b21;
  color: #dbe7ea;
  font-family: 'Consolas', 'Monaco', 'Courier New', monospace;
  font-size: 12px;
  white-space: pre-wrap;
  word-break: break-all;
  max-height: 240px;
  overflow: auto;
}
.console-pre--error {
  color: #ffb4ab;
}
.debug-note {
  margin: 10px 0 0;
  font-size: 12px;
  color: var(--color-text-3);
}
.tc-case {
  margin-top: 10px;
  padding-top: 10px;
  border-top: 1px dashed var(--color-border);
}
.tc-line {
  display: flex;
  align-items: flex-start;
  gap: 8px;
}
.tc-label {
  flex: none;
  min-width: 56px;
  padding-top: 4px;
  font-size: 12px;
  color: var(--color-text-3);
}
.tc-note {
  margin: 6px 0 0;
  font-size: 12px;
  color: var(--color-text-3);
}
.run-result {
  margin-top: 14px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
  overflow: hidden;
}
.run-message {
  margin: 0;
  padding: 10px 12px;
  font-size: 12px;
  color: var(--color-text-3);
  background: var(--color-surface-muted);
}
.run-summary {
  margin: 0;
  padding: 10px 12px;
  font-size: 12px;
  font-weight: 600;
  color: var(--color-danger);
  background: var(--color-danger-bg);
}
.run-summary.is-pass {
  color: var(--color-success);
  background: var(--color-success-bg);
}
.run-case {
  padding: 10px 12px;
  border-top: 1px solid var(--color-border);
}
.run-case.is-pass {
  background: var(--color-success-bg);
}
.run-case-head {
  font-size: 12px;
  font-weight: 600;
  color: var(--color-text-2);
}
.run-case-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
  margin-top: 6px;
}
.run-case-grid pre {
  margin: 4px 0 0;
  padding: 8px;
  border-radius: var(--radius-sm);
  background: var(--color-surface);
  font-family: 'Consolas', 'Monaco', 'Courier New', monospace;
  font-size: 12px;
  color: var(--color-text-2);
  white-space: pre-wrap;
  word-break: break-all;
}
.run-error {
  margin: 6px 0 0;
  font-family: 'Consolas', 'Monaco', 'Courier New', monospace;
  font-size: 12px;
  color: var(--color-danger);
  white-space: pre-wrap;
}
@media (max-width: 640px) {
  .run-case-grid {
    grid-template-columns: 1fr;
  }
}
.test-cases {
  margin-top: 14px;
  background: var(--color-surface-muted);
  border: 1px solid var(--color-border);
  padding: 12px;
  border-radius: var(--radius-sm);
}
.tc-title {
  margin: 0 0 6px;
  color: var(--color-text-3);
  font-size: 12px;
}
.tc-item {
  margin: 4px 0;
  font-family: 'Consolas', 'Monaco', 'Courier New', monospace;
  font-size: 12px;
  color: var(--color-text-2);
  white-space: pre-wrap;
}
.submit-bar {
  text-align: center;
  margin: 24px 0;
}
.grading-mask {
  position: fixed;
  inset: 0;
  z-index: 3000;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 20px;
  background: rgba(15, 27, 33, 0.38);
  backdrop-filter: blur(2px);
}
.grading-panel {
  width: min(460px, 92vw);
  padding: 20px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  background: var(--color-surface);
  box-shadow: var(--shadow-lg);
}
.grading-panel :deep(.generation-progress) {
  margin: 0;
  padding: 0;
  border: 0;
  background: transparent;
}
</style>
