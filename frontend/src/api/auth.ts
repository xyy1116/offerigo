import request from './request'
import type {
  ForgotQuestionForm,
  ForgotResetForm,
  LoginForm,
  RegisterForm,
  UserInfo
} from '@/types/api'

/** 图形验证码：返回标识与图片 */
export function getCaptcha() {
  return request.get<{ id: string; image: string }>('/auth/captcha')
}

export function login(data: LoginForm) {
  return request.post<UserInfo>('/auth/login', data)
}

export function register(data: RegisterForm) {
  return request.post<void>('/auth/register', data)
}

/** 忘记密码：查询密保问题 */
export function forgotQuestion(data: ForgotQuestionForm) {
  return request.post<{ question: string }>('/auth/forgot/question', data)
}

/** 忘记密码：验证密保答案并重置密码 */
export function forgotReset(data: ForgotResetForm) {
  return request.post<void>('/auth/forgot/reset', data)
}

export function getProfile() {
  return request.get<UserInfo>('/user/profile')
}

/** 头像：传空字符串表示恢复默认头像 */
export function saveUserAvatar(avatar: string) {
  return request.post<void>('/user/avatar', { avatar })
}