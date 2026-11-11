/**
 * 关键词高亮：返回 [{ text, hit }] 片段，由模板渲染 <mark>，不拼 HTML，避免 XSS。
 * 英文忽略大小写；关键词按长度倒序匹配，重叠区间自动合并。
 */
const escapeRegExp = (value) => value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')

/** 很短的英文词（RL、Go、K8s）必须独立成词，否则会命中 URL、Google 这类无关文本 */
function toPattern(word) {
  const escaped = escapeRegExp(word)
  if (/^[A-Za-z0-9+#.]+$/.test(word) && word.length <= 3) {
    return `(?<![A-Za-z0-9])${escaped}(?![A-Za-z0-9])`
  }
  return escaped
}

export function highlightSegments(text, keywords) {
  const source = String(text || '')
  const words = [...new Set((keywords || []).map((word) => String(word || '').trim()).filter(Boolean))]
    .sort((a, b) => b.length - a.length)

  if (!source || !words.length) {
    return [{ text: source, hit: false }]
  }

  let regexp
  try {
    regexp = new RegExp(words.map(toPattern).join('|'), 'gi')
  } catch (e) {
    return [{ text: source, hit: false }]
  }

  const ranges = []
  let match
  while ((match = regexp.exec(source)) !== null) {
    if (!match[0]) {
      regexp.lastIndex += 1
      continue
    }
    ranges.push([match.index, match.index + match[0].length])
  }
  if (!ranges.length) {
    return [{ text: source, hit: false }]
  }

  ranges.sort((a, b) => a[0] - b[0])
  const merged = []
  for (const [start, end] of ranges) {
    const last = merged[merged.length - 1]
    if (last && start <= last[1]) {
      last[1] = Math.max(last[1], end)
    } else {
      merged.push([start, end])
    }
  }

  const segments = []
  let cursor = 0
  for (const [start, end] of merged) {
    if (start > cursor) {
      segments.push({ text: source.slice(cursor, start), hit: false })
    }
    segments.push({ text: source.slice(start, end), hit: true })
    cursor = end
  }
  if (cursor < source.length) {
    segments.push({ text: source.slice(cursor), hit: false })
  }
  return segments
}