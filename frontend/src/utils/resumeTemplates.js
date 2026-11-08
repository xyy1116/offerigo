import { getResumeTemplates } from '@/api/jobmatch'

const CACHE_KEY = 'offergo_resume_templates_v2'
let memoryCache = null
let pending = null

/** 已缓存的模板列表：模板是固定内置项，先读缓存可实现打开即显示。 */
export function readCachedResumeTemplates() {
  if (memoryCache) return memoryCache
  try {
    const raw = localStorage.getItem(CACHE_KEY)
    const list = raw ? JSON.parse(raw) : null
    if (Array.isArray(list) && list.length) {
      memoryCache = list
      return list
    }
  } catch (e) {
    // 缓存损坏时忽略，回退到接口
  }
  return null
}

function writeCache(list) {
  if (!Array.isArray(list) || !list.length) return
  memoryCache = list
  try {
    localStorage.setItem(CACHE_KEY, JSON.stringify(list))
  } catch (e) {
    // 隐私模式/超限时忽略
  }
}

/** 拉取模板并写入缓存；并发调用复用同一请求。 */
export function fetchResumeTemplates() {
  if (pending) return pending
  pending = getResumeTemplates()
    .then((res) => {
      const list = Array.isArray(res.data) ? res.data : []
      writeCache(list)
      return list
    })
    .finally(() => { pending = null })
  return pending
}