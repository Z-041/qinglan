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
 * 游标是不透明字符串（信息流是帖子 id，审核队列是复合键），原样回传即可；
 * `nextCursor` 为 null 表示已到末页。
 */
export interface CursorPage<T> {
  items: T[]
  nextCursor: string | null
}
