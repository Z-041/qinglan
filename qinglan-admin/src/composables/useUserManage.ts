import { ref } from 'vue'

import { useSubmit } from '@shared/composables/useSubmit'

import { adjustCredit, banUser, fetchAdminUsers, unbanUser } from '@/api/admin'
import { toInstantOrNull } from '@/composables/datetime'
import { useFilteredCursorList } from '@/composables/useFilteredCursorList'
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
    createdFrom: null,
    createdTo: null,
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
 *
 * <p>分页状态机与「筛选已改但未查询」的续页闸门收在 {@link useFilteredCursorList}。</p>
 */
export function useUserManage() {
  const filters = ref<UserFilterState>(createEmptyFilters())
  /** 处置动作在途：行内按钮据此禁用，防重复提交 */
  const { submitting, run } = useSubmit()

  const { items, loading, loadingMore, hasMore, failed, refresh, loadMore, filtersDirty } =
    useFilteredCursorList<AdminUserItem, UserFilterState>({
      filters,
      fetchPage: (requestFilters, cursor) => fetchAdminUsers(toQuery(requestFilters, cursor)),
    })

  /** 清空筛选并回到第一页 */
  async function reset(): Promise<void> {
    filters.value = createEmptyFilters()
    await refresh()
  }

  return {
    filters,
    items,
    loading,
    loadingMore,
    hasMore,
    failed,
    filtersDirty,
    submitting,
    refresh,
    loadMore,
    reset,
    ban: (userId: number, payload: BanUserPayload) => run(() => banUser(userId, payload)),
    unban: (userId: number, payload: ReasonPayload) => run(() => unbanUser(userId, payload)),
    adjustCredit: (userId: number, payload: CreditAdjustPayload) => run(() => adjustCredit(userId, payload)),
  }
}
