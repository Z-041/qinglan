/**
 * 点赞 / 取消点赞的统一切换逻辑（帖子与评论共用）。
 *
 * 约定（与三处原实现一致）：
 * - 不做乐观更新——以后端返回的 liked/likeCount 为准，请求失败时本地不变，无需回滚；
 * - 请求错误一律由 http 拦截器 toast，本函数静默吞掉（不重复提示）；
 * - 请求函数由调用方注入（帖子 likePost/unlikePost、评论 likeComment/unlikeComment），
 *   本模块不绑定具体 api，便于测试与复用。
 */
export interface LikeState {
  liked: boolean
  likeCount: number
}

export type LikeRequest = (id: number) => Promise<LikeState>

export function useLikeToggle() {
  async function toggleLike(target: LikeState, id: number, like: LikeRequest, unlike: LikeRequest): Promise<void> {
    try {
      const state = target.liked ? await unlike(id) : await like(id)
      target.liked = state.liked
      target.likeCount = state.likeCount
    } catch {
      // 错误已由 http 拦截器提示；本地保持后端一致的旧状态
    }
  }

  return { toggleLike }
}
