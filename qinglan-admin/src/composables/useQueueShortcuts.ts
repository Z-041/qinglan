import { computed, toValue, type MaybeRefOrGetter } from 'vue'

import { usePermissions } from '@/composables/usePermissions'
import type { AdminPermission } from '@/types/permission'

export interface QueueShortcut<A extends string> {
  /** 触发键（单个字符，如 '1'） */
  key: string
  action: A
  /** 提示条上的动作名 */
  label: string
  /** 该动作所需权限点；当前账号未授予时本绑定既不渲染也不生效 */
  permission: AdminPermission
}

/**
 * 队列一键处置快捷键：把「可用动作」按权限点过滤成一份绑定表，
 * 提示条渲染与按键查找**共用同一份**，避免「按钮灰了但按键还能按」这类自相矛盾。
 *
 * <p>只做映射与过滤，真正的动作仍由页面走既有处置流程（含弹窗二次确认）。</p>
 */
export function useQueueShortcuts<A extends string>(
  defs: MaybeRefOrGetter<readonly QueueShortcut<A>[]>,
) {
  const { can } = usePermissions()

  /** 按触发键排序：数字键从小到大，提示条读起来与按键顺序一致 */
  const bindings = computed(() =>
    toValue(defs)
      .filter((item) => can(item.permission))
      .slice()
      .sort((a, b) => a.key.localeCompare(b.key)),
  )

  /** 按键 → 动作；无权限或未定义该键时返回 null（不执行任何动作） */
  function actionOf(key: string): A | null {
    return bindings.value.find((item) => item.key === key)?.action ?? null
  }

  return { bindings, actionOf }
}
