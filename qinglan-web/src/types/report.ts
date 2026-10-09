/**
 * 与后端 review/entity/ReportStatus 一一对应。
 * `PENDING` 处理中、`UPHELD` 举报成立（内容已处置）、`DISMISSED` 未获支持。
 */
export type ReportStatus = 'PENDING' | 'UPHELD' | 'DISMISSED'

/**
 * 与后端 review/dto/MyReportStatusResponse 一一对应。
 *
 * 只描述**当前用户自己**在该目标上的举报状态；未举报时 `reported` 为 false，
 * 其余字段均为 null。响应里不含任何他人举报信息（不变量 #1）。
 */
export interface MyReportState {
  reported: boolean
  status: ReportStatus | null
  createdAt: string | null
  handledAt: string | null
}
