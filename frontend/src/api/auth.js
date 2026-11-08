import request from './request'

// 获取图形验证码
export function getCaptcha() {
  return request.get('/auth/captcha')
}

export function login(data) {
  return request.post('/auth/login', data)
}

export function register(data) {
  return request.post('/auth/register', data)
}

// 忘记密码：查询密保问题
export function forgotQuestion(data) {
  return request.post('/auth/forgot/question', data)
}

// 忘记密码：验证密保答案并重置密码
export function forgotReset(data) {
  return request.post('/auth/forgot/reset', data)
}

export function getProfile() {
  return request.get('/user/profile')
}

// 头像：传空字符串表示恢复默认头像
export function saveUserAvatar(avatar) {
  return request.post('/user/avatar', { avatar })
}