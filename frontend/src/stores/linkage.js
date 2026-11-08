import { defineStore } from 'pinia'

/**
 * 跨模块数据联动（数据孤岛打通）的统一下文传递 store。
 * 各模块跳转前通过 setContext 写入数据，目标模块 onMounted 通过 consume 一次性读取并清空，
 * 统一承载：岗位名称 / JD 原文 / 简历原文 / 薄弱知识点 / 改进建议 等跨模块数据。
 */
export const useLinkageStore = defineStore('linkage', {
  state: () => ({
    from: '',
    jobTitle: '',
    jdText: '',
    techStack: '',
    resumeText: '',
    knowledgePoints: '',
    suggestions: []
  }),
  actions: {
    setContext(payload) {
      this.from = payload?.from || ''
      this.jobTitle = payload?.jobTitle || ''
      this.jdText = payload?.jdText || ''
      this.techStack = payload?.techStack || ''
      this.resumeText = payload?.resumeText || ''
      this.knowledgePoints = payload?.knowledgePoints || ''
      this.suggestions = Array.isArray(payload?.suggestions) ? [...payload.suggestions] : []
    },
    // 读取并清空，避免跨页面跳转后残留
    consume() {
      const ctx = {
        from: this.from,
        jobTitle: this.jobTitle,
        jdText: this.jdText,
        techStack: this.techStack,
        resumeText: this.resumeText,
        knowledgePoints: this.knowledgePoints,
        suggestions: [...this.suggestions]
      }
      this.$reset()
      return ctx
    }
  }
})