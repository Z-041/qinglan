<script setup lang="ts">
import BaseButton from '@shared/components/base/BaseButton.vue'
import RelativeTime from '@shared/components/RelativeTime.vue'
import DataTable from '@/components/admin/DataTable.vue'
import type { TableStateProps } from '@/components/admin/tableProps'
import { useUserLabels } from '@shared/composables/useUserLabels'
import type { AdminAppealItem } from '@/types/review'

const props = defineProps<
  TableStateProps<AdminAppealItem> & {
    /** 键盘行焦点所在行的键（appealId 字符串）；用于高亮当前处置目标 */
    activeKey?: string | null
    /**
     * 是否可发起复审（`APPEAL_REVIEW`）。不传即视为无权限——**默认安全**：
     * 漏传时表现为「按钮不出现」，而不是「任何人都能点」。真正的防线仍是后端注解。
     */
    canReview?: boolean
  }
>()

const emit = defineEmits<{ review: [item: AdminAppealItem] }>()

const { targetStatusLabel, reviewReasonText } = useUserLabels()

function takenDownText(reason: string | null): string {
  return reviewReasonText(reason) ?? '—'
}
</script>

<template>
  <DataTable
    :items="props.items"
    :colspan="6"
    :loading="props.loading"
    :failed="props.failed"
    empty-text="暂无待复审申诉"
  >
    <template #head>
      <th scope="col" class="ql-th">内容摘要</th>
      <th scope="col" class="ql-th">目标状态</th>
      <th scope="col" class="ql-th">原下架理由</th>
      <th scope="col" class="ql-th">申诉理由</th>
      <th scope="col" class="ql-th">申诉时间</th>
      <th scope="col" class="ql-th">操作</th>
    </template>

    <!-- key 用 appealId 只为稳定复用 DOM；该数字不渲染到界面上（决策 6） -->
    <tr
      v-for="row in props.items"
      :key="row.appealId"
      class="ql-tr"
      :class="props.activeKey === String(row.appealId) ? 'bg-primary-soft' : ''"
    >
      <td class="max-w-[320px] truncate px-3 py-3" :title="row.preview ?? '内容已不存在'">
        {{ row.preview ?? '内容已不存在' }}
      </td>
      <td class="px-3 py-3">{{ row.targetStatus ? targetStatusLabel(row.targetStatus) : '已删除' }}</td>
      <td class="max-w-[220px] truncate px-3 py-3" :title="takenDownText(row.takenDownReason)">
        {{ takenDownText(row.takenDownReason) }}
      </td>
      <td class="max-w-[320px] truncate px-3 py-3" :title="row.appealReason">{{ row.appealReason }}</td>
      <td class="px-3 py-3">
        <RelativeTime :iso="row.appealedAt" />
      </td>
      <td class="px-3 py-3">
        <BaseButton v-if="props.canReview" variant="ghost" size="xs" @click="emit('review', row)">
          复审
        </BaseButton>
      </td>
    </tr>
  </DataTable>
</template>
