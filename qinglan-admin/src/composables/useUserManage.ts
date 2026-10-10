import { computed, ref } from 'vue'

import { useCursorList } from '@shared/composables/useCursorList'
import { useSubmit } from '@shared/composables/useSubmit'

import { adjustCredit, banUser, fetchAdminUsers, unbanUser } from '@/api/admin'
import { toInstantOrNull } from '@/composables/datetime'
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
 * 用户管理：组合筛选 + 游标分页（状态机收在 {@link useCursorList}）+ 行内处置。
 * 只支持状态 / 角色 / 信用分 / 注册时间筛选，不做身份搜索（spec 7.2 隐私侧信道）。
 */
export function useUserManage() {
  const filters = ref<UserFilterState>(createEmptyFilters())
  /** 处置动作在途：行内按钮据此禁用，防重复提交 */
  const { submitting, run } = useSubmit()

  /** 上一次**成功查询**所用的筛选快照（JSON 串）。用来判断「筛选改了但还没查询」 */
  const appliedFilters = ref<string | null>(null)

  /**
   * 筛选已改但未查询。此时列表里是旧条件的结果、游标也属于旧结果集，
   * 若继续「加载更多」就会把两个条件的数据拼在一起（重复行 / 漏行）。
   */
  const filtersDirty = computed(
    () => appliedFilters.value !== null && appliedFilters.value !== JSON.stringify(filters.value),
  )

  const list = useCursorList<AdminUserItem>({
    fetchPage: (cursor) => fetchAdminUsers(toQuery(filters.value, cursor)),
    canLoadMore: () => !filtersDirty.value,
    // 只有成功应用了第一页，才算「当前筛选已查询」
    onApplied: () => {
      appliedFilters.value = JSON.stringify(filters.value)
    },
  })

  /** 清空筛选并回到第一页 */
  async function reset(): Promise<void> {
    filters.value = createEmptyFilters()
    await list.refresh()
  }

  return {
    filters,
    items: list.items,
    loading: list.loading,
    loadingMore: list.loadingMore,
    hasMore: list.hasMore,
    failed: list.failed,
    filtersDirty,
    submitting,
    refresh: list.refresh,
    loadMore: list.loadMore,
    reset,
    ban: (userId: number, payload: BanUserPayload) => run(() => banUser(userId, payload)),
    unban: (userId: number, payload: ReasonPayload) => run(() => unbanUser(userId, payload)),
    adjustCredit: (userId: number, payload: CreditAdjustPayload) => run(() => adjustCredit(userId, payload)),
  }
}
