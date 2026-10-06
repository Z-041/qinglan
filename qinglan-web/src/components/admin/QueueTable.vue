<script setup lang="ts">
import { isReportOverdue } from '@/composables/useReviewQueue'
import { useUserLabels } from '@/composables/useUserLabels'
import type { AdminQueueItem } from '@/types/review'

const props = defineProps<{ items: AdminQueueItem[]; loading?: boolean }>()

const emit = defineEmits<{ handle: [item: AdminQueueItem] }>()

const { targetTypeLabel, targetStatusLabel, reportReasonLabel } = useUserLabels()
</script>

<template>
  <table class="w-full border-collapse text-sm">
    <thead>
      <tr class="border-b border-line text-left text-xs text-muted">
        <th class="px-3 py-2.5 font-medium">目标类型</th>
        <th class="px-3 py-2.5 font-medium">内容摘要</th>
        <th class="px-3 py-2.5 font-medium">目标状态</th>
        <th class="px-3 py-2.5 font-medium">待处理举报</th>
        <th class="px-3 py-2.5 font-medium">首报时间</th>
        <th class="px-3 py-2.5 font-medium">理由</th>
        <th class="px-3 py-2.5 font-medium">操作</th>
      </tr>
    </thead>
    <tbody>
      <tr
        v-for="row in props.items"
        :key="`${row.targetType}-${row.targetId}`"
        class="border-b border-line last:border-0"
      >
        <td class="px-3 py-3">{{ targetTypeLabel(row.targetType) }}</td>
        <td class="max-w-[320px] truncate px-3 py-3" :title="row.preview">{{ row.preview }}</td>
        <td class="px-3 py-3">{{ targetStatusLabel(row.targetStatus) }}</td>
        <td class="px-3 py-3">{{ row.reportCount }}</td>
        <td
          class="px-3 py-3"
          :class="isReportOverdue(row.firstReportedAt) ? 'font-medium text-danger-deep' : ''"
        >
          {{ new Date(row.firstReportedAt).toLocaleString() }}
        </td>
        <td class="px-3 py-3">
          <span
            v-for="reason in row.reasons"
            :key="reason"
            class="ql-badge mr-1 bg-primary-tint text-primary-deep"
          >
            {{ reportReasonLabel(reason) }}
          </span>
        </td>
        <td class="px-3 py-3">
          <button
            type="button"
            class="cursor-pointer rounded-lg border border-line px-3 py-1.5 text-xs text-ink transition hover:border-primary hover:text-primary-deep"
            @click="emit('handle', row)"
          >
            处置
          </button>
        </td>
      </tr>
      <tr v-if="!props.items.length">
        <td colspan="7" class="px-3 py-10 text-center text-sm text-muted">
          {{ props.loading ? '加载中…' : '暂无待处理举报' }}
        </td>
      </tr>
    </tbody>
  </table>
</template>
