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

  async function load(): Promise<void> {
    loading.value = true
    try {
      stats.value = await fetchAdminStats()
    } catch {
      // 错误已由拦截器提示
    } finally {
      loading.value = false
    }
  }

  return { stats, loading, load }
}
