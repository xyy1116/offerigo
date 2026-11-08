import axios from 'axios'
import { ElMessage } from 'element-plus'
import { getToken, removeToken } from '@/utils/token'
import router from '@/router'

// 统一 axios 实例
const service = axios.create({
  baseURL: '/api',
  timeout: 15000
})

// 轮询/轻量请求：绕过统一错误提示，失败不影响主流程
export function silentGet(path, params) {
  const token = getToken()
  return axios.get(`/api${path}`, {
    params,
    headers: token ? { Authorization: `Bearer ${token}` } : {},
    timeout: 5000
  })
}

// 登录失效：回首页并自动打开登录弹窗，保留当前页作为登录后的跳转目标
function goLogin() {
  const current = router.currentRoute.value
  const redirect = current && current.path !== '/' ? current.fullPath : ''
  router.push({
    path: '/',
    query: { login: '1', ...(redirect ? { redirect } : {}) }
  })
}

// 请求拦截器：附加 JWT 令牌
service.interceptors.request.use(
  (config) => {
    const token = getToken()
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  },
  (error) => Promise.reject(error)
)

// 响应拦截器：统一处理业务码与错误
service.interceptors.response.use(
  (response) => {
    // 文件下载（blob）直接返回原始响应，跳过业务码校验
    if (response.config.responseType === 'blob') {
      return response
    }
    const res = response.data
    if (res.code !== 200) {
      ElMessage.error(res.message || '请求失败')
      if (res.code === 401) {
        removeToken()
        goLogin()
      }
      return Promise.reject(new Error(res.message || 'Error'))
    }
    return res
  },
  (error) => {
    const status = error.response?.status
    const data = error.response?.data
    // 401：登录失效，引导重新登录
    if (status === 401) {
      ElMessage.error('登录已过期，请重新登录')
      removeToken()
      goLogin()
      return Promise.reject(error)
    }
    // 请求超时
    if (error.code === 'ECONNABORTED' || /timeout/i.test(error.message || '')) {
      ElMessage.error('请求超时，请检查网络后重试')
      return Promise.reject(error)
    }
    // 无响应：网络异常
    if (!error.response) {
      ElMessage.error('网络连接失败，请检查网络后重试')
      return Promise.reject(error)
    }
    // 服务端错误
    if (status >= 500) {
      ElMessage.error('服务器繁忙，请稍后重试')
      return Promise.reject(error)
    }
    // 其余业务错误：优先展示后端中文提示，避免英文与堆栈信息
    const friendly =
      data?.message ||
      (status === 403
        ? '暂无权限执行此操作'
        : status === 404
        ? '请求的资源不存在'
        : status === 400
        ? '请求参数有误，请检查后重试'
        : null) ||
      '操作失败，请稍后重试'
    ElMessage.error(friendly)
    return Promise.reject(error)
  }
)

export default service