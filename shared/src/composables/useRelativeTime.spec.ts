import { describe, expect, it } from 'vitest'

import {
  formatAbsoluteTime,
  formatRelativeTime,
  formatShortDateTime,
} from '@shared/composables/useRelativeTime'

/** 固定「当前时刻」避免用例随真实时间漂移：2026-10-06 12:00 本地时间 */
const NOW = new Date(2026, 9, 6, 12, 0, 0)

function ago(ms: number): string {
  return new Date(NOW.getTime() - ms).toISOString()
}

describe('formatRelativeTime', () => {
  it('一分钟内为「刚刚」', () => {
    expect(formatRelativeTime(ago(30_000), NOW)).toBe('刚刚')
  })

  it('一小时以上用「N 分钟前」', () => {
    expect(formatRelativeTime(ago(3 * 60_000), NOW)).toBe('3 分钟前')
    expect(formatRelativeTime(ago(59 * 60_000), NOW)).toBe('59 分钟前')
  })

  it('同一天超过一小时用「N 小时前」', () => {
    expect(formatRelativeTime(ago(5 * 3_600_000), NOW)).toBe('5 小时前')
  })

  it('昨天用「昨天」，不写「N 小时前」', () => {
    expect(formatRelativeTime(new Date(2026, 9, 5, 20, 0, 0).toISOString(), NOW)).toBe('昨天')
  })

  it('同年更早用「M 月 D 日」', () => {
    expect(formatRelativeTime(new Date(2026, 9, 1, 8, 0, 0).toISOString(), NOW)).toBe('10 月 1 日')
  })

  it('跨年带年份', () => {
    expect(formatRelativeTime(new Date(2025, 11, 25, 8, 0, 0).toISOString(), NOW)).toBe(
      '2025 年 12 月 25 日',
    )
  })

  it('未来时间按「刚刚」处理，不出现负数', () => {
    expect(formatRelativeTime(new Date(NOW.getTime() + 60_000).toISOString(), NOW)).toBe('刚刚')
  })

  it('非法时间返回空串', () => {
    expect(formatRelativeTime('not-a-date', NOW)).toBe('')
  })
})

describe('formatAbsoluteTime', () => {
  it('有效时间返回本地完整格式，非法时间返回空串', () => {
    expect(formatAbsoluteTime(new Date(2026, 9, 6, 12, 30, 0).toISOString())).not.toBe('')
    expect(formatAbsoluteTime('not-a-date')).toBe('')
  })
})

describe('formatShortDateTime', () => {
  it('压成 `YYYY-MM-DD HH:mm` 短格式（本地时区，不含秒）', () => {
    expect(formatShortDateTime(new Date(2026, 9, 6, 9, 5, 30).toISOString())).toBe(
      '2026-10-06 09:05',
    )
  })

  it('非法时间返回空串', () => {
    expect(formatShortDateTime('not-a-date')).toBe('')
  })
})
