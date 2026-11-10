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
        <template v-for="item in navs" :key="item.view || item.path">
          <div
            v-if="item.view"
            ref="navItems"
            class="nav-view"
            @mouseenter="hoveredView = item.view"
            @mouseleave="hoveredView = null"
          >
            <span
              class="nav-item"
              :class="{ active: isNavActive(item) }"
              tabindex="0"
              @click="selectView(item.view)"
              @keydown.enter="selectView(item.view)"
              @keydown.space.prevent="selectView(item.view)"
            >
              {{ item.name }}
              <span
                v-if="item.view === 'recommend' && recommendUnseen"
                class="nav-new"
                role="status"
                aria-label="最新推荐已更新"
              >
                <svg class="nav-new-spark" viewBox="0 0 24 24" aria-hidden="true">
                  <path d="M12 2.4 14.2 8.2 20 10.4 14.2 12.6 12 18.4 9.8 12.6 4 10.4 9.8 8.2Z" />
                </svg>
                <span class="nav-new-text">NEW!</span>
              </span>
              <span v-if="isNavActive(item) && filterCount(item.view)" class="nav-filter-count">{{ filterCount(item.view) }}</span>
            </span>
            <button
              type="button"
              class="nav-mobile-caret"
              :class="{ open: mobileView === item.view }"
              :aria-label="`展开${item.name}的岗位类型`"
              :aria-expanded="mobileView === item.view"
              @click.stop="toggleMobileView(item.view)"
            >
              <svg viewBox="0 0 12 12" aria-hidden="true"><path d="M3 4.5 6 7.5 9 4.5" /></svg>
            </button>
            <div
              v-show="hoveredView === item.view || mobileView === item.view"
              class="nav-submenu"
              @mouseenter="hoveredView = item.view"
            >
              <p class="nav-submenu-title">
                <span>岗位类型 <span class="nav-submenu-hint">可多选</span></span>
                <span v-if="isNavActive(item) && filterCount(item.view)" class="nav-submenu-count">{{ filterCount(item.view) }} 已选</span>
              </p>
              <div v-if="jobViewStore.optionsFor(item.view).length" class="nav-submenu-grid">
                <button
                  v-for="type in jobViewStore.optionsFor(item.view)"
                  :key="type"
                  type="button"
                  class="nav-submenu-item"
                  :class="{ selected: jobViewStore.filtersFor(item.view).includes(type) }"
                  @click="pickType(item.view, type)"
                >
                  <span class="nav-submenu-check" aria-hidden="true">✓</span>
                  <span>{{ type }}</span>
                </button>
              </div>
              <p v-else class="nav-submenu-empty">暂无岗位类型</p>
              <button
                v-if="jobViewStore.filtersFor(item.view).length"
                type="button"
                class="nav-submenu-clear"
                @click="clearTypes(item.view)"
              >清除筛选</button>
            </div>
          </div>

          <el-tooltip
            v-else
            :content="!userStore.token ? '登录后使用' : ''"
            :disabled="!!userStore.token"
            placement="bottom"
          >
            <span
              ref="navItems"
              class="nav-item"
              :class="{ active: isNavActive(item) }"
              tabindex="0"
              @click="goPath(item.path)"
              @keydown.enter="goPath(item.path)"
              @keydown.space.prevent="goPath(item.path)"
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
        </template>
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
import { useJobViewStore } from '@/stores/jobView'
import { getBankVersion } from '@/api/exam'
import { getRecommendVersion } from '@/api/jobRecommend'
import LoginDialog from '@/views/Login.vue'
import GuideDialog from '@/components/GuideDialog.vue'
import ThemeSwitcher from '@/components/ThemeSwitcher.vue'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const jobViewStore = useJobViewStore()

const menuOpen = ref(false)
const loginVisible = ref(false)
const guideRef = ref(null)
const hoveredView = ref(null)
const mobileView = ref(null)

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
    if (isRouteActive('/exam')) markBankSeen(version)
    // 旧版本可能把"已看过"存在 anon 键下：读到后顺手迁移到用户键
    if (seen === version) markBankSeen(version)
  } catch (e) {
    // 静默失败，不影响导航
  }
}

// —— 最新推荐 NEW!：按日期+版本记住已看 ——
const recommendUnseen = ref(false)
const recommendSeenVersion = ref('')
let recommendTimer = null
const recommendIdentity = () => userStore.userInfo?.id || userStore.userInfo?.username || ''
const recommendSeenKey = () => 'recommend_seen_' + recommendIdentity()

