/**
 * 岗位要求的「覆盖判定」算法：判断某条简历素材是否覆盖某条岗位要求。
 * 供 jobMatchEngine 的 evaluateJob 使用，是岗位匹配分数的唯一判定来源。
 */

/** 不参与匹配的通用词（中文 bigram）。这些词几乎出现在所有岗位与素材里，命中没有区分度。 */
const CN_STOP_BIGRAMS = new Set([
  '以上', '以下', '以及', '并且', '同时', '或者', '其中', '通过', '进行', '完成', '实现',
  '能够', '具有', '具备', '熟悉', '熟练', '掌握', '了解', '精通', '优先', '相关', '要求',
  '负责', '参与', '工作', '经验', '能力', '良好', '较强', '一定', '加分', '任职', '岗位',
  '职责', '公司', '团队', '业务', '系统', '平台', '开发', '设计', '实现', '支持', '包括',
  '持续', '推动', '落地', '使用', '基础', '知识', '技术', '项目', '产品', '需求', '功能',
  '服务', '应用', '方案', '流程', '质量', '效率', '问题', '内容', '方向', '领域', '人员',
])

/** 英文停用词（技术词之外的常见英文）。 */
const LATIN_STOP = new Set([
  'and', 'or', 'the', 'for', 'with', 'from', 'this', 'that', 'you', 'are', 'our', 'all',
  'etc', 'eg', 'ie', 'to', 'in', 'on', 'of', 'is', 'be', 'as', 'at', 'by', 'we', 'it',
])

