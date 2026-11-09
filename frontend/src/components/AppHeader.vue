<template>
  <header class="app-header" :class="{ 'is-overlay': overlay }">
    <div class="header-inner">
      <div class="header-left">
        <button class="brand" @click="goHome">
          <span class="brand-mark" aria-hidden="true">
            <svg viewBox="0 0 32 32">
              <path
                d="M5 14.8 27 6l-6.1 20.9-7-7.6L5 14.8Z"
                fill="none"
                stroke="currentColor"
                stroke-width="2.3"
                stroke-linejoin="round"
              />
              <path d="M13.9 19.3 27 6" fill="none" stroke="currentColor" stroke-width="2.3" stroke-linejoin="round" />
            </svg>
          </span>
          <span class="brand-name">Offeri<span class="brand-go">Go</span></span>
          <span class="brand-sub">智能求职助手</span>
        </button>
        <button type="button" class="guide-entry" @click="guideRef?.open()">使用指南</button>
      </div>

      <nav class="nav" :class="{ open: menuOpen }">
        <span class="nav-glider" :style="gliderStyle" aria-hidden="true"></span>
        <el-tooltip
          v-for="item in navs"
          :key="item.path"
          :content="!userStore.token && item.path !== '/' ? '登录后使用' : ''"
          :disabled="!!userStore.token || item.path === '/'"
          placement="bottom"
        >
          <span
            ref="navItems"
            class="nav-item"
            :class="{ active: isActive(item.path) }"
            tabindex="0"
            @click="go(item.path)"
            @keydown.enter="go(item.path)"
            @keydown.space.prevent="go(item.path)"
          >
            {{ item.name }}
            <span
              v-if="item.path === '/exam' && bankUnseen"
              class="nav-hot"
              role="status"
              aria-label="题库已更新"
              title="题库已更新"
            >
              <svg class="nav-hot-flame" viewBox="0 0 24 24" aria-hidden="true">
                <path d="M12.4 2.2c.5 3-1.3 4.5-2.7 6.1-1.5 1.7-2.8 3.4-2.8 5.8a5.4 5.4 0 0 0 10.8 0c0-2.1-1-3.7-2-5-.9-1.1-1.9-2.2-2.3-3.9-.6 1.4-1.5 2.4-2.3 3.2.7-2.1.6-4.2 1.3-6.2Z" />
                <path class="core" d="M12.1 11.4c.9 1.1 1.9 2 1.9 3.4a1.9 1.9 0 0 1-3.8 0c0-1.2.9-2.1 1.9-3.4Z" />
              </svg>
              <span class="nav-hot-text">HOT!</span>
            </span>
          </span>
        </el-tooltip>
      </nav>

      <div class="right">
        <el-dropdown v-if="userStore.token" popper-class="user-dropdown" @command="handleCommand">
          <span class="user-info">
            <span class="user-dot">
              <img :src="userStore.userInfo?.avatar || '/default-avatar.svg'" alt="头像" />
            </span>
            <span class="user-name">{{ displayName }}</span>
            <el-icon :size="12"><ArrowDown /></el-icon>
          </span>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="profile">个人中心</el-dropdown-item>
              <el-dropdown-item divided command="logout">退出登录</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
        <button v-else class="button button--small header-login" @click="openLogin">登录</button>
        <ThemeSwitcher :overlay="overlay" />
        <div class="hamburger" @click="menuOpen = !menuOpen">
          <el-icon :size="20"><component :is="menuOpen ? Close : Menu" /></el-icon>
        </div>
      </div>
    </div>
  </header>

  <LoginDialog v-if="loginVisible" v-model="loginVisible" />
  <GuideDialog ref="guideRef" />
</template>

<script setup>
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessageBox } from 'element-plus'
import { Close, Menu } from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'
import { getBankVersion } from '@/api/exam'
import LoginDialog from '@/views/Login.vue'
import GuideDialog from '@/components/GuideDialog.vue'
import ThemeSwitcher from '@/components/ThemeSwitcher.vue'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const menuOpen = ref(false)
const loginVisible = ref(false)
const guideRef = ref(null)

// 首页：头部叠在 hero 上透明显示；滚过 hero 后切回实心，避免浅色内容上白字看不清
const isHome = ref(route.path === '/')
const overHero = ref(true)
const overlay = computed(() => isHome.value && overHero.value)
let contentEl = null

