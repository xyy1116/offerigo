import { createRouter, createWebHistory } from 'vue-router'
import { getToken } from '@/utils/token'

const routes = [
  {
    path: '/',
    name: 'Home',
    component: () => import('@/views/Home.vue'),
    meta: { title: '首页' }
  },
  {
    path: '/jobmatch',
    name: 'JobMatch',
    component: () => import('@/views/JobMatch.vue'),
    meta: { title: '智能岗位匹配', requiresAuth: true }
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
  if (to.meta.requiresAuth && !getToken()) {
    next({ path: '/', query: { login: '1', redirect: to.fullPath } })
  } else {
    next()
  }
})

export default router