/** 英文/技术词：字母开头，允许 + # . / - 等常见技术字符（如 C++、Vue.js、Node.js、CI/CD）。 */
const LATIN_RE = /[A-Za-z][A-Za-z0-9+#./-]{1,24}/g
/** 中文连续片段（用于拆短语 / bigram）。 */
const CN_RUN_RE = /[\u4e00-\u9fa5]+/g

function toText(v) {
  return v == null ? '' : String(v)
}

/** 抽取英文/技术词（小写、去重、去掉尾部多余的点/斜杠；保留 C++/C# 的技术符号）。 */
function latinTokens(text) {
  const out = new Set()
  const s = toText(text)
  const matches = s.match(LATIN_RE) || []
  for (const raw of matches) {
    // 「C++/Java」按斜杠拆开，两边都算命中
    for (const part of raw.toLowerCase().split('/')) {
      const w = part.replace(/[.-]+$/, '')
      if (w.length >= 2 && !LATIN_STOP.has(w)) out.add(w)
    }
  }
  return out
}

/** 抽取中文 bigram（过滤通用词），用于弱相关召回。 */
function cnBigrams(text) {
  const out = new Set()
  const runs = toText(text).match(CN_RUN_RE) || []
  for (const run of runs) {
    for (let i = 0; i + 1 < run.length; i++) {
      const g = run.slice(i, i + 2)
      if (!CN_STOP_BIGRAMS.has(g)) out.add(g)
    }
  }
  return out
}

/**
 * 从技术栈/核心要求里拆出「可高亮的中文短语」。
 * 技术栈字段通常是「大模型、强化学习、AI安全、对抗攻击与防御」这种顿号/逗号分隔的短语，
 * 整短语命中比 bigram 命中更可解释，也更适合高亮。
 */
function cnPhrases(text) {
  const out = new Set()
  const s = toText(text)
  // 先按分隔符切，再按「与/和/及/或」切，得到短语级关键词
  const parts = s.split(/[、,，;；/|｜\n\r\t（）()[\]【】]+/)
  for (const part of parts) {
    let p = part.trim()
    if (!p) continue
    p = p.replace(/^(熟悉|熟练|掌握|了解|精通|具备|具有|要求|参与|负责|基于|使用)+/, '')
    p = p.replace(/(等|等等|相关|优先|以上|以下|者)+$/, '')
    if (p.length < 2 || p.length > 12) continue
    if (!/[\u4e00-\u9fa5]/.test(p)) continue
    out.add(p)
    // 「对抗攻击与防御」这类复合短语，再拆出子短语以提升召回
    const subs = p.split(/[与和及或]/)
    if (subs.length > 1) {
      for (const sub of subs) {
        const t = sub.trim()
        if (t.length >= 2 && t.length <= 12) out.add(t)
      }
    }
  }
  return out
}

/** 具体资格信号：出现即可作为「覆盖」证据（英语 / 本科 / CISP…）。 */
const SPECIFIC_SIGNALS = ['英语', '普通话', '日语', '韩语', '法语', '德语', '四级', '六级', '雅思', '托福',
  '本科', '硕士', '博士', '大专', '专科', '学位', '统招', '双一流', '985', '211',
  'cisp', 'cissp', 'ccsk', 'pmp', 'cka', 'cpa']
/** 泛化资格词：单独命中不算覆盖，只在已命中具体信号时作为佐证。 */
const GENERIC_SIGNALS = ['学历', '专业', '证书', '认证', '资质', '资格', '语言']

/** 中文数字 → 数值（只处理 1-10，足够覆盖「五年以上经验」这类表述）。 */
const CN_NUM = { 一: 1, 二: 2, 三: 3, 四: 4, 五: 5, 六: 6, 七: 7, 八: 8, 九: 9, 十: 10 }

/** 把「N年」里的 N 转成数字；4 位年份（如 2020 年毕业）不当作经验年限。 */
function toYears(token) {
  if (/^\d+$/.test(token)) {
    const n = Number(token)
    return n >= 1900 ? null : n
  }
  return CN_NUM[token] ?? null
}

/** 从岗位要求里解析需要的工作年限，只认「N年(以上)…经验」类表述。 */
function parseRequiredYears(text) {
  const s = String(text || '')
  const m = s.match(/(\d{1,2}|[一二三四五六七八九十])\s*年\s*(?:及)?以上(?:的)?(?:相关)?(?:工作|开发|从业)?经验/)
    || s.match(/(\d{1,2}|[一二三四五六七八九十])\s*年(?:以上)?(?:的)?(?:相关)?(?:工作|开发|从业)经验/)
    || s.match(/工作(?:经验|年限)\s*[:：]?\s*(\d{1,2}|[一二三四五六七八九十])\s*年/)
  return m ? toYears(m[1]) : null
}

/** 从简历素材里解析已有工作年限，必须带「经验/工作年限」语境，避免误取毕业年份。 */
function parseHaveYears(text) {
  const s = String(text || '')
  const patterns = [
    /(\d{1,2}|[一二三四五六七八九十])\s*年(?:以上)?(?:的)?(?:相关)?(?:工作|开发|从业|项目)?经验/,
    /工作年限\s*[:：]?\s*(\d{1,2}|[一二三四五六七八九十])\s*年/,
    /(\d{1,2})\s*年(?:工作|开发|从业)/
  ]
  for (const p of patterns) {
    const m = s.match(p)
    if (!m) continue
    const n = toYears(m[1])
    if (n != null) return n
  }
  return null
}

/**
 * 经验年限要求是否被满足：要求「N 年以上经验」时，素材里写明的工作年限 ≥ N 才算覆盖。
 * （纯关键词匹配会把「五年以上经验」判成不匹配，这里单独按数值判断）
 */
function experienceRequirementCovered(req, text) {
  if (!/(经验|经历|年限|从业)/.test(req)) return false
  const need = parseRequiredYears(req)
  if (need == null) return false
  const have = parseHaveYears(text)
  return have != null && have >= need
}

/**
 * 一条「岗位要求」是否被某条素材覆盖。
 *
 * 判定规则（宁可漏判也不把通用词当成匹配）：
 * - 英文/技术词：长度 ≥ 3 的技术词命中即算（Java、Vue、MySQL…）；两字缩写（AI/Go/R）需再有一条佐证；
 * - 资格信号：命中「具体」信号（英语、本科、CISP…）才算；「语言/证书/学历」这类泛化词单独命中不算；
 * - 中文短语：≥3 字的岗位要求短语被素材原文包含才算；
 * - 中文 bigram：至少 2 个重叠且其中不全是泛化词。
 */
export function requirementCovered(requirement, entryText) {
  const req = toText(requirement)
  const text = toText(entryText)
  if (!req.trim() || !text.trim()) return false
  const lowerText = text.toLowerCase()
  const lowerReq = req.toLowerCase()

  // 0. 经验年限：按数值比较（「五年以上经验」vs「工作年限6年」）
  if (experienceRequirementCovered(req, text)) return true

  // 1. 英文/技术词
  const reqLatin = latinTokens(req)
  if (reqLatin.size) {
    const entryLatin = latinTokens(text)
    let strong = 0
    for (const w of reqLatin) {
      if (!entryLatin.has(w)) continue
      if (w.length >= 3) strong++
    }
    if (strong >= 1) return true
  }

  // 2. 资格信号：必须有具体信号
  let specific = 0
  let generic = 0
  for (const s of SPECIFIC_SIGNALS) {
    if (lowerReq.includes(s) && lowerText.includes(s)) specific++
  }
  for (const s of GENERIC_SIGNALS) {
    if (lowerReq.includes(s) && lowerText.includes(s)) generic++
  }
  if (specific >= 1) return true

  // 3. 中文短语直接包含（「大模型训练数据合成」这类具体要求条目）
  for (const p of cnPhrases(req)) {
    if (p.length >= 3 && text.includes(p)) return true
  }

  // 4. 中文 bigram 重叠：至少 3 个（仅靠零散双字词不足以判定覆盖）
  const reqBigrams = cnBigrams(req)
  if (reqBigrams.size) {
    const entryBigrams = cnBigrams(text)
    let hits = 0
    for (const g of reqBigrams) {
      if (entryBigrams.has(g)) {
        hits++
        if (hits >= 3) return true
      }
    }
  }
  return false
}
