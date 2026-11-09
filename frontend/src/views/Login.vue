<template>
  <el-dialog
    :model-value="modelValue"
    @update:model-value="$emit('update:modelValue', $event)"
    width="448px"
    align-center
    append-to-body
    class="login-dialog"
    aria-label="登录 / 注册"
    :close-on-click-modal="false"
  >
    <div class="login-body">
    <div class="auth-brand">
      <span class="brand-mark" aria-hidden="true">
        <svg viewBox="0 0 32 32">
          <path
            d="M5 14.8 27 6l-6.1 20.9-7-7.6L5 14.8Z"
            fill="none"
            stroke="currentColor"
            stroke-width="2.3"
            stroke-linejoin="round"
          />
          <path d="M13.9 19.3 27 6" fill="none" stroke="currentColor" stroke-width="2.3" stroke-linejoin="round" />
        </svg>
      </span>
      <span class="brand-name">Offeri<span class="brand-go">Go</span></span>
      <span class="brand-sub">智能求职助手</span>
    </div>
    <el-tabs v-model="tab" stretch class="login-tabs">
        <!-- 登录 -->
        <el-tab-pane label="登录" name="login">
          <form class="form" @submit.prevent="handleLogin">
            <div class="auth-head">
              <h2 class="auth-title">登录你的账户</h2>
              <p class="auth-sub">继续你的简历诊断与面试准备</p>
            </div>
            <div class="field">
              <label class="field-label" for="login-username">用户名</label>
              <input id="login-username" v-model="loginForm.username" placeholder="请输入用户名" type="text" @keyup.enter="handleLogin" />
            </div>
            <div class="field">
              <label class="field-label" for="login-password">密码</label>
              <input id="login-password" v-model="loginForm.password" placeholder="请输入密码" type="password" @keyup.enter="handleLogin" />
              <a class="field-link" role="button" tabindex="0" @click="openForgot" @keydown.enter="openForgot">忘记密码？</a>
            </div>
            <div class="field">
              <label class="field-label" for="login-captcha">验证码</label>
              <div class="captcha-row">
                <input id="login-captcha" v-model="loginForm.captchaCode" placeholder="请输入验证码" type="text" @keyup.enter="handleLogin" />
                <img :src="captcha.image" class="captcha-img" alt="验证码" title="点击刷新" role="button" tabindex="0" @click="loadCaptcha" @keydown.enter="loadCaptcha" />
              </div>
            </div>
            <div class="remember-row">
              <el-checkbox v-model="loginForm.rememberMe">记住我</el-checkbox>
            </div>
            <div id="footer-area">
              <button type="submit" class="login-submit" :disabled="loading">{{ loading ? '登录中…' : '登录' }}</button>
              <div id="text-inside">
                <p>还没有账号？</p>
                <a id="link" @click="tab = 'register'">立即注册</a>
              </div>
            </div>
          </form>
        </el-tab-pane>

        <!-- 注册 -->
        <el-tab-pane label="注册" name="register">
          <div class="auth-panel">
          <div class="auth-head">
            <h2 class="auth-title">创建账户</h2>
            <p class="auth-sub">注册后即可保存简历、面试与笔试记录</p>
          </div>
          <el-form ref="registerFormRef" :model="registerForm" :rules="registerRules" size="large" label-position="top">
            <el-form-item label="用户名" prop="username">
              <el-input v-model="registerForm.username" placeholder="3-20 位用户名" :prefix-icon="User" />
            </el-form-item>
            <el-form-item label="昵称" prop="nickname">
              <el-input v-model="registerForm.nickname" placeholder="选填" :prefix-icon="UserFilled" />
            </el-form-item>
            <el-form-item label="邮箱" prop="email">
              <el-input v-model="registerForm.email" placeholder="选填，用于找回与通知" :prefix-icon="Message" />
            </el-form-item>
            <el-form-item label="密码" prop="password">
              <el-input v-model="registerForm.password" type="password" placeholder="6-32 位密码" :prefix-icon="Lock" show-password />
            </el-form-item>
            <el-form-item v-if="registerForm.password" class="strength-item">
              <div class="strength">
                <span class="strength-label">密码强度</span>
                <div class="strength-bars">
                  <i v-for="n in 3" :key="n" :class="{ on: strength.level >= n }" :style="{ background: strength.level >= n ? strength.color : '' }"></i>
                </div>
                <span class="strength-text" :style="{ color: strength.color }">{{ strength.label }}</span>
              </div>
            </el-form-item>
            <el-form-item label="确认密码" prop="confirmPassword">
              <el-input v-model="registerForm.confirmPassword" type="password" placeholder="再次输入密码" :prefix-icon="Lock" show-password />
            </el-form-item>
            <el-form-item label="密保问题" prop="securityQuestion">
              <el-select v-model="registerForm.securityQuestion" placeholder="选择密保问题" filterable allow-create style="width: 100%">
                <el-option v-for="q in securityQuestions" :key="q" :label="q" :value="q" />
              </el-select>
            </el-form-item>
            <el-form-item label="密保答案" prop="securityAnswer">
              <el-input v-model="registerForm.securityAnswer" placeholder="用于忘记密码时验证身份" :prefix-icon="Key" />
            </el-form-item>
            <el-form-item label="验证码" prop="captchaCode">
              <div class="captcha-row">
                <el-input v-model="registerForm.captchaCode" placeholder="验证码" :prefix-icon="Key" />
                <img :src="captcha.image" class="captcha-img" alt="验证码" title="点击刷新" role="button" tabindex="0" @click="loadCaptcha" @keydown.enter="loadCaptcha" />
              </div>
            </el-form-item>
            <el-form-item>
              <button class="button login-btn" :disabled="registering" :class="{ 'is-loading': registering }" @click="handleRegister">注册</button>
            </el-form-item>
          </el-form>
          </div>
        </el-tab-pane>
      </el-tabs>
    </div>
  </el-dialog>

  <!-- 忘记密码 -->
    <el-dialog v-model="forgotVisible" title="找回密码" width="420px" append-to-body :close-on-click-modal="false">
      <el-steps :active="forgotStep" align-center finish-status="success" class="forgot-steps">
        <el-step title="验证身份" />
        <el-step title="重置密码" />
      </el-steps>

      <el-form v-if="forgotStep === 0" ref="forgotFormRef" :model="forgotForm" :rules="forgotRules" size="large" label-position="top" class="forgot-form">
        <el-form-item label="用户名" prop="username">
          <el-input v-model="forgotForm.username" placeholder="请输入注册用户名" />
        </el-form-item>
        <el-form-item>
          <button class="button login-btn" :disabled="forgotLoading" :class="{ 'is-loading': forgotLoading }" @click="handleForgotQuestion">下一步</button>
        </el-form-item>
      </el-form>

      <el-form v-else ref="forgotResetFormRef" :model="forgotForm" :rules="forgotResetRules" size="large" label-position="top" class="forgot-form">
        <el-alert type="info" :closable="false" class="forgot-question" :title="`密保问题：${forgotQuestion}`" />
        <el-form-item label="密保答案" prop="securityAnswer">
          <el-input v-model="forgotForm.securityAnswer" placeholder="请输入密保答案" />
        </el-form-item>
        <el-form-item label="新密码" prop="newPassword">
          <el-input v-model="forgotForm.newPassword" type="password" placeholder="6-32 位新密码" show-password />
        </el-form-item>
        <el-form-item>
          <button class="button login-btn" :disabled="forgotLoading" :class="{ 'is-loading': forgotLoading }" @click="handleForgotReset">重置密码</button>
        </el-form-item>
      </el-form>
    </el-dialog>
