import { ref } from 'vue'

import { fetchAdminStats } from '@/api/admin'
import type { AdminStats } from '@/types/admin'

/**
 * 看板数据：一次拉取 + 手动刷新。
 * 「今日」由后端按 Asia/Shanghai 切分，前端不做任何时区或补 0 计算。
 */
export function useDashboard() {
  const stats = ref<AdminStats | null>(null)
  const loading = ref(false)
  /**
   * 首屏拉取是否失败过。用于把「加载失败」与「真的没有数据」分成两屏——
   * 否则一次网络故障会被看板说成「暂无数据」。
   */
  const failed = ref(false)

  /**
   * 请求序号。挂载时的首屏加载与用户点「刷新」可能同时在飞，**先发的不一定先到**：
   * 没有它的话，晚到的旧响应会把新数据盖回去，loading 也会被先到的 finally 提前复位。
   */
  let requestSeq = 0

  async function load(): Promise<void> {
    const seq = ++requestSeq
    loading.value = true
    failed.value = false
    try {
      const result = await fetchAdminStats()
      // 过期响应必须丢弃
      if (seq !== requestSeq) {
        return
      }
      stats.value = result
    } catch {
      // 错误已由拦截器提示；已有数据时只保留旧数据（刷新失败不盖掉内容），
      // 仅首屏为空才置失败态，由页面给出「重试」
      if (seq === requestSeq && stats.value === null) {
        failed.value = true
      }
    } finally {
      if (seq === requestSeq) {
        loading.value = false
      }
    }
  }

  return { stats, loading, failed, load }
}
