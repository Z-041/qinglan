<script setup lang="ts">
import { computed, onMounted } from 'vue'

import DataTable from '@/components/admin/DataTable.vue'
import PageHeader from '@/components/admin/PageHeader.vue'
import BaseCheckbox from '@/components/base/BaseCheckbox.vue'
import BaseSelect from '@/components/base/BaseSelect.vue'
import BaseButton from '@shared/components/base/BaseButton.vue'
import FormField from '@shared/components/base/FormField.vue'
import LoadMoreButton from '@shared/components/LoadMoreButton.vue'
import RelativeTime from '@shared/components/RelativeTime.vue'
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

/** 原生 <select> 只认字符串；ProposalStatus 是联合类型，进出各转一次 */
const statusModel = computed<string>({
  get: () => status.value,
  set: (value) => {
    status.value = value as ProposalStatus
  },
})

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
    <PageHeader title="我的提案与投票" :refreshing="loading" @refresh="refresh" />

    <!-- 筛选栏容器与其它列表页统一：独立 ql-card + FormField 标签 + items-end；改动即重查（见 useProposalList） -->
    <form class="ql-card flex flex-wrap items-end gap-4 p-4" @submit.prevent="refresh">
      <FormField label="状态">
        <BaseSelect v-model="statusModel" class="w-[160px]" :options="STATUS_OPTIONS" aria-label="提案状态" />
      </FormField>

      <div class="flex h-8 items-center">
        <BaseCheckbox v-model="mine">只看我发起的</BaseCheckbox>
      </div>

      <BaseButton html-type="submit" size="sm" :loading="loading">查询</BaseButton>
    </form>

    <section class="ql-card overflow-clip">
      <DataTable
        :items="items"
        :colspan="6"
        :loading="loading"
        :failed="failed"
        @retry="refresh"
        empty-text="暂无提案"
      >
        <template #head>
          <th scope="col" class="ql-th">内容摘要</th>
          <th scope="col" class="ql-th">动作</th>
          <th scope="col" class="ql-th">状态</th>
          <th scope="col" class="ql-th">进度</th>
          <th scope="col" class="ql-th">创建时间</th>
          <th scope="col" class="ql-th">我的选择 / 操作</th>
        </template>

        <tr v-for="row in items" :key="row.proposalId" class="ql-tr">
          <td class="max-w-[320px] truncate px-3 py-3" :title="row.preview">{{ row.preview }}</td>
          <td class="px-3 py-3">{{ proposalActionLabel(row.action) }}</td>
          <td class="px-3 py-3">{{ proposalStatusLabel(row.status) }}</td>
          <td class="px-3 py-3 tabular-nums">
            {{ row.agreeCount }}/{{ row.needed }} 同意（已投 {{ row.votedCount }} 票）
          </td>
          <td class="px-3 py-3">
            <RelativeTime :iso="row.createdAt" />
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
      </DataTable>

      <LoadMoreButton variant="table" :has-more="hasMore" :loading="loadingMore" @load="loadMore" />
    </section>
  </div>
</template>
