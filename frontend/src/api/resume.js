import request from './request'

// 获取当前用户简历
export function getResume() {
  return request.get('/resume')
}

// 全局证件照（右上角用户菜单与简历导出共用）
export function getResumePhoto() {
  return request.get('/resume/photo')
}

export function saveResumePhoto(photo) {
  return request.post('/resume/photo', { photo })
}