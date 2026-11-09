<template>
  <div ref="homeRef" class="home-body">
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
          <span class="hero-line"><span class="hero-rise">让每一次投递，</span></span>
          <span class="hero-line"><span class="hero-rise hero-title-accent">都更有把握。</span></span>
        </h1>
        <p class="hero-sub">
          每日推荐岗位，AI 匹配评估、定制文书、模拟面试与笔试训练，一站式求职准备。
        </p>
        <div class="hero-actions">
          <button class="hero-btn hero-btn--primary" @click="scrollToMatch">立即体验</button>
        </div>
      </div>
      <button type="button" class="hero-scroll" aria-label="下滑到智能岗位匹配" @click="scrollToMatch">
        <span class="scroll-mouse" aria-hidden="true"><span class="scroll-wheel"></span></span>
        <span class="scroll-chevrons" aria-hidden="true">
          <svg viewBox="0 0 24 24"><path d="M5 9l7 7 7-7" /></svg>
          <svg viewBox="0 0 24 24"><path d="M5 9l7 7 7-7" /></svg>
        </span>
      </button>
    </section>

    <!-- 第二屏：智能岗位匹配 -->
    <section ref="matchSection" class="landing-match">
      <JobMatch v-if="userStore.token" />
      <div v-else class="match-login">
        <p class="match-login-eyebrow">AI 智能岗位匹配</p>
        <h2 class="match-login-title">登录后开始你的岗位匹配</h2>
        <p class="match-login-sub">AI 会根据简历素材与收藏岗位做匹配评估，并生成定制简历、面试与笔试训练。</p>
        <button class="button" @click="openLogin">登录 / 注册</button>
      </div>
    </section>
  </div>
</template>