function syncOverHero() {
  // hero 占满首屏：滑到 hero 底部（header 高 56）时再切换，之后浅色内容不会垫在白字下面
  const limit = Math.max(80, window.innerHeight - 60)
  overHero.value = !contentEl || contentEl.scrollTop < limit
}

function openLogin() {
  loginVisible.value = true
}

// —— 题库更新提示：有未看过的每日更新时，AI 笔试助手右上角显示火苗 + HOT! ——
const bankUnseen = ref(false)
const bankVersion = ref('')
const bankIdentity = () => userStore.userInfo?.id || userStore.userInfo?.username || ''
const bankSeenKey = () => 'exam_bank_seen_' + bankIdentity()
let bankTimer = null

function markBankSeen(version) {
  // 用户信息未就绪时不要写入，避免和加载后用的键不一致导致反复提示
  if (!version || !bankIdentity()) return
  bankUnseen.value = false
  try {
    localStorage.setItem(bankSeenKey(), version)
  } catch (e) {
    // 隐私模式下忽略
  }
}

async function checkBankUpdate() {
  if (!userStore.token) {
    bankUnseen.value = false
    return
  }
  if (!bankIdentity()) {
    // 刷新后用户信息还在加载：此时无法确定"已看过"的存储键，直接跳过
    return
  }
  try {
    const res = await getBankVersion()
    const version = res.data?.data?.version || ''
    bankVersion.value = version
    if (!version) {
      bankUnseen.value = false
      return
    }
    let seen = ''
    try {
      seen = localStorage.getItem(bankSeenKey())
        || localStorage.getItem('exam_bank_seen_anon')
        || ''
    } catch (e) {
      seen = ''
    }
    bankUnseen.value = version !== seen
    if (isActive('/exam')) markBankSeen(version)
    // 旧版本可能把"已看过"存在 anon 键下：读到后顺手迁移到用户键
    if (seen === version) markBankSeen(version)
  } catch (e) {
    // 静默失败，不影响导航
  }
}

// 登录成功后关闭弹窗，并同步拉取头像
watch(() => userStore.token, (token) => {
  if (!token) {
    bankUnseen.value = false
    return
  }
  loginVisible.value = false
  userStore.fetchProfile()
    .catch(() => {})
    .finally(checkBankUpdate)
})

// 未登录访问受保护页 / 登录过期时，路由带 login=1 回首页并自动打开弹窗
// login 只作为一次性触发参数，打开后立即从 URL 移除，保留 redirect 供登录后跳转
watch(() => route.query.login, (value) => {
  if (!value) return
  if (userStore.token) userStore.logout()
  loginVisible.value = true
  const query = { ...route.query }
  delete query.login
  router.replace({ path: route.path, query })
}, { immediate: true })

/** 导航滑动指示器：按当前项的实际宽度/位置定位 */
const navItems = ref([])
const gliderStyle = ref({ left: '0px', width: '0px', opacity: 0 })

function updateGlider() {
  const idx = navs.findIndex((item) => isActive(item.path))
  const el = navItems.value[idx]
  if (!el) {
    gliderStyle.value = { left: '0px', width: '0px', opacity: 0 }
    return
  }
  gliderStyle.value = {
    left: `${el.offsetLeft}px`,
    width: `${el.offsetWidth}px`,
    opacity: 1
  }
}

watch(() => route.path, (path) => {
  // 立即切换状态，颜色由 CSS 渐变完成，不再等旧页面淡出后硬切
  isHome.value = path === '/'
  if (path.startsWith('/exam')) checkBankUpdate()
  nextTick(() => {
    updateGlider()
    syncOverHero()
  })
})

const navs = [
  { name: '智能岗位匹配', path: '/jobmatch' },
  { name: 'AI 面试官', path: '/interview' },
  { name: 'AI 笔试助手', path: '/exam' }
]

const displayName = computed(() => userStore.userInfo?.username || '用户')

function isActive(path) {
  if (path === '/') return route.path === '/'
  return route.path === path || route.path.startsWith(path + '/')
}

function go(path) {
  menuOpen.value = false
  // 已经在单页落地页上时，"智能岗位匹配"直接滚到第二屏，而不是重新进入路由
  if (path === '/jobmatch' && route.path === '/') {
    document.querySelector('.landing-match')?.scrollIntoView({ behavior: 'smooth', block: 'start' })
    return
  }
  router.push(path)
}

