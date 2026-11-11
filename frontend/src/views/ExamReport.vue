<template>
  <div class="report-page">
    <div class="page-header" v-reveal>
      <h1 class="page-title">笔试评估报告</h1>
      <div class="header-actions">
        <button class="button" @click="goInterview">一键开启该岗位 AI 面试</button>
        <button class="button button--default" @click="$router.push('/exam')">返回笔试助手</button>
      </div>
    </div>

    <template v-if="report">
      <!-- 总览 -->
      <el-card shadow="never" class="block" v-reveal>
        <div class="overview">
          <div class="score-main">
            <div class="score-label">{{ report.jobTitle || '岗位笔试' }}</div>
            <div class="score-value">{{ Number(report.userScore || 0).toFixed(1) }}
              <span class="score-unit">/ {{ Number(report.totalScore || 0).toFixed(1) }}</span>
            </div>
            <div class="rate">正确率 {{ accuracy }}% · 错题 {{ report.wrongCount || 0 }} 道</div>
          </div>
          <div class="summary">
            <p>{{ report.summary }}</p>
          </div>
        </div>
      </el-card>

      <!-- 雷达图 -->
      <el-card shadow="never" class="block" v-if="radarDims.length">
        <template #header>知识点掌握雷达图</template>
        <RadarChart :dimensions="radarDims" />
      </el-card>

      <!-- 题型得分 -->
      <el-card shadow="never" class="block" v-if="typeStats.length">
        <template #header>题型得分</template>
        <div class="type-stats">
          <div v-for="s in typeStats" :key="s.type" class="type-stat">
            <span class="ts-label">{{ s.label }}</span>
            <span class="ts-score">{{ Number(s.earned).toFixed(1) }} / {{ Number(s.full).toFixed(1) }}</span>
            <el-progress :percentage="s.percent" :color="s.percent >= 60 ? '#177a4c' : '#b42318'" />
          </div>
        </div>
      </el-card>

      <!-- 逐题明细 -->
      <el-card shadow="never" class="block" v-reveal>
        <template #header>逐题评析</template>
        <div v-for="(item, i) in report.items" :key="i" class="item">
          <div class="item-head">
            <span class="q-num">{{ i + 1 }}.</span>
            <el-tag size="small" :type="item.correct ? 'success' : 'danger'">
              {{ item.correct ? '正确' : '错误' }}
            </el-tag>
            <el-tag size="small" type="info">{{ typeLabel(item.type) }}</el-tag>
            <span class="item-score">{{ Number(item.score || 0).toFixed(1) }}/{{ Number(item.fullScore || 0).toFixed(1) }} 分</span>
          </div>
          <p class="item-stem">{{ item.stem }}</p>

          <div class="qa-row">
            <strong>我的答案：</strong>
            <span :class="item.correct ? 'ok' : 'bad'">{{ item.userAnswer || '（未作答）' }}</span>
          </div>
          <div class="qa-row">
            <strong>正确答案：</strong>
            <span v-if="item.type === 'coding'" class="code-ans" v-html="codeHtml(item.standardAnswer)"></span>
            <span v-else class="ans-text">{{ item.standardAnswer || '—' }}</span>
          </div>
          <div class="qa-row qa-verdict">
            <strong>错误原因：</strong>
            <span>{{ item.errorReason || '—' }}</span>
          </div>
          <p class="analysis">{{ item.analysis }}</p>
          <p class="extension" v-if="item.extension && item.extension !== item.analysis">
            <strong>延伸学习：</strong>{{ item.extension }}
          </p>
        </div>
      </el-card>
    </template>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import hljs from 'highlight.js/lib/common'
import 'highlight.js/styles/github.css'
import { getExamReport } from '@/api/exam'
import { useLinkageStore } from '@/stores/linkage'
import RadarChart from '@/components/RadarChart.vue'

const route = useRoute()
const router = useRouter()
const linkage = useLinkageStore()
const report = ref(null)

onMounted(async () => {
  try {
    const res = await getExamReport(route.params.id)
    report.value = res.data
  } catch (e) {
    // 报告不存在或接口异常：给出提示并退回列表，避免整页空白
    ElMessage.error(e?.message || '报告加载失败，请返回重试')
    router.replace('/exam')
  }
})

