/** 与后端 common/web/ApiResponse 一一对应 */
export interface ApiResponse<T> {
  code: string
  message: string
  data: T
}

/** 后端成功码常量，见 ApiResponse.SUCCESS_CODE */
export const SUCCESS_CODE = 'QL-000-000'

/**
 * 与后端 common/web/CursorPage 一一对应。
 * 游标是上一页最后一条的 id，`nextCursor` 为 null 表示已到末页。
 */
export interface CursorPage<T> {
  items: T[]
  nextCursor: number | null
}
