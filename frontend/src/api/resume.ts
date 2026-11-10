import request from './request'

/** 获取当前用户简历 */
export function getResume() {
  return request.get<Record<string, unknown>>('/resume')
}

/** 全局证件照（右上角用户菜单与简历导出共用），返回 data URI */
export function getResumePhoto() {
  return request.get<string>('/resume/photo')
}

/** 保存/删除证件照，传空字符串表示删除 */
export function saveResumePhoto(photo: string) {
  return request.post<void>('/resume/photo', { photo })
}