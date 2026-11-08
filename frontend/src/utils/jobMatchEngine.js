/**
 * 岗位匹配确定性引擎（前端实时评估）。
 *
 * 设计目标（产业级）：
 * 1. 单一事实来源：岗位的「要求清单」+ 当前我的简历素材 → 覆盖率 → 分数 / 维度 / 待补足 / 文案。
 *    同一份输入永远得到同一份输出，可解释、可测试、无随机波动。
 * 2. 实时：素材增删改后浏览器本地即时重算（毫秒级），不需要等待大模型。
 * 3. 大模型只负责「岗位要求清单」的抽取（JD 解析），不再参与每次打分，因此素材变化零等待。
 * 4. 「预计加分」= 该要求被覆盖后引擎重算的真实增量，天然满足「预计 = 实际」。
 */
import { requirementCovered } from './materialMatch.js'

/** 维度归一：把模型/JD 里五花八门的维度名收敛到 6 类。 */
function normalizeDim(dim) {
  const d = String(dim || '')
  if (/技能|技术|工具|框架|语言/.test(d)) return '技能'
  if (/项目|实践|落地|作品/.test(d)) return '项目'
  if (/经历|经验|年限|工作/.test(d)) return '经历'
  if (/学历|院校|教育|专业|学位/.test(d)) return '学历'
  if (/证书|认证|资质/.test(d)) return '证书'
  return '其他'
}

/** 维度 → 评分四维（技能40 / 经历30 / 学历证书15 / 契合15）。 */
const DIM_TO_SCORE = { 技能: 'skill', 项目: 'experience', 经历: 'experience', 学历: 'education', 证书: 'education', 其他: 'fit' }
const SCORE_WEIGHT = { skill: 0.4, experience: 0.3, education: 0.15, fit: 0.15 }

/** 非「能力要求」的行（地点/薪资/福利/免责说明等）不参与匹配打分。 */
const NON_REQUIREMENT = /^(工作地点|薪资|薪资范围|福利|招聘人数|公司|公司地址|邮箱|联系方式|投递方式|面议)|未提及|无明确|不限制|不限|非真实招聘|学习指南/i

/** 从 JD 文本兜底抽取要求清单（大模型结果不可用时使用）。 */
function catalogFromJd(job) {
  const text = [job.jobName, job.jobRequirement, job.techStack].filter(Boolean).join('\n')
  const items = []
  const seen = new Set()
  const push = (raw, fallbackDim) => {
    let t = String(raw || '')
      .replace(/^[\s\-•·*·\d.、）)（(]+/, '')
      .replace(/^(岗位职责|任职要求|技能要求|工作内容|加分项|补充信息)\s*[:：]\s*/, '')
      .trim()
    if (t.length < 4 || t.length > 120) return
    if (/^(面议|未提及|无)$/.test(t)) return
    if (NON_REQUIREMENT.test(t)) return
    const key = t.slice(0, 24)
    if (seen.has(key)) return
    seen.add(key)
    items.push({ requirement: t, dimension: normalizeDim(inferDim(t) || fallbackDim) })
  }
  for (const line of String(job.jobRequirement || '').split(/\n+/)) {
    for (const seg of splitRequirementLine(line)) push(seg)
  }
  for (const tech of String(job.techStack || '').split(/[、,，;；/|]+/)) {
    const t = tech.trim()
    if (t.length >= 2 && t.length <= 24) push(t, '技能')
  }
  // 每个维度最多保留 6 条，总体最多 18 条，避免要求清单无限膨胀
  const byDim = {}
  const out = []
  for (const it of items) {
    byDim[it.dimension] = (byDim[it.dimension] || 0) + 1
    if (byDim[it.dimension] > 6) continue
    out.push(it)
  }
  return out.slice(0, 18)
}

/**
 * JD 兜底拆分：先按句号/分号切句，句子过长再按逗号切分句。
 * 推荐岗位没有大模型抽取的要求清单，只能走这里；整段当作一条会让「命中一个词 = 整条覆盖」，分数虚高。
 */
function splitRequirementLine(line) {
  const out = []
  for (const seg of String(line || '').split(/[；;。]\s*/)) {
    const text = seg.trim()
    if (!text) continue
    if (text.length <= 40) {
      out.push(text)
      continue
    }
    const parts = text.split(/[，,]\s*/).map((s) => s.trim()).filter((s) => s.length >= 6)
    out.push(...(parts.length ? parts : [text]))
  }
  return out
}

