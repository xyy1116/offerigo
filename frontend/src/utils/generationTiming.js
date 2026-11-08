const KEY_PREFIX = 'offergo_gen_duration_'
const MAX_SAMPLES = 5

/** 记录一次成功生成的真实耗时（秒），用于下一次展示参考范围。 */
export function recordGenerationDuration(kind, seconds) {
  if (!kind || !Number.isFinite(seconds) || seconds <= 0 || seconds > 300) return
  try {
    const raw = JSON.parse(localStorage.getItem(KEY_PREFIX + kind) || '[]')
    const list = Array.isArray(raw) ? raw : []
    list.push(Math.round(seconds * 10) / 10)
    while (list.length > MAX_SAMPLES) list.shift()
    localStorage.setItem(KEY_PREFIX + kind, JSON.stringify(list))
  } catch (e) {
    // 隐私模式/超限时忽略
  }
}

/** 读取同类生成最近几次的真实耗时统计；没有记录返回 null。 */
export function readGenerationStats(kind) {
  if (!kind) return null
  try {
    const raw = JSON.parse(localStorage.getItem(KEY_PREFIX + kind) || '[]')
    if (!Array.isArray(raw)) return null
    const values = raw.filter((v) => Number.isFinite(v) && v > 0)
    if (!values.length) return null
    const sorted = [...values].sort((a, b) => a - b)
    return {
      count: values.length,
      min: Math.round(sorted[0]),
      max: Math.round(sorted[sorted.length - 1]),
      last: Math.round(values[values.length - 1])
    }
  } catch (e) {
    return null
  }
}