<script setup>
import { ref, onBeforeUnmount, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import JobMatch from '@/views/JobMatch.vue'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const matchSection = ref(null)
const homeRef = ref(null)
const heroParallaxRef = ref(null)

let autoTimer = null
let interacted = false
let scrolledToMatch = false
let scrollRaf = 0
let scrollEl = null

const prefersReduced = () =>
  window.matchMedia && window.matchMedia('(prefers-reduced-motion: reduce)').matches

function clearAutoTimer() {
  if (autoTimer) {
    clearTimeout(autoTimer)
    autoTimer = null
  }
}

function scrollToMatch() {
  scrolledToMatch = true
  clearAutoTimer()
  const section = matchSection.value
  const container = scrollEl || document.querySelector('.app-content')
  if (!section) return
  const behavior = prefersReduced() ? 'auto' : 'smooth'
  if (container) {
    const top = section.getBoundingClientRect().top + container.scrollTop - 56
    container.scrollTo({ top: Math.max(0, top), behavior })
  } else {
    section.scrollIntoView({ behavior, block: 'start' })
  }
}

function onUserAction() {
  interacted = true
  clearAutoTimer()
}

function bindActions() {
  const opts = { passive: true }
  window.addEventListener('wheel', onUserAction, opts)
  window.addEventListener('touchstart', onUserAction, opts)
  window.addEventListener('pointerdown', onUserAction, opts)
  window.addEventListener('keydown', onUserAction)
}
function unbindActions() {
  window.removeEventListener('wheel', onUserAction)
  window.removeEventListener('touchstart', onUserAction)
  window.removeEventListener('pointerdown', onUserAction)
  window.removeEventListener('keydown', onUserAction)
}

function openLogin() {
  router.replace({ path: '/', query: { ...route.query, login: '1', redirect: '/' } })
}

// 分层视差：首屏滚动进度写入 CSS 变量，背景、标题、滚动球各自以不同速度移动
function updateHeroProgress() {
  scrollRaf = 0
  if (!homeRef.value) return
  const top = scrollEl ? scrollEl.scrollTop : window.scrollY
  const h = window.innerHeight || 1
  const progress = Math.max(0, Math.min(1, top / h))
  homeRef.value.style.setProperty('--hp', progress.toFixed(3))
}
function onScroll() {
  if (!scrollRaf) scrollRaf = requestAnimationFrame(updateHeroProgress)
}

// Hero 背景随鼠标缓慢反向漂移：滞后的 lerp 让画面像沉在水里
let parallaxRaf = 0
let px = 0
let py = 0
let tx = 0
let ty = 0
let cleanupParallax = null

function initHeroParallax() {
  const el = heroParallaxRef.value
  const reduced = prefersReduced()
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

onMounted(() => {
  initHeroParallax()
  scrollEl = document.querySelector('.app-content')
  scrollEl?.addEventListener('scroll', onScroll, { passive: true })
  updateHeroProgress()
  bindActions()
  // 进入页面 2 秒内没有任何操作时，自动下滑到岗位匹配
  autoTimer = window.setTimeout(() => {
    if (!interacted && !scrolledToMatch) scrollToMatch()
  }, 2000)
})

onBeforeUnmount(() => {
  clearAutoTimer()
  unbindActions()
  scrollEl?.removeEventListener('scroll', onScroll)
  if (scrollRaf) cancelAnimationFrame(scrollRaf)
  if (cleanupParallax) cleanupParallax()
})
</script>

<style scoped>
.home-body {
  --ink: var(--color-text);
  --body-c: var(--color-text-2);
  --muted: var(--color-text-3);
  min-height: 100vh;
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
  transform: translate(-45.5%, -50%) scale(calc(1.1 + var(--hp, 0) * 0.06));
  filter: blur(calc(var(--hp, 0) * 2px));
  transition: filter 0.2s linear;
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
  opacity: calc(1 - var(--hp, 0) * 0.35);
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
  transform: translateY(calc(-50% + var(--hp, 0) * -36px));
  opacity: calc(1 - var(--hp, 0) * 1.15);
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
/* 底部下滑提示：玻璃质感鼠标 + 双层箭头，随滚动淡出下沉 */
.hero-scroll {
  position: absolute;
  bottom: clamp(16px, 2.8vh, 30px);
  left: 50%;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  padding: 0;
  border: 0;
  background: transparent;
  color: rgba(255, 255, 255, 0.75);
  cursor: pointer;
  transform: translate(-50%, calc(var(--hp, 0) * 18px));
  opacity: calc(1 - var(--hp, 0) * 2);
  transition: color 0.2s ease;
}
.hero-scroll:hover {
  color: #ffffff;
}
.scroll-mouse {
  position: relative;
  display: block;
  width: 24px;
  height: 36px;
  border: 1.5px solid currentColor;
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.06);
  -webkit-backdrop-filter: blur(4px);
  backdrop-filter: blur(4px);
  box-shadow: 0 0 0 6px rgba(255, 255, 255, 0.05), 0 8px 24px rgba(0, 0, 0, 0.25);
}
.scroll-wheel {
  position: absolute;
  left: 50%;
  top: 7px;
  width: 2px;
  height: 6px;
  margin-left: -1px;
  border-radius: 2px;
  background: currentColor;
  animation: scrollWheel 1.6s ease-in-out infinite;
}
.scroll-chevrons {
  display: flex;
  flex-direction: column;
  align-items: center;
  margin-top: -2px;
}
.scroll-chevrons svg {
  width: 14px;
  height: 14px;
  fill: none;
  stroke: currentColor;
  stroke-width: 1.6;
  stroke-linecap: round;
  stroke-linejoin: round;
  opacity: 0.3;
  animation: scrollChevron 1.6s ease-in-out infinite;
}
.scroll-chevrons svg:nth-child(2) {
  margin-top: -6px;
  opacity: 0.15;
  animation-delay: 0.15s;
}
@keyframes scrollWheel {
  0% { opacity: 0.2; transform: translateY(0); }
  30% { opacity: 1; }
  100% { opacity: 0; transform: translateY(10px); }
}
@keyframes scrollChevron {
  0%, 100% { opacity: 0.25; transform: translateY(0); }
  50% { opacity: 0.9; transform: translateY(3px); }
}
/* 第二屏：岗位匹配 */
.landing-match {
  position: relative;
  min-height: calc(100vh - 56px);
  background: var(--color-page-bg);
  /* 固定头部高 56px：滚动定位时给头部留出空间 */
  scroll-margin-top: 56px;
}
.match-login {
  min-height: calc(100vh - 56px);
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 10px;
  padding: 80px 24px;
  text-align: center;
}
.match-login-eyebrow {
  margin: 0;
  font-size: 12px;
  letter-spacing: 0.16em;
  color: var(--color-accent);
}
.match-login-title {
  margin: 0;
  font-size: 28px;
  letter-spacing: -0.02em;
  color: var(--color-text);
}
.match-login-sub {
  margin: 0 0 10px;
  max-width: 520px;
  font-size: 14px;
  line-height: 1.8;
  color: var(--color-text-2);
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
  max-height: 62vh;
  overflow-y: auto;
  padding-right: 6px;
  font-size: 14px;
  color: var(--body-c);
  line-height: 1.7;
}
.guide-steps strong {
  color: var(--color-text);
  font-weight: 600;
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