function inferDim(text) {
  const t = String(text)
  if (/学历|本科|硕士|博士|大专|学位|院校|专业|毕业/.test(t)) return '学历'
  if (/证书|认证|资格|CISP|CISSP|CCSK|CKA|PMP/.test(t)) return '证书'
  if (/年.{0,4}经验|工作年限|从业|任职年限|应届/.test(t)) return '经历'
  if (/项目|平台|系统|落地|作品|实践/.test(t)) return '项目'
  if (/技能|技术|语言|框架|工具|数据库|算法|开发/.test(t)) return '技能'
  return null
}

/**
 * 构建岗位要求清单：大模型 details 与 JD 文本兜底取「更完整」的一方。
 * 关键：大模型 details 会随知识库变化而缩水（把不再匹配的要求移到 gaps 或直接丢弃），
 * 若只信 details，删素材后岗位要求清单会变短、分数被误算回满分。JD 文本兜底是确定性的，
 * 作为下限始终参与比较，保证目录稳定。
 */
export function buildCatalog(job) {
  const details = (job && job.match && Array.isArray(job.match.details)) ? job.match.details : []
  const items = []
  const seen = new Set()
  for (const d of details) {
    const req = String((d && d.requirement) || '').trim()
    if (req.length < 4) continue
    if (NON_REQUIREMENT.test(req)) continue
    const key = req.slice(0, 24)
    if (seen.has(key)) continue
    seen.add(key)
    items.push({ requirement: req, dimension: normalizeDim(d.dimension) })
  }
  const fallback = catalogFromJd(job || {})
  // 大模型抽出的清单本身已是细项，优先使用；只有清单缺失/过短时才回退到 JD 文本。
  // 否则细拆后的 JD 兜底会因条数更多而覆盖掉更准确的大模型清单。
  const chosen = items.length >= 3 ? items : (fallback.length > items.length ? fallback : items)
  return (chosen.length ? chosen : fallback).slice(0, 18)
}

/** 单条要求在素材库里的最佳证据（返回命中的素材列表，按内容长度取前 2 条用于展示）。 */
function evidenceFor(requirement, entries) {
  const hit = []
  for (const e of entries) {
    if (!e || !e.content) continue
    if (requirementCovered(requirement, e.content)) hit.push(e)
  }
  return hit.slice(0, 2)
}

function round1(v) {
  return Math.round(v * 10) / 10
}

/**
 * 用当前素材库评估岗位。
 * @param {Array}  catalog  岗位要求清单
 * @param {Array}  entries  我的简历素材
 * @param {object} [weights] 可选维度权重（比例，如 { skill:0.4, experience:0.3, education:0.15, fit:0.15 }），
 *                           缺省时使用内置 SCORE_WEIGHT。总分按「存在要求的维度」自归一化，权重缩放不改变结果。
 * @param {object} [closedGaps] 明确已补齐的待补足项映射：{ 要求原文 -> 素材id }。
 *                           快速补充是用户明确针对某个待补足项的操作，即便 LLM 改写后关键词对不上，也应判定为已覆盖。
 * @returns {{score:number, skillScore:number, experienceScore:number, educationScore:number, fitScore:number,
 *   qualified:boolean, level:string, details:Array, gaps:string[], hardRequirementMisses:string[],
 *   summary:string, advantages:string, disadvantages:string, optimization:string}}
 */
