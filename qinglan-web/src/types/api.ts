/** 与后端 common/web/ApiResponse 一一对应 */
export interface ApiResponse<T> {
  code: string
  message: string
  data: T
}

/** 后端成功码常量，见 ApiResponse.SUCCESS_CODE */
export const SUCCESS_CODE = 'QL-000-000'
