<template>
  <div ref="homeRef" class="home-body" :class="{ 'is-instant-entry': instantEntry }">
    <!-- ======================= 1. Hero ======================= -->
    <section class="hero" aria-label="OfferiGo 智能求职助手">
      <div ref="heroParallaxRef" class="hero-parallax" role="img"
        aria-label="暮色湖面中央，一座浅色石材拱门立于水中，暖光从门内透出">
        <div class="hero-media">
          <div class="hero-shot">
            <img class="hero-bg" src="/stratum-vision-poster.jpg" alt="" fetchpriority="high" decoding="async" />
            <video
              ref="heroVideoRef"
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
      <div class="hero-veil" aria-hidden="true"></div>
      <div class="hero-copy">
        <h1 class="hero-title">
          <span class="hero-line"><span class="hero-rise">每日智能搜，岗位精准投。</span></span>
          <span class="hero-line"><span class="hero-rise">简历不怕乱，<span class="hero-title-accent">面试不怕难。</span></span></span>
        </h1>
        <p class="hero-sub">
          每日推荐岗位，AI 匹配评估、定制文书、模拟面试与笔试训练，一站式求职准备。
        </p>
        <div class="hero-actions">
          <button class="hero-btn hero-btn--primary" @click="scrollToMatch">立即体验</button>
        </div>
      </div>
      <button type="button" class="hero-scroll" aria-label="下滑到智能岗位匹配" @click="scrollToMatch">
        <span class="scroll-ring" aria-hidden="true">
          <svg viewBox="0 0 24 24"><path d="M12 6.5v10M7.8 12.3 12 16.5l4.2-4.2" /></svg>
        </span>
      </button>
    </section>

    <!-- 第二屏：智能岗位匹配 -->
    <section ref="matchSection" class="landing-match">
      <JobMatch v-if="userStore.token" :fab-visible="matchReached" />
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
import { consumeLandingMode } from '@/utils/landingEntry'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
// 首次打开保留 hero 入场和自动下滑；站内进入直接就位，不再自动播
const entryMode = consumeLandingMode() || 'initial'
const instantEntry = entryMode !== 'initial'
const autoEnabled = entryMode === 'initial'

const matchSection = ref(null)
const homeRef = ref(null)
const heroParallaxRef = ref(null)
const heroVideoRef = ref(null)
const matchReached = ref(false)

let autoTimer = null
let cinematicTimer = null
let scrollAnimRaf = 0
let interacted = false
let scrolledToMatch = false
let guideOpen = false
let lastScrollTop = 0
let videoPausedByScroll = false
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

function scheduleAuto(delay = 2000) {
  clearAutoTimer()
  autoTimer = window.setTimeout(() => {
    if (!interacted && !scrolledToMatch && !guideOpen) scrollToMatch()
  }, delay)
}

function cancelScrollAnim() {
  if (scrollAnimRaf) cancelAnimationFrame(scrollAnimRaf)
  scrollAnimRaf = 0
}

// 1 秒长镜头：ease-out-cubic，前段推进、后段缓慢落定
function animateScroll(container, target) {
  cancelScrollAnim()
  if (prefersReduced()) {
    container.scrollTop = target
    updateHeroProgress()
    return
  }
  const start = container.scrollTop
  const distance = target - start
  if (Math.abs(distance) < 1) return
  const duration = 1000
  const t0 = performance.now()
  const ease = (t) => 1 - Math.pow(1 - t, 3)
  const step = (now) => {
    const p = Math.min(1, (now - t0) / duration)
    container.scrollTop = start + distance * ease(p)
    updateHeroProgress()
    scrollAnimRaf = p < 1 ? requestAnimationFrame(step) : 0
  }
  scrollAnimRaf = requestAnimationFrame(step)
}

function endCinematic() {
  if (cinematicTimer) {
    clearTimeout(cinematicTimer)
    cinematicTimer = null
  }
  homeRef.value?.classList.remove('is-cinematic')
}

// 镜头推进：只加一个类，位移/缩放/淡出全部交给 GPU 合成
function startCinematic() {
  if (!homeRef.value || prefersReduced()) return
  homeRef.value.classList.add('is-cinematic')
  if (cinematicTimer) clearTimeout(cinematicTimer)
  cinematicTimer = window.setTimeout(endCinematic, 1100)
}

