import request, { silentGet } from './request'
import { getToken } from '@/utils/token'

// ===== 简历投递准备（岗位匹配度评估 / 定制简历 / 求职信）=====

// 岗位匹配度评估报告
export function evaluateApplication(jobId) {
  return request.get(`/application/${jobId}/evaluate`, { timeout: 180000 })
}

// 针对岗位定制简历（复用生成简历链路，返回结构化 JobResumeVO）
export function tailorApplication(jobId) {
  return request.get(`/application/${jobId}/tailor`, { timeout: 180000 })
}

// 生成求职信
export function coverLetterApplication(jobId) {
  return request.get(`/application/${jobId}/cover-letter`, { timeout: 180000 })
}

/** 流式生成回调：正文逐段到达，结束时拿到结构与引用素材 */
export interface CoverLetterStreamHandlers {
  onDelta: (text: string) => void
  onDone: (data: Record<string, unknown>) => void
  onError: (message: string) => void
}

/**
 * 流式生成求职信。用 fetch 而非 EventSource，因为需要携带 JWT 请求头。
 */
export async function streamCoverLetter(
  jobId: string | number,
  handlers: CoverLetterStreamHandlers
): Promise<void> {
  const token = getToken()
  const response = await fetch(`/api/application/${jobId}/cover-letter/stream`, {
    headers: token ? { Authorization: `Bearer ${token}` } : {}
  })
  if (!response.ok || !response.body) {
    throw new Error('流式生成请求失败，请稍后重试')
  }
  const reader = response.body.getReader()
  const decoder = new TextDecoder()
  let buffer = ''
  let finished = false
  for (;;) {
    const { done, value } = await reader.read()
    if (done) break
    buffer += decoder.decode(value, { stream: true })
    let index = buffer.indexOf('\n\n')
    while (index >= 0) {
      if (handleSseFrame(buffer.slice(0, index), handlers)) finished = true
      buffer = buffer.slice(index + 2)
      index = buffer.indexOf('\n\n')
    }
  }
  // 连接被静默关闭（超时/服务重启）时不会有 done 事件，不能把半截正文当成成功
  if (!finished) {
    handlers.onError('生成中断，请重试')
  }
}

/** 处理一个 SSE 帧；返回 true 表示收到了终止事件（done / error） */
function handleSseFrame(frame: string, handlers: CoverLetterStreamHandlers): boolean {
  let event = 'message'
  const dataLines: string[] = []
  for (const line of frame.split('\n')) {
    if (line.startsWith('event:')) event = line.slice(6).trim()
    else if (line.startsWith('data:')) dataLines.push(line.slice(5).trim())
  }
  if (!dataLines.length) return false
  try {
    const payload = JSON.parse(dataLines.join('\n'))
    if (event === 'delta') {
      handlers.onDelta(String(payload))
      return false
    }
    if (event === 'done') {
      handlers.onDone(payload)
      return true
    }
    if (event === 'error') {
      handlers.onError(String(payload))
      return true
    }
  } catch {
    // 无法解析的帧直接忽略，不影响后续流
  }
  return false
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