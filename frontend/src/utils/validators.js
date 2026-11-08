// ============================================================
// 统一表单校验规则 / 校验器
// 用于各视图 el-form 的 :rules，避免规则重复定义并统一文案
// ============================================================

/** 必填（默认 blur 触发，可传入 change 等） */
export const required = (message, trigger = 'blur') => ({ required: true, message, trigger })

/** 长度区间限制 */
export const minMax = (min, max, message, trigger = 'blur') => ({ min, max, message, trigger })

/** 邮箱格式 */
export const emailRule = (message = '邮箱格式不正确', trigger = 'blur') => ({
  type: 'email',
  message,
  trigger
})

/** 确认密码一致性（getPassword 返回要比较的目标值） */
export const confirmPasswordRule = (getPassword, message = '两次输入的密码不一致', trigger = 'blur') => ({
  validator: (rule, value, callback) => {
    if (value !== getPassword()) callback(new Error(message))
    else callback()
  },
  trigger
})