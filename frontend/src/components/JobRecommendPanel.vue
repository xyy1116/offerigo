<template>
  <div class="recommend-panel">
    <div class="panel-head">
      <span class="today-count">今日推荐 <b>{{ sourceJobs.length }}</b> 条 · 待加入 <b>{{ pendingCount }}</b> 条</span>
      <div class="panel-head-actions">
        <span class="auto-hint">每日 08:00 自动更新</span>
        <button type="button" class="panel-link" @click="emit('compare')">在「推荐 + 目标岗位」中查看</button>
      </div>
    </div>

    <div v-loading="loading" element-loading-text="正在加载今日岗位…" class="job-list">
      <template v-if="sourceJobs.length">
        <article v-for="job in pagedJobs" :key="job.id" class="rec-card">
          <div class="rec-check">
            <el-checkbox
              :model-value="selectedIds.includes(job.id)"
              :disabled="job.isInTarget"
              :aria-label="`选择 ${job.jobName}`"
              @change="(checked) => toggleSelect(job, checked)"
            />
          </div>

          <div class="rec-main">
            <div class="rec-top">
              <div class="rec-title-wrap">
                <h2 class="rec-title">{{ job.jobName }}</h2>
                <span v-if="job.isInTarget" class="added-tag">已加入目标库</span>
              </div>
              <div class="rec-score">
                <span class="score-num">{{ displayScore(job) }}<i>%</i></span>
                <el-progress
                  :percentage="displayScore(job)"
                  :show-text="false"
                  :stroke-width="4"
                  color="var(--color-primary)"
                />
              </div>
            </div>

            <div class="rec-meta">
              <span v-if="job.companyName">{{ job.companyName }}</span>
              <span v-if="job.location">{{ job.location }}</span>
              <span v-if="job.salary">{{ job.salary }}</span>
              <span v-if="job.source" class="rec-source">{{ job.source }}</span>
            </div>

            <p class="rec-req">
              <template
                v-for="(seg, i) in highlightSegments(job.requirement || job.jobDesc || '', job.matchSkillList || [])"
                :key="i"
              >
                <mark v-if="seg.hit" class="kw-hit">{{ seg.text }}</mark><template v-else>{{ seg.text }}</template>
              </template>
            </p>

            <div class="rec-bottom">
              <span class="rec-spacer"></span>
              <el-button type="primary" size="small" :disabled="!job.sourceUrl" @click="openOriginal(job)">
                查看岗位
              </el-button>
            </div>
          </div>
        </article>

        <div class="pager">
          <el-pagination
            v-model:current-page="page"
            :page-size="pageSize"
            :total="sourceJobs.length"
            layout="prev, pager, next"
            background
          />
        </div>
      </template>

      <el-empty v-else-if="!loading" description="今日暂无岗位" />
    </div>

    <transition name="slide-up">
      <div v-if="selectedIds.length" class="batch-bar">
        <span>已选 <b>{{ selectedIds.length }}</b> 个岗位</span>
        <el-button type="primary" :loading="adding" @click="batchAdd">批量添加到我的目标岗位</el-button>
      </div>
    </transition>
  </div>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { batchAddTargetJobs } from '@/api/jobRecommend'
import { highlightSegments } from '@/utils/highlight'

const props = defineProps({
  open: { type: Boolean, default: false },
  jobs: { type: Array, default: () => [] },
  loading: { type: Boolean, default: false },
  scoreMap: { type: Object, default: () => ({}) }
})

const emit = defineEmits(['added', 'compare'])

const adding = ref(false)
const page = ref(1)
const pageSize = 10
const selectedIds = ref([])

const sourceJobs = computed(() => props.jobs || [])
const pendingCount = computed(() => sourceJobs.value.filter((job) => !job.isInTarget).length)

const pagedJobs = computed(() => {
  const start = (page.value - 1) * pageSize
  return sourceJobs.value.slice(start, start + pageSize)
})

watch(() => props.open, (open) => {
  if (open) {
    page.value = 1
    selectedIds.value = []
  }
}, { immediate: true })

function toggleSelect(job, checked) {
  const set = new Set(selectedIds.value)
  if (checked) {
    set.add(job.id)
  } else {
    set.delete(job.id)
  }
  selectedIds.value = [...set]
}

function displayScore(job) {
  const score = props.scoreMap?.[job.id]
  return Number.isFinite(Number(score)) ? Math.round(Number(score)) : (Number(job.matchPercent) || 0)
}