</template>

<script setup>
import { reactive, ref, computed } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { User, Lock, Key, UserFilled, Message } from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'
import { getCaptcha, register, forgotQuestion as fetchForgotQuestion, forgotReset } from '@/api/auth'
import { required, minMax, emailRule, confirmPasswordRule } from '@/utils/validators'

defineProps({
  modelValue: { type: Boolean, default: false }
})
defineEmits(['update:modelValue'])

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

const tab = ref('login')
const loading = ref(false)
const registering = ref(false)

const captcha = reactive({ captchaId: '', image: '' })

const securityQuestions = [
  '你的出生城市是？',
  '你的小学名称是？',
  '你母亲的姓名是？',
  '你最喜欢的电影是？',
  '你的第一辆车品牌是？'
]

// ===== 登录 =====
const loginForm = reactive({ username: '', password: '', captchaCode: '', rememberMe: false })

async function handleLogin() {
  if (!loginForm.username.trim() || !loginForm.password.trim() || !loginForm.captchaCode.trim()) {
    ElMessage.warning('请输入用户名、密码和验证码')
    return
  }
  loading.value = true
  try {
    await userStore.login({
      username: loginForm.username,
      password: loginForm.password,
      captchaId: captcha.captchaId,
      captchaCode: loginForm.captchaCode,
      rememberMe: loginForm.rememberMe
    })
    ElMessage.success('登录成功')
    router.push(route.query.redirect || '/jobmatch')
  } catch (e) {
    loadCaptcha()
  } finally {
    loading.value = false
  }
}

