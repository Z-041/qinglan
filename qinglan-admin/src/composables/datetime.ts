/**
 * datetime-local（本地时间、不带时区）→ 后端要的 Instant（ISO-8601 UTC）。
 *
 * 空串或非法日期返回 null——筛选条件的「不限」、公告的「长期有效」都用 null 表达。
 * 原先三处各写一份（审计日志 / 用户筛选 / 公告表单），其中一处不 trim 且非法日期会抛
 * RangeError，故统一到此处：健壮版可安全用于「用户可清空的筛选输入」。
 */
export function toInstantOrNull(value: string): string | null {
  const trimmed = value.trim()
  if (!trimmed) {
    return null
  }
  const parsed = new Date(trimmed)
  return Number.isNaN(parsed.getTime()) ? null : parsed.toISOString()
}