async function batchAdd() {
  if (!selectedIds.value.length || adding.value) {
    return
  }
  adding.value = true
  try {
    // 用户身份由后端从 JWT 取，不信任前端 userId
    const res = await batchAddTargetJobs({ jobIds: selectedIds.value })
    const added = Number(res.data) || 0
    ElMessage.success(added > 0 ? `已添加 ${added} 个岗位到我的目标岗位` : '所选岗位已在「我的目标岗位」中')
    const addedIds = new Set(selectedIds.value)
    emit('added', [...addedIds])
    selectedIds.value = []
    page.value = 1
  } catch (e) {
    // 错误已由响应拦截器统一提示
  } finally {
    adding.value = false
  }
}

function openOriginal(job) {
  const url = String(job.sourceUrl || '').trim()
  if (!/^https?:\/\//i.test(url)) {
    ElMessage.warning('该岗位没有可跳转的外部链接')
    return
  }
  // 用 a[target=_blank] 而不是 window.open(...,'noopener')：后者在部分浏览器总是返回 null，无法判断是否被拦截
  const link = document.createElement('a')
  link.href = url
  link.target = '_blank'
  link.rel = 'noopener noreferrer'
  document.body.appendChild(link)
  link.click()
  link.remove()
}
</script>

<style scoped>
.recommend-panel {
  display: flex;
  flex-direction: column;
  min-height: 100%;
  box-sizing: border-box;
  padding-bottom: 8px;
}

.panel-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
  margin-bottom: 12px;
}
.panel-head-actions {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}
.panel-link {
  padding: 0;
  border: none;
  background: none;
  color: var(--color-accent);
  font-family: inherit;
  font-size: 12px;
  font-weight: 500;
  cursor: pointer;
}
.panel-link:hover {
  text-decoration: underline;
}
.today-count {
  font-size: 13px;
  color: var(--color-text-2);
}
.today-count b {
  color: var(--color-text);
  font-weight: 600;
}
.auto-hint {
  font-size: 12px;
  color: var(--color-text-3);
  font-variant-numeric: tabular-nums;
}

.job-list {
  min-height: 240px;
}

.rec-card {
  display: flex;
  gap: 14px;
  padding: 16px;
  margin-bottom: 12px;
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  transition: border-color 0.2s ease, box-shadow 0.2s ease;
}
.rec-card:hover {
  border-color: var(--color-border-strong);
  box-shadow: var(--shadow-sm);
}
.rec-check {
  padding-top: 2px;
  flex: none;
}
.rec-main {
  flex: 1;
  min-width: 0;
}

.rec-top {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}
.rec-title-wrap {
  min-width: 0;
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.rec-title {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
  color: var(--color-text);
  line-height: 1.4;
}
.added-tag {
  flex: none;
  padding: 1px 8px;
  border-radius: 999px;
  background: var(--color-surface-muted);
  color: var(--color-text-3);
  font-size: 11px;
  font-weight: 500;
  line-height: 1.6;
}

.rec-score {
  flex: none;
  width: 96px;
  text-align: right;
}
.score-num {
  font-family: var(--font-display);
  font-size: 22px;
  font-weight: 600;
  color: var(--color-text);
  font-variant-numeric: tabular-nums;
}
.score-num i {
  margin-left: 1px;
  font-style: normal;
  font-size: 12px;
  font-weight: 400;
  color: var(--color-text-3);
}

.rec-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 4px 14px;
  margin-top: 6px;
  font-size: 12px;
  color: var(--color-text-3);
}
.rec-source {
  color: var(--color-text-2);
}

.rec-req {
  margin: 10px 0 12px;
  font-size: 13px;
  line-height: 1.7;
  color: var(--color-text-2);
  white-space: pre-wrap;
}
.kw-hit {
  padding: 0 2px;
  border-radius: 3px;
  background: var(--color-accent-soft);
  color: inherit;
}

.rec-bottom {
  display: flex;
  align-items: center;
  gap: 12px;
}
.rec-spacer {
  flex: 1;
}

.pager {
  display: flex;
  justify-content: center;
  margin-top: 18px;
}

.batch-bar {
  position: sticky;
  bottom: 0;
  z-index: 5;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-top: 14px;
  padding: 10px 14px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-surface);
  box-shadow: var(--shadow-sm);
  font-size: 13px;
  color: var(--color-text-2);
}
.batch-bar b {
  color: var(--color-text);
}

.slide-up-enter-active,
.slide-up-leave-active {
  transition: opacity 0.2s ease, transform 0.2s var(--ease-out-expo);
}
.slide-up-enter-from,
.slide-up-leave-to {
  opacity: 0;
  transform: translateY(10px);
}
.slide-up-enter-to,
.slide-up-leave-from {
  opacity: 1;
  transform: translateY(0);
}

.rec-check :deep(.el-checkbox__input.is-disabled .el-checkbox__inner) {
  background: var(--color-surface-muted);
  border-color: var(--color-border);
}

@media (max-width: 640px) {
  .rec-card {
    padding: 14px;
    gap: 10px;
  }
  .rec-score {
    width: 72px;
  }
}
</style>