// 统一表单校验规则：供各视图 el-form 的 :rules 复用，避免规则与文案重复定义

/** 必填（默认 blur 触发，可传入 change 等） */
export const required = (message: string, trigger = 'blur') => ({ required: true, message, trigger })

/** 长度区间限制 */
export const minMax = (min: number, max: number, message: string, trigger = 'blur') => ({
  min,
  max,
  message,
  trigger
})

/** 邮箱格式 */
export const emailRule = (message = '邮箱格式不正确', trigger = 'blur') => ({
  type: 'email',
  message,
  trigger
})

/** 确认密码一致性（getPassword 返回要比较的目标值） */
export const confirmPasswordRule = (
  getPassword: () => unknown,
  message = '两次输入的密码不一致',
  trigger = 'blur'
) => ({
  validator: (_rule: unknown, value: unknown, callback: (error?: Error) => void) => {
    if (value !== getPassword()) callback(new Error(message))
    else callback()
  },
  trigger
})