// ===== 注册 =====
const registerFormRef = ref()
const registerForm = reactive({
  username: '',
  nickname: '',
  email: '',
  password: '',
  confirmPassword: '',
  securityQuestion: '',
  securityAnswer: '',
  captchaCode: ''
})

const registerRules = {
  username: [
    required('请输入用户名'),
    minMax(3, 20, '用户名长度需在 3-20 之间')
  ],
  email: [emailRule('邮箱格式不正确')],
  password: [
    required('请输入密码'),
    minMax(6, 32, '密码长度需在 6-32 之间')
  ],
  confirmPassword: [
    required('请再次输入密码'),
    confirmPasswordRule(() => registerForm.password)
  ],
  securityQuestion: [required('请选择或输入密保问题', 'change')],
  securityAnswer: [required('请输入密保答案')],
  captchaCode: [required('请输入验证码')]
}

const strength = computed(() => {
  const pwd = registerForm.password
  if (!pwd) return { level: 0, label: '', color: '' }
  let score = 0
  if (pwd.length >= 6) score++
  if (pwd.length >= 10) score++
  if (/[a-z]/.test(pwd) && /[A-Z]/.test(pwd)) score++
  if (/\d/.test(pwd)) score++
  if (/[^a-zA-Z0-9]/.test(pwd)) score++
  if (score <= 2) return { level: 1, label: '弱', color: '#b42318' }
  if (score <= 3) return { level: 2, label: '中', color: '#b45309' }
  return { level: 3, label: '强', color: '#177a4c' }
})

async function handleRegister() {
  await registerFormRef.value.validate()
  registering.value = true
  try {
    await register({
      username: registerForm.username,
      nickname: registerForm.nickname,
      email: registerForm.email,
      password: registerForm.password,
      securityQuestion: registerForm.securityQuestion,
      securityAnswer: registerForm.securityAnswer,
      captchaId: captcha.captchaId,
      captchaCode: registerForm.captchaCode
    })
    ElMessage.success('注册成功，请登录')
    loginForm.username = registerForm.username
    loginForm.password = ''
    tab.value = 'login'
    loadCaptcha()
  } catch (e) {
    loadCaptcha()
  } finally {
    registering.value = false
  }
}

// ===== 忘记密码 =====
const forgotVisible = ref(false)
const forgotStep = ref(0)
const forgotLoading = ref(false)
const forgotQuestion = ref('')
const forgotFormRef = ref()
const forgotResetFormRef = ref()
const forgotForm = reactive({ username: '', securityAnswer: '', newPassword: '' })

const forgotRules = {
  username: [required('请输入用户名')]
}
const forgotResetRules = {
  securityAnswer: [required('请输入密保答案')],
  newPassword: [
    required('请输入新密码'),
    minMax(6, 32, '密码长度需在 6-32 之间')
  ]
}

