const KEY_PREFIX = 'offergo_gen_duration_'
const MAX_SAMPLES = 5

export interface GenerationStats {
  count: number
  min: number
  max: number
  last: number
}

/** 记录一次成功生成的真实耗时（秒），用于下一次展示参考范围。 */
export function recordGenerationDuration(kind: string, seconds: number): void {
  if (!kind || !Number.isFinite(seconds) || seconds <= 0 || seconds > 300) return
  try {
    const raw: unknown = JSON.parse(localStorage.getItem(KEY_PREFIX + kind) || '[]')
    const list: number[] = Array.isArray(raw) ? (raw as number[]) : []
    list.push(Math.round(seconds * 10) / 10)
    while (list.length > MAX_SAMPLES) list.shift()
    localStorage.setItem(KEY_PREFIX + kind, JSON.stringify(list))
  } catch {
    // 隐私模式或存储超限时忽略
  }
}

/** 读取同类生成最近几次的真实耗时统计；没有记录返回 null。 */
export function readGenerationStats(kind: string): GenerationStats | null {
  if (!kind) return null
  try {
    const raw: unknown = JSON.parse(localStorage.getItem(KEY_PREFIX + kind) || '[]')
    if (!Array.isArray(raw)) return null
    const values = (raw as number[]).filter((v) => Number.isFinite(v) && v > 0)
    if (!values.length) return null
    const sorted = [...values].sort((a, b) => a - b)
    return {
      count: values.length,
      min: Math.round(sorted[0]),
      max: Math.round(sorted[sorted.length - 1]),
      last: Math.round(values[values.length - 1])
    }
  } catch {
    return null
  }
}