<script setup lang="ts">
import { onMounted } from 'vue'

import BaseButton from '@shared/components/base/BaseButton.vue'
import { formatAbsoluteTime, formatRelativeTime } from '@shared/composables/useRelativeTime'
import { pushToast } from '@shared/composables/useToast'
import { useProposalList } from '@/composables/useProposalList'
import {
  proposalActionLabel,
  proposalStatusLabel,
  type ProposalStatus,
  type ProposalSummary,
  type VoteDecision,
} from '@/types/proposal'

/** 顶部筛选只提供这两态：进行中（可投票）与僵局（等超管裁决） */
const STATUS_OPTIONS: { value: ProposalStatus; label: string }[] = [
  { value: 'OPEN', label: '进行中' },
  { value: 'ESCALATED', label: '僵局' },
]

const { items, loading, loadingMore, hasMore, failed, submitting, status, mine, refresh, loadMore, vote } =
  useProposalList()

onMounted(() => {
  void refresh()
})

function decisionLabel(decision: VoteDecision): string {
  return decision === 'AGREE' ? '我已投同意' : '我已投不同意'
}

/** 进行中且我尚未投票时才能投票 */
function canVote(row: ProposalSummary): boolean {
  return row.status === 'OPEN' && row.myDecision === null
}

async function handleVote(row: ProposalSummary, decision: VoteDecision): Promise<void> {
  if (await vote(row.proposalId, decision)) {
    pushToast(decision === 'AGREE' ? '已投同意' : '已投不同意', 'info')
  }
}
</script>

<template>
  <div class="flex flex-col gap-5">
    <div class="flex items-center justify-between">
      <h1 class="text-lg font-semibold text-ink">我的提案与投票</h1>
      <BaseButton variant="ghost" size="sm" :loading="loading" @click="refresh">刷新</BaseButton>
    </div>

    <div class="flex flex-wrap items-center gap-4">
      <label class="flex items-center gap-2 text-sm text-ink">
        <span class="text-muted">状态</span>
        <select v-model="status" class="ql-field">
          <option v-for="option in STATUS_OPTIONS" :key="option.value" :value="option.value">
            {{ option.label }}
          </option>
        </select>
      </label>

      <label class="flex cursor-pointer items-center gap-2 text-sm text-ink">
        <input v-model="mine" type="checkbox" class="cursor-pointer" />
        只看我发起的
      </label>
    </div>

    <section class="ql-card overflow-hidden">
      <table class="w-full border-collapse text-sm">
        <thead>
          <tr class="border-b border-line text-left text-xs text-muted">
            <th class="px-3 py-2.5 font-medium">内容摘要</th>
            <th class="px-3 py-2.5 font-medium">动作</th>
            <th class="px-3 py-2.5 font-medium">状态</th>
            <th class="px-3 py-2.5 font-medium">进度</th>
            <th class="px-3 py-2.5 font-medium">创建时间</th>
            <th class="px-3 py-2.5 font-medium">我的选择 / 操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="row in items" :key="row.proposalId" class="border-b border-line last:border-0">
            <td class="max-w-[320px] truncate px-3 py-3" :title="row.preview">{{ row.preview }}</td>
            <td class="px-3 py-3">{{ proposalActionLabel(row.action) }}</td>
            <td class="px-3 py-3">{{ proposalStatusLabel(row.status) }}</td>
            <td class="px-3 py-3">
              {{ row.agreeCount }}/{{ row.needed }} 同意（已投 {{ row.votedCount }} 票）
            </td>
            <td class="px-3 py-3" :title="formatAbsoluteTime(row.createdAt)">
              {{ formatRelativeTime(row.createdAt) }}
            </td>
            <td class="px-3 py-3">
              <span v-if="row.myDecision" class="text-xs text-muted">{{ decisionLabel(row.myDecision) }}</span>
              <div v-else-if="canVote(row)" class="flex gap-2">
                <BaseButton size="sm" :disabled="submitting" @click="handleVote(row, 'AGREE')">同意</BaseButton>
                <BaseButton variant="ghost" size="sm" :disabled="submitting" @click="handleVote(row, 'DISAGREE')">
                  不同意
                </BaseButton>
              </div>
              <span v-else class="text-xs text-muted">—</span>
            </td>
          </tr>
          <tr v-if="!items.length">
            <td colspan="6" class="px-3 py-10 text-center text-sm text-muted">
              {{ loading ? '加载中…' : failed ? '加载失败，请点击「刷新」重试' : '暂无提案' }}
            </td>
          </tr>
        </tbody>
      </table>

      <div v-if="hasMore" class="border-t border-line px-3 py-3 text-center">
        <BaseButton variant="ghost" size="sm" :loading="loadingMore" @click="loadMore">加载更多</BaseButton>
      </div>
    </section>
  </div>
</template>