function openForgot() {
  forgotStep.value = 0
  forgotForm.username = ''
  forgotForm.securityAnswer = ''
  forgotForm.newPassword = ''
  forgotQuestion.value = ''
  forgotVisible.value = true
}

async function handleForgotQuestion() {
  await forgotFormRef.value.validate()
  forgotLoading.value = true
  try {
    const res = await fetchForgotQuestion({ username: forgotForm.username })
    forgotQuestion.value = res.data.securityQuestion
    forgotStep.value = 1
  } catch (e) {
    // 错误已由拦截器提示
  } finally {
    forgotLoading.value = false
  }
}

async function handleForgotReset() {
  await forgotResetFormRef.value.validate()
  forgotLoading.value = true
  try {
    await forgotReset({
      username: forgotForm.username,
      securityAnswer: forgotForm.securityAnswer,
      newPassword: forgotForm.newPassword
    })
    ElMessage.success('密码重置成功，请使用新密码登录')
    forgotVisible.value = false
    loginForm.username = forgotForm.username
    loginForm.password = ''
    tab.value = 'login'
  } catch (e) {
    // 错误已由拦截器提示
  } finally {
    forgotLoading.value = false
  }
}

// ===== 验证码 =====
async function loadCaptcha() {
  try {
    const res = await getCaptcha()
    captcha.captchaId = res.data.captchaId
    captcha.image = res.data.image
  } catch (e) {
    // 请求失败由拦截器处理
  }
}

loadCaptcha()
</script>

<style scoped>
.login-dialog {
  border-top: 2px solid var(--color-primary);
}
.login-dialog :deep(.el-dialog__header) {
  padding: 14px 20px 0;
  border-bottom: none;
}
.login-dialog :deep(.el-dialog__body) {
  padding: 0 24px 24px;
  max-height: 70vh;
  overflow-y: auto;
}
.login-body {
  padding: 0;
}