function markRecommendSeen() {
  const wasUnseen = recommendUnseen.value
  recommendUnseen.value = false
  if (!wasUnseen) return
  const version = recommendSeenVersion.value
  if (!version || !recommendIdentity()) return
  try {
    localStorage.setItem(recommendSeenKey(), version)
  } catch (e) {
    // 隐私模式下忽略
  }
}

async function checkRecommendUpdate() {
  if (!userStore.token || !recommendIdentity()) {
    recommendUnseen.value = false
    return
  }
  try {
    const res = await getRecommendVersion()
    const data = res.data?.data || {}
    const count = Number(data.count) || 0
    const version = String(data.version || '')
    const boot = String(data.runVersion || '')
    const key = version && count ? `${data.date || ''}:${version}:${count}:${boot}` : ''
    recommendSeenVersion.value = key
    if (!key) {
      recommendUnseen.value = false
      return
    }
    let seen = ''
    try {
      seen = localStorage.getItem(recommendSeenKey()) || ''
    } catch (e) {
      seen = ''
    }
    recommendUnseen.value = key !== seen
  } catch (e) {
    // 版本检查失败不打扰用户
  }
}

function onRecommendSeen() {
  markRecommendSeen()
}

// 登录成功后关闭弹窗，并同步拉取头像
watch(() => userStore.token, (token) => {
  if (!token) {
    bankUnseen.value = false
    recommendUnseen.value = false
    return
  }
  loginVisible.value = false
  userStore.fetchProfile()
    .catch(() => {})
    .finally(() => {
      checkBankUpdate()
      checkRecommendUpdate()
    })
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
  const idx = navs.findIndex((item) => isNavActive(item))
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
  hoveredView.value = null
  if (path.startsWith('/exam')) checkBankUpdate()
  nextTick(() => {
    updateGlider()
    syncOverHero()
  })
})

watch(menuOpen, (open) => {
  if (!open) mobileView.value = null
})
watch(() => jobViewStore.view, () => nextTick(updateGlider))
watch(() => jobViewStore.filtersByView, () => nextTick(updateGlider), { deep: true })

const navs = [
  { name: '最新推荐', view: 'recommend' },
  { name: '我的收藏', view: 'all' },
  { name: '查看所有', view: 'combined' },
  { name: 'AI 面试官', path: '/interview' },
  { name: 'AI 笔试助手', path: '/exam' }
]

const displayName = computed(() => userStore.userInfo?.username || '用户')

function isRouteActive(path) {
  return route.path === path || route.path.startsWith(path + '/')
}

function isNavActive(item) {
  if (item.view) return route.path === '/' && jobViewStore.view === item.view
  return isRouteActive(item.path)
}

function filterCount(view) {
  return jobViewStore.filtersFor(view).length
}

function selectView(view) {
  menuOpen.value = false
  if (view === 'recommend') markRecommendSeen()
  jobViewStore.setView(view)
  if (route.path === '/') {
    window.dispatchEvent(new CustomEvent('landing-scroll-match'))
  } else {
    router.push('/jobmatch')
  }
}

function pickType(view, type) {
  if (view === 'recommend') markRecommendSeen()
  jobViewStore.toggleType(view, type)
  jobViewStore.setView(view)
  if (route.path === '/') {
    window.dispatchEvent(new CustomEvent('landing-scroll-match'))
  } else {
    router.push('/jobmatch')
  }
}

function clearTypes(view) {
  jobViewStore.clearTypes(view)
}

function toggleMobileView(view) {
  mobileView.value = mobileView.value === view ? null : view
}

