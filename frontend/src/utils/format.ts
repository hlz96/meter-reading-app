// 格式化工具(前端 TRD §2)。金额/用量后端为 BigDecimal → JSON number。

/** 数字保留 N 位小数(去尾随零),null/undefined → 占位符。 */
export function fmtNum(v: number | null | undefined, digits = 2, placeholder = '—'): string {
  if (v === null || v === undefined || Number.isNaN(v)) return placeholder
  const fixed = Number(v).toFixed(digits)
  return fixed.replace(/\.?0+$/, '') || '0'
}

/** 金额:¥ 前缀,保留 2 位;null → 「未定价」。 */
export function fmtMoney(v: number | null | undefined, unpriced = '未定价'): string {
  if (v === null || v === undefined) return unpriced
  return `¥${Number(v).toFixed(2)}`
}

/** 用量:保留 2 位 + 单位(度/吨)。 */
export function fmtUsage(v: number | null | undefined, type?: number): string {
  const num = fmtNum(v, 2)
  if (num === '—') return num
  const unit = type === 1 ? ' 度' : type === 2 ? ' 吨' : ''
  return `${num}${unit}`
}

/** 日期区间显示。 */
export function fmtDateRange(start: string | null, end: string | null): string {
  if (!start && !end) return '未设置日期'
  return `${start || '—'} ~ ${end || '—'}`
}
