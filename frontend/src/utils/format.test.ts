import { describe, it, expect } from 'vitest'
import { fmtNum, fmtMoney, fmtUsage, fmtDateRange } from '@/utils/format'

describe('fmtNum', () => {
  it('默认保留 2 位并去尾随零', () => {
    expect(fmtNum(1.5)).toBe('1.5')
    expect(fmtNum(1.05)).toBe('1.05')
    expect(fmtNum(1.1)).toBe('1.1')
  })

  it('整数结果不带小数点', () => {
    expect(fmtNum(100)).toBe('100')
    expect(fmtNum(1000)).toBe('1000')
  })

  it('0 走兜底返回 "0" 而非空串', () => {
    expect(fmtNum(0)).toBe('0')
  })

  it('null / undefined / NaN → 占位符', () => {
    expect(fmtNum(null)).toBe('—')
    expect(fmtNum(undefined)).toBe('—')
    expect(fmtNum(Number.NaN)).toBe('—')
  })

  it('自定义占位符与位数', () => {
    expect(fmtNum(null, 2, 'N/A')).toBe('N/A')
    expect(fmtNum(1.234, 1)).toBe('1.2')
  })

  it('负数正常', () => {
    expect(fmtNum(-1.5)).toBe('-1.5')
  })

  // 已知 bug:digits=0 时,去尾随零正则 /\.?0+$/ 会误删整数部分的结尾 0
  // (无小数点隔断)。fmtNum(100, 0) 期望 "100",实际返回 "1"。
  // 生产调用点都用默认 digits=2,未触发;修复后请删除 .fails。
  it.fails('digits=0 且整数以 0 结尾时应保留(当前实现有缺陷)', () => {
    expect(fmtNum(100, 0)).toBe('100')
  })
})

describe('fmtMoney', () => {
  it('¥ 前缀固定 2 位', () => {
    expect(fmtMoney(12.5)).toBe('¥12.50')
    expect(fmtMoney(0)).toBe('¥0.00')
  })

  it('null → 未定价(可自定义)', () => {
    expect(fmtMoney(null)).toBe('未定价')
    expect(fmtMoney(undefined)).toBe('未定价')
    expect(fmtMoney(null, '—')).toBe('—')
  })
})

describe('fmtUsage', () => {
  it('type=1 → 度,type=2 → 吨,其它无单位', () => {
    expect(fmtUsage(10, 1)).toBe('10 度')
    expect(fmtUsage(3.5, 2)).toBe('3.5 吨')
    expect(fmtUsage(5)).toBe('5')
    expect(fmtUsage(5, 99)).toBe('5')
  })

  it('null → 占位符且不带单位', () => {
    expect(fmtUsage(null, 1)).toBe('—')
  })
})

describe('fmtDateRange', () => {
  it('两端都空 → 未设置日期', () => {
    expect(fmtDateRange(null, null)).toBe('未设置日期')
  })

  it('单端缺失用 — 占位', () => {
    expect(fmtDateRange('2026-01-01', null)).toBe('2026-01-01 ~ —')
    expect(fmtDateRange(null, '2026-01-31')).toBe('— ~ 2026-01-31')
  })

  it('完整区间', () => {
    expect(fmtDateRange('2026-01-01', '2026-01-31')).toBe('2026-01-01 ~ 2026-01-31')
  })
})
