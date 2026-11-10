/** 后端统一响应结构，与 Result<T> 对应。 */
export interface ApiResult<T = unknown> {
  code: number
  message: string
  data: T
}

/** 登录用户信息（/api/auth 与 /api/user 返回）。 */
export interface UserInfo {
  id?: number
  username?: string
  nickname?: string
  avatar?: string
  email?: string
  phone?: string
  token?: string
}

/** 登录表单，与后端 LoginDTO 对应 */
export interface LoginForm {
  username: string
  password: string
  captchaId?: string
  captchaCode?: string
  rememberMe?: boolean
}

/** 注册表单，与后端 RegisterDTO 对应 */
export interface RegisterForm {
  username: string
  password: string
  nickname?: string
  email?: string
  securityQuestion: string
  securityAnswer: string
  captchaId?: string
  captchaCode?: string
}

/** 忘记密码：查询密保问题 */
export interface ForgotQuestionForm {
  username: string
}

/** 忘记密码：校验密保答案并重置 */
export interface ForgotResetForm {
  username: string
  securityAnswer: string
  newPassword: string
}