import request, { silentGet } from './request'

// ===== 简历投递准备（岗位匹配度评估 / 定制简历 / 求职信）=====

// 岗位匹配度评估报告
export function evaluateApplication(jobId) {
  return request.get(`/application/${jobId}/evaluate`, { timeout: 60000 })
}

// 针对岗位定制简历（复用生成简历链路，返回结构化 JobResumeVO）
export function tailorApplication(jobId) {
  return request.get(`/application/${jobId}/tailor`, { timeout: 60000 })
}

// 生成求职信
export function coverLetterApplication(jobId) {
  return request.get(`/application/${jobId}/cover-letter`, { timeout: 60000 })
}

// 生成任务真实阶段（准备素材 / AI 生成 / 整理结果）
export function getGenerationProgress(jobId, type) {
  return silentGet(`/application/${jobId}/progress`, { type })
}

// 导出求职信 PDF
export function exportCoverLetterPdf(data) {
  return request.post('/application/export/cover-letter/pdf', data, {
    responseType: 'blob',
    timeout: 60000
  })
}

// 导出求职信 Word(.docx)
export function exportCoverLetterDocx(data) {
  return request.post('/application/export/cover-letter/docx', data, {
    responseType: 'blob',
    timeout: 60000
  })
}