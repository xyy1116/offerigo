<template>
  <div class="home-body">
    <!-- ======================= 1. Hero ======================= -->
    <section class="hero" aria-label="OfferiGo 智能求职助手">
      <div ref="heroParallaxRef" class="hero-parallax" role="img"
        aria-label="暮色湖面中央，一座浅色石材拱门立于水中，暖光从门内透出">
        <div class="hero-media">
          <div class="hero-shot">
            <img class="hero-bg" src="/stratum-vision-poster.jpg" alt="" fetchpriority="high" decoding="async" />
            <video
              class="hero-video"
              src="/stratum-vision.mp4"
              poster="/stratum-vision-poster.jpg"
              autoplay
              muted
              loop
              playsinline
              preload="auto"
              aria-hidden="true"
            ></video>
          </div>
        </div>
      </div>
      <div class="hero-scrim" aria-hidden="true"></div>
      <div class="hero-copy">
        <h1 class="hero-title">
          <span class="hero-line"><span class="hero-rise">从简历素材出发，</span></span>
          <span class="hero-line"><span class="hero-rise hero-title-accent">找到更匹配的岗位。</span></span>
        </h1>
        <p class="hero-sub">
          以简历素材为基础，AI 对标岗位做匹配评估，<br />
          定制文书、模拟面试、笔试冲刺，让求职准备更有方向。
        </p>
        <div class="hero-actions">
          <button class="hero-btn hero-btn--primary" @click="go('/jobmatch')">立即体验</button>
          <button class="hero-btn hero-btn--ghost" @click="overviewVisible = true">使用指南</button>
        </div>
      </div>
      <button class="hero-scroll" aria-label="向下滚动" @click="scrollToWorkflow">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.4" aria-hidden="true">
          <path d="M6 9l6 6 6-6" stroke-linecap="round" stroke-linejoin="round" />
        </svg>
      </button>
    </section>

    <!-- ============ 2. 痛点 + 时间线 + 功能入口 ============ -->
    <section class="workflow">
      <div class="pain" v-reveal.repeat>
        <span class="section-eyebrow">求职工作流</span>
        <h2 class="section-title">毕业生的求职困境</h2>
        <p class="pain-text">
          绝大多数毕业生从一张空白简历起步，既不知如何把零散的经历整理成
          能通过筛选的简历，也常常对决定成败的面试与笔试准备不足。
        </p>
        <ul class="pain-tags">
          <li>简历盲区</li>
          <li>简历与 JD 不匹配</li>
          <li>面试紧张</li>
          <li>笔试准备不足</li>
        </ul>
      </div>

      <div class="timeline" v-reveal.repeat="60">
        <div class="timeline-rail"></div>
        <div
          v-for="(s, i) in steps"
          :key="s.path"
          class="timeline-node"
          :class="{ active: isActive(s.path) }"
          role="button"
          tabindex="0"
          @click="go(s.path)"
          @keydown.enter="go(s.path)"
          @keydown.space.prevent="go(s.path)"
        >
          <div class="timeline-dot">{{ i + 1 }}</div>
          <div class="timeline-label">{{ s.label }}</div>
        </div>
      </div>

      <div class="functions" v-reveal.repeat="100">
        <article
          v-for="f in functions"
          :key="f.path"
          class="func"
          role="button"
          tabindex="0"
          @click="go(f.path)"
          @keydown.enter="go(f.path)"
          @keydown.space.prevent="go(f.path)"
        >
          <div class="func-icon"><el-icon :size="26"><component :is="f.icon" /></el-icon></div>
          <h3 class="func-title">{{ f.title }}</h3>
          <p class="func-desc">{{ f.desc }}</p>
          <span class="func-link">进入模块 <el-icon><Right /></el-icon></span>
        </article>
      </div>
    </section>

    <!-- ======================= 3. 数据统计 ======================= -->
    <section ref="statsRef" class="stats" v-reveal.repeat>
      <div class="stats-inner">
        <div v-for="(s, i) in stats" :key="s.label" class="stat">
          <div class="stat-value">
            <span class="stat-num">{{ displayed[i] }}</span>{{ s.suffix }}
          </div>
          <div class="stat-label">{{ s.label }}</div>
        </div>
      </div>
    </section>

    <!-- ================= 4. 技术亮点 ================= -->
    <section class="tech" v-reveal.repeat="60">
      <div class="tech-inner">
        <span class="section-eyebrow">能力矩阵</span>
        <h2 class="tech-title">平台核心优势</h2>
        <div class="tech-tags">
          <div v-for="t in techs" :key="t.name" class="tech-tag">
            <div class="tech-tag-icon"><el-icon :size="20"><component :is="t.icon" /></el-icon></div>
            <span class="tech-tag-name">{{ t.name }}</span>
          </div>
        </div>
      </div>
    </section>

    <!-- 使用指南弹窗 -->
    <el-dialog v-model="overviewVisible" title="使用指南" width="620px" align-center>
      <div class="guide">
        <p class="guide-intro">
          欢迎使用 AI 智能求职助手。本平台面向所有 IT 行业求职者，一站式完成简历素材管理、岗位匹配、简历打磨、求职备考。
        </p>
        <h4 class="guide-heading">建议使用流程</h4>
        <ol class="guide-steps">
          <li>添加多条简历素材，录入多个目标岗位 JD，平台会对每一组素材与岗位做匹配分析</li>
          <li>查看实时岗位评估打分，清晰看到匹配项、待补全项，按照 AI 提示补齐内容</li>
          <li>挑选简历模板、上传证件照，预览效果，一键生成对应岗位的定制简历与求职信，支持导出 PDF / Word</li>
          <li>根据岗位评估持续调整素材，反复迭代优化简历内容；也可获取各大第三方平台匹配岗位，跳转对应官方网站投递</li>
          <li>进入 AI 模拟面试官模块，开展语音模拟面试，锻炼临场应答</li>
          <li>使用 AI 笔试助手，进行岗位针对性笔试刷题训练</li>
        </ol>
        <p class="guide-tip">
          温馨提示：岗位匹配评估、简历与求职信生成、AI 面试以及笔试训练功能需要登录账号之后才可以正常使用。
        </p>
      </div>
      <template #footer>
        <button class="btn btn--primary" @click="overviewVisible = false">关闭</button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onBeforeUnmount, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  Right,
  Aim,
  Microphone,
  EditPen,
  Connection,
  Lock,
  DataAnalysis
} from '@element-plus/icons-vue'

