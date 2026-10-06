<script setup lang="ts">
import { onMounted, ref } from 'vue'

import AppealReviewDialog from '@/components/admin/AppealReviewDialog.vue'
import AppealTable from '@/components/admin/AppealTable.vue'
import BaseButton from '@/components/base/BaseButton.vue'
import { pushToast } from '@/composables/useToast'
import { useAppealQueue } from '@/composables/useAppealQueue'
import { useReviewActions } from '@/composables/useReviewActions'
import type { AdminAppealItem } from '@/types/review'

const { items, loading, loadingMore, hasMore, refresh, loadMore } = useAppealQueue()
const { submitting, acceptAppeal, rejectAppeal } = useReviewActions()

const dialogOpen = ref(false)
const current = ref<AdminAppealItem | null>(null)

onMounted(() => {
  void refresh()
})

function openReview(row: AdminAppealItem): void {
  current.value = row
  dialogOpen.value = true
}

async function handleSubmit(approved: boolean, reason: string): Promise<void> {
  const row = current.value
  if (!row) {
    return
  }
  const ok = approved ? await acceptAppeal(row.appealId, { reason }) : await rejectAppeal(row.appealId, { reason })
  if (!ok) {
    // 失败原因（含「复审人不得是原处置人」）已由拦截器提示，弹窗保持打开便于改判
    return
  }
  dialogOpen.value = false
  current.value = null
  pushToast(approved ? '已通过申诉，内容恢复可见' : '已维持原判', 'info')
  await refresh()
}
</script>

<template>
  <div class="flex flex-col gap-5">
    <div class="flex items-center justify-between">
      <h1 class="text-lg font-semibold text-ink">申诉复审</h1>
      <BaseButton variant="ghost" size="sm" :loading="loading" @click="refresh">刷新</BaseButton>
    </div>

    <section class="ql-card overflow-hidden">
      <AppealTable :items="items" :loading="loading" @review="openReview" />

      <div v-if="hasMore" class="border-t border-line px-3 py-3 text-center">
        <BaseButton variant="ghost" size="sm" :loading="loadingMore" @click="loadMore">加载更多</BaseButton>
      </div>
    </section>

    <AppealReviewDialog
      :open="dialogOpen"
      :submitting="submitting"
      @submit="handleSubmit"
      @close="dialogOpen = false"
    />
  </div>
</template>
