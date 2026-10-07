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

  async function load(): Promise<void> {
    loading.value = true
    failed.value = false
    try {
      stats.value = await fetchAdminStats()
    } catch {
      // 错误已由拦截器提示；已有数据时只保留旧数据（刷新失败不盖掉内容），
      // 仅首屏为空才置失败态，由页面给出「重试」
      if (stats.value === null) {
        failed.value = true
      }
    } finally {
      loading.value = false
    }
  }

  return { stats, loading, failed, load }
}
