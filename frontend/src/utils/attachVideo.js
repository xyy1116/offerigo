/**
 * 将 MediaStream 绑定到 video 并确保自动播放。
 * 显式 muted + play，规避浏览器自动播放策略；失败时再兜底一次。
 */
export function attachVideo(el, stream) {
  if (!el || !stream) return
  el.srcObject = stream
  el.muted = true
  const p = el.play()
  if (p && typeof p.catch === 'function') {
    p.catch(() => {
      el.muted = true
      el.play().catch(() => {})
    })
  }
}