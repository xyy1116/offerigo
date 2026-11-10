import request, { silentGet } from './request'

// 今日推荐岗位（userId 可选，后端以 JWT 中的用户为准）
// 当天尚无岗位时后端会实时爬取真实岗位，耗时可能超过默认 15s，因此单独放宽超时。
export function getTodayRecommend(params) {
  return request.get('/job/recommend/today', { params, timeout: 120000 })
}

// 批量加入我的目标岗位
export function batchAddTargetJobs(data) {
  return request.post('/job/target/batchAdd', data)
}

// 轻量版本检查：用于顶部通知红点；轮询失败不触发全局错误提示
export function getRecommendVersion() {
  return silentGet('/job/recommend/version')
}