function scrollToMatch() {
  scrolledToMatch = true
  clearAutoTimer()
  const section = matchSection.value
  const container = scrollEl || document.querySelector('.app-content')
  if (!section) return
  heroVideoRef.value?.pause()
  videoPausedByScroll = true
  // 先算目标：is-cinematic 会给岗位区加上入场位移
  const target = container
    ? Math.max(0, section.getBoundingClientRect().top + container.scrollTop - 56)
    : null
  if (!container) {
    startCinematic()
    section.scrollIntoView({ behavior: prefersReduced() ? 'auto' : 'smooth', block: 'start' })
    return
  }
  if (Math.abs(container.scrollTop - target) < 2) return
  startCinematic()
  animateScroll(container, target)
}

// 只有真正会改变滚动的操作才算用户接管，点按钮、打字不取消自动下滑
const SCROLL_KEYS = ['ArrowDown', 'ArrowUp', 'PageDown', 'PageUp', 'Home', 'End', ' ']
function onUserAction(e) {
  if (e.type === 'keydown' && !SCROLL_KEYS.includes(e.key)) return
  cancelScrollAnim()
  endCinematic()
  interacted = true
  clearAutoTimer()
}

function onGuideToggle(e) {
  guideOpen = !!e.detail?.open
  if (guideOpen) {
    clearAutoTimer()
  } else if (autoEnabled && !interacted && !scrolledToMatch) {
    scheduleAuto()
  }
}

function bindActions() {
  const opts = { passive: true }
  window.addEventListener('wheel', onUserAction, opts)
  window.addEventListener('touchmove', onUserAction, opts)
  window.addEventListener('keydown', onUserAction)
  window.addEventListener('guide-toggle', onGuideToggle)
  window.addEventListener('landing-scroll-match', scrollToMatch)
}
function unbindActions() {
  window.removeEventListener('wheel', onUserAction)
  window.removeEventListener('touchmove', onUserAction)
  window.removeEventListener('keydown', onUserAction)
  window.removeEventListener('guide-toggle', onGuideToggle)
  window.removeEventListener('landing-scroll-match', scrollToMatch)
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
  // 离开首屏就停掉视频解码，回到顶部再继续，避免过渡时和动画抢 GPU
  const video = heroVideoRef.value
  const goingUp = top < lastScrollTop
  lastScrollTop = top
  // 岗位区上边缘越过导航下方才显示悬浮球；hero 一露头就隐藏，避免压在 hero 上
  if (!matchReached.value || goingUp) {
    matchReached.value = !!matchSection.value && matchSection.value.getBoundingClientRect().top <= 72
  }
  if (video) {
    if (progress > 0.45 && !video.paused) {
      video.pause()
      videoPausedByScroll = true
    } else if (progress < 0.3 && goingUp && video.paused && videoPausedByScroll) {
      video.play().catch(() => {})
      videoPausedByScroll = false
    }
  }
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
  bindActions()

  // 从其他页面进入岗位匹配：旧页淡出期间直接落位，用户看不到滚动过程
  if (entryMode === 'match' && scrollEl && matchSection.value) {
    const target = Math.max(0, matchSection.value.getBoundingClientRect().top + scrollEl.scrollTop - 56)
    scrollEl.scrollTop = target
    scrolledToMatch = true
    updateHeroProgress()
    return
  }

  if (scrollEl) scrollEl.scrollTop = 0
  updateHeroProgress()
  // 只有首次打开/刷新才自动下滑；站内回首页只展示 hero
  if (autoEnabled) scheduleAuto(2000)
})

onBeforeUnmount(() => {
  clearAutoTimer()
  unbindActions()
  scrollEl?.removeEventListener('scroll', onScroll)
  if (scrollRaf) cancelAnimationFrame(scrollRaf)
  cancelScrollAnim()
  endCinematic()
  if (cleanupParallax) cleanupParallax()
})
</script>

