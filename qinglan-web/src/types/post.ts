/**
 * 与后端 post/dto/TopicResponse 一一对应。
 * `requiresContact` 为真时发布页必须填联系方式。
 */
export interface Topic {
  id: number
  name: string
  rules: string
  requiresContact: boolean
}

/**
 * 与后端 post/dto/PostSummaryResponse 一一对应。
 *
 * 不变量 #1：列表中只有匿名代号，没有作者 id；「是否本人」由 `mine` 表达。
 * 列表不下发 `contact`，联系方式只在详情页按访问者身份返回（不变量 #2）。
 */
export interface PostSummary {
  id: number
  topicId: number
  topicName: string
  content: string
  images: string[]
  anonymousCode: string
  likeCount: number
  liked: boolean
  commentCount: number
  mine: boolean
  createdAt: string
}

/**
 * 与后端 post/dto/PostDetailResponse 一一对应。
 *
 * `contact` 由服务端按 viewer 身份脱敏（不变量 #2）：未认证时必为 null，
 * `contactVisible` 为 false 时前端显示「认证后可查看联系方式」而非空白。
 * `reviewReason` 仅作者本人可见，其余为 null。
 * `takenDown` 为下架态标记：下架帖只有作者能打开，作者据此拿到申诉入口。
 * 不能用 `reviewReason` 非空代替——驳回、恢复后的帖子都会残留该字段。
 */
export interface PostDetail {
  id: number
  topicId: number
  topicName: string
  topicRules: string
  content: string
  contact: string | null
  contactVisible: boolean
  images: string[]
  anonymousCode: string
  likeCount: number
  liked: boolean
  commentCount: number
  mine: boolean
  reviewReason: string | null
  takenDown: boolean
  createdAt: string
}

/** 与后端 post/dto/CreatePostRequest 一一对应 */
export interface CreatePostRequest {
  topicId: number
  content: string
  contact?: string | null
  images?: string[]
}

/** 与后端 post/dto/UploadedImageResponse 一一对应 */
export interface UploadedImage {
  url: string
}
