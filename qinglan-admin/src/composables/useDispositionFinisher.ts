import { pushToast } from '@shared/composables/useToast'

export interface DispositionFinisherOptions {
  /** 收尾前的清理：关闭弹窗、清临时状态等 */
  cleanup?: () => void
  /** 重新拉取第一页 */
  refresh: () => Promise<void> | void
  /** 焦点推进（例如把越界焦点夹回末行后再选中） */
  advance?: () => void
}

/**
 * 处置成功后的统一收尾：**清理 → 提示 → 刷新 → 焦点推进**。
 *
 * <p>三个队列视图此前各写一份同构的收尾（`advanceAfterHandled` 及其内联版本），差异只在
 * 「关哪些弹窗」与「是否推进焦点」。这里把顺序固定下来收成一份，各视图绑定一次即可。</p>
 *
 * <p>刻意不做的事：失败提示、loading 复位——前者由 http 拦截器统一弹窗（AGENTS.md 第七章），
 * 后者已由 `useReviewActions` 的 `submitting` 负责。</p>
 */
export function useDispositionFinisher(options: DispositionFinisherOptions) {
  async function finish(message: string): Promise<void> {
    options.cleanup?.()
    pushToast(message, 'info')
    await options.refresh()
    options.advance?.()
  }

  return { finish }
}
