import { ref } from 'vue'

/**
 * 提交态包装：统一「置真 → 执行 → 成功 true / 失败 false → finally 复位」这套约定。
 *
 * <p>调用方据返回值决定是否关弹窗 / 刷新 / 继续流程；失败**不在此提示**——请求错误一律由
 * http 拦截器统一弹窗（AGENTS.md 第七章：请求错误只有一个出口）。</p>
 *
 * <p>不覆盖的形态（刻意）：返回结果对象（如发布返回详情）、返回联合枚举（如公告已读需要按
 * 状态分类）、以及需要在 `catch` 里 rethrow 的场景——强套会扭曲它们的契约。</p>
 */
export function useSubmit() {
  const submitting = ref(false)

  /** 成功返回 true；失败返回 false（错误已由拦截器提示） */
  async function run(action: () => Promise<unknown>): Promise<boolean> {
    submitting.value = true
    try {
      await action()
      return true
    } catch {
      return false
    } finally {
      submitting.value = false
    }
  }

  return { submitting, run }
}
