/**
 * 与后端 common/notify/NotificationType 一致。
 * 申诉复审结果归入 `REVIEW_RESULT`，不单列类型（P4 决策 D10）。
 */
export type NotificationType = 'COMMENT' | 'LIKE' | 'REVIEW_RESULT' | 'REPORT_FEEDBACK'

/**
 * 与后端 notification/dto/NotificationItemResponse 一一对应。
 *
 * `title` / `body` / `link` 是通知产生时的**快照**（决策 D14），不随源对象变化。
 * 不变量 #1：出于匿名考虑，通知内容不含作者 `author_id`，至多含匿名代号。
 * `readAt` 为 null 表示未读。
 */
export interface NotificationItem {
  id: number
  type: NotificationType
  title: string
  body: string | null
  link: string | null
  readAt: string | null
  createdAt: string
}

/** 与后端 notification/dto/UnreadCountResponse 一一对应 */
export interface UnreadCount {
  count: number
}