const route = useRoute()
const router = useRouter()

const overviewVisible = ref(false)
const statsRef = ref()

const steps = [
  { label: '智能岗位匹配', path: '/jobmatch' },
  { label: 'AI 语音模拟面试', path: '/interview' },
  { label: '岗位定向笔试训练', path: '/exam' }
]

const functions = [
  {
    title: '智能岗位匹配',
    path: '/jobmatch',
    icon: Aim,
    desc: '投喂个人信息与目标岗位，AI 综合打分并智能排名。'
  },
  {
    title: 'AI 语音面试',
    path: '/interview',
    icon: Microphone,
    desc: '与 AI 面试官进行语音模拟面试，还原真实场景。'
  },
  {
    title: '笔试训练',
    path: '/exam',
    icon: EditPen,
    desc: '岗位定向组卷训练，错题自动归档、随时巩固。'
  }
]

const stats = [
  { label: '累计完成简历分析', value: 1280, suffix: '+' },
  { label: 'AI 模拟面试场次', value: 760, suffix: '+' },
  { label: '简历优化建议', value: 3600, suffix: '+' },
  { label: '专项题库条目', value: 2100, suffix: '+' }
]
const displayed = ref(stats.map(() => 0))

const techs = [
  { name: '岗位定向AI分析', icon: Aim },
  { name: '完整求职链路服务', icon: Connection },
  { name: '个人简历数据安全保护', icon: Lock },
  { name: '求职练习过程数据留存', icon: DataAnalysis }
]

const stack = [
  { name: 'Vue 3', desc: '响应式单页应用，结合 Element Plus 组件库，PC 优先的现代界面。' },
  { name: 'Spring Boot', desc: 'RESTful 后端服务，JWT 鉴权、分层架构与 OSS 对象存储。' },
  { name: 'MySQL', desc: '关系型数据库，持久化用户、简历、面试与笔试记录。' },
  { name: 'DashScope LLM', desc: '驱动简历分析、语音面试与岗位定向出题。' }
]

