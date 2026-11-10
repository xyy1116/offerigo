import { defineStore } from 'pinia'

export type MomentType = 'score_up' | 'applied' | 'material_added' | 'info_completed' | 'custom'

export interface MomentPayload {
  type?: MomentType | string
  title?: string
  message?: string
  duration?: number
}

export interface Moment {
  id: number
  type: string
  title: string
  message: string
  duration: number
}

let seq = 0

/**
 * 正向成长提示：只负责「什么时候提示」与提示内容的生命周期，不定义样式。
 * 渲染层（PositiveMomentLayer）决定外观；使用者可按需替换样式方案。
 */
export const usePositiveMomentStore = defineStore('positiveMoment', {
  state: (): { moments: Moment[] } => ({
    moments: []
  }),
  actions: {
    /** 触发一条提示，返回提示 id；内容为空时不提示 */
    trigger(payload: MomentPayload): number | null {
      if (!payload || (!payload.title && !payload.message)) return null
      const id = ++seq
      const moment: Moment = {
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
    dismiss(id: number): void {
      this.moments = this.moments.filter((m) => m.id !== id)
    }
  }
})