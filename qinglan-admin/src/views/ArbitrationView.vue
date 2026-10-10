<script setup lang="ts">
import { onMounted, ref } from 'vue'

import ArbitrationDialog from '@/components/admin/ArbitrationDialog.vue'
import DataTable from '@/components/admin/DataTable.vue'
import PageHeader from '@/components/admin/PageHeader.vue'
import BaseButton from '@shared/components/base/BaseButton.vue'
import LoadMoreButton from '@shared/components/LoadMoreButton.vue'
import RelativeTime from '@shared/components/RelativeTime.vue'
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
    <PageHeader title="待裁决" :refreshing="loading" @refresh="refresh" />

    <section class="ql-card overflow-clip">
      <DataTable
        :items="items"
        :colspan="5"
        :loading="loading"
        :failed="failed"
        empty-text="暂无待裁决提案"
      >
        <template #head>
          <th class="px-3 py-2.5 font-medium">内容摘要</th>
          <th class="px-3 py-2.5 font-medium">动作</th>
          <th class="px-3 py-2.5 font-medium">票数进度</th>
          <th class="px-3 py-2.5 font-medium">投票明细</th>
          <th class="px-3 py-2.5 font-medium">操作</th>
        </template>

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
                <RelativeTime class="text-muted" :iso="vote.createdAt" />
              </li>
            </ul>
            <span v-else class="text-xs text-muted">暂无投票</span>
          </td>
          <td class="px-3 py-3">
            <BaseButton size="sm" :disabled="submitting" @click="openArbitration(row)">裁决</BaseButton>
          </td>
        </tr>
      </DataTable>

      <LoadMoreButton variant="table" :has-more="hasMore" :loading="loadingMore" @load="loadMore" />
    </section>

    <ArbitrationDialog
      :open="arbitratingId !== null"
      :submitting="submitting"
      @submit="handleArbitrate"
      @close="arbitratingId = null"
    />
  </div>
</template>