function isActive(path) {
  return route.path === path || route.path.startsWith(path + '/')
}

function go(path) {
  router.push(path)
}

function scrollToWorkflow() {
  const reduced = window.matchMedia && window.matchMedia('(prefers-reduced-motion: reduce)').matches
  document.querySelector('.workflow')?.scrollIntoView({ behavior: reduced ? 'auto' : 'smooth', block: 'start' })
}

// Hero 背景随鼠标缓慢反向漂移：滞后的 lerp 让画面像沉在水里
const heroParallaxRef = ref(null)
let parallaxRaf = 0
let px = 0
let py = 0
let tx = 0
let ty = 0
let cleanupParallax = null

function initHeroParallax() {
  const el = heroParallaxRef.value
  const reduced = window.matchMedia && window.matchMedia('(prefers-reduced-motion: reduce)').matches
  const fine = window.matchMedia && window.matchMedia('(hover: hover) and (pointer: fine)').matches
  if (!el || reduced || !fine) return

  const tick = () => {
    px += (tx - px) * 0.045
    py += (ty - py) * 0.045
    const W = window.innerWidth
    const H = window.innerHeight
    const cover = 2 * Math.max(Math.abs(px) / W, Math.abs(py) / H)
    const scale = 1 + cover + 0.014 * Math.max(0, -py / H)
    el.style.transform = `translate3d(${px.toFixed(2)}px, ${py.toFixed(2)}px, 0) scale(${scale.toFixed(4)})`
    parallaxRaf = Math.abs(tx - px) > 0.05 || Math.abs(ty - py) > 0.05 ? requestAnimationFrame(tick) : 0
  }
  const kick = () => {
    if (!parallaxRaf) parallaxRaf = requestAnimationFrame(tick)
  }
  const onMove = (e) => {
    const cx = (e.clientX / window.innerWidth) * 2 - 1
    const cy = (e.clientY / window.innerHeight) * 2 - 1
    tx = -cx * window.innerWidth * 0.0156
    ty = -cy * window.innerHeight * 0.015
    kick()
  }
  const onLeave = () => {
    tx = 0
    ty = 0
    kick()
  }
  window.addEventListener('pointermove', onMove, { passive: true })
  document.addEventListener('mouseleave', onLeave)
  cleanupParallax = () => {
    window.removeEventListener('pointermove', onMove)
    document.removeEventListener('mouseleave', onLeave)
    if (parallaxRaf) cancelAnimationFrame(parallaxRaf)
    parallaxRaf = 0
  }
}

onMounted(initHeroParallax)

onBeforeUnmount(() => {
  if (cleanupParallax) cleanupParallax()
})

function openLogin() {
  router.push({ path: '/', query: { login: '1' } })
}


let statsTriggered = false
function runStats() {
  const start = performance.now()
  const duration = 1600
  function tick(now) {
    const p = Math.min(1, (now - start) / duration)
    const eased = 1 - Math.pow(1 - p, 3) // easeOutCubic
    stats.forEach((s, i) => {
      displayed.value[i] = Math.round(s.value * eased)
    })
    if (p < 1) requestAnimationFrame(tick)
  }
  requestAnimationFrame(tick)
}

onMounted(() => {
  // 尊重「减少动态效果」：直接显示最终数值，不播放数字滚动
  if (window.matchMedia && window.matchMedia('(prefers-reduced-motion: reduce)').matches) {
    displayed.value = stats.map((s) => s.value)
    return
  }
  const observer = new IntersectionObserver(
    (entries) => {
      if (entries.some((e) => e.isIntersecting) && !statsTriggered) {
        statsTriggered = true
        runStats()
        observer.disconnect()
      }
    },
    { threshold: 0.3 }
  )
  if (statsRef.value) observer.observe(statsRef.value)
})
</script>

<style scoped>
.home-body {
  --ink: var(--color-text);
  --body-c: var(--color-text-2);
  --muted: var(--color-text-3);
  background: var(--color-page-bg);
  color: var(--body-c);
  line-height: 1.6;
  padding-bottom: 0;
  /* 头部 fixed 后内容区预留了 56px；hero 顶上去铺满视口，由透明头部叠在上面 */
  margin-top: -56px;
}

