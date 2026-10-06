import { ref } from 'vue'

import { adjustCredit, banUser, fetchAdminUsers, unbanUser } from '@/api/admin'
import type {
  AdminUserItem,
  AdminUserQuery,
  BanUserPayload,
  CreditAdjustPayload,
  UserFilterState,
} from '@/types/admin'
import type { ReasonPayload } from '@/types/review'

/** 筛选表单初值：全部不限 */
export function createEmptyFilters(): UserFilterState {
  return {
    status: '',
    role: '',
    minCredit: '',
    maxCredit: '',
    createdFrom: '',
    createdTo: '',
  }
}

/** 空串视为「不限」，转成 null；非法数字也当「不限」处理，不把 NaN 发给后端 */
function toNumberOrNull(value: string): number | null {
  const trimmed = value.trim()
  if (!trimmed) {
    return null
  }
  const parsed = Number(trimmed)
  return Number.isInteger(parsed) ? parsed : null
}

/** datetime-local 是本地时间且不带时区，后端要的是 Instant，故转成 ISO-8601 UTC */
function toInstantOrNull(value: string): string | null {
  const trimmed = value.trim()
  if (!trimmed) {
    return null
  }
  const parsed = new Date(trimmed)
  return Number.isNaN(parsed.getTime()) ? null : parsed.toISOString()
}

/** 表单状态 → 请求参数。规范化只此一处，视图不做转换 */
export function toQuery(filters: UserFilterState, cursor: string | null): AdminUserQuery {
  return {
    cursor,
    status: filters.status || null,
    role: filters.role || null,
    minCredit: toNumberOrNull(filters.minCredit),
    maxCredit: toNumberOrNull(filters.maxCredit),
    createdFrom: toInstantOrNull(filters.createdFrom),
    createdTo: toInstantOrNull(filters.createdTo),
  }
}

/**
 * 用户管理：组合筛选 + 游标分页 + 行内处置。
 * 只支持状态 / 角色 / 信用分 / 注册时间筛选，不做身份搜索（spec 7.2 隐私侧信道）。
 */
export function useUserManage() {
  const filters = ref<UserFilterState>(createEmptyFilters())
  const items = ref<AdminUserItem[]>([])
  const loading = ref(false)
  const loadingMore = ref(false)
  const hasMore = ref(false)
  const submitting = ref(false)

  /** 不放进 ref：只作为下一次请求的入参，变化不需要触发渲染 */
  let cursor: string | null = null

  function applyPage(page: { items: AdminUserItem[]; nextCursor: string | null }, append: boolean): void {
    items.value = append ? [...items.value, ...page.items] : page.items
    cursor = page.nextCursor
    hasMore.value = page.nextCursor !== null
  }

  /** 查询第一页。筛选条件变化后必须走这里，否则游标仍指向旧条件的结果集 */
  async function refresh(): Promise<void> {
    loading.value = true
    try {
      applyPage(await fetchAdminUsers(toQuery(filters.value, null)), false)
    } catch {
      // 错误已由拦截器提示
    } finally {
      loading.value = false
    }
  }

  async function loadMore(): Promise<void> {
    if (!hasMore.value || loading.value || loadingMore.value) {
      return
    }
    loadingMore.value = true
    try {
      applyPage(await fetchAdminUsers(toQuery(filters.value, cursor)), true)
    } catch {
      // 错误已由拦截器提示
    } finally {
      loadingMore.value = false
    }
  }

  /** 清空筛选并回到第一页 */
  async function reset(): Promise<void> {
    filters.value = createEmptyFilters()
    await refresh()
  }

  /** 处置动作的统一收尾：成功返回 true；失败只复位 submitting，错误由拦截器提示 */
  async function run(action: () => Promise<void>): Promise<boolean> {
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

  return {
    filters,
    items,
    loading,
    loadingMore,
    hasMore,
    submitting,
    refresh,
    loadMore,
    reset,
    ban: (userId: number, payload: BanUserPayload) => run(() => banUser(userId, payload)),
    unban: (userId: number, payload: ReasonPayload) => run(() => unbanUser(userId, payload)),
    adjustCredit: (userId: number, payload: CreditAdjustPayload) => run(() => adjustCredit(userId, payload)),
  }
}
