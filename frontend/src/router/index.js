import { createRouter, createWebHistory } from 'vue-router'
import { getToken } from '@/utils/token'
import { setLandingMode } from '@/utils/landingEntry'

const routes = [
  {
    path: '/',
    name: 'Landing',
    component: () => import('@/views/Home.vue'),
    meta: { title: 'OfferiGo 智能求职助手' }
  },
  {
    path: '/jobmatch',
    redirect: '/'
  },
  {
    path: '/interview',
    name: 'Interview',
    component: () => import('@/views/Interview.vue'),
    meta: { title: 'AI 面试官', requiresAuth: true }
  },
  {
    path: '/exam',
    name: 'Exam',
    component: () => import('@/views/Exam.vue'),
    meta: { title: 'AI 笔试助手', requiresAuth: true }
  },
  {
    path: '/profile',
    name: 'Profile',
    component: () => import('@/views/Profile.vue'),
    meta: { title: '个人中心', requiresAuth: true }
  },
  {
    path: '/exam/paper/:id',
    name: 'ExamPaper',
    component: () => import('@/views/ExamPaper.vue'),
    meta: { title: '笔试作答', requiresAuth: true }
  },
  {
    path: '/exam/report/:id',
    name: 'ExamReport',
    component: () => import('@/views/ExamReport.vue'),
    meta: { title: '笔试报告', requiresAuth: true }
  },
  {
    path: '/:pathMatch(.*)*',
    redirect: '/'
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

// 全局前置守卫：未登录访问受保护页时回首页并打开登录弹窗
router.beforeEach((to, from, next) => {
  document.title = to.meta.title ? `${to.meta.title} - OfferiGo` : 'OfferiGo 智能求职助手'
  const isInitial = !from.matched.length
  if (!isInitial && from.path !== '/' && from.path !== '/jobmatch') {
    const target = to.redirectedFrom?.path || to.path
    if (target === '/jobmatch') setLandingMode('match')
    else if (target === '/') setLandingMode('home')
  }
  if (to.meta.requiresAuth && !getToken()) {
    return next({ path: '/', query: { login: '1', redirect: to.fullPath } })
  }
  next()
})

export default router