/* ===== Hero ===== */
.hero {
  position: relative;
  height: 100vh;
  overflow: hidden;
  background: #04070d;
}
.hero-parallax,
.hero-media {
  position: absolute;
  inset: 0;
}
.hero-parallax {
  will-change: transform;
}
/* 入场：画面落定（路由切换时也够快，不会长时间像没加载） */
.hero-media {
  animation: heroMediaIn 0.8s cubic-bezier(0.16, 1, 0.3, 1) both;
}
/* 16:9 取景框：视频和静态图共用同一套裁切，切换时不跳位 */
.hero-shot {
  position: absolute;
  left: 50%;
  top: 50%;
  width: max(100vw, 177.78vh);
  height: max(100vh, 56.25vw);
  transform: translate(-45.5%, -50%) scale(1.1);
}
/* 呼吸感：极慢的推拉，避免一张死图 */
.hero-bg,
.hero-video {
  position: absolute;
  inset: 0;
  display: block;
  width: 100%;
  height: 100%;
  object-fit: cover;
  object-position: 50% 50%;
  animation: heroBreathe 26s ease-in-out infinite alternate;
}
@keyframes heroMediaIn {
  from {
    opacity: 0;
    transform: scale(1.05);
  }
  to {
    opacity: 1;
    transform: none;
  }
}
@keyframes heroBreathe {
  from {
    transform: scale(1.01) translate3d(0, 0, 0);
  }
  to {
    transform: scale(1.045) translate3d(-0.6%, -0.6%, 0);
  }
}
/* 左侧压暗，保证白字落在山体与水面而不是亮部石面上 */
.hero-scrim {
  position: absolute;
  inset: 0;
  background:
    linear-gradient(90deg, rgba(4, 7, 13, 0.9) 0%, rgba(4, 7, 13, 0.66) 30%,
      rgba(4, 7, 13, 0.18) 56%, rgba(4, 7, 13, 0) 76%),
    linear-gradient(0deg, rgba(4, 7, 13, 0.66) 0%, rgba(4, 7, 13, 0) 34%);
}
.hero-copy {
  position: absolute;
  left: 0;
  top: 50%;
  width: min(720px, 46vw);
  padding-left: clamp(24px, 8.2vw, 118px);
  transform: translateY(-50%);
  color: #fff;
}
.hero-title {
  font-size: clamp(2rem, 3vw, 3.2rem);
  font-weight: 600;
  line-height: 1.16;
  letter-spacing: -0.02em;
  text-wrap: balance;
}
.hero-title-accent {
  background: linear-gradient(120deg, #ffffff 0%, #b8ecf2 52%, #6fd3df 100%);
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
}
.hero-sub {
  margin-top: clamp(16px, 2.2vh, 24px);
  max-width: 34em;
  font-size: clamp(0.95rem, 1.05vw, 1.15rem);
  line-height: 1.75;
  color: rgba(224, 232, 245, 0.76);
}
.hero-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  margin-top: clamp(22px, 3.4vh, 36px);
}
.hero-btn {
  position: relative;
  overflow: hidden;
  display: inline-flex;
  align-items: center;
  gap: 8px;
  height: clamp(42px, 5.2vh, 48px);
  padding: 0 clamp(18px, 1.6vw, 24px);
  border: 1px solid transparent;
  border-radius: 8px;
  font-family: inherit;
  font-size: clamp(13px, 1vw, 15px);
  font-weight: 500;
  cursor: pointer;
  transition:
    background-color 0.2s ease,
    border-color 0.2s ease,
    color 0.2s ease,
    transform 0.2s ease;
}
.hero-btn:hover {
  transform: translateY(-1px);
}
.hero-btn:focus-visible {
  outline: 2px solid #e8d1cb;
  outline-offset: 3px;
}
.hero-btn--primary {
  background: #f5f5f2;
  color: #101820;
}
.hero-btn--primary::after {
  content: '';
  position: absolute;
  top: 0;
  left: -60%;
  width: 40%;
  height: 100%;
  background: linear-gradient(100deg, transparent, rgba(16, 24, 32, 0.12), transparent);
  transform: skewX(-18deg);
  transition: left 0.55s var(--ease-out-expo);
  pointer-events: none;
}
.hero-btn--primary:hover::after {
  left: 120%;
}
.hero-btn--primary:hover {
  background: #ffffff;
}
.hero-btn-arrow {
  font-size: 0.9em;
  line-height: 1;
  transform: translateY(-1px);
}
.hero-btn--ghost {
  background: rgba(255, 255, 255, 0.04);
  border-color: rgba(255, 255, 255, 0.3);
  color: rgba(255, 255, 255, 0.9);
  -webkit-backdrop-filter: blur(10px);
  backdrop-filter: blur(10px);
}
.hero-btn--ghost:hover {
  background: rgba(255, 255, 255, 0.1);
  border-color: rgba(255, 255, 255, 0.5);
  color: #ffffff;
}
.hero-scroll {
  position: absolute;
  bottom: clamp(14px, 2.4vh, 26px);
  left: 50%;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 40px;
  height: 40px;
  border: 0;
  border-radius: 999px;
  background: transparent;
  color: rgba(255, 255, 255, 0.5);
  cursor: pointer;
  transform: translateX(-50%);
  transition: color 0.2s ease, background-color 0.2s ease;
  animation: heroHint 2.8s ease-in-out infinite;
}
.hero-scroll:hover {
  color: #ffffff;
  background: rgba(255, 255, 255, 0.08);
}
.hero-scroll svg {
  width: 20px;
  height: 20px;
}
@keyframes heroHint {
  0%,
  100% {
    opacity: 0.45;
    transform: translate(-50%, 0);
  }
  50% {
    opacity: 0.9;
    transform: translate(-50%, 6px);
  }
}
/* 标题逐行从遮罩里升起 */
.hero-line {
  display: block;
  overflow: hidden;
}
.hero-rise {
  display: block;
}
.hero-line:first-child .hero-rise {
  animation: heroMaskUp 0.95s cubic-bezier(0.16, 1, 0.3, 1) 0.22s both;
}
.hero-line:last-child .hero-rise {
  animation: heroMaskUp 0.95s cubic-bezier(0.16, 1, 0.3, 1) 0.34s both;
}
@keyframes heroMaskUp {
  from {
    transform: translateY(112%);
  }
  to {
    transform: translateY(0);
  }
}
@keyframes heroUp {
  from {
    opacity: 0;
    transform: translateY(18px);
  }
  to {
    opacity: 1;
    transform: none;
  }
}
.hero-sub,
.hero-actions {
  animation: heroUp 0.85s cubic-bezier(0.16, 1, 0.3, 1) both;
}
.hero-sub {
  animation-delay: 0.5s;
}
.hero-actions {
  animation-delay: 0.62s;
}

