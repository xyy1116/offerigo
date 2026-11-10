import request from './request'

// ===== 我的简历素材 =====
export function listKnowledge() {
  return request.get('/jobmatch/knowledge')
}

export function splitKnowledge(content) {
  return request.post('/jobmatch/knowledge/split', { content }, { timeout: 60000 })
}

export function importResume(file, onUploadProgress) {
  const formData = new FormData()
  formData.append('file', file)
  return request.post('/jobmatch/knowledge/import', formData, {
    timeout: 300000,
    onUploadProgress: onUploadProgress
      ? (e) => { if (e.total) onUploadProgress(Math.round((e.loaded / e.total) * 100)) }
      : undefined
  })
}

export function saveBasicInfo(data) {
  return request.post('/jobmatch/knowledge/basic', data)
}

export function batchSaveKnowledge(items) {
  return request.post('/jobmatch/knowledge/batch', { items })
}

export function updateKnowledge(id, data) {
  return request.put(`/jobmatch/knowledge/${id}`, data)
}

export function addKnowledgeItem(data) {
  return request.post('/jobmatch/knowledge/item', data)
}

// 快速补充：将岗位待补足项转化为简历化素材草稿（含自动匹配分类，不入库）
export function generateMaterial(requirement) {
  return request.post('/jobmatch/knowledge/generate-material', { requirement }, { timeout: 60000 })
}

export function deleteKnowledge(id) {
  return request.delete(`/jobmatch/knowledge/${id}`)
}

// ===== 我的目标岗位 =====
export function listJobs() {
  return request.get('/jobmatch/jobs')
}

// 粘贴岗位描述并预览 AI 提取结果（不入库）
export function previewJob(content) {
  return request.post('/jobmatch/jobs/preview', { content }, { timeout: 120000 })
}

// 链接导入岗位：爬取网页正文并预览 AI 标准化结果（不入库）
export function previewJobByUrl(url) {
  return request.post('/jobmatch/jobs/preview/link', { url }, { timeout: 60000 })
}

// 导入岗位文档并预览 AI 提取结果（不入库，图片型 PDF 自动 OCR）
// 扫描件逐页 OCR 耗时较长，超时放宽至 5 分钟，避免前端提前中断
export function importJob(file, onUploadProgress) {
  const formData = new FormData()
  formData.append('file', file)
  return request.post('/jobmatch/jobs/import', formData, {
    timeout: 300000,
    onUploadProgress: onUploadProgress
      ? (e) => { if (e.total) onUploadProgress(Math.round((e.loaded / e.total) * 100)) }
      : undefined
  })
}

export function addJob(data) {
  return request.post('/jobmatch/jobs', data)
}

// 批量删除岗位
export function deleteJobs(ids) {
  return request.delete('/jobmatch/jobs/batch', { data: ids, timeout: 60000 })
}

// ===== AI 综合评估 =====
// 获取最近一次评估结果（不触发重新评估，用于进入页面时回显）
export function getLatestAnalysis() {
  return request.get('/jobmatch/analyze', { timeout: 30000 })
}

// 校验存储结果与当前数据版本是否一致（不触发计算，渲染前快速核对）
export function verifyMatch() {
  return request.get('/jobmatch/analyze/verify', { timeout: 30000 })
}

// 异步发起排名计算，返回任务句柄（RUNNING/DONE）
export function analyzeAsync() {
  return request.post('/jobmatch/analyze/async', {}, { timeout: 30000 })
}

// 查询异步排名任务进度与分批结果
export function getAnalyzeTask(taskId) {
  return request.get(`/jobmatch/analyze/task/${taskId}`, { timeout: 30000 })
}

// 针对单个岗位生成定制简历（生成耗时较长，延长超时）
export function generateResume(jobId) {
  return request.post(`/jobmatch/resume/${jobId}`, {}, { timeout: 180000 })
}

// 简历模板列表（后端实时爬取 + 内置兜底）
export function getResumeTemplates() {
  return request.get('/jobmatch/resume/templates', { timeout: 30000 })
}

// 导出岗位定制简历为 Word(.docx)，返回 blob
export function exportResumeDocx(data, templateId, filename) {
  return request.post('/jobmatch/resume/export/docx', data, {
    params: { templateId, filename },
    responseType: 'blob',
    timeout: 60000
  })
}

// 导出岗位定制简历为 PDF，返回 blob
export function exportResumePdf(data, templateId, filename) {
  return request.post('/jobmatch/resume/export/pdf', data, {
    params: { templateId, filename },
    responseType: 'blob',
    timeout: 60000
  })
}

// 对目标岗位截图进行 OCR 识别（识别全文 + 提取技术栈并回填）
export function ocrJob(jobId, file) {
  const formData = new FormData()
  formData.append('file', file)
  return request.post(`/jobmatch/jobs/${jobId}/ocr`, formData, { timeout: 60000 })
}

// ===== 投简历双渠道 =====
// 首次触发：识别岗位所属公司名称（可能返回公司名 / 候选列表 / 空）
export function recognizeApplyChannel(jobId) {
  return request.get(`/jobmatch/apply/channel/${jobId}`, { timeout: 60000 })
}

// 补填招聘单位/招聘邮箱，返回更新后的双渠道状态
export function patchApplyChannel(data) {
  return request.post('/jobmatch/apply/channel', data, { timeout: 60000 })
}

// 「去投递」跳转地址：推荐来源直达原始岗位详情，其余岗位后端检索校验；无结果返回空
export function resolveApplyDetailLink(jobId) {
  return request.get(`/jobmatch/apply/detail-link/${jobId}`, { timeout: 90000 })
}

// 岗位页已真正打开后生成投递草稿（只读已保存链接，不触发检索）
export function createApplyDraft(jobId) {
  return request.post(`/jobmatch/apply/draft/${jobId}`)
}

// ===== 投递台账 =====
// 进入/切回应用时拉取：待确认草稿 + 到期跟进数量
export function getApplicationReminders() {
  return request.get('/jobmatch/applications/reminders')
}

// 正式投递列表（状态/关键词/日期筛选，服务端按投递时间倒序）
export function listApplications(params) {
  return request.get('/jobmatch/applications', { params })
}

// 确认投递成功：草稿转正式记录
export function confirmApplication(id, data) {
  return request.post(`/jobmatch/applications/${id}/confirm`, data, { timeout: 180000 })
}

// 投递归档的简历文件（用户自己上传的 PDF/Word）
export function getApplicationResume(id) {
  return request.get(`/jobmatch/applications/${id}/resume`, {
    responseType: 'blob',
    timeout: 60000
  })
}

// 上传本次投递实际使用的简历文件
export function uploadApplicationResume(id, file) {
  const formData = new FormData()
  formData.append('file', file)
  return request.post(`/jobmatch/applications/${id}/resume`, formData, { timeout: 60000 })
}

// 删除已上传的投递简历
export function deleteApplicationResume(id) {
  return request.delete(`/jobmatch/applications/${id}/resume`)
}

// 修改投递状态/备注
export function updateApplication(id, data) {
  return request.put(`/jobmatch/applications/${id}`, data)
}

// 删除投递记录（含草稿「暂不记录」）
export function deleteApplication(id) {
  return request.delete(`/jobmatch/applications/${id}`)
}

// 跟进提醒天数（全局）
export function getApplicationSettings() {
  return request.get('/jobmatch/applications/settings')
}

export function saveApplicationSettings(data) {
  return request.put('/jobmatch/applications/settings', data)
}


