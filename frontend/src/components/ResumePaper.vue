<template>
  <div class="resume-paper" :class="'layout-' + layout" :style="paperVars">
    <!-- 侧边栏双栏 -->
    <div v-if="layout === 'sidebar'" class="rp-cols">
      <aside class="rp-side">
        <img v-if="photo" class="rp-photo" :src="photo" alt="证件照" />
        <div class="rp-name">{{ displayName }}</div>
        <div v-if="basicInfo" class="rp-basic">{{ basicInfo }}</div>
        <section v-if="skillsText" class="rp-section">
          <h4 class="rp-section-title">核心技能</h4>
          <p class="rp-skills">{{ skillsText }}</p>
        </section>
        <section v-if="certificates.length" class="rp-section">
          <h4 class="rp-section-title">证书奖项</h4>
          <ul class="rp-list">
            <li v-for="(item, i) in certificates" :key="i">{{ item }}</li>
          </ul>
        </section>
      </aside>
      <main class="rp-main">
        <section v-if="selfEvaluation" class="rp-section">
          <h4 class="rp-section-title">自我评价</h4>
          <p class="rp-self">{{ selfEvaluation }}</p>
        </section>
        <section v-if="experiences.length" class="rp-section">
          <h4 class="rp-section-title">实习/项目经历</h4>
          <ul class="rp-list">
            <li v-for="(item, i) in experiences" :key="i">{{ item }}</li>
          </ul>
        </section>
        <section v-if="highlights.length" class="rp-section">
          <h4 class="rp-section-title">项目亮点</h4>
          <ul class="rp-list">
            <li v-for="(item, i) in highlights" :key="i">{{ item }}</li>
          </ul>
        </section>
        <section v-if="education.length" class="rp-section">
          <h4 class="rp-section-title">教育背景</h4>
          <ul class="rp-list">
            <li v-for="(item, i) in education" :key="i">{{ item }}</li>
          </ul>
        </section>
      </main>
    </div>

    <!-- 单栏 / 宽页眉 / 轻商务 -->
    <template v-else>
      <header :class="layout === 'banner' ? 'rp-banner' : 'rp-head'">
        <div class="rp-head-main">
          <div class="rp-name">{{ displayName }}</div>
          <div v-if="basicInfo" class="rp-basic">{{ basicInfo }}</div>
        </div>
        <img v-if="photo" class="rp-photo" :src="photo" alt="证件照" />
      </header>
      <div class="rp-body">
        <section v-if="selfEvaluation" class="rp-section">
          <h4 class="rp-section-title">自我评价</h4>
          <p class="rp-self">{{ selfEvaluation }}</p>
        </section>
        <section v-if="skillsText" class="rp-section">
          <h4 class="rp-section-title">核心技能</h4>
          <p class="rp-skills">{{ skillsText }}</p>
        </section>
        <section v-for="section in listSections" :key="section.title" class="rp-section">
          <h4 class="rp-section-title">{{ section.title }}</h4>
          <ul class="rp-list">
            <li v-for="(item, i) in section.items" :key="i">{{ item }}</li>
          </ul>
        </section>
      </div>
    </template>
  </div>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  resume: { type: Object, default: null },
  template: { type: Object, default: null },
  photo: { type: String, default: '' }
})

const layout = computed(() => {
  const l = props.template?.layout
  if (!l) return 'single'
  return l === 'double' ? 'sidebar' : l
})
const primary = computed(() => safeColor(props.template?.primary, '#1f2933'))
const secondary = computed(() => safeColor(props.template?.secondary, '#6b7280'))
const background = computed(() => safeColor(props.template?.background, '#ffffff'))
const text = computed(() => safeColor(props.template?.text, '#1f2933'))
const contrast = computed(() => (isLight(primary.value) ? '#111827' : '#ffffff'))

const paperVars = computed(() => ({
  '--rp-primary': primary.value,
  '--rp-secondary': secondary.value,
  '--rp-bg': background.value,
  '--rp-text': text.value,
  '--rp-contrast': contrast.value
}))

const displayName = computed(() => textOr(props.resume?.name, '求职者'))
const basicInfo = computed(() => textOr(props.resume?.basicInfo, ''))
const selfEvaluation = computed(() => textOr(props.resume?.selfEvaluation, ''))
const experiences = computed(() => cleanList(props.resume?.experiences))
const highlights = computed(() => cleanList(props.resume?.projectHighlights))
const education = computed(() => cleanList(props.resume?.education))
const certificates = computed(() => cleanList(props.resume?.certificates))
const skillsText = computed(() =>
  (props.resume?.skills || [])
    .map((s) => (s && s.name ? String(s.name).trim() : ''))
    .filter(Boolean)
    .join(' · ')
)
const listSections = computed(() =>
  [
    { title: '实习/项目经历', items: experiences.value },
    { title: '项目亮点', items: highlights.value },
    { title: '教育背景', items: education.value },
    { title: '证书奖项', items: certificates.value }
  ].filter((s) => s.items.length)
)

