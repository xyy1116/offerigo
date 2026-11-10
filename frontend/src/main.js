import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import { ElMessage } from 'element-plus'
import 'element-plus/dist/index.css'
import 'element-plus/theme-chalk/dark/css-vars.css'
import './styles/theme.css'
import './styles/font-noto-sans-sc.css'
import './styles/button.css'
import './styles/form.css'
import './styles/card.css'
import './styles/business-theme.css'
import './styles/tailwind.css'
import zhCn from 'element-plus/dist/locale/zh-cn.mjs'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'
import App from './App.vue'
import router from './router'
import reveal from './directives/reveal'
import { initTheme } from './utils/theme.ts'

const app = createApp(App)
app.directive('reveal', reveal)
initTheme()

// 全局注册 Element Plus 图标
for (const [key, component] of Object.entries(ElementPlusIconsVue)) {
  app.component(key, component)
}

app.use(createPinia())
app.use(router)
app.use(ElementPlus, { locale: zhCn })

// 兜底：单个组件抛错时提示并记录，避免整页白屏；未处理的 Promise 只记录不打断交互
app.config.errorHandler = (err, instance, info) => {
  console.error('[全局异常]', info, err)
  try {
    ElMessage.error('页面出现异常，请刷新重试')
  } catch (e) {
    // 提示本身失败时忽略，不能再次抛错
  }
}
window.addEventListener('unhandledrejection', (event) => {
  console.error('[未处理的 Promise 异常]', event.reason)
})

app.mount('#app')