function goPath(path) {
  menuOpen.value = false
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
  checkRecommendUpdate()
  // 启动补跑可能在页面加载后才完成，稍后再确认几次
  setTimeout(checkBankUpdate, 30000)
  setTimeout(checkBankUpdate, 120000)
  setTimeout(checkRecommendUpdate, 30000)
  window.addEventListener('focus', checkBankUpdate)
  window.addEventListener('focus', checkRecommendUpdate)
  window.addEventListener('recommend-seen', onRecommendSeen)
  bankTimer = setInterval(checkBankUpdate, 5 * 60 * 1000)
  recommendTimer = setInterval(checkRecommendUpdate, 5 * 60 * 1000)
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
  window.removeEventListener('focus', checkRecommendUpdate)
  window.removeEventListener('recommend-seen', onRecommendSeen)
  clearInterval(bankTimer)
  clearInterval(recommendTimer)
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
  border-bottom: 1px solid var(--color-border);
  z-index: 1000;
  /* 首页切换：分割线渐变；底色直接切换，避免落点停留在过渡灰色 */
  transition: border-color 0.24s ease;
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
  background: linear-gradient(135deg, #0f3b44 0%, #17505c 45%, #2f9e9a 100%);
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
  background-image: linear-gradient(100deg, #17505c 10%, #2f8f8a 90%);
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
.nav-view {
  position: relative;
  z-index: 1;
  display: flex;
  align-items: center;
}
.nav-filter-count {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 15px;
  height: 15px;
  margin-left: 4px;
  padding: 0 4px;
  border-radius: 999px;
  background: var(--color-primary);
  color: #ffffff;
  font-size: 10px;
  font-weight: 600;
  line-height: 1;
  vertical-align: 1px;
}
.nav-mobile-caret {
  display: none;
  align-items: center;
  justify-content: center;
  width: 22px;
  height: 26px;
  padding: 0;
  border: 0;
  border-radius: 6px;
  background: transparent;
  color: var(--color-text-3);
  cursor: pointer;
}
.nav-mobile-caret svg {
  width: 12px;
  height: 12px;
  fill: none;
  stroke: currentColor;
  stroke-width: 1.6;
  stroke-linecap: round;
  stroke-linejoin: round;
  transition: transform 0.2s ease;
}
.nav-mobile-caret.open svg {
  transform: rotate(180deg);
}
.nav-submenu {
  position: absolute;
  top: calc(100% + 8px);
  left: 50%;
  z-index: 30;
  width: 220px;
  padding: 11px;
  border: 1px solid var(--color-border);
  border-radius: 12px;
  background: var(--color-surface);
  box-shadow: var(--shadow-lg);
  transform: translateX(-50%);
  animation: navSubmenuIn 0.18s var(--ease-out-expo);
}
/* 补上主项和面板之间的 hover 桥，移入面板时不会闪断 */
.nav-submenu::before {
  content: '';
  position: absolute;
  left: 0;
  right: 0;
  top: -10px;
  height: 10px;
}
/* 指向主项的小箭头 */
.nav-submenu::after {
  content: '';
  position: absolute;
  top: -5px;
  left: 50%;
  width: 8px;
  height: 8px;
  border-left: 1px solid var(--color-border);
  border-top: 1px solid var(--color-border);
  background: var(--color-surface);
  transform: translateX(-50%) rotate(45deg);
}
@keyframes navSubmenuIn {
  from {
    opacity: 0;
    transform: translate(-50%, -4px) scale(0.98);
  }
  to {
    opacity: 1;
    transform: translate(-50%, 0) scale(1);
  }
}
.nav-submenu-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin: 0 0 8px;
  padding: 0 2px 8px;
  border-bottom: 1px solid var(--color-border);
  font-size: 11px;
  font-weight: 500;
  letter-spacing: 0.04em;
  color: var(--color-text-3);
}
.nav-submenu-hint {
  margin-left: 4px;
  font-size: 10px;
  letter-spacing: 0.06em;
  opacity: 0.8;
}
.nav-submenu-count {
  flex: none;
  padding: 1px 6px;
  border-radius: 999px;
  background: var(--color-accent-soft);
  color: var(--color-primary);
  font-size: 10px;
  letter-spacing: 0;
}
.nav-submenu-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 4px;
}
.nav-submenu-item {
  display: flex;
  align-items: center;
  gap: 5px;
  min-width: 0;
  padding: 7px 8px;
  border: 0;
  border-radius: 7px;
  background: transparent;
  color: var(--color-text-2);
  font-family: inherit;
  font-size: 12px;
  text-align: left;
  cursor: pointer;
  transition: background-color 0.16s ease, color 0.16s ease;
}
.nav-submenu-item:hover {
  background: var(--color-surface-muted);
  color: var(--color-text);
}
.nav-submenu-item.selected {
  background: var(--color-accent-soft);
  color: var(--color-primary);
  font-weight: 500;
}
.nav-submenu-check {
  flex: none;
  width: 12px;
  color: var(--color-primary);
  font-size: 11px;
  font-weight: 700;
  opacity: 0;
}
.nav-submenu-item.selected .nav-submenu-check {
  opacity: 1;
}
.nav-submenu-item > span:last-child {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.nav-submenu-empty {
  margin: 2px 0 4px;
  font-size: 12px;
  color: var(--color-text-3);
}
.nav-submenu-clear {
  display: block;
  width: 100%;
  margin-top: 8px;
  padding: 8px 2px 0;
  border: 0;
  border-top: 1px solid var(--color-border);
  background: transparent;
  color: var(--color-primary);
  font-family: inherit;
  font-size: 11px;
  text-align: right;
  cursor: pointer;
}
.nav-submenu-clear:hover {
  color: var(--color-primary-mid);
}
.app-header.is-overlay .nav-submenu {
  background: rgba(9, 12, 18, 0.96);
  border-color: rgba(255, 255, 255, 0.14);
  box-shadow: 0 16px 40px rgba(0, 0, 0, 0.4);
}
.app-header.is-overlay .nav-submenu::after {
  border-color: rgba(255, 255, 255, 0.14);
  background: rgba(9, 12, 18, 0.96);
}
.app-header.is-overlay .nav-submenu-title {
  border-bottom-color: rgba(255, 255, 255, 0.12);
}
.app-header.is-overlay .nav-submenu-count {
  background: rgba(47, 158, 138, 0.28);
  color: #ffffff;
}
.app-header.is-overlay .nav-submenu-clear {
  border-top-color: rgba(255, 255, 255, 0.12);
}
.app-header.is-overlay .nav-submenu-title,
.app-header.is-overlay .nav-submenu-empty {
  color: rgba(255, 255, 255, 0.5);
}
.app-header.is-overlay .nav-submenu-item {
  color: rgba(255, 255, 255, 0.78);
}
.app-header.is-overlay .nav-submenu-item:hover {
  background: rgba(255, 255, 255, 0.1);
  color: #ffffff;
}
.app-header.is-overlay .nav-submenu-item.selected {
  background: rgba(47, 158, 138, 0.28);
  color: #ffffff;
}
.app-header.is-overlay .nav-submenu-clear {
  color: #9fe3d8;
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
/* 最新推荐的每日 NEW!：和 HOT 同一套小标样式，只换颜色 */
.nav-new {
  position: absolute;
  top: -9px;
  right: -13px;
  display: inline-flex;
  align-items: flex-end;
  gap: 1px;
  color: var(--color-lilac-deep);
  pointer-events: none;
  user-select: none;
  white-space: nowrap;
  filter: drop-shadow(0 1px 3px rgba(107, 95, 140, 0.22));
  animation: nav-hot-bob 2.4s ease-in-out infinite;
}
.nav-new-spark {
  width: 11px;
  height: 13px;
  fill: currentColor;
  transform-origin: 50% 50%;
  animation: nav-new-twinkle 1.8s ease-in-out infinite;
}
.nav-new-text {
  font-size: 10px;
  font-weight: 800;
  letter-spacing: 0.02em;
  line-height: 1;
  transform: translateY(-1px) skewX(-6deg);
}
.app-header.is-overlay .nav-new {
  color: var(--color-lilac-mid);
  filter: drop-shadow(0 1px 4px rgba(0, 0, 0, 0.3));
}
.app-header.is-overlay .nav-new-text {
  text-shadow: 0 1px 2px rgba(0, 0, 0, 0.25);
}
@keyframes nav-new-twinkle {
  0%, 100% { transform: scale(1) rotate(0deg); opacity: 1; }
  50% { transform: scale(0.82) rotate(18deg); opacity: 0.7; }
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
  .nav-new,
  .nav-hot-flame,
  .nav-new-spark { animation: none; }
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
.nav-item.active::after {
  content: '';
  position: absolute;
  left: 12px;
  right: 12px;
  bottom: 1px;
  height: 2px;
  border-radius: 2px;
  background: var(--color-mist);
  opacity: 0.9;
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
  /* 移动端菜单项是整行，HOT 跟着文字走，避免被推到屏幕边缘裁切 */
  .nav-hot {
    position: static;
    margin-left: 6px;
    vertical-align: middle;
  }
  .nav-hot-text {
    transform: none;
  }
  .nav-new {
    position: static;
    margin-left: 6px;
    vertical-align: 1px;
  }
  .nav-new-text {
    transform: none;
  }
  .nav-view {
    position: static;
    display: flex;
    flex-wrap: wrap;
    align-items: center;
  }
  .nav-view .nav-item {
    flex: 1;
  }
  .nav-mobile-caret {
    display: inline-flex;
    flex: none;
  }
  .nav-submenu {
    position: static;
    flex: 0 0 100%;
    width: 100%;
    margin: 2px 0 6px;
    padding: 8px;
    border-color: transparent;
    background: var(--color-surface-muted);
    box-shadow: none;
    transform: none;
    animation: none;
  }
  .nav-submenu::before {
    display: none;
  }
  .nav-submenu::after {
    display: none;
  }
  .is-overlay .nav-mobile-caret {
    color: rgba(255, 255, 255, 0.72);
  }
  .is-overlay .nav-submenu {
    background: rgba(255, 255, 255, 0.08);
    border-color: transparent;
  }
}
</style>
