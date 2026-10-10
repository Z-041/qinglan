<script setup lang="ts">
import { onMounted, ref } from 'vue'

import ArbitrationDialog from '@/components/admin/ArbitrationDialog.vue'
import BaseButton from '@shared/components/base/BaseButton.vue'
import { formatAbsoluteTime, formatRelativeTime } from '@shared/composables/useRelativeTime'
import { pushToast } from '@shared/composables/useToast'
import { useArbitration } from '@/composables/useArbitration'
import {
  proposalActionLabel,
  type ArbitratePayload,
  type EscalatedProposal,
  type VoteDecision,
} from '@/types/proposal'

const { items, loading, loadingMore, hasMore, failed, submitting, refresh, loadMore, arbitrate } =
  useArbitration()

/** 正在裁决的提案 id；null 表示弹窗关闭 */
const arbitratingId = ref<number | null>(null)

onMounted(() => {
  void refresh()
})

function decisionLabel(decision: VoteDecision): string {
  return decision === 'AGREE' ? '同意' : '不同意'
}

function openArbitration(row: EscalatedProposal): void {
  arbitratingId.value = row.proposalId
}

async function handleArbitrate(payload: ArbitratePayload): Promise<void> {
  const proposalId = arbitratingId.value
  if (proposalId === null) {
    return
  }
  if (await arbitrate(proposalId, payload)) {
    arbitratingId.value = null
    pushToast(payload.decision === 'GRANT' ? '已准予处置' : '已不予处置', 'info')
  }
}
</script>

<template>
  <div class="flex flex-col gap-5">
    <div class="flex items-center justify-between">
      <h1 class="text-lg font-semibold text-ink">待裁决</h1>
      <BaseButton variant="ghost" size="sm" :loading="loading" @click="refresh">刷新</BaseButton>
    </div>

    <section class="ql-card overflow-hidden">
      <table class="w-full border-collapse text-sm">
        <thead>
          <tr class="border-b border-line text-left text-xs text-muted">
            <th class="px-3 py-2.5 font-medium">内容摘要</th>
            <th class="px-3 py-2.5 font-medium">动作</th>
            <th class="px-3 py-2.5 font-medium">票数进度</th>
            <th class="px-3 py-2.5 font-medium">投票明细</th>
            <th class="px-3 py-2.5 font-medium">操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="row in items" :key="row.proposalId" class="border-b border-line last:border-0">
            <td class="max-w-[280px] truncate px-3 py-3" :title="row.preview">{{ row.preview }}</td>
            <td class="px-3 py-3">{{ proposalActionLabel(row.action) }}</td>
            <td class="px-3 py-3 tabular-nums">
              {{ row.agreeCount }}/{{ row.needed }} 同意（已投 {{ row.votedCount }} 票）
            </td>
            <td class="px-3 py-3">
              <ul v-if="row.votes.length" class="flex flex-col gap-1">
                <li v-for="vote in row.votes" :key="vote.voterId" class="text-xs text-muted">
                  投票人 #{{ vote.voterId }} · {{ decisionLabel(vote.decision) }} ·
                  <span :title="formatAbsoluteTime(vote.createdAt)">{{ formatRelativeTime(vote.createdAt) }}</span>
                </li>
              </ul>
              <span v-else class="text-xs text-muted">暂无投票</span>
            </td>
            <td class="px-3 py-3">
              <BaseButton size="sm" :disabled="submitting" @click="openArbitration(row)">裁决</BaseButton>
            </td>
          </tr>
          <tr v-if="!items.length">
            <td colspan="5" class="px-3 py-10 text-center text-sm text-muted">
              {{ loading ? '加载中…' : failed ? '加载失败，请点击「刷新」重试' : '暂无待裁决提案' }}
            </td>
          </tr>
        </tbody>
      </table>

      <div v-if="hasMore" class="border-t border-line px-3 py-3 text-center">
        <BaseButton variant="ghost" size="sm" :loading="loadingMore" @click="loadMore">加载更多</BaseButton>
      </div>
    </section>

    <ArbitrationDialog
      :open="arbitratingId !== null"
      :submitting="submitting"
      @submit="handleArbitrate"
      @close="arbitratingId = null"
    />
  </div>
</template>
