<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'

import PendingPostTable from '@/components/admin/PendingPostTable.vue'
import ReviewContextPanel from '@/components/admin/ReviewContextPanel.vue'
import BaseButton from '@shared/components/base/BaseButton.vue'
import { pushToast } from '@shared/composables/useToast'
import { usePendingPostQueue } from '@/composables/usePendingPostQueue'
import { useReviewActions } from '@/composables/useReviewActions'
import { useReviewContext } from '@/composables/useReviewContext'
import type { AdminPendingPostItem } from '@/types/review'

const { items, loading, loadingMore, hasMore, failed, refresh, loadMore } = usePendingPostQueue()
const {
  target,
  context,
  loading: contextLoading,
  failed: contextFailed,
  open,
  close,
  reload,
} = useReviewContext()
const { submitting, approvePost, rejectPost } = useReviewActions()

const panelOpen = ref(false)

onMounted(() => {
  void refresh()
})

/** 面板关闭时清空上下文，避免下次打开先闪出上一个目标的旧数据 */
watch(panelOpen, (open) => {
  if (!open) {
    close()
  }
})

function handleSelect(row: AdminPendingPostItem): void {
  panelOpen.value = true
  void open({ targetType: 'POST', targetId: row.postId })
}

/** 处置成功后的统一收尾：关面板、提示、刷新队列与上下文 */
async function afterHandled(message: string): Promise<void> {
  panelOpen.value = false
  pushToast(message, 'info')
  await Promise.all([refresh(), reload()])
}

async function handleApprove(): Promise<void> {
  if (!target.value) {
    return
  }
  if (await approvePost(target.value.targetId)) {
    await afterHandled('已通过，帖子对外可见')
  }
}

/** 驳回必须填理由：理由会随通知发给作者，也会写入审计日志 */
async function handleReject(): Promise<void> {
  if (!target.value) {
    return
  }
  let reason: string
  try {
    const result = await ElMessageBox.prompt('驳回理由会发给作者，并写入审计日志', '驳回帖子', {
      confirmButtonText: '确认',
      cancelButtonText: '取消',
      inputPlaceholder: '理由',
      inputValidator: (value: string) => (value.trim().length > 0 ? true : '理由不能为空'),
    })
    reason = result.value.trim()
  } catch {
    // 用户取消不是错误，静默返回
    return
  }
  if (await rejectPost(target.value.targetId, { reason })) {
    await afterHandled('已驳回，仅作者可见')
  }
}
</script>

<template>
  <div class="flex flex-col gap-5">
    <div class="flex items-center justify-between">
      <h1 class="text-lg font-semibold text-ink">待审帖子</h1>
      <BaseButton variant="ghost" size="sm" :loading="loading" @click="refresh">刷新</BaseButton>
    </div>

    <p class="text-sm text-muted">主帖先审后发：这里的帖子尚未对外可见，放行后才会出现在信息流。</p>

    <section class="ql-card overflow-hidden">
      <PendingPostTable :items="items" :loading="loading" :failed="failed" @handle="handleSelect" />

      <div v-if="hasMore" class="border-t border-line px-3 py-3 text-center">
        <BaseButton variant="ghost" size="sm" :loading="loadingMore" @click="loadMore">加载更多</BaseButton>
      </div>
    </section>

    <ReviewContextPanel
      v-model:open="panelOpen"
      :context="context"
      :loading="contextLoading"
      :failed="contextFailed"
    >
      <!-- 只用「通过 / 驳回」两个动作，因此不挂 ReviewActionBar：那条处置栏还会给出
           「下架 / 隐藏联系方式 / 举报不成立」，而待审帖从未对外可见，下架会触发非法状态迁移 -->
      <div v-if="context" class="flex flex-wrap gap-2">
        <BaseButton variant="primary" size="sm" :disabled="submitting" :loading="submitting" @click="handleApprove">
          通过
        </BaseButton>
        <BaseButton variant="ghost" size="sm" :disabled="submitting" @click="handleReject">
          驳回
        </BaseButton>
      </div>
    </ReviewContextPanel>
  </div>
</template>