.auth-brand {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 16px;
}
.auth-brand .brand-mark {
  flex: none;
  display: grid;
  place-items: center;
  width: 30px;
  height: 30px;
  border-radius: 9px;
  color: #ffffff;
  background: linear-gradient(135deg, #143f47 0%, #1f5f6b 45%, #2f9e9a 100%);
  box-shadow: 0 2px 8px rgba(31, 95, 107, 0.24);
}
.auth-brand .brand-mark svg {
  display: block;
  width: 30px;
  height: 30px;
}
.auth-brand .brand-name {
  font-family: var(--font-brand);
  font-size: 17px;
  font-weight: 700;
  line-height: 1;
  color: var(--color-text);
  letter-spacing: -0.02em;
}
.auth-brand .brand-go {
  background-image: linear-gradient(100deg, #1f5f6b 10%, #2f8f8a 90%);
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
}
.auth-brand .brand-sub {
  padding-left: 8px;
  border-left: 1px solid var(--color-border);
  font-size: 12px;
  color: var(--color-text-3);
}

/* 登录 / 注册 切换 */
.login-tabs {
  width: 100%;
  max-width: 400px;
}
.login-tabs :deep(.el-tabs__header) {
  margin: 0 0 16px;
}
.login-tabs :deep(.el-tabs__nav-wrap::after) {
  height: 1px;
  background: var(--color-border);
}
.login-tabs :deep(.el-tabs__item) {
  height: 40px;
  font-size: 13px;
  font-weight: 500;
  color: var(--color-text-2);
}
.login-tabs :deep(.el-tabs__item.is-active) {
  color: var(--color-text);
}
.login-tabs :deep(.el-tabs__active-bar) {
  height: 2px;
  background: var(--color-primary);
}
.login-tabs :deep(.el-tabs__content) {
  width: 100%;
  overflow: visible;
}

/* ===== 登录表单 ===== */
.form {
  display: flex;
  flex-direction: column;
  box-sizing: border-box;
}

.auth-head {
  margin-bottom: 20px;
}
.auth-title {
  margin: 0;
  font-size: 20px;
  font-weight: 600;
  letter-spacing: -0.01em;
  color: var(--color-text);
}
.auth-sub {
  margin: 6px 0 0;
  font-size: 13px;
  color: var(--color-text-2);
}

.field {
  display: flex;
  flex-direction: column;
  margin-bottom: 16px;
}
.field-label {
  margin-bottom: 6px;
  font-size: 12px;
  font-weight: 500;
  color: var(--color-text-2);
}
.field input {
  width: 100%;
  height: 38px;
  padding: 0 12px;
  font-family: inherit;
  font-size: 14px;
  color: var(--color-text);
  background: var(--color-surface);
  border: 1px solid var(--color-border-strong);
  border-radius: var(--radius-sm);
  outline: none;
  box-sizing: border-box;
  transition: var(--transition), box-shadow 0.15s ease;
}
.field input::placeholder {
  color: var(--color-text-3);
}
.field input:hover {
  border-color: var(--color-border-hover);
}
.field input:focus {
  border-color: var(--color-primary);
  box-shadow: 0 0 0 3px rgba(31, 95, 107, 0.14);
}
.field-link {
  align-self: flex-end;
  margin-top: 8px;
  font-size: 12px;
  color: var(--color-text-2);
  cursor: pointer;
  transition: color 0.15s ease;
}
.field-link:hover {
  color: var(--color-accent);
}

.remember-row {
  margin-top: 2px;
}
.remember-row :deep(.el-checkbox__label) {
  font-size: 13px;
  color: var(--color-text-2);
}

#footer-area {
  display: flex;
  flex-direction: column;
  margin-top: 20px;
}
.login-submit {
  width: 100%;
  height: 38px;
  font-family: inherit;
  font-size: 13px;
  font-weight: 500;
  color: #ffffff;
  background: var(--color-primary);
  border: 1px solid var(--color-primary);
  border-radius: var(--radius-sm);
  cursor: pointer;
  transition:
    background-color 0.18s ease,
    border-color 0.18s ease,
    box-shadow 0.18s ease,
    transform 0.18s var(--ease-out-expo);
}
.login-submit:hover:not(:disabled) {
  background: var(--color-primary-mid);
  border-color: var(--color-primary-mid);
  transform: translateY(-1px);
  box-shadow: var(--shadow-teal);
}
.login-submit:active:not(:disabled) {
  background: var(--color-primary-deep);
  transform: translateY(0) scale(0.98);
  box-shadow: none;
}
.login-submit:focus-visible {
  outline: 2px solid var(--color-primary);
  outline-offset: 2px;
}
.login-submit:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
#text-inside {
  display: flex;
  justify-content: center;
  gap: 4px;
  margin-top: 16px;
  font-size: 12px;
}
#text-inside p {
  margin: 0;
  color: var(--color-text-3);
}
#link {
  color: var(--color-accent);
  font-weight: 500;
  cursor: pointer;
}

/* ===== 注册面板 ===== */
.auth-panel {
  box-sizing: border-box;
}
.auth-panel :deep(.el-form-item__label) {
  color: var(--color-text);
  font-weight: 500;
}
.auth-panel :deep(.el-form-item) {
  margin-bottom: 18px;
}

/* 验证码 */
.captcha-row {
  display: flex;
  gap: 10px;
  width: 100%;
  align-items: center;
}
.captcha-row input {
  flex: 1;
  min-width: 0;
}
.captcha-img {
  height: 38px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
  cursor: pointer;
  flex-shrink: 0;
}

.login-btn {
  width: 100%;
  height: 38px;
}

/* 密码强度 */
.strength-item {
  margin-top: -8px;
}
.strength {
  display: flex;
  align-items: center;
  gap: 8px;
}
.strength-label {
  font-size: 12px;
  color: var(--color-text-3);
}
.strength-bars {
  display: flex;
  gap: 4px;
}
.strength-bars i {
  display: block;
  width: 40px;
  height: 4px;
  border-radius: 2px;
  background: var(--color-surface-muted);
  transition: background 0.2s;
}
.strength-text {
  font-size: 12px;
}

.forgot-steps {
  margin-bottom: 20px;
}
.forgot-form {
  margin-top: 8px;
}
.forgot-question {
  margin-bottom: 16px;
}
</style>
