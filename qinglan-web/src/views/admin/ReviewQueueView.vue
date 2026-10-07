<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'

import DismissReportsDialog from '@/components/admin/DismissReportsDialog.vue'
import QueueTable from '@/components/admin/QueueTable.vue'
import ReviewActionBar from '@/components/admin/ReviewActionBar.vue'
import ReviewContextPanel from '@/components/admin/ReviewContextPanel.vue'
import TakeDownDialog from '@/components/admin/TakeDownDialog.vue'
import BaseButton from '@/components/base/BaseButton.vue'
import { pushToast } from '@/composables/useToast'
import { useReviewActions } from '@/composables/useReviewActions'
import { useReviewContext } from '@/composables/useReviewContext'
import { useReviewQueue } from '@/composables/useReviewQueue'
import type { AdminQueueItem, ReasonPayload, ReviewAction, TakeDownPayload } from '@/types/review'

/** 需要理由的动作 → MessageBox 标题 / 成功提示 */
const REASON_ACTIONS: Partial<Record<ReviewAction, { title: string; success: string }>> = {
  reject: { title: '驳回帖子', success: '已驳回' },
  restore: { title: '恢复帖子可见', success: '已恢复可见' },
  uphold: { title: '维持下架', success: '已维持下架' },
  'hide-contact': { title: '隐藏联系方式', success: '已隐藏联系方式' },
  'show-contact': { title: '恢复联系方式', success: '已恢复联系方式' },
  'remove-comment': { title: '移除评论', success: '已移除评论' },
}

const { items, loading, loadingMore, hasMore, failed, refresh, loadMore } = useReviewQueue()
const {
  target,
  context,
  loading: contextLoading,
  failed: contextFailed,
  open,
  close,
  reload,
} = useReviewContext()
const {
  submitting,
  approvePost,
  rejectPost,
  takeDownPost,
  restorePost,
  hidePostContact,
  showPostContact,
  removeComment,
  dismissReports,
  upholdReports,
} = useReviewActions()

const panelOpen = ref(false)
const takeDownOpen = ref(false)
const dismissOpen = ref(false)

onMounted(() => {
  void refresh()
})

/** 面板关闭时清空上下文，避免下次打开先闪出上一个目标的旧数据 */
watch(panelOpen, (open) => {
  if (!open) {
    close()
  }
})

function handleSelect(row: AdminQueueItem): void {
  panelOpen.value = true
  void open({ targetType: row.targetType, targetId: row.targetId })
}

/** 处置成功后的统一收尾：关弹窗、提示、刷新队列与上下文 */
async function afterHandled(message: string): Promise<void> {
  takeDownOpen.value = false
  dismissOpen.value = false
  pushToast(message, 'info')
  await Promise.all([refresh(), reload()])
}

/** 二次确认 + 填理由；用户取消返回 null（不是错误，静默返回） */
async function promptReason(title: string): Promise<string | null> {
  try {
    const result = await ElMessageBox.prompt('请填写理由，会写入审计日志', title, {
      confirmButtonText: '确认',
      cancelButtonText: '取消',
      inputPlaceholder: '理由',
      inputValidator: (value: string) => (value.trim().length > 0 ? true : '理由不能为空'),
    })
    return result.value.trim()
  } catch {
    return null
  }
}

async function runReasonAction(action: ReviewAction, targetId: number, payload: ReasonPayload): Promise<boolean> {
  switch (action) {
    case 'reject':
      return rejectPost(targetId, payload)
    case 'restore':
      return restorePost(targetId, payload)
    case 'uphold':
      return upholdReports(targetId, payload)
    case 'hide-contact':
      return hidePostContact(targetId, payload)
    case 'show-contact':
      return showPostContact(targetId, payload)
    case 'remove-comment':
      return removeComment(targetId, payload)
    default:
      return false
  }
}

async function handleAction(action: ReviewAction): Promise<void> {
  const current = target.value
  if (!current) {
    return
  }
  const targetId = current.targetId

  if (action === 'approve') {
    if (await approvePost(targetId)) {
      await afterHandled('已通过')
    }
    return
  }
  if (action === 'take-down') {
    takeDownOpen.value = true
    return
  }
  if (action === 'dismiss') {
    dismissOpen.value = true
    return
  }

  const meta = REASON_ACTIONS[action]
  if (!meta) {
    return
  }
  const reason = await promptReason(meta.title)
  if (reason === null) {
    return
  }
  if (await runReasonAction(action, targetId, { reason })) {
    await afterHandled(meta.success)
  }
}

async function handleTakeDown(payload: TakeDownPayload): Promise<void> {
  const current = target.value
  if (!current) {
    return
  }
  if (await takeDownPost(current.targetId, payload)) {
    await afterHandled(payload.banDays ? '已下架并封禁作者' : '已下架')
  }
}

async function handleDismiss(reason: string): Promise<void> {
  const current = target.value
  if (!current) {
    return
  }
  const payload = { targetType: current.targetType, targetId: current.targetId, reason }
  if (await dismissReports(payload)) {
    await afterHandled('已驳回举报')
  }
}
</script>

<template>
  <div class="flex flex-col gap-5">
    <div class="flex items-center justify-between">
      <h1 class="text-lg font-semibold text-ink">审核队列</h1>
      <BaseButton variant="ghost" size="sm" :loading="loading" @click="refresh">刷新</BaseButton>
    </div>

    <section class="ql-card overflow-hidden">
      <QueueTable :items="items" :loading="loading" :failed="failed" @handle="handleSelect" />

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
      <ReviewActionBar
        v-if="context"
        :target-type="context.targetType"
        :target-status="context.targetStatus"
        :submitting="submitting"
        @action="handleAction"
      />
    </ReviewContextPanel>

    <TakeDownDialog
      :open="takeDownOpen"
      :submitting="submitting"
      @submit="handleTakeDown"
      @close="takeDownOpen = false"
    />
    <DismissReportsDialog
      :open="dismissOpen"
      :submitting="submitting"
      @submit="handleDismiss"
      @close="dismissOpen = false"
    />
  </div>
</template>
