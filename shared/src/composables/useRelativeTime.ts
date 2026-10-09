/**
 * 时间展示（学生端信息流 + 管理端表格共用）。
 *
 * 信息流、评论区、消息中心与后台各列表统一用「刚刚 / 3 分钟前 / 昨天 / 10 月 1 日」这类
 * 高信息密度文案，精确时刻放到元素的 `title` 上，悬停可见——不牺牲可核查性。
 *
 * 合规例外：审计日志必须能精确核对时刻，故保留**绝对时间**（短格式 `formatShortDateTime`），
 * 短格式只是为了密集表格里能扫读，不是相对化。
 *
 * 刻意做成**纯函数**而非响应式状态：列表每次渲染按「当前时刻」重算即可，
 * 不为逐秒刷新挂定时器（社区惯例如此，也避免列表里几十个定时器）。
 *
 * 放在 shared 而不是学生端：两端都在用（学生端信息流 + 管理端阶段 B 的表格时间相对化），
 * 两处各写一份必然漂移（同 `useUserLabels` 的先例）。
 */

const MINUTE = 60_000
const HOUR = 60 * MINUTE
const DAY = 24 * HOUR

/** 当天零点的时间戳，用于按自然日判断「昨天 / 更早」 */
function startOfDay(date: Date): number {
  return new Date(date.getFullYear(), date.getMonth(), date.getDate()).getTime()
}

/** 两位补零，供短绝对时间拼接 */
function pad(value: number): string {
  return String(value).padStart(2, '0')
}

/** 绝对时间（本地时区完整格式），供 `title` 悬停展示 */
export function formatAbsoluteTime(iso: string): string {
  const date = new Date(iso)
  return Number.isNaN(date.getTime()) ? '' : date.toLocaleString()
}

/**
 * 短绝对时间 `YYYY-MM-DD HH:mm`（本地时区），供**审计日志**这类合规场景使用：
 * 时刻必须可精确核对，但 `toLocaleString` 的长格式在密集表格里难以扫读。
 * 完整时刻（含秒）仍放在 `title` 上悬停可见。
 */
export function formatShortDateTime(iso: string): string {
  const date = new Date(iso)
  if (Number.isNaN(date.getTime())) {
    return ''
  }
  const ymd = `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`
  return `${ymd} ${pad(date.getHours())}:${pad(date.getMinutes())}`
}

/**
 * 相对时间文案。`now` 可注入，便于测试固定时刻。
 * 无法解析的时间返回空串（调用方原样渲染即可，不显示错误文案）。
 *
 * 注意：**未来时间会归为「刚刚」**（时钟漂移兜底）。因此「封禁至」「公告生效结束」这类
 * 指向未来的时刻不要用它，应改用绝对时间。
 */
export function formatRelativeTime(iso: string, now: Date = new Date()): string {
  const date = new Date(iso)
  if (Number.isNaN(date.getTime())) {
    return ''
  }

  const diff = now.getTime() - date.getTime()
  // 未来时间（时钟漂移）按「刚刚」处理，不出现负数
  if (diff < MINUTE) {
    return '刚刚'
  }
  if (diff < HOUR) {
    return `${Math.floor(diff / MINUTE)} 分钟前`
  }

  const dayDiff = Math.round((startOfDay(now) - startOfDay(date)) / DAY)
  if (dayDiff <= 0) {
    return `${Math.floor(diff / HOUR)} 小时前`
  }
  if (dayDiff === 1) {
    return '昨天'
  }
  if (date.getFullYear() === now.getFullYear()) {
    return `${date.getMonth() + 1} 月 ${date.getDate()} 日`
  }
  return `${date.getFullYear()} 年 ${date.getMonth() + 1} 月 ${date.getDate()} 日`
}
