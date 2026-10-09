import { ref, type Ref } from 'vue'

import type { CursorPage } from '@shared/types/api'

/** 取页函数：给定游标（首页传 null）返回一页数据。调用方在闭包内读取自己的筛选 / 话题条件 */
export type FetchPage<T> = (cursor: string | null) => Promise<CursorPage<T>>

export interface UseCursorListOptions<T> {
  fetchPage: FetchPage<T>
  /**
   * 是否允许继续翻页。默认恒允许。
   *
   * 「筛选已改但未重查」这类场景要注入谓词拦住续页：此时列表与游标仍属**旧结果集**，
   * 续页会把两个条件的数据拼在一起（重复行 / 漏行）。
   */
  canLoadMore?: () => boolean
  /**
   * 第一页**成功应用**时回调一次（失败、或被更新的请求取代时都不调用）。
   * 供调用方做「已应用筛选快照」这类与成功与否绑定的记账；不需要记账的调用方不传。
   */
  onApplied?: () => void
}

/** 游标列表的对外接口。字段名与各调用方既有实现保持一致，便于无缝替换。 */
export interface CursorList<T> {
  items: Ref<T[]>
  /** 首屏 / 重新加载在途 */
  loading: Ref<boolean>
  /** 续页在途 */
  loadingMore: Ref<boolean>
  /** 还有下一页（由 nextCursor !== null 推导） */
  hasMore: Ref<boolean>
  /** 首屏是否失败过。用于把「加载失败」与「真的没有内容」分成两屏 */
  failed: Ref<boolean>
  /** 拉第一页并重置游标 */
  refresh: () => Promise<void>
  /** 续页追加。失败不清空已加载内容 */
  loadMore: () => Promise<void>
}

/**
 * 游标列表：游标分页的状态机（学生端信息流 / 我的帖子 / 消息中心，管理端各队列共用）。
 *
 * <p>把三件此前在 9 个 composable 里逐字重复的事收敛到一处：</p>
 * <ol>
 *   <li><b>清空再请求</b>：refresh 先把列表与游标清干净，否则本次失败时游标会留在旧结果集上，
 *       此后点「加载更多」就会发「新条件 + 旧游标」这种自相矛盾的请求；</li>
 *   <li><b>请求序号防竞态</b>：切换话题 / 处置后刷新与用户点刷新可能同时在飞，后发的**不一定后到**，
 *       晚到的旧响应必须按序号丢弃，否则列表会被写成旧条件的内容；</li>
 *   <li><b>四态</b>：loading（首屏）/ loadingMore（续页）/ hasMore / failed，语义固定。</li>
 * </ol>
 *
 * <p><b>边界</b>：请求错误一律由 http 拦截器弹窗提示（AGENTS.md 第七章），本模块不渲染任何错误文案；
 * 它与列表状态无关的东西（取页参数拼装、轮询探针 headKeys、行内处置、提交态）都留在调用方。</p>
 *
 * @param options.fetchPage   取页函数（闭包内读取当前筛选 / 话题）
 * @param options.canLoadMore 可选的续页闸门
 * @param options.onApplied   可选的「第一页已成功应用」回调
 */
export function useCursorList<T>(options: UseCursorListOptions<T>): CursorList<T> {
  const items = ref([]) as Ref<T[]>
  const loading = ref(false)
  const loadingMore = ref(false)
  const hasMore = ref(false)
  const failed = ref(false)

  /** 不放进 ref：只作为下一次请求的入参，变化不需要触发渲染 */
  let cursor: string | null = null

  /**
   * 请求序号。切换条件会有多个请求同时在飞，**后发的不一定后到**：
   * 没有它的话，先点 A 再点 B、而 A 的响应晚到时，列表会写成 A 的内容。
   */
  let requestSeq = 0

  function applyPage(page: CursorPage<T>, append: boolean): void {
    items.value = append ? [...items.value, ...page.items] : page.items
    cursor = page.nextCursor
    hasMore.value = page.nextCursor !== null
  }

  async function refresh(): Promise<void> {
    const seq = ++requestSeq
    loading.value = true
    failed.value = false
    // 先清干净再请求：见函数注释「清空再请求」
    items.value = []
    cursor = null
    hasMore.value = false
    try {
      const page = await options.fetchPage(null)
      // 过期响应必须丢弃：只有最新的那次请求才有权写状态
      if (seq !== requestSeq) {
        return
      }
      applyPage(page, false)
      options.onApplied?.()
    } catch {
      // 错误已由拦截器提示；只有当前这次请求才有权把状态置为失败
      if (seq === requestSeq) {
        failed.value = true
      }
    } finally {
      if (seq === requestSeq) {
        loading.value = false
      }
    }
  }

  async function loadMore(): Promise<void> {
    if (!hasMore.value || loading.value || loadingMore.value) {
      return
    }
    if (options.canLoadMore && !options.canLoadMore()) {
      return
    }
    // 记下发起时的序号：期间若刷新过（requestSeq 会 +1），这次的响应必须丢弃，
    // 否则旧结果会被 append 进新结果的列表
    const seq = requestSeq
    loadingMore.value = true
    try {
      const page = await options.fetchPage(cursor)
      if (seq !== requestSeq) {
        return
      }
      applyPage(page, true)
    } catch {
      // 错误已由拦截器提示；加载更多失败不清空已加载内容
    } finally {
      loadingMore.value = false
    }
  }

  return { items, loading, loadingMore, hasMore, failed, refresh, loadMore }
}
