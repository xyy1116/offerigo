import request from './request'

// AI 面试历史记录列表
export function getInterviewHistory() {
  return request.get('/interview/history')
}

// AI 面试记录详情
export function getInterviewDetail(id) {
  return request.get(`/interview/history/${id}`)
}

// 删除 AI 面试记录
export function deleteInterviewHistory(id) {
  return request.delete(`/interview/history/${id}`)
}

// 获取进行中的面试（无则返回 null），用于中断续接
export function getActiveInterview() {
  return request.get('/interview/active')
}