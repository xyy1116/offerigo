<template>
  <div class="app-root">
    <AppHeader />
    <div ref="contentRef" class="app-content" :class="{ 'is-home': route.path === '/' }">
      <router-view v-slot="{ Component }">
        <!-- 新旧页面同时存在：旧页固定在上层淡出，新页在下面直接呈现，避免 out-in 中间的空黑帧 -->
        <transition name="page" @before-leave="pinPage" @before-enter="resetScroll">
          <div class="page-shell" :key="route.path">
            <component :is="Component" />
          </div>
        </transition>
      </router-view>
    </div>
    <PositiveMomentLayer />
    <JobApplicationPrompt />
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useRoute } from 'vue-router'
import AppHeader from '@/components/AppHeader.vue'
import PositiveMomentLayer from '@/components/PositiveMomentLayer.vue'
import JobApplicationPrompt from '@/components/JobApplicationPrompt.vue'

const route = useRoute()
const contentRef = ref(null)

// 路由切换后从页面顶部开始，避免新页面沿用上一页的滚动位置
function resetScroll() {
  if (contentRef.value) contentRef.value.scrollTop = 0
}

// 离场页原地固定：记录当前视口位置后改为 fixed，滚动归零时它不会跟着跳
function pinPage(el) {
  // 路由进入的页面首屏元素直接呈现，不再做 30px 上浮入场（否则切页像“抖一下”）
  window.__pageEnteringUntil = Date.now() + 400
  const rect = el.getBoundingClientRect()
  el.style.position = 'fixed'
  el.style.top = `${rect.top}px`
  el.style.left = `${rect.left}px`
  el.style.width = `${rect.width}px`
  el.style.background = 'var(--color-page-bg)'
  el.style.zIndex = '2'
  el.style.pointerEvents = 'none'
}
</script>

<style>
* {
  margin: 0;
  padding: 0;
  box-sizing: border-box;
}
html,
body,
#app {
  height: 100%;
  background: var(--color-page-bg);
  font-family: 'Plus Jakarta Sans', 'Noto Sans SC', 'Helvetica Neue', 'Segoe UI', 'PingFang SC', 'Microsoft YaHei', sans-serif;
  -webkit-font-smoothing: antialiased;
  text-rendering: optimizeLegibility;
}
.app-root {
  height: 100vh;
  display: flex;
  flex-direction: column;
}
.app-content {
  flex: 1;
  min-height: 0;
  overflow: auto;
  background-color: var(--color-bg);
  background-image:
    radial-gradient(55% 45% at 12% 6%, var(--bg-glow-1), transparent 62%),
    radial-gradient(50% 42% at 88% 2%, var(--bg-glow-2), transparent 60%),
    radial-gradient(48% 42% at 68% 92%, var(--bg-glow-3), transparent 65%);
  background-repeat: no-repeat;
  background-size: 140% 140%;
  background-position: 0% 0%, 100% 0%, 50% 100%;
  animation: canvas-drift 26s ease-in-out infinite alternate;
  /* 头部始终 fixed：内容区自留 56px，首页用负 margin 顶到头部下面，
     这样切换路由时页面不会因为头部进出文档流而上下跳 56px */
  padding-top: 56px;
  /* 页面高度变化时预留滚动条槽位，避免切换瞬间内容左右抖 10px */
  scrollbar-gutter: stable;
}
/* 切到首页时内容区跟着 hero 一起变暗，淡入过程中不会先闪一帧浅色底 */
.app-content.is-home {
  background: #04070d;
  animation: none;
}
/* 背景光斑缓慢漂移，制造轻微的流动感 */
@keyframes canvas-drift {
  from { background-position: 0% 0%, 100% 0%, 50% 100%; }
  to { background-position: 32% 16%, 68% 12%, 42% 72%; }
}
@media (prefers-reduced-motion: reduce) {
  .app-content {
    animation: none;
  }
}
/* 让首页的 -56px 负 margin 留在 shell 内部，避免离场固定时子元素再跳 56px */
.page-shell {
  display: flow-root;
}
</style>