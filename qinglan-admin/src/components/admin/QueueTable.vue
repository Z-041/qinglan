<script setup lang="ts">
import BaseButton from '@shared/components/base/BaseButton.vue'
import RelativeTime from '@shared/components/RelativeTime.vue'
import DataTable from '@/components/admin/DataTable.vue'
import type { TableStateProps } from '@/components/admin/tableProps'
import { isReportOverdue } from '@/composables/useReviewQueue'
import { proposalActionLabel, proposalStatusLabel } from '@/types/proposal'
import { useUserLabels } from '@shared/composables/useUserLabels'
import type { AdminQueueItem, AdminQueueProposalProgress } from '@/types/review'

const props = defineProps<
  TableStateProps<AdminQueueItem> & {
    /** 键盘行焦点所在行的键（`${targetType}-${targetId}`）；用于高亮当前处置目标 */
    activeKey?: string | null
  }
>()

const emit = defineEmits<{ select: [item: AdminQueueItem] }>()

const { targetTypeLabel, targetStatusLabel, reportReasonLabel } = useUserLabels()

/** 行键：与 `:key` 同一口径，焦点态与 DOM 复用都据它 */
function rowKey(row: AdminQueueItem): string {
  return `${row.targetType}-${row.targetId}`
}

/** 提案进度文案：同意票进度 + 是否已投票 + 僵局提示（只展示汇总，不展示谁投了什么） */
function progressText(proposal: AdminQueueProposalProgress): string {
  const base = `${proposalActionLabel(proposal.action)} ${proposal.agreeCount}/${proposal.needed} 同意`
  if (proposal.status === 'ESCALATED') {
    return `${base} · 僵局待超管裁决`
  }
  return proposal.myDecision === null ? `${base} · 我未投` : `${base} · 我已投`
}

/** 该行是否已有进行中的提案：有则不可再发起（后端会以唯一键挡成 409） */
function hasOpenProposal(item: AdminQueueItem): boolean {
  return item.proposal !== null
}
</script>

<template>
  <DataTable
    :items="props.items"
    :colspan="9"
    :loading="props.loading"
    :failed="props.failed"
    empty-text="暂无待处理举报"
  >
    <template #head>
      <th class="px-3 py-2.5 font-medium">目标类型</th>
      <th class="px-3 py-2.5 font-medium">内容摘要</th>
      <th class="px-3 py-2.5 font-medium">图片</th>
      <th class="px-3 py-2.5 font-medium">目标状态</th>
      <th class="px-3 py-2.5 font-medium">待处理举报</th>
      <th class="px-3 py-2.5 font-medium">首报时间</th>
      <th class="px-3 py-2.5 font-medium">理由</th>
      <th class="px-3 py-2.5 font-medium">提案进度</th>
      <th class="px-3 py-2.5 font-medium">操作</th>
    </template>

    <tr
      v-for="row in props.items"
      :key="rowKey(row)"
      class="border-b border-line last:border-0"
      :class="props.activeKey === rowKey(row) ? 'bg-primary-soft' : ''"
    >
      <td class="px-3 py-3">{{ targetTypeLabel(row.targetType) }}</td>
      <td class="max-w-[320px] truncate px-3 py-3" :title="row.preview">{{ row.preview }}</td>
      <td class="px-3 py-3">
        <!-- 缩略图：审核必须看得见全部图片，只给正文摘要等于让图片随帖放行。
             点开在新标签页看原图；URL 只做 :src/:href 绑定，不拼 HTML -->
        <div v-if="row.images.length" class="flex flex-wrap gap-1">
          <a
            v-for="url in row.images"
            :key="url"
            :href="url"
            target="_blank"
            rel="noopener noreferrer"
            class="block overflow-hidden rounded border border-line"
          >
            <img :src="url" alt="帖子图片" class="h-12 w-12 object-cover" loading="lazy" />
          </a>
        </div>
        <!-- 无图时给占位，避免空列看起来像渲染坏了 -->
        <span v-else class="text-xs text-muted">—</span>
      </td>
      <td class="px-3 py-3">{{ targetStatusLabel(row.targetStatus) }}</td>
      <td class="px-3 py-3">{{ row.reportCount }}</td>
      <td
        class="px-3 py-3"
        :class="isReportOverdue(row.firstReportedAt) ? 'font-medium text-danger-deep' : ''"
      >
        <RelativeTime :iso="row.firstReportedAt" />
      </td>
      <td class="px-3 py-3">
        <span
          v-for="reason in row.reasons"
          :key="reason"
          class="ql-badge mr-1 bg-primary-tint text-primary-deep"
        >
          {{ reportReasonLabel(reason) }}
        </span>
        <!-- 举报人填的补充说明：选「其他」时这是唯一线索，故与理由一并展示 -->
        <p
          v-for="detail in row.details"
          :key="detail"
          class="max-w-[320px] truncate text-xs text-muted"
          :title="detail"
        >
          {{ detail }}
        </p>
      </td>
      <td class="px-3 py-3">
        <!-- 有进行中提案时把入口显出来：否则点下架的同事只会撞到 409 而不知道该去哪儿投票 -->
        <span
          v-if="row.proposal"
          class="ql-badge bg-warning-soft text-warning"
          :title="`提案 #${row.proposal.proposalId}：${proposalStatusLabel(row.proposal.status)}`"
        >
          {{ progressText(row.proposal) }}
        </span>
        <span v-else class="text-xs text-muted">—</span>
      </td>
      <td class="px-3 py-3">
        <BaseButton variant="ghost" size="xs" @click="emit('select', row)">
          处置
        </BaseButton>
        <span v-if="hasOpenProposal(row)" class="ml-2 text-xs text-muted">表决中</span>
      </td>
    </tr>
  </DataTable>
</template>
