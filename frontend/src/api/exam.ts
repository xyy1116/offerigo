import request, { silentGet } from './request'

// 出卷阶段轮询：失败不触发全局错误提示
export function getExamProgress(params) {
  return silentGet('/exam/progress', params)
}

// 批改阶段轮询：失败不触发全局错误提示
export function getExamGradeProgress(params) {
  return silentGet('/exam/grade/progress', params)
}

// 提取岗位技术栈
export function extractTechStack(params) {
  return request.post('/exam/techstack', null, { params, timeout: 180000 })
}

// 真题目库状态
export function getBankStatus() {
  return request.get('/exam/bank/status')
}

// 题库版本：用于导航栏"题库已更新"提示，失败不打扰用户
export function getBankVersion() {
  return silentGet('/exam/bank/version')
}

// 当前岗位能匹配到的真题数量
export function getBankMatch(params) {
  return request.get('/exam/bank/match', { params })
}

// 生成笔试题目（专项/限时模拟）
export function generateExam(data) {
  return request.post('/exam/generate', data, { timeout: 240000 })
}

// 试卷详情（作答）
export function getExamPaper(id) {
  return request.get(`/exam/paper/${id}`)
}

// 在线运行编程题
export function runExamCode(data) {
  return request.post('/exam/code/run', data, { timeout: 30000 })
}

// 提交作答
export function submitExam(data) {
  return request.post('/exam/submit', data, { timeout: 300000 })
}

// 历史试卷列表
export function getExamHistory() {
  return request.get('/exam/history')
}

// 删除历史试卷
export function deleteExamHistory(id) {
  return request.delete(`/exam/history/${id}`)
}

// 批改报告
export function getExamReport(id) {
  return request.get(`/exam/report/${id}`)
}

// 错题本列表
export function getWrongList(params) {
  return request.get('/exam/wrong/list', { params })
}

// 错题详情
export function getWrongDetail(id) {
  return request.get(`/exam/wrong/${id}`)
}

// 错题重做
export function redoWrong(id, data) {
  return request.post(`/exam/wrong/${id}/redo`, data, { timeout: 120000 })
}

// 标记已掌握
export function markMastered(id) {
  return request.put(`/exam/wrong/${id}/mastered`)
}

// 删除错题
export function deleteWrong(id) {
  return request.delete(`/exam/wrong/${id}`)
}

// 错题本「岗位」聚合列表（按岗位名称自动归类）
export function getWrongJobs() {
  return request.get('/exam/wrong/jobs')
}