/**
 * 与后端 post/dto/PostSort 一一对应。信息流排序口径：
 * `latest`（默认）按时间倒序，`hot` 按热度（点赞 + 评论 + 时间衰减）。
 * 两种排序的游标格式不同，切换排序必须把该值一并下发给后端。
 */
export type PostSort = 'latest' | 'hot'

/** 与后端 post/entity/PostStatus 一一对应（状态迁移合法性由后端状态机裁决，前端只负责展示） */
export type PostStatus = 'DRAFT' | 'PENDING' | 'REVIEWING' | 'APPROVED' | 'REJECTED' | 'TAKEN_DOWN'

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

/**
 * 与后端 post/dto/MyPostItemResponse 一一对应（本人视角）。
 *
 * 比 {@link PostSummary} 多 `status` 与 `reviewReason`：覆盖待审 / 已驳回 / 已下架 / 已通过
 * 全部状态。`reviewReason` **仅本人可见**（该列表本身只对本人开放），驳回与下架时才有值。
 * 不含他人身份（没有 authorId、邮箱、学号）。
 */
export interface MyPostItem {
  id: number
  topicId: number
  topicName: string
  content: string
  images: string[]
  anonymousCode: string
  status: PostStatus
  reviewReason: string | null
  likeCount: number
  commentCount: number
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

/**
 * 与后端 post/dto/ImageUrlRefreshItem 一一对应（批量换取可用图片地址）。
 *
 * 私有图在浏览器 `<img>` 里不带令牌，故草稿保存的原 URL 取不到图；恢复草稿时调用批量端点换取
 * `usableUrl`（签名 URL 或公开原 URL）。`available` 为 false 表示当前用户无权访问（已清理 / 无权），
 * 此时 `usableUrl` 为 null，须提示用户重新上传，不要静默破图。
 */
export interface ImageUrlRefreshItem {
  url: string
  available: boolean
  usableUrl: string | null
}