function goHome() {
  if (route.path === '/') {
    document.querySelector('.app-content')?.scrollTo({ top: 0, behavior: 'smooth' })
    return
  }
  router.push('/')
}

async function handleCommand(command) {
  if (command === 'profile') {
    router.push('/profile')
  } else if (command === 'logout') {
    await ElMessageBox.confirm('确定退出登录吗？', '提示', { type: 'warning' })
    userStore.logout()
    router.push('/')
  }
}

onMounted(async () => {
  // 刷新后 store 里没有用户信息，补拉一次以显示注册用户名与头像
  if (userStore.token && !userStore.userInfo) {
    await userStore.fetchProfile().catch(() => {})
  }
  checkBankUpdate()
  // 启动补跑可能在页面加载后才完成，稍后再确认几次
  setTimeout(checkBankUpdate, 30000)
  setTimeout(checkBankUpdate, 120000)
  window.addEventListener('focus', checkBankUpdate)
  bankTimer = setInterval(checkBankUpdate, 5 * 60 * 1000)
  await nextTick()
  updateGlider()
  contentEl = document.querySelector('.app-content')
  contentEl?.addEventListener('scroll', syncOverHero, { passive: true })
  syncOverHero()
  window.addEventListener('resize', updateGlider)
  window.addEventListener('resize', syncOverHero)
  if (document.fonts && document.fonts.ready) {
    document.fonts.ready.then(updateGlider).catch(() => {})
  }
})

onUnmounted(() => {
  window.removeEventListener('resize', updateGlider)
  window.removeEventListener('resize', syncOverHero)
  window.removeEventListener('focus', checkBankUpdate)
  clearInterval(bankTimer)
  contentEl?.removeEventListener('scroll', syncOverHero)
})
</script>

