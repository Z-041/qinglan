<script setup lang="ts">
import { useUserLabels } from '@shared/composables/useUserLabels'
import type { AdminAppealItem } from '@/types/review'

const props = defineProps<{ items: AdminAppealItem[]; loading?: boolean; failed?: boolean }>()

const emit = defineEmits<{ review: [item: AdminAppealItem] }>()

const { targetStatusLabel, reviewReasonText } = useUserLabels()

function takenDownText(reason: string | null): string {
  return reviewReasonText(reason) ?? '—'
}
</script>

<template>
  <table class="w-full border-collapse text-sm">
    <thead>
      <tr class="border-b border-line text-left text-xs text-muted">
        <th class="px-3 py-2.5 font-medium">内容摘要</th>
        <th class="px-3 py-2.5 font-medium">目标状态</th>
        <th class="px-3 py-2.5 font-medium">原下架理由</th>
        <th class="px-3 py-2.5 font-medium">申诉理由</th>
        <th class="px-3 py-2.5 font-medium">申诉时间</th>
        <th class="px-3 py-2.5 font-medium">操作</th>
      </tr>
    </thead>
    <tbody>
      <!-- key 用 appealId 只为稳定复用 DOM；该数字不渲染到界面上（决策 6） -->
      <tr v-for="row in props.items" :key="row.appealId" class="border-b border-line last:border-0">
        <td class="max-w-[320px] truncate px-3 py-3" :title="row.preview ?? ''">
          {{ row.preview ?? '内容已不存在' }}
        </td>
        <td class="px-3 py-3">{{ row.targetStatus ? targetStatusLabel(row.targetStatus) : '已删除' }}</td>
        <td class="max-w-[220px] truncate px-3 py-3" :title="takenDownText(row.takenDownReason)">
          {{ takenDownText(row.takenDownReason) }}
        </td>
        <td class="max-w-[320px] truncate px-3 py-3" :title="row.appealReason">{{ row.appealReason }}</td>
        <td class="px-3 py-3">{{ new Date(row.appealedAt).toLocaleString() }}</td>
        <td class="px-3 py-3">
          <button
            type="button"
            class="cursor-pointer rounded-lg border border-line px-3 py-1.5 text-xs text-ink transition hover:border-primary hover:text-primary-deep"
            @click="emit('review', row)"
          >
            复审
          </button>
        </td>
      </tr>
      <tr v-if="!props.items.length">
        <td colspan="6" class="px-3 py-10 text-center text-sm text-muted">
          {{ props.loading ? '加载中…' : props.failed ? '加载失败，请点击「刷新」重试' : '暂无待复审申诉' }}
        </td>
      </tr>
    </tbody>
  </table>
</template>
