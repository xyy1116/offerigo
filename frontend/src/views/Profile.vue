<template>
  <div class="profile-page">
    <div class="page-header">
      <div>
        <h1 class="page-title page-title-anim">个人中心</h1>
        <p class="page-sub">管理你的面试、笔试记录、错题本与投递台账，随时回顾复盘</p>
      </div>
    </div>

    <!-- 账户信息：头像与证件照并排，减少首屏纵向堆叠 -->
    <el-card shadow="never" class="user-card page-card-anim" style="animation-delay: 0.02s">
      <div class="account-grid">
        <div class="account-profile">
          <img class="user-avatar" :src="userStore.userInfo?.avatar || '/default-avatar.svg'" alt="头像" />
          <div class="user-meta">
            <div class="user-name">{{ userStore.userInfo?.username || '—' }}</div>
            <button class="button button--small button--plain user-avatar-btn" @click="avatarVisible = true">更换头像</button>
          </div>
        </div>
        <div class="account-photo">
          <div class="account-photo-title">证件照</div>
          <PhotoEditor :photo="userStore.photo" @change="onPhotoChange" />
        </div>
      </div>
    </el-card>
    <AvatarDialog v-model="avatarVisible" />

    <!-- 历史数据管理 -->
    <el-card shadow="never" class="page-card-anim" style="animation-delay: 0.14s">
      <template #header>历史数据管理</template>
      <el-tabs v-model="activeTab">
        <!-- 面试记录 -->
        <el-tab-pane label="面试记录" name="interview">
          <div class="toolbar">
            <el-input v-model="interviewKw" clearable placeholder="搜索面试岗位" style="width: 220px" :prefix-icon="Search" />
            <button class="button button--default" @click="loadInterview">刷新</button>
          </div>
          <el-table :data="sortedInterview" v-loading="interviewLoading">
            <el-table-column prop="position" label="面试岗位" show-overflow-tooltip />
            <el-table-column prop="score" label="得分" width="110" />
            <el-table-column prop="createTime" label="时间" width="180" />
            <el-table-column label="操作" width="150" fixed="right" align="center">
              <template #default="{ row }">
                <div class="row-actions">
                  <button class="row-action" @click="openInterviewDetail(row)">查看</button>
                  <button class="row-action row-action--danger" @click="removeInterview(row)">删除</button>
                </div>
              </template>
            </el-table-column>
            <template #empty>
              <div class="table-empty">
                <p>暂无面试记录，完成一次 AI 模拟面试后会显示在这里</p>
                <button class="button button--small button--plain" @click="router.push('/interview')">去 AI 面试</button>
              </div>
            </template>
          </el-table>
        </el-tab-pane>

        <!-- 笔试记录 -->
        <el-tab-pane label="笔试记录" name="exam">
          <div class="toolbar">
            <el-input v-model="examKw" clearable placeholder="搜索岗位名称" style="width: 220px" :prefix-icon="Search" />
            <button class="button button--default" @click="loadExam">刷新</button>
          </div>
          <el-table :data="sortedExam" v-loading="examLoading">
            <el-table-column prop="jobTitle" label="岗位" show-overflow-tooltip />
            <el-table-column label="类型" width="110">
              <template #default="{ row }">{{ row.paperType === 'mock' ? '限时模拟' : '自由练习' }}</template>
            </el-table-column>
            <el-table-column label="得分" width="120">
              <template #default="{ row }">
                <span v-if="row.userScore != null">{{ row.userScore }} / {{ row.totalScore }}</span>
                <el-tag v-else size="small" type="info">未作答</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="createTime" label="时间" width="180" />
            <el-table-column label="操作" width="170" fixed="right">
              <template #default="{ row }">
                <div class="row-actions">
                  <button class="row-action" @click="openExamDetail(row)">查看</button>
                  <button class="row-action row-action--danger" @click="removeExam(row)">删除</button>
                </div>
              </template>
            </el-table-column>
            <template #empty>
              <div class="table-empty">
                <p>暂无笔试记录，生成一份岗位试卷开始练习</p>
                <button class="button button--small button--plain" @click="router.push('/exam')">去 AI 笔试</button>
              </div>
            </template>
          </el-table>
        </el-tab-pane>

        <!-- 错题本 -->
        <el-tab-pane label="笔试错题" name="wrong">
          <div class="toolbar">
            <el-select v-model="wrongJobTitle" clearable placeholder="按岗位筛选" style="width: 160px" @change="loadWrong">
              <el-option v-for="j in wrongJobs" :key="j.jobTitle" :label="j.jobTitle" :value="j.jobTitle" />
            </el-select>
            <el-select v-model="wrongType" clearable placeholder="题型" style="width: 120px" @change="loadWrong">
              <el-option label="单选题" value="single" />
              <el-option label="多选题" value="multiple" />
              <el-option label="简答题" value="short_answer" />
              <el-option label="编程题" value="coding" />
            </el-select>
            <el-input v-model="wrongKw" clearable placeholder="搜索知识点/题干" style="width: 200px" :prefix-icon="Search" @keyup.enter="loadWrong" />
            <button class="button button--default" @click="loadWrong">查询</button>
          </div>
          <el-table :data="wrongList" v-loading="wrongLoading">
            <el-table-column prop="jobTitle" label="岗位" width="160" show-overflow-tooltip />
            <el-table-column prop="knowledgePoint" label="知识点" show-overflow-tooltip />
            <el-table-column label="题型" width="90">
              <template #default="{ row }">{{ typeLabel(row.questionType) }}</template>
            </el-table-column>
            <el-table-column prop="timesWrong" label="错次数" width="80" />
            <el-table-column label="状态" width="90">
              <template #default="{ row }">
                <el-tag :type="row.status === 'mastered' ? 'success' : 'danger'" size="small">
                  {{ row.status === 'mastered' ? '已掌握' : '待巩固' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="240" fixed="right" align="center">
              <template #default="{ row }">
                <div class="row-actions">
                  <button class="row-action" @click="openWrongDetail(row)">查看</button>
                  <el-tooltip v-if="row.status !== 'mastered'" content="标记为已掌握，不再出现在待巩固列表" placement="top">
                    <button class="row-action row-action--success" @click="markWrongMastered(row)">掌握</button>
                  </el-tooltip>
                  <span v-else class="row-action row-action--placeholder" aria-hidden="true">掌握</span>
                  <button class="row-action row-action--danger" @click="removeWrong(row)">删除</button>
                </div>
              </template>
            </el-table-column>
            <template #empty>
              <div class="table-empty">
                <p>暂无错题，完成笔试后错题会自动归档</p>
                <button class="button button--small button--plain" @click="router.push('/exam')">去 AI 笔试</button>
              </div>
            </template>
          </el-table>
        </el-tab-pane>

        <!-- 我的投递（投递台账） -->
        <el-tab-pane label="我的投递" name="applications">
          <div class="toolbar">
            <el-input
              v-model="appKeyword"
              clearable
              placeholder="搜索岗位/公司"
              style="width: 200px"
              :prefix-icon="Search"
              @keyup.enter="loadApplications"
            />
            <el-select v-model="appStatus" clearable placeholder="全部状态" style="width: 140px" @change="loadApplications">
              <el-option v-for="s in APP_STATUSES" :key="s" :label="s" :value="s" />
            </el-select>
            <el-date-picker
              v-model="appDateRange"
              type="daterange"
              value-format="YYYY-MM-DD"
              range-separator="至"
              start-placeholder="投递开始"
              end-placeholder="投递结束"
              style="width: 260px"
              @change="loadApplications"
            />
            <button class="button button--small" @click="loadApplications">查询</button>
            <button class="button button--small button--plain" @click="resetAppFilters">重置</button>
            <div class="app-follow-setting">
              跟进提醒：满
              <el-input-number v-model="appFollowUpDays" :min="1" :max="365" size="small" controls-position="right" class="app-days-input" />
              天
              <button class="button button--small button--plain" :disabled="appSettingsSaving" @click="saveFollowUpSettings">保存</button>
            </div>
          </div>

          <div v-if="appDueCount" class="app-due-banner">
            有 {{ appDueCount }} 条投递已到跟进时间，记得关注进展～
          </div>

          <el-table :data="applications" v-loading="appLoading">
            <el-table-column prop="jobName" label="岗位" min-width="150" show-overflow-tooltip />
            <el-table-column prop="companyName" label="公司" width="150" show-overflow-tooltip />
            <el-table-column label="投递时间" width="170">
              <template #default="{ row }">{{ formatTime(row.appliedAt) }}</template>
            </el-table-column>
            <el-table-column label="简历" width="110" align="center">
              <template #default="{ row }">
                <button
                  v-if="row.hasResume"
                  class="row-action"
                  :title="row.resumeFileName"
                  @click="openAppliedResume(row)"
                >查看简历</button>
                <button v-else class="row-action row-action--success" @click="pickResumeFile(row)">上传简历</button>
              </template>
            </el-table-column>
            <el-table-column label="岗位链接" width="95" align="center">
              <template #default="{ row }">
                <button v-if="row.jobLink" class="row-action" @click="openApplicationLink(row)">查看</button>
                <span v-else>—</span>
              </template>
            </el-table-column>
            <el-table-column label="状态" width="190">
              <template #default="{ row }">
                <div class="app-status-cell">
                  <el-tag :type="appStatusTagType(row.status)" size="small">{{ row.status }}</el-tag>
                  <span v-if="row.followUpDue" class="app-due-tag">该跟进了</span>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="备注" min-width="160" show-overflow-tooltip>
              <template #default="{ row }">{{ row.remark || '—' }}</template>
            </el-table-column>
            <el-table-column label="操作" width="150" fixed="right" align="center">
              <template #default="{ row }">
                <div class="row-actions">
                  <button class="row-action" @click="openApplicationEdit(row)">编辑</button>
                  <button class="row-action row-action--danger" @click="removeApplication(row)">删除</button>
                </div>
              </template>
            </el-table-column>
            <template #empty>
              <div class="table-empty">
                <p>暂无投递记录，去岗位匹配页点击「投递」并确认后即可生成</p>
                <button class="button button--small button--plain" @click="router.push('/jobmatch')">去岗位匹配</button>
              </div>
            </template>
          </el-table>
        </el-tab-pane>
      </el-tabs>
    </el-card>

    <!-- 面试详情 -->
    <el-dialog v-model="interviewDetailVisible" title="面试记录详情" width="640px" align-center class="detail-dialog">
      <template v-if="interviewDetail">
        <div class="detail-head">
          <span class="detail-title">{{ interviewDetail.position }}</span>
          <span class="text-[12px] text-[var(--color-text-3)]">{{ formatTime(interviewDetail.createTime) }}</span>
        </div>
        <InterviewReportDetail :report="interviewDetail.report" :score="interviewDetail.score" />
      </template>
    </el-dialog>

    <!-- 错题详情 -->
    <el-dialog v-model="wrongDetailVisible" title="错题详情" width="620px" class="detail-dialog">
      <template v-if="wrongDetail">
        <div class="wrong-question">
          <el-tag size="small" type="info">{{ typeLabel(wrongDetail.questionType) }}</el-tag>
          <el-tag size="small" type="warning" style="margin-left: 6px">{{ wrongDetail.jobTitle }}</el-tag>
          <h4 class="q-stem">{{ wrongDetail.question?.stem }}</h4>
          <div class="q-answer">
            <div class="q-label">标准答案</div>
            <div class="q-text">{{ wrongDetail.standardAnswer || '—' }}</div>
          </div>
          <div class="q-answer">
            <div class="q-label">解析</div>
            <div class="q-text">{{ wrongDetail.analysis || '—' }}</div>
          </div>
        </div>
      </template>
    </el-dialog>

    <!-- 投递记录编辑 -->
    <el-dialog v-model="appEditVisible" title="编辑投递记录" width="480px" align-center class="lib-dialog">
      <div class="app-edit-form">
        <div class="app-edit-row">
          <span class="app-edit-label">投递状态</span>
          <el-select v-model="appEditForm.status" class="full-width">
            <el-option v-for="s in APP_STATUSES" :key="s" :label="s" :value="s" />
          </el-select>
        </div>
        <div class="app-edit-row">
          <span class="app-edit-label">备注</span>
          <el-input v-model="appEditForm.remark" type="textarea" :rows="4" maxlength="1000" show-word-limit placeholder="记录面试反馈、HR 沟通、薪资进展等" />
        </div>
      </div>
      <template #footer>
        <button class="button button--plain" @click="appEditVisible = false">取消</button>
        <button class="button" :disabled="appEditing" :class="{ 'is-loading': appEditing }" @click="saveApplicationEdit">保存</button>
      </template>
    </el-dialog>

    <!-- 投递简历预览 -->
    <el-dialog
      v-model="appliedResumeVisible"
      title="投递简历"
      width="760px"
      align-center
      class="detail-dialog"
      @closed="closeAppliedResume"
    >
      <div v-if="resumeRow" class="applied-resume-meta">{{ resumeRow.resumeFileName }}</div>
      <div v-loading="appliedResumeLoading" class="applied-resume-frame">
        <iframe v-if="appliedResumeUrl && appliedResumeIsPdf" :src="appliedResumeUrl" title="投递简历预览"></iframe>
        <p v-else-if="appliedResumeUrl" class="applied-resume-tip">Word 文件暂不支持在线预览，请下载后查看。</p>
      </div>
      <template #footer>
        <button v-if="resumeRow?.hasResume" class="button button--plain button--danger" @click="removeResumeFile(resumeRow)">删除简历</button>
        <button v-if="resumeRow?.hasResume" class="button button--plain" @click="pickResumeFile(resumeRow)">重新上传</button>
        <button class="button" :disabled="!appliedResumeUrl" @click="downloadAppliedResume">下载</button>
        <button class="button button--plain" @click="appliedResumeVisible = false">关闭</button>
      </template>
    </el-dialog>

    <input ref="resumeFileInput" type="file" accept=".pdf,.docx,.doc" class="hidden-file" @change="onResumeFileChange" />
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search } from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'
import { getInterviewHistory, getInterviewDetail, deleteInterviewHistory } from '@/api/interview'
import { getExamHistory, deleteExamHistory, getWrongList, getWrongJobs, getWrongDetail, deleteWrong, markMastered } from '@/api/exam'
import {
  listApplications, updateApplication, deleteApplication,
  getApplicationSettings, saveApplicationSettings, getApplicationResume,
  uploadApplicationResume, deleteApplicationResume
} from '@/api/jobmatch'
import AvatarDialog from '@/components/AvatarDialog.vue'
import PhotoEditor from '@/components/PhotoEditor.vue'
import InterviewReportDetail from '@/components/InterviewReportDetail.vue'

const router = useRouter()
const userStore = useUserStore()

const activeTab = ref('interview')
const avatarVisible = ref(false)

async function onPhotoChange(dataUrl) {
  try {
    await userStore.updatePhoto(dataUrl)
    ElMessage.success(dataUrl ? '证件照已更新' : '已删除证件照')
  } catch (e) {
    ElMessage.error('证件照保存失败，请稍后重试')
  }
}

// ===== 面试 =====
const interviewKw = ref('')
const interviewList = ref([])
const interviewLoading = ref(false)
const interviewDetailVisible = ref(false)
const interviewDetail = ref(null)

const sortedInterview = computed(() => {
  let list = [...interviewList.value]
  if (interviewKw.value) {
    list = list.filter((r) => (r.position || '').includes(interviewKw.value))
  }
  return list.sort((a, b) => (b.id || 0) - (a.id || 0))
})

async function loadInterview() {
  interviewLoading.value = true
  try {
    const res = await getInterviewHistory()
    interviewList.value = res.data
  } finally {
    interviewLoading.value = false
  }
}

async function openInterviewDetail(row) {
  try {
    const res = await getInterviewDetail(row.id)
    interviewDetail.value = res.data
    interviewDetailVisible.value = true
  } catch (e) {}
}

async function removeInterview(row) {
  await ElMessageBox.confirm('确定删除该面试记录吗？', '提示', { type: 'warning' })
  await deleteInterviewHistory(row.id)
  ElMessage.success('已删除')
  loadInterview()
}

/** 将 ISO 时间字符串格式化为「yyyy-MM-dd HH:mm:ss」 */
function formatTime(v) {
  if (!v) return ''
  return String(v).replace('T', ' ').slice(0, 19)
}

// ===== 笔试 =====
const examKw = ref('')
const examList = ref([])
const examLoading = ref(false)

const sortedExam = computed(() => {
  let list = [...examList.value]
  if (examKw.value) {
    list = list.filter((r) => (r.jobTitle || '').includes(examKw.value))
  }
  return list.sort((a, b) => (b.id || 0) - (a.id || 0))
})

async function loadExam() {
  examLoading.value = true
  try {
    const res = await getExamHistory()
    examList.value = res.data
  } finally {
    examLoading.value = false
  }
}

async function openExamDetail(row) {
  if (row.userScore != null) {
    router.push(`/exam/report/${row.id}`)
  } else {
    router.push(`/exam/paper/${row.id}`)
  }
}

async function removeExam(row) {
  await ElMessageBox.confirm('确定删除该笔试记录吗？', '提示', { type: 'warning' })
  await deleteExamHistory(row.id)
  ElMessage.success('已删除')
  loadExam()
}

// ===== 错题 =====
const wrongKw = ref('')
const wrongJobTitle = ref('')
const wrongType = ref('')
const wrongJobs = ref([])
const wrongList = ref([])
const wrongLoading = ref(false)
const wrongDetailVisible = ref(false)
const wrongDetail = ref(null)

function typeLabel(t) {
  return { single: '单选题', multiple: '多选题', short_answer: '简答题', coding: '编程题' }[t] || t
}

async function loadWrongJobs() {
  const res = await getWrongJobs()
  wrongJobs.value = res.data
}

async function loadWrong() {
  wrongLoading.value = true
  try {
    const res = await getWrongList({
      jobTitle: wrongJobTitle.value || undefined,
      type: wrongType.value || undefined,
      keyword: wrongKw.value || undefined
    })
    wrongList.value = res.data
  } finally {
    wrongLoading.value = false
  }
}

async function openWrongDetail(row) {
  try {
    const res = await getWrongDetail(row.id)
    wrongDetail.value = res.data
    wrongDetailVisible.value = true
  } catch (e) {}
}

async function markWrongMastered(row) {
  await markMastered(row.id)
  ElMessage.success('已标记为掌握')
  loadWrong()
}

async function removeWrong(row) {
  await ElMessageBox.confirm('确定删除该错题吗？', '提示', { type: 'warning' })
  await deleteWrong(row.id)
  ElMessage.success('已删除')
  loadWrong()
  loadWrongJobs()
}

// ===== 我的投递（投递台账） =====
const APP_STATUSES = ['待回复', '收到邀约面试', '已面试', '已录用', '落选', '已放弃']
const applications = ref([])
const appLoading = ref(false)
const appKeyword = ref('')
const appStatus = ref('')
const appDateRange = ref([])
const appFollowUpDays = ref(7)
const appSettingsSaving = ref(false)
const appEditVisible = ref(false)
const appEditing = ref(false)
const appEditForm = ref({ id: null, status: '待回复', remark: '' })

const appDueCount = computed(() => applications.value.filter((a) => a.followUpDue).length)

async function loadApplications() {
  appLoading.value = true
  try {
    const params = {}
    if (appKeyword.value.trim()) params.keyword = appKeyword.value.trim()
    if (appStatus.value) params.status = appStatus.value
    if (Array.isArray(appDateRange.value) && appDateRange.value[0]) {
      params.from = appDateRange.value[0]
      params.to = appDateRange.value[1]
    }
    const res = await listApplications(params)
    applications.value = res.data || []
  } catch (e) {
    // 错误已由拦截器统一提示
  } finally {
    appLoading.value = false
  }
}

async function loadApplicationSettings() {
  try {
    const res = await getApplicationSettings()
    appFollowUpDays.value = Number(res.data?.followUpDays) || 7
  } catch (e) {}
}

function resetAppFilters() {
  appKeyword.value = ''
  appStatus.value = ''
  appDateRange.value = []
  loadApplications()
}

function appStatusTagType(status) {
  if (status === '已录用') return 'success'
  if (status === '落选') return 'danger'
  if (status === '收到邀约面试' || status === '已面试') return 'warning'
  return 'info'
}

function openApplicationLink(row) {
  if (row.jobLink) window.open(row.jobLink, '_blank', 'noopener,noreferrer')
}

function openApplicationEdit(row) {
  appEditForm.value = { id: row.id, status: row.status || '待回复', remark: row.remark || '' }
  appEditVisible.value = true
}

async function saveApplicationEdit() {
  if (!appEditForm.value.id) return
  appEditing.value = true
  try {
    await updateApplication(appEditForm.value.id, {
      status: appEditForm.value.status,
      remark: appEditForm.value.remark
    })
    appEditVisible.value = false
    ElMessage.success('投递记录已更新')
    loadApplications()
  } catch (e) {
    // 错误已由拦截器统一提示
  } finally {
    appEditing.value = false
  }
}

async function removeApplication(row) {
  try {
    await ElMessageBox.confirm(`确定删除「${row.jobName || '该投递'}」的记录吗？`, '提示', { type: 'warning' })
  } catch (e) {
    return
  }
  await deleteApplication(row.id)
  ElMessage.success('已删除')
  loadApplications()
}

async function saveFollowUpSettings() {
  appSettingsSaving.value = true
  try {
    const res = await saveApplicationSettings({ followUpDays: appFollowUpDays.value })
    appFollowUpDays.value = Number(res.data?.followUpDays) || appFollowUpDays.value
    ElMessage.success('跟进提醒设置已保存')
    loadApplications()
  } catch (e) {
    // 错误已由拦截器统一提示
  } finally {
    appSettingsSaving.value = false
  }
}

// 切换 tab 时才加载投递台账，避免进入个人中心就发起额外请求
watch(activeTab, (tab) => {
  if (tab === 'applications') {
    loadApplications()
    loadApplicationSettings()
  }
})

// 全局确认弹窗完成/撤销后，若台账正打开则同步刷新
function onApplicationChanged() {
  if (activeTab.value === 'applications') loadApplications()
}

onMounted(async () => {
  if (!userStore.userInfo) {
    try {
      await userStore.fetchProfile()
    } catch (e) {}
  }
  userStore.fetchPhoto().catch(() => {})
  loadInterview()
  loadExam()
  loadWrongJobs()
  loadWrong()
  window.addEventListener('job-application-changed', onApplicationChanged)
})

// —— 投递简历预览 ——
const appliedResumeVisible = ref(false)
const appliedResumeLoading = ref(false)
const appliedResumeUrl = ref('')
const resumeRow = ref(null)
const resumeFileInput = ref(null)
const resumeUploadId = ref(null)
const appliedResumeIsPdf = computed(() => /\.pdf$/i.test(resumeRow.value?.resumeFileName || ''))

function pickResumeFile(row) {
  resumeUploadId.value = row.id
  resumeFileInput.value?.click()
}

async function onResumeFileChange(event) {
  const file = event.target.files?.[0]
  event.target.value = ''
  const id = resumeUploadId.value
  resumeUploadId.value = null
  if (!file || id == null) return
  try {
    await uploadApplicationResume(id, file)
    ElMessage.success('简历已上传')
    await loadApplications()
    if (resumeRow.value?.id === id) {
      const fresh = applications.value.find((a) => a.id === id)
      if (fresh) await openAppliedResume(fresh)
    }
  } catch (e) {
    // 错误已由拦截器统一提示
  }
}

async function removeResumeFile(row) {
  if (!row) return
  try {
    await ElMessageBox.confirm('确定删除这条投递的简历文件吗？', '删除简历', {
      type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消'
    })
  } catch (e) {
    return
  }
  try {
    await deleteApplicationResume(row.id)
    ElMessage.success('简历已删除')
    appliedResumeVisible.value = false
    await loadApplications()
  } catch (e) {
    // 错误已由拦截器统一提示
  }
}

async function openAppliedResume(row) {
  resumeRow.value = row
  appliedResumeVisible.value = true
  appliedResumeLoading.value = true
  revokeAppliedResume()
  try {
    const res = await getApplicationResume(row.id)
    appliedResumeUrl.value = URL.createObjectURL(new Blob([res.data], { type: 'application/pdf' }))
  } catch (e) {
    ElMessage.error('简历加载失败，请稍后重试')
    appliedResumeVisible.value = false
  } finally {
    appliedResumeLoading.value = false
  }
}

function revokeAppliedResume() {
  if (appliedResumeUrl.value) {
    URL.revokeObjectURL(appliedResumeUrl.value)
    appliedResumeUrl.value = ''
  }
}

function closeAppliedResume() {
  revokeAppliedResume()
  resumeRow.value = null
}

function downloadAppliedResume() {
  if (!appliedResumeUrl.value) return
  const a = document.createElement('a')
  a.href = appliedResumeUrl.value
  a.download = resumeRow.value?.resumeFileName || '简历'
  document.body.appendChild(a)
  a.click()
  a.remove()
}

onUnmounted(() => {
  revokeAppliedResume()
  window.removeEventListener('job-application-changed', onApplicationChanged)
})
</script>

<style scoped>
.profile-page {
  max-width: 1200px;
  margin: 0 auto;
  padding: 24px;
  box-sizing: border-box;
}
.user-card {
  margin-bottom: 16px;
}
.account-grid {
  display: grid;
  grid-template-columns: minmax(220px, 280px) 1fr;
  gap: 24px;
  align-items: start;
}
.account-profile {
  display: flex;
  align-items: center;
  gap: 14px;
}
.account-photo-title {
  margin-bottom: 10px;
  font-size: 13px;
  font-weight: 600;
  color: var(--color-text);
}
@media (max-width: 900px) {
  .account-grid {
    grid-template-columns: 1fr;
    gap: 16px;
  }
}
/* 错题操作：同尺寸胶囊按钮 */
.row-actions {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}
.row-action {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 64px;
  height: 28px;
  padding: 0 12px;
  border: 1px solid var(--color-border);
  border-radius: 999px;
  background: var(--color-surface);
  color: var(--color-text-2);
  font-family: inherit;
  font-size: 12px;
  line-height: 1;
  cursor: pointer;
  transition: border-color 0.15s ease, color 0.15s ease, background-color 0.15s ease;
}
.row-action:hover {
  border-color: var(--color-border-hover);
  color: var(--color-text);
}
.row-action--success {
  background: var(--color-success-bg);
  border-color: var(--color-success-bg);
  color: var(--color-success);
}
.row-action--success:hover {
  border-color: var(--color-success);
  color: var(--color-success);
}
.row-action--danger {
  background: var(--color-danger-bg);
  border-color: var(--color-danger-bg);
  color: var(--color-danger);
}
.row-action--danger:hover {
  border-color: var(--color-danger);
  color: var(--color-danger);
}
/* 占位：已掌握时保留位置，保证各行按钮纵向对齐 */
.row-action--placeholder {
  visibility: hidden;
}
.applied-resume-meta {
  margin-bottom: 10px;
  font-size: 13px;
  color: var(--color-text-2);
}
.applied-resume-frame {
  height: 68vh;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
  overflow: hidden;
  background: var(--color-surface-muted);
}
.applied-resume-frame iframe {
  display: block;
  width: 100%;
  height: 100%;
  border: 0;
}
.applied-resume-tip {
  margin: 0;
  padding: 40px 16px;
  text-align: center;
  font-size: 13px;
  color: var(--color-text-2);
}
.hidden-file {
  display: none;
}
.page-header {
  margin-bottom: 20px;
}
.page-sub {
  margin: 6px 0 0;
  color: var(--color-text-2);
  font-size: 13px;
}
.user-row {
  display: flex;
  align-items: center;
  gap: 16px;
}
.user-avatar {
  width: 48px;
  height: 48px;
  border-radius: 50%;
  object-fit: cover;
  background: var(--color-accent-soft);
  border: 1px solid var(--color-border);
  flex: none;
}
.user-avatar-btn {
  margin-top: 6px;
}
.user-meta {
  flex: 1;
}
.user-name {
  font-size: 16px;
  font-weight: 600;
  color: var(--color-text);
  letter-spacing: -0.01em;
}
.toolbar {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  margin-bottom: 14px;
}
.detail-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
  margin-bottom: 12px;
}
.detail-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--color-text);
}
.wrong-question .q-stem {
  margin: 12px 0;
  font-size: 14px;
  font-weight: 500;
  color: var(--color-text);
  line-height: 1.6;
}
.q-answer {
  margin-bottom: 12px;
}
.q-label {
  font-size: 12px;
  color: var(--color-text-3);
  margin-bottom: 4px;
}
.q-text {
  white-space: pre-wrap;
  color: var(--color-text-2);
  line-height: 1.6;
  background: var(--color-surface-muted);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
  padding: 10px 12px;
  font-size: 13px;
}
.detail-dialog :deep(.el-dialog__body) {
  max-height: 70vh;
  overflow-y: auto;
}
.detail-dialog :deep(.el-dialog) {
  max-width: calc(100vw - 32px);
}