<style scoped>
.home-body {
  --ink: var(--color-text);
  --body-c: var(--color-text-2);
  --muted: var(--color-text-3);
  --ease-cinematic: cubic-bezier(0.33, 1, 0.68, 1);
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
  position: sticky;
  top: -56px;
  z-index: 0;
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
  /* 只缩放/移动，不做实时滤镜：滚动和过渡都交给合成层 */
  transform: translate(-45.5%, -50%) scale(calc(1.1 + var(--hp, 0) * 0.18));
  opacity: calc(1 - var(--hp, 0) * 0.65);
  backface-visibility: hidden;
}
.hero-bg,
.hero-video {
  position: absolute;
  inset: 0;
  display: block;
  width: 100%;
  height: 100%;
  object-fit: cover;
  object-position: 50% 50%;
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
/* 左侧压暗，保证白字落在山体与水面而不是亮部石面上 */
.hero-scrim {
  position: absolute;
  inset: 0;
  opacity: calc(1 - var(--hp, 0) * 0.25);
  background:
    linear-gradient(90deg, rgba(4, 7, 13, 0.9) 0%, rgba(4, 7, 13, 0.66) 30%,
      rgba(4, 7, 13, 0.18) 56%, rgba(4, 7, 13, 0) 76%),
    linear-gradient(0deg, rgba(4, 7, 13, 0.66) 0%, rgba(4, 7, 13, 0) 34%);
}
/* 下滑时页面底色自下而上揭开 hero，上滑时再退回去 */
.hero-veil {
  position: absolute;
  inset: 0;
  z-index: 1;
  pointer-events: none;
  background: var(--color-page-bg);
  -webkit-mask-image: linear-gradient(0deg, #000 0%, transparent 72%);
  mask-image: linear-gradient(0deg, #000 0%, transparent 72%);
  opacity: calc(var(--hp, 0) * 0.5);
}
.hero-copy {
  position: absolute;
  left: 0;
  top: 50%;
  z-index: 2;
  width: min(720px, 46vw);
  padding-left: clamp(24px, 8.2vw, 118px);
  transform: translateY(calc(-50% - var(--hp, 0) * 38px));
  color: #fff;
}
/* 自动下滑：镜头推近、内容分层让位；幕布由岗位区整块盖上 */
.is-cinematic .hero-shot {
  transform: translate(-45.5%, -51%) scale(1.36);
  transition: transform 1s var(--ease-cinematic);
  will-change: transform;
}
.is-cinematic .hero-copy {
  transform: translateY(calc(-50% - 52px)) scale(1.02);
  transition: transform 1s var(--ease-cinematic);
}
.is-cinematic .hero-veil {
  opacity: 0.6;
  transition: opacity 0.9s ease 0.05s;
}
.is-cinematic .hero-scrim {
  opacity: 0.7;
  transition: opacity 0.8s ease;
}
.hero-title {
  font-size: clamp(1.9rem, 2.8vw, 3.1rem);
  font-weight: 600;
  line-height: 1.14;
  letter-spacing: -0.02em;
  text-wrap: balance;
}
.hero-title-accent {
  background: linear-gradient(120deg, #ffffff 0%, #d7e8f1 42%, #6fa8c4 100%);
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
  transform: translateY(calc(var(--hp, 0) * -14px));
}
.hero-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  margin-top: clamp(22px, 3.4vh, 36px);
  transform: translateY(calc(var(--hp, 0) * -8px));
}
.is-cinematic .hero-sub {
  animation: none;
  transform: translateY(-18px);
  transition: transform 0.65s var(--ease-cinematic) 0.08s;
}
.is-cinematic .hero-actions {
  animation: none;
  transform: translateY(-10px);
  transition: transform 0.6s var(--ease-cinematic);
}
/* 从其他页面进入：hero 直接就位，只保留 1.2s 停留后下滑 */
.home-body.is-instant-entry .hero-media,
.home-body.is-instant-entry .hero-rise,
.home-body.is-instant-entry .hero-sub,
.home-body.is-instant-entry .hero-actions {
  animation: none !important;
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
/* 底部下滑提示：30px 细环 + 小箭头，克制、随滚动淡出 */
.hero-scroll {
  position: absolute;
  bottom: clamp(18px, 3vh, 34px);
  left: 50%;
  z-index: 2;
  display: grid;
  place-items: center;
  width: 44px;
  height: 44px;
  padding: 0;
  border: 0;
  background: transparent;
  color: rgba(255, 255, 255, 0.78);
  cursor: pointer;
  transform: translate(-50%, calc(var(--hp, 0) * 14px));
  opacity: calc(1 - var(--hp, 0) * 2.4);
  transition: color 0.2s ease, opacity 0.2s linear;
}
.scroll-ring {
  display: grid;
  place-items: center;
  width: 34px;
  height: 34px;
  border: 1px solid rgba(255, 255, 255, 0.46);
  border-radius: 50%;
  background: rgba(4, 7, 13, 0.24);
  transition: border-color 0.22s ease, background-color 0.22s ease,
    transform 0.28s var(--ease-out-expo);
}
.scroll-ring svg {
  width: 14px;
  height: 14px;
  fill: none;
  stroke: currentColor;
  stroke-width: 1.3;
  stroke-linecap: round;
  stroke-linejoin: round;
  animation: scrollCue 2.4s ease-in-out infinite;
}
.hero-scroll:hover {
  color: #ffffff;
}
.hero-scroll:hover .scroll-ring {
  border-color: rgba(255, 255, 255, 0.64);
  background: rgba(4, 7, 13, 0.3);
  transform: scale(1.08);
}
.hero-scroll:focus-visible {
  outline: none;
}
.hero-scroll:focus-visible .scroll-ring {
  outline: 2px solid rgba(255, 255, 255, 0.75);
  outline-offset: 3px;
}
.is-cinematic .hero-scroll {
  opacity: 0;
}
@keyframes scrollCue {
  0%, 100% { transform: translateY(-1px); opacity: 0.55; }
  50% { transform: translateY(2px); opacity: 1; }
}
@media (prefers-reduced-motion: reduce) {
  .scroll-ring svg { animation: none; }
}
/* 第二屏：岗位匹配 */
.landing-match {
  position: relative;
  z-index: 1;
  min-height: calc(100vh - 56px);
  background: var(--bg-canvas);
  background-attachment: fixed;
  box-shadow: 0 -20px 52px rgba(3, 7, 13, 0.16);
  /* 固定头部高 56px：滚动定位时给头部留出空间 */
  scroll-margin-top: 56px;
}
/* 自动下滑时整块从下方翻上来；手动上滑不受影响 */
.is-cinematic .landing-match {
  animation: matchRise 1s var(--ease-cinematic) 0.05s both;
}
@keyframes matchRise {
  from {
    transform: translateY(72px) scale(0.985);
  }
  to {
    transform: none;
  }
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
  transform: translateY(calc(var(--hp, 0) * -28px));
}
.hero-line:nth-child(2) {
  transform: translateY(calc(var(--hp, 0) * -14px));
}
.is-cinematic .hero-line {
  transition: transform 0.6s var(--ease-cinematic);
}
.is-cinematic .hero-line:nth-child(1) {
  transform: translateY(-28px);
  transition-delay: 0.03s;
}
.is-cinematic .hero-line:nth-child(2) {
  transform: translateY(-14px);
  transition-delay: 0.08s;
}
.hero-rise {
  display: block;
}
.hero-line:nth-child(1) .hero-rise {
  animation: heroMaskUp 0.95s cubic-bezier(0.16, 1, 0.3, 1) 0.18s both;
}
.hero-line:nth-child(2) .hero-rise {
  animation: heroMaskUp 0.95s cubic-bezier(0.16, 1, 0.3, 1) 0.32s both;
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
  animation: heroUp 0.85s cubic-bezier(0.16, 1, 0.3, 1) backwards;
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
    font-size: clamp(1.5rem, 7vw, 2.3rem);
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
  .hero-shot {
    transform: translate(-45.5%, -50%) scale(1.1);
    opacity: 1;
  }
  .hero-copy {
    transform: translateY(-50%);
    opacity: 1;
  }
  .hero-line,
  .hero-sub,
  .hero-actions {
    transform: none;
    opacity: 1;
  }
  .hero-veil {
    opacity: 0;
  }
  .is-cinematic .landing-match {
    animation: none;
  }
}
</style>
