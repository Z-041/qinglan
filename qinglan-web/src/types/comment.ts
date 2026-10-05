/**
 * 与后端 comment/dto/CommentResponse 一一对应。
 *
 * 只支持两级：一级评论的 `replies` 非空，回复的为空数组。
 * 同帖子列表：不返回作者 id，只用匿名代号 + `mine`（不变量 #1）。
 */
export interface Comment {
  id: number
  content: string
  anonymousCode: string
  likeCount: number
  liked: boolean
  mine: boolean
  createdAt: string
  replies: Comment[]
}

/**
 * 与后端 comment/dto/CreateCommentRequest 一一对应。
 * `parentId` 为 null 表示发一级评论；否则是回复某条一级评论。
 */
export interface CreateCommentRequest {
  content: string
  parentId?: number | null
}