/* ===== 我的投递 ===== */
.full-width {
  width: 100%;
}
.app-follow-setting {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-left: auto;
  font-size: 12.5px;
  color: var(--color-text-3);
}
.app-days-input {
  width: 96px;
}
.app-due-banner {
  margin-bottom: 12px;
  padding: 10px 12px;
  border-radius: var(--radius-sm);
  background: var(--color-danger-bg);
  color: var(--color-danger);
  font-size: 12.5px;
}
.app-status-cell {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: nowrap;
}
.app-due-tag {
  font-size: 11px;
  color: var(--color-danger);
  white-space: nowrap;
}
.app-edit-form {
  display: flex;
  flex-direction: column;
  gap: 14px;
}
.app-edit-row {
  display: flex;
  align-items: flex-start;
  gap: 12px;
}
.app-edit-label {
  flex: 0 0 64px;
  padding-top: 6px;
  font-size: 13px;
  color: var(--color-text-3);
}
@media (max-width: 768px) {
  .profile-page {
    padding: 16px;
  }
  .toolbar {
    flex-wrap: wrap;
  }
  .toolbar :deep(.el-input),
  .toolbar :deep(.el-select),
  .toolbar :deep(.el-date-editor) {
    width: 100% !important;
  }
  .app-follow-setting {
    flex-wrap: wrap;
  }
  :deep(.el-dialog) {
    width: 92vw !important;
  }
}
</style>