<style scoped>
/* 头部始终 fixed：路由切换时不再进出文档流，旧页面不会跳 56px */
.app-header {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  height: 56px;
  background: var(--color-nav);
  background: color-mix(in srgb, var(--color-nav) 84%, transparent);
  -webkit-backdrop-filter: blur(14px) saturate(1.08);
  backdrop-filter: blur(14px) saturate(1.08);
  border-bottom: 1px solid var(--color-border);
  z-index: 1000;
  /* 首页切换：底色、分割线一起渐变，不再等旧页淡出后硬切 */
  transition:
    background-color 0.24s ease,
    border-color 0.24s ease;
}
/* 首页 hero 上方：透明叠加，不占文档流 */
.app-header.is-overlay {
  background: transparent;
  border-bottom-color: transparent;
}
/* 顶部极淡的暗色渐变遮罩，保证白字在亮一点的画面上也能看清 */
.app-header::before {
  content: '';
  position: absolute;
  inset: 0;
  background: linear-gradient(180deg, rgba(3, 3, 7, 0.62), rgba(3, 3, 7, 0));
  opacity: 0;
  pointer-events: none;
  transition: opacity 0.24s ease;
}
.app-header.is-overlay::before {
  opacity: 1;
}
.is-overlay .brand-name,
.is-overlay .user-name {
  color: #ffffff;
  text-shadow: 0 1px 3px rgba(0, 0, 0, 0.55);
}
.is-overlay .brand-go {
  background-image: linear-gradient(100deg, #ffffff 15%, #9fe3d8 90%);
}
/* 深色底上 hover 同样整体换成一个颜色 */
.is-overlay .brand:hover .brand-name,
.is-overlay .brand:hover .brand-go {
  color: #9fe3d8;
}
.is-overlay .brand-sub {
  color: rgba(255, 255, 255, 0.66);
  border-left-color: rgba(255, 255, 255, 0.28);
}
.is-overlay .nav-item {
  color: rgba(255, 255, 255, 0.74);
  text-shadow: 0 1px 3px rgba(0, 0, 0, 0.55);
}
.is-overlay .nav-item:hover,
.is-overlay .nav-item.active {
  color: #ffffff;
}
.is-overlay .nav-glider {
  background: rgba(255, 255, 255, 0.14);
}
.is-overlay .user-info {
  color: #ffffff;
  text-shadow: 0 1px 3px rgba(0, 0, 0, 0.55);
}
.is-overlay .user-info:hover {
  background: rgba(255, 255, 255, 0.12);
  color: #ffffff;
}
.is-overlay .user-dot {
  background: rgba(255, 255, 255, 0.16);
  color: #ffffff;
}
.is-overlay .hamburger {
  color: #ffffff;
}
/* 深色底上的登录按钮改浅色实心，对比度更高 */
.is-overlay .header-login {
  background: #f5f5f2;
  border-color: #f5f5f2;
  color: #101820;
}
.is-overlay .header-login:hover {
  background: #ffffff;
  border-color: #ffffff;
  color: #101820;
}
/* 深色底上焦点圈用白色，保证可见 */
.is-overlay .brand:focus-visible,
.is-overlay .nav-item:focus-visible {
  outline-color: #ffffff;
}
.header-inner {
  position: relative;
  z-index: 1;
  height: 100%;
  max-width: 1200px;
  margin: 0 auto;
  padding: 0 24px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24px;
}
.header-left {
  display: flex;
  align-items: center;
  gap: 14px;
  min-width: 0;
}
/* 左上角 logo 右侧的使用指南入口 */
.guide-entry {
  flex: none;
  padding: 4px 8px;
  border: 1px solid transparent;
  border-radius: var(--radius-sm);
  background: transparent;
  color: var(--color-text-2);
  font-family: inherit;
  font-size: 12px;
  cursor: pointer;
  transition: color 0.18s ease, background-color 0.18s ease;
}
.guide-entry:hover {
  color: var(--color-primary);
  background: var(--color-surface-muted);
}
.app-header.is-overlay .guide-entry {
  color: rgba(255, 255, 255, 0.86);
}
.app-header.is-overlay .guide-entry:hover {
  color: #ffffff;
  background: rgba(255, 255, 255, 0.12);
}
.brand {
  display: flex;
  align-items: center;
  gap: 9px;
  padding: 0;
  border: none;
  background: none;
  color: var(--color-text);
  font-family: inherit;
  cursor: pointer;
  white-space: nowrap;
}
.brand:focus-visible {
  outline: 2px solid var(--color-primary);
  outline-offset: 4px;
  border-radius: var(--radius-sm);
}
/* 图标：圆环 + 右上箭头，“O 出发” */
.brand-mark {
  flex: none;
  display: grid;
  place-items: center;
  width: 28px;
  height: 28px;
  border-radius: 9px;
  color: #ffffff;
  background: linear-gradient(135deg, #143f47 0%, #1f5f6b 45%, #2f9e9a 100%);
  box-shadow: 0 2px 8px rgba(31, 95, 107, 0.24);
}
.brand-mark svg {
  display: block;
  width: 28px;
  height: 28px;
}
.brand-name {
  font-family: var(--font-brand);
  font-size: 16px;
  font-weight: 700;
  line-height: 1;
  letter-spacing: -0.02em;
  transition: color 0.24s ease;
}
.brand-go {
  background-image: linear-gradient(100deg, #1f5f6b 10%, #2f8f8a 90%);
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
  transition: color 0.24s ease;
}
/* hover 时两段统一成同一实色，避免 Offeri 变色、Go 仍是渐变 */
.brand:hover .brand-name {
  color: var(--color-primary);
}
.brand:hover .brand-go {
  color: var(--color-primary);
}
.brand-sub {
  padding-left: 8px;
  border-left: 1px solid var(--color-border);
  font-size: 12px;
  color: var(--color-text-3);
  transition: color 0.24s ease, border-color 0.24s ease;
}
.nav {
  position: relative;
  display: flex;
  align-items: center;
  gap: 2px;
}
.nav-glider {
  position: absolute;
  top: 50%;
  left: 0;
  height: 30px;
  border-radius: var(--radius-sm);
  background: var(--color-nav-glider);
  transform: translateY(-50%);
  transition:
    left 0.32s var(--ease-out-expo),
    width 0.32s var(--ease-out-expo),
    opacity 0.2s ease,
    background-color 0.24s ease;
  z-index: 0;
  pointer-events: none;
}
.nav-item {
  position: relative;
  z-index: 1;
  padding: 6px 12px;
  border-radius: var(--radius-sm);
  font-size: 13px;
  font-weight: 400;
  color: var(--color-text-2);
  cursor: pointer;
  white-space: nowrap;
  transition: color 0.24s ease, background-color 0.18s ease;
}
/* 题库有未看过的更新：AI 笔试助手右上角 火苗 + HOT! */
.nav-hot {
  position: absolute;
  top: -9px;
  right: -13px;
  display: inline-flex;
  align-items: flex-end;
  gap: 1px;
  color: var(--color-danger);
  pointer-events: none;
  user-select: none;
  white-space: nowrap;
  filter: drop-shadow(0 1px 3px rgba(180, 35, 24, 0.28));
  animation: nav-hot-bob 2.2s ease-in-out infinite;
}
.nav-hot-flame {
  width: 13px;
  height: 15px;
  fill: currentColor;
  transform-origin: 50% 92%;
  animation: nav-hot-flicker 1.5s ease-in-out infinite;
}
.nav-hot-flame .core {
  fill: #ffcf66;
}
.nav-hot-text {
  font-size: 10px;
  font-weight: 800;
  letter-spacing: 0.02em;
  line-height: 1;
  transform: translateY(-1px) skewX(-6deg);
}
.app-header.is-overlay .nav-hot {
  color: #ff7a6e;
  filter: drop-shadow(0 1px 4px rgba(0, 0, 0, 0.3));
}
.app-header.is-overlay .nav-hot-text {
  text-shadow: 0 1px 2px rgba(0, 0, 0, 0.25);
}
@keyframes nav-hot-bob {
  0%, 100% { transform: translateY(0); }
  50% { transform: translateY(-2px); }
}
@keyframes nav-hot-flicker {
  0%, 100% { transform: scale(1, 1) rotate(-2deg); }
  30% { transform: scale(1.05, 0.95) rotate(2deg); }
  60% { transform: scale(0.97, 1.05) rotate(-1deg); }
  80% { transform: scale(1.03, 0.98) rotate(1deg); }
}
@media (prefers-reduced-motion: reduce) {
  .nav-hot,
  .nav-hot-flame { animation: none; }
}
.nav-item:hover {
  color: var(--color-nav-active);
  background: transparent;
}
.nav-item.active {
  color: var(--color-nav-active);
  background: transparent;
  font-weight: 500;
}
.nav-item:focus-visible {
  outline: 2px solid var(--color-primary);
  outline-offset: 1px;
}
.right {
  display: flex;
  align-items: center;
  gap: 12px;
}
.user-info {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 4px 6px;
  border-radius: var(--radius-sm);
  color: var(--color-text);
  font-size: 13px;
  cursor: pointer;
  transition: var(--transition);
}
.user-info:hover {
  background: var(--color-surface-muted);
  color: var(--color-nav-active);
}
.user-dot {
  width: 24px;
  height: 24px;
  border-radius: 50%;
  display: grid;
  place-items: center;
  overflow: hidden;
  background: var(--color-accent-soft);
  color: var(--color-text-2);
  font-size: 12px;
  font-weight: 600;
  line-height: 1;
  transition: background-color 0.24s ease, color 0.24s ease;
}
.user-dot img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}
.hamburger {
  display: none;
  cursor: pointer;
  color: var(--color-text);
  transition: color 0.24s ease;
}

@media (max-width: 720px) {
  .header-inner {
    padding: 0 20px;
  }
  .brand-sub {
    display: none;
  }
  .user-name {
    display: none;
  }
  .right {
    gap: 8px;
  }
  .hamburger {
    display: flex;
  }
  .nav {
    position: absolute;
    top: 56px;
    left: 0;
    right: 0;
    flex-direction: column;
    align-items: stretch;
    gap: 2px;
    background: var(--color-nav);
    border-bottom: 1px solid var(--color-border);
    padding: 8px 12px 12px;
    display: none;
  }
  .nav.open {
    display: flex;
  }
  /* 透明头部下展开的移动菜单也要是深色底，否则白字看不清 */
  .is-overlay .nav {
    background: rgba(8, 8, 12, 0.94);
    border-bottom-color: rgba(255, 255, 255, 0.1);
    backdrop-filter: blur(12px);
  }
  .is-overlay .nav-item:hover {
    background: rgba(255, 255, 255, 0.1);
    color: #ffffff;
  }
  .is-overlay .nav-item.active {
    background: rgba(255, 255, 255, 0.14);
    color: #ffffff;
  }
  .nav-glider {
    display: none;
  }
  .nav-item {
    padding: 10px 12px;
  }
  .nav-item:hover {
    background: var(--color-surface-muted);
  }
  .nav-item.active {
    background: var(--color-nav-glider);
  }
}
</style>
