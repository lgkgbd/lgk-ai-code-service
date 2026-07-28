/**
 * 单词记忆功能的展示辅助函数
 */

/** 掌握阶段 → 文案 + 颜色（对齐后端 MasteryEnum 与原型配色） */
export const MASTERY_META: Record<number, { label: string; color: string; bg: string }> = {
  0: { label: '新词', color: '#722ed1', bg: '#f9f0ff' },
  1: { label: '学习中', color: '#d46b08', bg: '#fff7e6' },
  2: { label: '复习中', color: '#1890ff', bg: '#e6f7ff' },
  3: { label: '已掌握', color: '#52c41a', bg: '#f6ffed' },
}

/** 掌握度分段（我的词库筛选用） */
export const MASTERY_SEGMENTS = [
  { value: undefined as number | undefined, label: '全部' },
  { value: 0, label: '新词' },
  { value: 1, label: '学习中' },
  { value: 2, label: '复习中' },
  { value: 3, label: '已掌握' },
]

/** 复习作答四档（对齐后端 ReviewQualityEnum，含键位与预计间隔文案） */
export const REVIEW_GRADES = [
  { quality: 0, key: '1', title: '完全忘记', sub: '今天再来', cls: 'g0' },
  { quality: 1, key: '2', title: '有点模糊', sub: '间隔退一级', cls: 'g1' },
  { quality: 2, key: '3', title: '想起来了', sub: '间隔进一级', cls: 'g2' },
  { quality: 3, key: '4', title: '秒答', sub: '间隔进两级', cls: 'g3' },
]

/**
 * 考试标签（如 "gk cet4 cet6 ky toefl ielts gre"）→ 最值得展示的一个中文标签。
 * 全空时返回空串，调用方据此整块不渲染。
 */
export function examLabel(tag?: string | null): string {
  if (!tag) return ''
  const map: Array<[string, string]> = [
    ['cet6', '六级'],
    ['cet4', '四级'],
    ['ky', '考研'],
    ['toefl', '托福'],
    ['ielts', '雅思'],
    ['gre', 'GRE'],
    ['gk', '高考'],
    ['zk', '中考'],
  ]
  const lower = tag.toLowerCase()
  for (const [k, v] of map) {
    if (lower.includes(k)) return v
  }
  return ''
}

/** 相对时间：刚刚 / N 分钟前 / N 小时前 / N 天前 / 具体日期 */
export function relativeTime(iso?: string | null): string {
  if (!iso) return ''
  const t = new Date(iso.replace(' ', 'T')).getTime()
  if (Number.isNaN(t)) return ''
  const diff = Date.now() - t
  const min = 60_000
  const hour = 60 * min
  const day = 24 * hour
  if (diff < min) return '刚刚'
  if (diff < hour) return `${Math.floor(diff / min)} 分钟前`
  if (diff < day) return `${Math.floor(diff / hour)} 小时前`
  if (diff < 7 * day) return `${Math.floor(diff / day)} 天前`
  return formatDate(iso)
}

/** 下次复习时间的友好文案：已到期 / 今天 / 明天 / N 天后 / 日期 */
export function dueLabel(iso?: string | null): string {
  if (!iso) return ''
  const due = new Date(iso.replace(' ', 'T'))
  if (Number.isNaN(due.getTime())) return ''
  const startOfToday = new Date()
  startOfToday.setHours(0, 0, 0, 0)
  const dueDay = new Date(due)
  dueDay.setHours(0, 0, 0, 0)
  const dayDiff = Math.round((dueDay.getTime() - startOfToday.getTime()) / (24 * 3600_000))
  if (dayDiff <= 0) return '待复习'
  if (dayDiff === 1) return '明天'
  if (dayDiff < 30) return `${dayDiff} 天后`
  return formatDate(iso)
}

/** yyyy-MM-dd */
export function formatDate(iso?: string | null): string {
  if (!iso) return ''
  const d = new Date(iso.replace(' ', 'T'))
  if (Number.isNaN(d.getTime())) return ''
  const p = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}`
}

/** 中文释义原始文本按行拆分（后端 translation 用 \n 分隔多个义项） */
export function splitTranslation(translation?: string | null): string[] {
  if (!translation) return []
  return translation
    .split(/\r?\n|\\n/)
    .map((s) => s.trim())
    .filter(Boolean)
}

/**
 * 把原句里的目标词高亮为片段数组，供模板渲染 <em>。大小写不敏感、全词匹配。
 * 返回 [{ text, hit }]，hit=true 的片段用主色加粗。
 */
export function highlightWord(
  sentence?: string | null,
  word?: string | null,
): Array<{ text: string; hit: boolean }> {
  if (!sentence) return []
  if (!word) return [{ text: sentence, hit: false }]
  const re = new RegExp(`\\b(${word.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')})\\b`, 'ig')
  const parts: Array<{ text: string; hit: boolean }> = []
  let last = 0
  let m: RegExpExecArray | null
  while ((m = re.exec(sentence)) !== null) {
    if (m.index > last) parts.push({ text: sentence.slice(last, m.index), hit: false })
    parts.push({ text: m[0], hit: true })
    last = m.index + m[0].length
    if (m.index === re.lastIndex) re.lastIndex++
  }
  if (last < sentence.length) parts.push({ text: sentence.slice(last), hit: false })
  return parts
}
