import { silentGet } from './request'

// 首页公开统计（题库规模），失败静默
export function getPublicStats() {
  return silentGet('/public/stats')
}