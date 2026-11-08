/**
 * v-reveal：滚动进入视口时轻微上浮淡入。
 * 用法：v-reveal 或 v-reveal="120"（延迟毫秒）。
 * 加 .repeat 修饰符（v-reveal.repeat）：每次进入视口都重新渐入，离开视口后复位。
 * 尊重 prefers-reduced-motion；不支持 IntersectionObserver 时直接显示。
 */
let observer = null
const repeatTargets = new WeakSet()

function getObserver() {
  if (observer) return observer
  observer = new IntersectionObserver(
    (entries) => {
      entries.forEach((entry) => {
        const repeat = repeatTargets.has(entry.target)
        if (entry.isIntersecting) {
          entry.target.classList.add('is-revealed')
          if (!repeat) observer.unobserve(entry.target)
        } else if (repeat) {
          entry.target.classList.remove('is-revealed')
        }
      })
    },
    { rootMargin: '0px 0px -8% 0px', threshold: 0.12 }
  )
  return observer
}

export default {
  mounted(el, binding) {
    if (typeof window === 'undefined') return
    const reduced =
      window.matchMedia && window.matchMedia('(prefers-reduced-motion: reduce)').matches
    const delay = typeof binding.value === 'number' ? binding.value : 0
    const repeat = !!binding.modifiers.repeat
    el.classList.add('reveal')
    if (delay > 0) el.style.animationDelay = `${delay}ms`
    if (repeat) repeatTargets.add(el)
    // 路由刚切换且元素已在首屏：直接显示，避免新页面内容再上浮一次
    const entering = window.__pageEnteringUntil && Date.now() < window.__pageEnteringUntil
    const rect = entering ? el.getBoundingClientRect() : null
    const inView = rect && rect.top < window.innerHeight && rect.bottom > 0
    if (reduced || !('IntersectionObserver' in window) || inView) {
      el.classList.add('is-revealed')
      if (inView) el.classList.add('is-instant')
      el.style.animationDelay = ''
      return
    }
    getObserver().observe(el)
  },
  unmounted(el) {
    repeatTargets.delete(el)
    if (observer) observer.unobserve(el)
  }
}
