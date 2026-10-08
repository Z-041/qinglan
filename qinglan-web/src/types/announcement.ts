// 级别枚举是双端共用的（管理端公告列表与新建表单也在用），定义已下沉到 shared。
import type { AnnouncementLevel } from '@shared/types/announcement'

/**
 * 与后端 announcement/dto/PendingAnnouncementResponse 一一对应。
 *
 * `GET /api/announcements/pending` 的 data；无未读时后端返回 null。
 * 发帖被强制阅读拦下（HTTP 428，QL-428-001）时，响应体的 data 也是本结构，
 * 前端据此**直接弹窗、不再多发一次请求**（计划 D15）。
 */
export interface PendingAnnouncement {
  id: number
  title: string
  content: string
  level: AnnouncementLevel
  minReadSeconds: number
  requireScrollToEnd: boolean
  requireConfirm: boolean
  publishedAt: string
}

/**
 * 与后端 announcement/dto/AnnouncementSummaryResponse 一一对应。
 *
 * `read` 为当前 viewer 是否已读（`userId` 或 `deviceId` 任一命中即视为已读，见计划 D5）。
 */
export interface AnnouncementSummary {
  id: number
  title: string
  content: string
  level: AnnouncementLevel
  publishedAt: string
  read: boolean
}

/**
 * 与后端 announcement/dto/MarkReadRequest 一一对应。
 *
 * `durationMs` 必须是弹窗展示至今的**真实**毫秒数（不变量 #6）；服务端会校验其下限，
 * 不足 `minReadSeconds * 1000` 一律拒绝。`scrolledToEnd` / `confirmed` 为客户端信号，如实上报。
 */
export interface MarkAnnouncementReadPayload {
  deviceId: string | null
  durationMs: number
  scrolledToEnd: boolean
  confirmed: boolean
}