/* ===== Buttons ===== */
.btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  height: 40px;
  padding: 0 16px;
  font-family: inherit;
  font-size: 13px;
  font-weight: 500;
  border-radius: var(--radius-sm);
  border: 1px solid transparent;
  cursor: pointer;
  transition:
    background-color 0.18s ease,
    border-color 0.18s ease,
    color 0.18s ease,
    box-shadow 0.18s ease,
    transform 0.18s var(--ease-out-expo);
}
.btn:hover {
  transform: translateY(-1px);
}
.btn:active {
  transform: translateY(0) scale(0.98);
}
.btn:focus-visible {
  outline: 2px solid var(--color-primary);
  outline-offset: 2px;
}
.btn--primary {
  background: var(--color-primary);
  border-color: var(--color-primary);
  color: #ffffff;
}
.btn--primary:hover {
  background: var(--color-primary-mid);
  border-color: var(--color-primary-mid);
  box-shadow: var(--shadow-teal);
}
.btn--ghost {
  background: var(--color-surface);
  border-color: var(--color-border-strong);
  color: var(--color-text);
}
.btn--ghost:hover {
  background: var(--color-surface-muted);
  border-color: var(--color-border-hover);
  box-shadow: var(--shadow-sm);
}

/* ===== Workflow ===== */
.workflow {
  max-width: 1200px;
  margin: 0 auto;
  padding: 72px 24px 0;
  /* 头部固定浮层，滚到此处时留出 56px，避免标题被遮住 */
  scroll-margin-top: 56px;
}
.section-title {
  font-size: 24px;
  font-weight: 600;
  letter-spacing: -0.02em;
  color: var(--ink);
}
.section-eyebrow {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 10px;
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.18em;
  color: var(--color-accent);
}
.section-eyebrow::before {
  content: '';
  width: 22px;
  height: 1px;
  background: var(--color-accent);
  opacity: 0.7;
}
.pain-text {
  margin-top: 12px;
  max-width: 640px;
  color: var(--ink);
  font-size: 16px;
  line-height: 1.7;
}
.pain-tags {
  margin-top: 20px;
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  list-style: none;
  padding: 0;
}
.pain-tags li {
  padding: 4px 10px;
  font-size: 12px;
  color: var(--body-c);
  border: 1px solid var(--color-border);
  border-radius: 4px;
  background: var(--color-surface);
  transition: border-color 0.18s ease, color 0.18s ease;
}
.pain-tags li:hover {
  border-color: var(--color-border-hover);
  color: var(--color-text);
}

