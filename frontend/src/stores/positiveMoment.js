import { defineStore } from 'pinia'

let seq = 0

/**
 * 正向成长提示：只负责「什么时候提示」与提示内容的生命周期，不定义样式。
 * 渲染层（PositiveMomentLayer）决定外观；使用者可按需替换样式方案。
 * type 约定：score_up / applied / material_added / info_completed / custom
 */
export const usePositiveMomentStore = defineStore('positiveMoment', {
  state: () => ({
    moments: []
  }),
  actions: {
    /**
     * @param {{ type?: string, title?: string, message?: string, duration?: number }} payload
     * @returns {number|null} 提示 id
     */
    trigger(payload) {
      if (!payload || (!payload.title && !payload.message)) return null
      const id = ++seq
      const moment = {
        id,
        type: payload.type || 'custom',
        title: payload.title || '',
        message: payload.message || '',
        duration: payload.duration || 3200
      }
      // 最多同时保留 3 条，超出淘汰最早的，避免刷屏
      this.moments = [...this.moments, moment].slice(-3)
      setTimeout(() => this.dismiss(id), moment.duration)
      return id
    },
    dismiss(id) {
      this.moments = this.moments.filter((m) => m.id !== id)
    }
  }
})