// 模块四 → 模块三联动：一键开启该岗位 AI 面试
function goInterview() {
  linkage.setContext({ from: 'exam', jobTitle: report.value?.jobTitle || '' })
  router.push('/interview')
}

const accuracy = computed(() => {
  const total = Number(report.value?.totalScore || 0)
  if (!total) return 0
  return Math.round((Number(report.value?.userScore || 0) / total) * 100)
})

const radarDims = computed(() => {
  const map = report.value?.dimensionScores || {}
  return Object.entries(map).map(([name, score]) => ({ name, score: Number(score) || 0 }))
})

const typeStats = computed(() => {
  const items = report.value?.items || []
  const map = {}
  items.forEach((it) => {
    if (!map[it.type]) map[it.type] = { earned: 0, full: 0 }
    map[it.type].earned += Number(it.score || 0)
    map[it.type].full += Number(it.fullScore || 0)
  })
  return Object.entries(map).map(([type, v]) => ({
    type,
    label: typeLabel(type),
    earned: v.earned,
    full: v.full,
    percent: v.full ? Math.round((v.earned / v.full) * 100) : 0
  }))
})

function codeHtml(code) {
  try {
    return hljs.highlightAuto(code || '').value
  } catch (e) {
    return (code || '').replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
  }
}
function typeLabel(t) {
  return { single: '单选题', multiple: '多选题', short_answer: '简答题', coding: '编程题' }[t] || t
}
</script>

<style scoped>
.report-page {
  max-width: 1100px;
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
.header-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}
.block {
  margin-bottom: 16px;
}
.overview {
  display: flex;
  align-items: flex-start;
  gap: 40px;
}
.score-main {
  flex-shrink: 0;
}
.score-label {
  margin-bottom: 8px;
  font-size: 13px;
  color: var(--color-text-2);
}
.score-value {
  font-size: 40px;
  font-weight: 600;
  line-height: 1;
  letter-spacing: -0.02em;
  color: var(--color-lilac-deep);
  font-family: var(--font-display);
  font-variant-numeric: tabular-nums;
}
.score-unit {
  font-size: 16px;
  font-weight: 400;
  color: var(--color-text-3);
}
.rate {
  margin-top: 10px;
  font-size: 13px;
  color: var(--color-text-2);
}
.summary {
  flex: 1;
  font-size: 14px;
  color: var(--color-text-2);
  line-height: 1.8;
}
.type-stats {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(240px, 1fr));
  gap: 12px;
}
.type-stat {
  padding: 12px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
}
.ts-label {
  font-size: 13px;
  font-weight: 500;
  color: var(--color-text);
}
.ts-score {
  float: right;
  font-size: 12px;
  color: var(--color-text-3);
  font-variant-numeric: tabular-nums;
}
.item {
  padding: 16px 0;
  border-bottom: 1px solid var(--color-border);
}
.item:last-child {
  border-bottom: none;
  padding-bottom: 0;
}
.item-head {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}
.q-num {
  font-size: 13px;
  font-weight: 600;
  color: var(--color-accent);
}
.item-score {
  margin-left: auto;
  font-size: 12px;
  color: var(--color-text-3);
  font-variant-numeric: tabular-nums;
}
.item-stem {
  margin: 0 0 10px;
  font-size: 14px;
  color: var(--color-text);
  line-height: 1.7;
  white-space: pre-wrap;
}
.qa-row {
  margin: 6px 0;
  font-size: 13px;
  color: var(--color-text-2);
  line-height: 1.6;
}
.qa-row strong {
  font-weight: 500;
  color: var(--color-text);
}
.qa-row .ok {
  color: var(--color-success);
}
.qa-row .bad {
  color: var(--color-danger);
}
.qa-verdict {
  color: var(--color-warning);
}
.ans-text {
  white-space: pre-wrap;
}
.code-ans {
  display: block;
  margin-top: 4px;
  padding: 10px 12px;
  background: var(--color-surface-muted);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
  font-size: 13px;
}
.analysis {
  margin: 8px 0 0;
  font-size: 13px;
  color: var(--color-text-2);
  line-height: 1.7;
  white-space: pre-wrap;
}
.extension {
  margin: 8px 0 0;
  font-size: 13px;
  color: var(--color-text-2);
  line-height: 1.7;
}
</style>