/* Timeline */
.timeline {
  position: relative;
  display: flex;
  justify-content: space-between;
  margin-top: 48px;
  padding: 8px 0;
}
.timeline-rail {
  position: absolute;
  top: 26px;
  left: 4%;
  right: 4%;
  height: 1px;
  background: var(--color-border);
}
.timeline-node {
  position: relative;
  z-index: 1;
  width: 24%;
  text-align: center;
  cursor: pointer;
}
.timeline-node:focus-visible,
.func:focus-visible {
  outline: 2px solid var(--color-primary);
  outline-offset: 3px;
  border-radius: var(--radius-sm);
}
.timeline-dot {
  width: 36px;
  height: 36px;
  margin: 0 auto;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 13px;
  font-weight: 500;
  color: var(--muted);
  background: var(--color-surface);
  border: 1px solid var(--color-border-strong);
  transition:
    background-color 0.18s ease,
    border-color 0.18s ease,
    color 0.18s ease,
    box-shadow 0.18s ease,
    transform 0.18s var(--ease-out-expo);
}
.timeline-node:hover .timeline-dot,
.timeline-node.active .timeline-dot {
  color: #ffffff;
  background: var(--color-primary);
  border-color: var(--color-primary);
  box-shadow: var(--shadow-teal);
}
.timeline-node:hover .timeline-dot {
  transform: translateY(-2px);
}
.timeline-label {
  margin-top: 12px;
  font-size: 13px;
  color: var(--body-c);
  transition: color 0.15s ease;
}
.timeline-node.active .timeline-label {
  color: var(--ink);
  font-weight: 500;
}

/* Functions */
.functions {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 16px;
  margin-top: 40px;
}
.func {
  position: relative;
  overflow: hidden;
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  padding: 24px;
  cursor: pointer;
  transition:
    border-color 0.2s ease,
    box-shadow 0.2s ease,
    transform 0.2s var(--ease-out-expo);
}
.func::after {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: 2px;
  background: var(--gradient-action);
  opacity: 0;
  transition: opacity 0.2s ease;
}
.func:hover::after {
  opacity: 1;
}
.func:hover {
  border-color: var(--color-border-hover);
  box-shadow: var(--shadow-hover);
  transform: translateY(-3px);
}
.func-icon {
  display: flex;
  color: var(--color-accent);
  transition: transform 0.2s var(--ease-out-expo);
}
.func:hover .func-icon {
  transform: translateY(-2px);
}
.func-title {
  margin-top: 16px;
  font-size: 16px;
  font-weight: 600;
  color: var(--ink);
}
.func-desc {
  margin-top: 8px;
  font-size: 13px;
  line-height: 1.65;
  color: var(--body-c);
}
.func-link {
  margin-top: 16px;
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 13px;
  font-weight: 500;
  color: var(--color-accent);
}

/* ===== Statistics ===== */
.stats {
  margin-top: 72px;
  background: linear-gradient(180deg, var(--color-accent-soft) 0%, var(--color-surface) 100%);
  border-top: 1px solid var(--color-border);
  border-bottom: 1px solid var(--color-border);
}
.stats-inner {
  max-width: 1200px;
  margin: 0 auto;
  padding: 48px 24px;
  display: grid;
  grid-template-columns: repeat(4, 1fr);
}
.stat {
  padding: 0 24px;
  border-left: 1px solid var(--color-border);
}
.stat:first-child {
  padding-left: 0;
  border-left: none;
}
.stat-value {
  font-size: 40px;
  font-weight: 600;
  letter-spacing: -0.03em;
  line-height: 1;
  font-family: var(--font-display);
  color: var(--color-primary);
  background: var(--gradient-title);
  -webkit-background-clip: text;
  background-clip: text;
  -webkit-text-fill-color: transparent;
}
.stat-value .stat-num {
  font-variant-numeric: tabular-nums;
}
.stat-label {
  margin-top: 12px;
  font-size: 12px;
  letter-spacing: 0.02em;
  color: var(--color-text-2);
}