function cleanList(list) {
  return (list || []).map((x) => (x == null ? '' : String(x).trim())).filter(Boolean)
}

function textOr(value, fallback) {
  return value == null ? fallback : String(value)
}

function safeColor(value, fallback) {
  return /^#?[0-9a-fA-F]{6}$/.test(String(value || '').trim())
    ? (String(value).trim().startsWith('#') ? String(value).trim() : '#' + String(value).trim())
    : fallback
}

function isLight(hex) {
  const h = hex.replace('#', '')
  const r = parseInt(h.slice(0, 2), 16)
  const g = parseInt(h.slice(2, 4), 16)
  const b = parseInt(h.slice(4, 6), 16)
  return (r * 299 + g * 587 + b * 114) / 1000 > 170
}
</script>

<style scoped>
.resume-paper {
  min-height: 540px;
  background: var(--rp-bg, #ffffff);
  color: var(--rp-text, #1f2933);
  border: 1px solid var(--color-border);
  border-radius: 2px;
  overflow: hidden;
  font-family: 'SimHei', 'Noto Sans SC', 'Microsoft YaHei', sans-serif;
  font-size: 14px;
  line-height: 1.55;
}
.rp-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 20px;
  padding: 28px 32px 0;
}
.rp-head-main {
  min-width: 0;
}
.rp-name {
  font-size: 26px;
  font-weight: 600;
  line-height: 1.2;
  letter-spacing: 0.5px;
  color: var(--rp-primary, #1f2933);
}
.rp-basic {
  margin-top: 6px;
  font-size: 12px;
  color: var(--rp-secondary, #6b7280);
  white-space: pre-line;
}
.rp-photo {
  width: 78px;
  height: 104px;
  flex: none;
  object-fit: cover;
  border: 1px solid #d9d9d9;
  border-radius: 2px;
}
.rp-body {
  padding: 0 32px 30px;
}
.rp-section {
  margin-top: 18px;
}
.rp-section-title {
  margin: 0 0 8px;
  padding-bottom: 4px;
  font-size: 15px;
  font-weight: 600;
  color: var(--rp-primary, #1f2933);
  border-bottom: 1px solid #d9d9d9;
}
.rp-self {
  margin: 0;
  white-space: pre-line;
}
.rp-skills {
  margin: 0;
}
.rp-list {
  margin: 0;
  padding-left: 20px;
}
.rp-list li {
  margin-bottom: 3px;
}

/* 侧边栏双栏 */
.rp-cols {
  display: flex;
  align-items: stretch;
  min-height: 540px;
}
.rp-side {
  width: 33%;
  flex: none;
  padding: 26px 20px;
  background: var(--rp-primary, #143252);
  color: var(--rp-contrast, #ffffff);
}
.rp-side .rp-name {
  font-size: 20px;
  text-align: center;
  color: var(--rp-contrast, #ffffff);
}
.rp-side .rp-basic {
  text-align: center;
  color: var(--rp-contrast, #ffffff);
}
.rp-side .rp-photo {
  display: block;
  margin: 0 auto 12px;
  border-color: var(--rp-contrast, #ffffff);
}
.rp-side .rp-section-title {
  color: var(--rp-contrast, #ffffff);
  border-bottom-color: var(--rp-contrast, #ffffff);
}
.rp-main {
  flex: 1;
  min-width: 0;
  padding: 26px 28px 30px;
}
.rp-main .rp-section:first-child {
  margin-top: 0;
}

/* 宽页眉 */
.rp-banner {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  padding: 22px 32px;
  background: var(--rp-primary, #1e3a5f);
  color: var(--rp-contrast, #ffffff);
}
.rp-banner .rp-name,
.rp-banner .rp-basic {
  color: var(--rp-contrast, #ffffff);
}
.rp-banner .rp-photo {
  border-color: var(--rp-contrast, #ffffff);
}

/* 轻商务 */
.layout-serif .rp-name {
  letter-spacing: 1.5px;
}
.layout-serif .rp-section-title {
  letter-spacing: 0.5px;
  border-bottom: 2px solid var(--rp-primary, #2f4f3e);
}
</style>
