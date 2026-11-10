import { defineStore } from 'pinia'
import { ref } from 'vue'
import { login as loginApi, getProfile, saveUserAvatar } from '@/api/auth'
import { getResumePhoto, saveResumePhoto } from '@/api/resume'
import { getToken, setToken, removeToken } from '@/utils/token'
import type { LoginForm, UserInfo } from '@/types/api'

export const useUserStore = defineStore('user', () => {
  const token = ref<string>(getToken() || '')
  const userInfo = ref<UserInfo | null>(null)
  /** 全局证件照（data URI），保存在用户简历中，导出时复用 */
  const photo = ref('')
  const photoLoaded = ref(false)

  async function login(loginForm: LoginForm) {
    const res = await loginApi(loginForm)
    const data = res.data
    token.value = data.token || ''
    setToken(token.value)
    userInfo.value = data
    return res
  }

  async function fetchProfile(): Promise<UserInfo | null> {
    const res = await getProfile()
    userInfo.value = res.data || null
    return userInfo.value
  }

  /** 读取证件照；已加载过则直接返回缓存，force=true 时强制刷新 */
  async function fetchPhoto(force = false): Promise<string> {
    if (!token.value) return ''
    if (photoLoaded.value && !force) return photo.value
    const res = await getResumePhoto()
    photo.value = res.data || ''
    photoLoaded.value = true
    return photo.value
  }

  /** 保存/删除证件照，传空字符串表示删除 */
  async function updatePhoto(dataUrl: string): Promise<string> {
    await saveResumePhoto(dataUrl)
    photo.value = dataUrl || ''
    photoLoaded.value = true
    return photo.value
  }

  /** 保存/删除头像，空字符串表示恢复默认头像 */
  async function updateAvatar(avatar: string): Promise<string> {
    await saveUserAvatar(avatar)
    if (userInfo.value) userInfo.value = { ...userInfo.value, avatar: avatar || '' }
    return avatar || ''
  }

  function logout(): void {
    token.value = ''
    userInfo.value = null
    photo.value = ''
    photoLoaded.value = false
    removeToken()
  }

  return {
    token,
    userInfo,
    photo,
    photoLoaded,
    login,
    fetchProfile,
    fetchPhoto,
    updatePhoto,
    updateAvatar,
    logout
  }
})