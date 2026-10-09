import { getCurrentScope, onMounted, onScopeDispose, ref } from 'vue'

import { fetchAdminTodoCounts } from '@/api/review'
import { hasPermission, type AdminPermission } from '@/types/permission'
import type { AdminTodoCounts } from '@/types/admin'

/** 待办计数轮询间隔：与队列轮询同节奏（30 秒）；页面不可见即暂停 */
export const TODO_POLL_INTERVAL_MS = 30_000

/** 待办分组元数据：分组 → 文案 / 跳转路由 / 权限点 */
export interface TodoGroupMeta {
  key: keyof AdminTodoCounts
  label: string
  routeName: string
  permission: AdminPermission
}

/** 分组口径与后端 AdminTodoCountService 一致（顺序即界面展示顺序） */
export const TODO_GROUPS: readonly TodoGroupMeta[] = [
  { key: 'pendingPosts', label: '新待审', routeName: 'admin-pending-posts', permission: 'MODERATION_VIEW' },
  { key: 'pendingReports', label: '新举报', routeName: 'admin-review-queue', permission: 'MODERATION_VIEW' },
  { key: 'proposable', label: '待表决', routeName: 'admin-proposals', permission: 'PROPOSAL_VOTE' },
  { key: 'escalated', label: '待裁决', routeName: 'admin-arbitration', permission: 'PROPOSAL_ARBITRATE' },
]

/** 一个可点击的待办分组（含真实计数） */
export interface TodoGroup {
  key: keyof AdminTodoCounts
  label: string
  count: number
  routeName: string
}

/**
 * 待办计数 → 可见分组。
 *
 * <p><b>无权限或字段缺失的分组不展示</b>：后端对无权限的组直接**不返回字段**（不是返回 0），
 * 所以「字段存在」本身即代表「有权限且这是真实计数」。这里再按权限点复核一次，
 * 让「权限裁剪」成为可测的显式规则，而不是只靠后端不发字段。</p>
 */
export function buildTodoGroups(
  counts: AdminTodoCounts,
  permissions: readonly AdminPermission[],
): TodoGroup[] {
  return TODO_GROUPS.filter(
    (group) => counts[group.key] !== undefined && hasPermission(permissions, group.permission),
  ).map((group) => ({
    key: group.key,
    label: group.label,
    count: counts[group.key] ?? 0,
    routeName: group.routeName,
  }))
}

/**
 * 顶栏待办计数（阶段 B）：30 秒轮询 + 可见性暂停 + 点击分组本地清零。
 *
 * <p>只在 `document.visibilityState === 'visible'` 时轮询，切后台即暂停、切回自动恢复，
 * 与 {@link import('./useQueuePolling')} 同一取向（低频轮询，不为后台标签页空转）。</p>
 */
export function useTodoCounts() {
  const counts = ref<AdminTodoCounts>({})

  let timer: ReturnType<typeof setInterval> | null = null
  /** 防重入：上一次请求未返回时不再叠加 */
  let inFlight = false

  function isVisible(): boolean {
    return typeof document === 'undefined' || document.visibilityState === 'visible'
  }

  async function load(): Promise<void> {
    if (inFlight) {
      return
    }
    inFlight = true
    try {
      counts.value = await fetchAdminTodoCounts()
    } catch {
      // 错误提示由 http 拦截器统一负责；保留上一次计数，下一轮自会重试
    } finally {
      inFlight = false
    }
  }

  /** 点击某分组后本地清零该组计数（下一次轮询仍会拉回真实值） */
  function clear(key: keyof AdminTodoCounts): void {
    if (counts.value[key] === undefined) {
      return
    }
    counts.value = { ...counts.value, [key]: 0 }
  }

  function startTimer(): void {
    if (timer !== null) {
      return
    }
    timer = setInterval(() => {
      void load()
    }, TODO_POLL_INTERVAL_MS)
  }

  function stopTimer(): void {
    if (timer !== null) {
      clearInterval(timer)
      timer = null
    }
  }

  function handleVisibilityChange(): void {
    if (isVisible()) {
      void load()
      startTimer()
    } else {
      stopTimer()
    }
  }

  function start(): void {
    document.addEventListener('visibilitychange', handleVisibilityChange)
    if (isVisible()) {
      void load()
      startTimer()
    }
  }

  function stop(): void {
    document.removeEventListener('visibilitychange', handleVisibilityChange)
    stopTimer()
  }

  // 组件内使用时自动随生命周期启停；单测里在无 scope 环境调用则手动 start/stop
  if (getCurrentScope()) {
    onMounted(start)
    onScopeDispose(stop)
  }

  return { counts, load, clear, start, stop }
}