export function evaluateJob(catalog, entries, weights, closedGaps) {
  const W = weights || SCORE_WEIGHT
  const list = Array.isArray(catalog) ? catalog : []
  const pool = Array.isArray(entries) ? entries : []
  const poolEmpty = !pool.length

  // 逐条要求判定是否被素材覆盖，并归入评分维度
  const rows = list.map((it) => {
    let ev = evidenceFor(it.requirement, pool)
    // 明确补齐的待补足项：素材仍在库中即视为已覆盖（覆盖关键词匹配的改写盲区）
    const forcedId = closedGaps && closedGaps[it.requirement]
    if (!ev.length && forcedId != null) {
      const forcedEntry = pool.find((e) => e && e.id === forcedId)
      if (forcedEntry) ev = [forcedEntry]
    }
    const field = DIM_TO_SCORE[it.dimension] || 'fit'
    return { ...it, field, evidence: ev, matched: ev.length > 0 }
  })

  // 固定分值分配：把 100 分按「维度权重 ÷ 该维度要求数」落到每条要求（整数、Σ=100）。
  // 分数因此线性可加：补一条要求 = 加该条固定分值，天然满足
  // ① Σ待补足加分 + 当前分 ≡ 100；② 每条加分 = 实际涨分；③ 全部补齐 → 100。
  const points = allocatePoints(rows, W)

  // 总分 = 已覆盖要求的分值之和
  const score = rows.reduce((sum, r, i) => sum + (r.matched ? points[i] : 0), 0)

  // 各维度子分（该维度覆盖率百分比，仅作方向性展示）
  const dims = { skill: [], experience: [], education: [], fit: [] }
  for (const r of rows) dims[r.field].push(r)
  const dimScore = (key) => {
    const arr = dims[key]
    if (!arr.length) return null
    return Math.round((arr.filter((r) => r.matched).length / arr.length) * 100)
  }
  const skillScore = dimScore('skill')
  const experienceScore = dimScore('experience')
  const educationScore = dimScore('education')
  const fitScore = dimScore('fit')

  const anyCovered = rows.some((r) => r.matched)
  const qualified = rows.length > 0 && anyCovered && score >= 40

  // 每条要求的固定分值（points）= 其 quota：
  // 已覆盖时是它对总分的贡献，未覆盖时是补齐后的加分，二者数值一致。
  const details = rows.map((r, i) => ({
    dimension: r.dimension,
    requirement: r.requirement,
    resumeContent: r.matched ? (r.evidence[0] && r.evidence[0].content) || '' : '',
    matched: r.matched,
    matchType: r.matched ? '简历素材命中' : '',
    missReason: r.matched ? '' : (poolEmpty ? '我的简历素材为空' : '我的简历素材中暂无对应内容'),
    quota: points[i],
    evidenceIds: r.evidence.map((e) => e.id).filter((id) => id != null),
  }))

  const gapRows = rows.filter((r) => !r.matched)
  const gaps = gapRows.map((r) => r.requirement)
  const coveredTop = rows.filter((r) => r.matched).slice(0, 3).map((r) => r.requirement)
  const gapTop = gapRows.slice(0, 2).map((r) => r.requirement)
  const coverage = rows.length ? Math.round((rows.filter((r) => r.matched).length / rows.length) * 100) : 0

  const hardMisses = gapRows
    .filter((r) => /学历|本科|硕士|博士|证书|认证|年限|年.{0,3}经验/.test(r.requirement))
    .map((r) => r.requirement)

  const summary = poolEmpty
    ? '我的简历素材为空，请先添加素材后再评估'
    : (rows.length ? `共 ${rows.length} 项岗位要求，已用素材覆盖 ${rows.length - gapRows.length} 项（覆盖率 ${coverage}%）` : '暂无可评估的岗位要求')
  const advantages = poolEmpty ? '暂无匹配素材' : (coveredTop.length ? `已匹配：${coveredTop.join('；')}` : '暂无明显匹配素材')
  const disadvantages = poolEmpty ? '全部岗位要求均无素材支撑' : (gapTop.length ? `主要缺口：${gapTop.join('；')}` : '暂无缺口')
  const optimization = poolEmpty
    ? '先到右侧「我的简历素材」导入简历或新增素材，系统会即时给出匹配度'
    : (gapTop.length ? `优先补足「${gapTop[0]}」，可用「快速补充」直接生成素材草稿` : '素材已覆盖全部要求，可继续优化项目量化成果')

  const level = !qualified ? '不合格' : score >= 90 ? 'S' : score >= 80 ? 'A' : score >= 70 ? 'B' : 'C'

  return {
    score,
    skillScore,
    experienceScore,
    educationScore,
    fitScore,
    qualified,
    level,
    details,
    gaps,
    hardRequirementMisses: hardMisses,
    summary,
    advantages,
    disadvantages,
    optimization,
    coverage,
    catalogSize: rows.length,
  }
}

/** 把 100 分按「维度权重 ÷ 该维度要求数」固定分配到每条要求，最大余数法整数化，保证 Σ=100。
 *  固定分值使分数线性可加：补一条要求 = 加该条分值，因此 Σ待补足加分 + 当前分 ≡ 100。 */
function allocatePoints(rows, W) {
  const n = rows.length
  if (!n) return []
  const dimTotal = {}
  for (const r of rows) dimTotal[r.field] = (dimTotal[r.field] || 0) + 1
  const frac = rows.map((r) => (W[r.field] || 0) / (dimTotal[r.field] || 1))
  const total = frac.reduce((s, f) => s + f, 0)
  if (total <= 0) return rows.map(() => 0)
  const scaled = frac.map((f) => (f / total) * 100)
  const points = scaled.map((s) => Math.floor(s))
  let assigned = points.reduce((s, p) => s + p, 0)
  // 最大余数法补足到 100（余数降序 → 下标升序，保证结果确定性）
  const order = scaled
    .map((_, i) => i)
    .sort((a, b) => (scaled[b] - points[b]) - (scaled[a] - points[a]) || a - b)
  let k = 0
  while (assigned < 100 && k < order.length) { points[order[k]]++; assigned++; k++ }
  return points
}

/** 数值格式化（保留 1 位小数，供 UI 复用）。 */
export function fmtScore(v) {
  const n = Number(v)
  return Number.isFinite(n) ? round1(n) : 0
}