/* ===== Tech ===== */
.tech {
  padding: 72px 24px 88px;
}
.tech-inner {
  max-width: 1152px;
  margin: 0 auto;
  text-align: center;
}
.tech-title {
  font-size: 26px;
  font-weight: 600;
  letter-spacing: -0.02em;
  color: var(--ink);
}
.tech-tags {
  margin-top: 28px;
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 10px;
}
.tech-tag {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 9px 16px 9px 9px;
  border-radius: var(--radius-md);
  background: var(--color-surface);
  color: var(--body-c);
  border: 1px solid var(--color-border);
  transition:
    border-color 0.2s ease,
    box-shadow 0.2s ease,
    transform 0.2s var(--ease-out-expo);
}
.tech-tag:hover {
  border-color: var(--color-border-hover);
  box-shadow: var(--shadow-sm);
  transform: translateY(-2px);
}
.tech-tag-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  flex: none;
  width: 30px;
  height: 30px;
  border-radius: 8px;
  background: var(--color-accent-soft);
  color: var(--color-accent);
}
.tech-tag-name {
  font-size: 13px;
  font-weight: 500;
}

/* ===== Guide modal ===== */
.guide {
  font-size: 14px;
  color: var(--body-c);
  line-height: 1.7;
}
.guide-intro {
  margin: 0 0 16px;
}
.guide-heading {
  margin: 0 0 10px;
  font-size: 14px;
  font-weight: 600;
  color: var(--ink);
}
.guide-steps {
  margin: 0;
  padding-left: 20px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.guide-steps li {
  line-height: 1.6;
}
.guide-tip {
  margin: 20px 0 0;
  padding: 12px 14px;
  border-radius: var(--radius-sm);
  background: var(--color-surface-muted);
  border: 1px solid var(--color-border);
  color: var(--body-c);
  font-size: 13px;
}

@media (max-width: 768px) {
  /* 竖屏裁到画面中央，石面变亮，遮罩整体加重 */
  .hero-scrim {
    background: linear-gradient(180deg, rgba(4, 7, 13, 0.42) 0%,
      rgba(4, 7, 13, 0.66) 55%, rgba(4, 7, 13, 0.82) 100%);
  }
  .hero-copy {
    right: 0;
    width: auto;
    padding-left: 20px;
    padding-right: 20px;
    top: 52%;
  }
  .hero-title {
    font-size: clamp(1.9rem, 8vw, 2.6rem);
  }
  .workflow,
  .tech {
    padding-left: 20px;
    padding-right: 20px;
  }
  .functions {
    grid-template-columns: 1fr;
  }
  .timeline {
    flex-direction: column;
    gap: 12px;
    margin-top: 32px;
  }
  .timeline-rail {
    display: none;
  }
  .timeline-node {
    width: 100%;
    display: flex;
    align-items: center;
    gap: 12px;
    text-align: left;
  }
  .timeline-dot {
    width: 28px;
    height: 28px;
    margin: 0;
    font-size: 12px;
  }
  .timeline-label {
    margin-top: 0;
  }
  .stats-inner {
    grid-template-columns: repeat(2, 1fr);
    gap: 32px 0;
    padding: 40px 20px;
  }
  .stat {
    padding: 0 20px;
  }
  .stat:nth-child(odd) {
    padding-left: 0;
    border-left: none;
  }
}

/* Hero 下方内容每次滚入视口时的渐入更柔和 */
.home-body .reveal.is-revealed {
  animation-duration: 0.65s;
}

@media (prefers-reduced-motion: reduce) {
  .hero-media,
  .hero-bg,
  .hero-rise,
  .hero-sub,
  .hero-actions,
  .hero-scroll {
    animation: none !important;
  }
  .hero-video {
    display: none;
  }
}
</style>
