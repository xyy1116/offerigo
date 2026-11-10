<template>
  <el-dialog
    v-model="visible"
    title="投递确认"
    width="480px"
    align-center
    class="lib-dialog job-application-prompt"
    :close-on-click-modal="false"
    @close="onDialogClose"
  >
    <p class="prompt-tip">是否已经在招聘平台完成投递？确认后加入投递台账。</p>
    <div v-if="current" class="prompt-fields">
      <div class="prompt-field"><span class="label">岗位</span><span class="value">{{ current.jobName || '—' }}</span></div>
      <div class="prompt-field"><span class="label">公司</span><span class="value">{{ current.companyName || '未填写' }}</span></div>
      <div class="prompt-field"><span class="label">投递时间</span><span class="value">{{ formatTime(current.appliedAt) }}</span></div>
      <div class="prompt-field">
        <span class="label">简历</span>
        <span class="value value--muted">简历可在个人中心「我的投递」里上传</span>
      </div>
      <div v-if="current.jobLink" class="prompt-field">
        <span class="label">岗位链接</span>
        <span class="value value--link" :title="current.jobLink">{{ current.jobLink }}</span>
      </div>
    </div>
    <template #footer>
      <div class="prompt-footer">
        <span v-if="drafts.length > 1" class="prompt-more">还有 {{ drafts.length - 1 }} 条待确认</span>
        <button class="button button--plain" :disabled="saving" @click="dismissDraft">暂不记录</button>
        <button class="button" :disabled="saving" :class="{ 'is-loading': saving }" @click="confirmDraft">确认投递成功</button>
      </div>
    </template>
  </el-dialog>
</template>

<script setup>
import { onMounted, onUnmounted, ref } from 'vue'
import { getToken } from '@/utils/token'
import { confirmApplication, deleteApplication, getApplicationReminders } from '@/api/jobmatch'
import { usePositiveMomentStore } from '@/stores/positiveMoment'

const positiveMoment = usePositiveMomentStore()

const visible = ref(false)
const drafts = ref([])
const current = ref(null)
const saving = ref(false)
// 本次会话内被关闭/跳过的草稿，避免每次切回窗口都重复打扰
const skipped = new Set()
let checking = false
let recheck = false
let checkTimer = null

function formatTime(v) {
  if (!v) return '—'
  return String(v).replace('T', ' ').slice(0, 16)
}

function showDraft(d) {
  current.value = d
  visible.value = true
}

function advance() {
  drafts.value = drafts.value.slice(1)
  if (drafts.value.length) {
    showDraft(drafts.value[0])
  } else {
    visible.value = false
    current.value = null
  }
}

function onDialogClose() {
  // 关闭（X/ESC）视为稍后处理，草稿保留到 24 小时后自动清除
  if (current.value) skipped.add(current.value.id)
  current.value = null
  drafts.value = []
}

async function confirmDraft() {
  if (!current.value || saving.value) return
  saving.value = true
  try {
    await confirmApplication(current.value.id, {})
    positiveMoment.trigger({
      type: 'custom',
      title: '已加入投递台账',
      message: '投递记录已归档，记得及时跟进进展～'
    })
    notifyApplicationsChanged()
    advance()
  } catch (e) {
    // 错误已由拦截器统一提示
  } finally {
    saving.value = false
  }
}

async function dismissDraft() {
  if (!current.value || saving.value) return
  saving.value = true
  try {
    await deleteApplication(current.value.id)
    notifyApplicationsChanged()
    advance()
  } catch (e) {
    // 错误已由拦截器统一提示
  } finally {
    saving.value = false
  }
}

function notifyApplicationsChanged() {
  window.dispatchEvent(new CustomEvent('job-application-changed'))
}

/** 投递满 N 天的跟进提醒：每天最多提醒一次 */
function remindFollowUp(data) {
  const count = Number(data.followUpDueCount || 0)
  if (count <= 0) return
  const key = 'job_followup_reminded_' + new Date().toISOString().slice(0, 10)
  try {
    if (localStorage.getItem(key)) return
    localStorage.setItem(key, '1')
  } catch (e) {
    // localStorage 不可用时仍提示
  }
  positiveMoment.trigger({
    type: 'custom',
    title: '投递跟进提醒',
    message: `有 ${count} 条投递已满 ${data.followUpDays || 7} 天，去个人中心「我的投递」跟进一下～`
  })
}

/** 延后检查：给「打开岗位页 → 生成草稿」的请求留出完成时间 */
function scheduleCheck(delay = 800) {
  clearTimeout(checkTimer)
  checkTimer = setTimeout(() => {
    checkTimer = null
    checkReminders()
  }, delay)
}

async function checkReminders() {
  if (!getToken()) return
  if (checking) {
    // 已有请求在途：标记再查一次，避免这次切回窗口被吞掉
    recheck = true
    return
  }
  checking = true
  try {
    const res = await getApplicationReminders()
    const data = res.data || {}
    remindFollowUp(data)
    if (visible.value) return
    const list = (data.drafts || []).filter((d) => !skipped.has(d.id))
    if (list.length) {
      drafts.value = list
      showDraft(list[0])
    }
  } catch (e) {
    // 静默失败，不打扰用户
  } finally {
    checking = false
    if (recheck) {
      recheck = false
      scheduleCheck(600)
    }
  }
}

function onVisibilityChange() {
  if (document.visibilityState === 'visible') scheduleCheck()
}

function onWindowFocus() {
  scheduleCheck()
}

// 草稿刚生成（例如用户在检索完成前已切回应用）时立即补一次检查
function onDraftCreated() {
  scheduleCheck(100)
}

onMounted(() => {
  scheduleCheck(0)
  document.addEventListener('visibilitychange', onVisibilityChange)
  window.addEventListener('focus', onWindowFocus)
  window.addEventListener('job-application-draft-created', onDraftCreated)
})

onUnmounted(() => {
  clearTimeout(checkTimer)
  document.removeEventListener('visibilitychange', onVisibilityChange)
  window.removeEventListener('focus', onWindowFocus)
  window.removeEventListener('job-application-draft-created', onDraftCreated)
})
</script>

<style scoped>
.prompt-tip {
  margin: 0 0 14px;
  font-size: 13px;
  line-height: 1.6;
  color: var(--color-text-2);
}
.prompt-fields {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.prompt-field {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 13px;
}
.prompt-field .label {
  flex: 0 0 64px;
  color: var(--color-text-3);
}
.prompt-field .value {
  min-width: 0;
  color: var(--color-text);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.prompt-field .value--link {
  font-size: 12px;
  color: var(--color-text-3);
}
.prompt-field .value--muted {
  font-size: 12px;
  color: var(--color-text-3);
}
.prompt-footer {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 10px;
}
.prompt-more {
  margin-right: auto;
  font-size: 12px;
  color: var(--color-text-3);
}
</style>
