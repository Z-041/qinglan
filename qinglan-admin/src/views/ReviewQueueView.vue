<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'

import DismissReportsDialog from '@/components/admin/DismissReportsDialog.vue'
import ProposalReasonDialog from '@/components/admin/ProposalReasonDialog.vue'
import QueueTable from '@/components/admin/QueueTable.vue'
import ReviewActionBar from '@/components/admin/ReviewActionBar.vue'
import ReviewContextPanel from '@/components/admin/ReviewContextPanel.vue'
import BaseButton from '@shared/components/base/BaseButton.vue'
import { pushToast } from '@shared/composables/useToast'
import { useReviewActions } from '@/composables/useReviewActions'
import { usePermissions } from '@/composables/usePermissions'
import { useReviewContext } from '@/composables/useReviewContext'
import { useReviewQueue } from '@/composables/useReviewQueue'
import type {
  AdminQueueItem,
  ProposalDispositionPayload,
  ReasonPayload,
  ReviewAction,
} from '@/types/review'

/**
 * 二期起转为「发起提案」的四个动作（P5 决策 D5）：不再直接处置，需委员会表决。
 * 标题与确认文案按动作区分，其余交给通用的 ProposalReasonDialog。
 */
const PROPOSAL_ACTIONS: Partial<Record<ReviewAction, { title: string; confirmText: string }>> = {
  'take-down': { title: '发起下架提案', confirmText: '发起下架提案' },
  'hide-contact': { title: '发起隐藏联系方式提案', confirmText: '发起隐藏联系方式提案' },
  uphold: { title: '发起维持下架提案', confirmText: '发起维持下架提案' },
  'remove-comment': { title: '发起移除评论提案', confirmText: '发起移除评论提案' },
}

/** 仍需单人处置、只填自由文本理由的动作 → MessageBox 标题 / 成功提示 */
const REASON_ACTIONS: Partial<Record<ReviewAction, { title: string; success: string }>> = {
  reject: { title: '驳回帖子', success: '已驳回' },
  restore: { title: '恢复帖子可见', success: '已恢复可见' },
  'show-contact': { title: '恢复联系方式', success: '已恢复联系方式' },
}

/** 发起提案成功后的统一提示：把用户引到「我的提案与投票」跟进后续表决 */
const PROPOSAL_TOAST = '已发起处置提案，请在「我的提案与投票」中跟进'

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

const { can } = usePermissions()

/** 撤销已作出的处置（恢复可见 / 撤销隐藏）仅超管：前端据此隐藏按钮，后端注解才是防线 */
const canRevoke = computed(() => can('DISPOSITION_REVOKE'))

const panelOpen = ref(false)
const dismissOpen = ref(false)
const proposalOpen = ref(false)
/** 本次发起的提案动作；决定 ProposalReasonDialog 的标题与提交后调哪个接口 */
const proposalAction = ref<ReviewAction | null>(null)

/**
 * 当前面板目标的进行中提案：直接从队列数据派生，而不是另存一份状态。
 * 发起提案成功后会 refresh()，派生值随之更新，按钮立刻变为禁用——不必手工同步两份状态。
 */
const selectedProposal = computed(() => {
  const current = target.value
  if (!current) {
    return null
  }
  return (
    items.value.find(
      (row) => row.targetType === current.targetType && row.targetId === current.targetId,
    )?.proposal ?? null
  )
})

const proposalMeta = computed(
  () =>
    (proposalAction.value ? PROPOSAL_ACTIONS[proposalAction.value] : undefined) ?? {
      title: '',
      confirmText: '',
    },
)

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

/** 单人处置动作（仍直接执行）；发起提案的动作不走这里 */
async function runReasonAction(action: ReviewAction, targetId: number, payload: ReasonPayload): Promise<boolean> {
  switch (action) {
    case 'reject':
      return rejectPost(targetId, payload)
    case 'restore':
      return restorePost(targetId, payload)
    case 'show-contact':
      return showPostContact(targetId, payload)
    default:
      return false
  }
}

/** 发起提案：四个动作各自对应一个「不直接处置」的端点，返回提案号 */
async function runProposalAction(
  action: ReviewAction,
  targetId: number,
  payload: ProposalDispositionPayload,
): Promise<boolean> {
  switch (action) {
    case 'take-down':
      return takeDownPost(targetId, payload)
    case 'hide-contact':
      return hidePostContact(targetId, payload)
    case 'uphold':
      return upholdReports(targetId, payload)
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
  if (action === 'dismiss') {
    dismissOpen.value = true
    return
  }
  if (PROPOSAL_ACTIONS[action]) {
    proposalAction.value = action
    proposalOpen.value = true
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

async function handleProposalSubmit(payload: ProposalDispositionPayload): Promise<void> {
  const current = target.value
  const action = proposalAction.value
  if (!current || !action) {
    return
  }
  if (await runProposalAction(action, current.targetId, payload)) {
    proposalOpen.value = false
    proposalAction.value = null
    pushToast(PROPOSAL_TOAST, 'info')
    await Promise.all([refresh(), reload()])
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
        :has-open-proposal="selectedProposal !== null"
        :can-revoke="canRevoke"
        @action="handleAction"
      />
    </ReviewContextPanel>

    <ProposalReasonDialog
      :open="proposalOpen"
      :submitting="submitting"
      :title="proposalMeta.title"
      :confirm-text="proposalMeta.confirmText"
      @submit="handleProposalSubmit"
      @close="proposalOpen = false"
    />
    <DismissReportsDialog
      :open="dismissOpen"
      :submitting="submitting"
      @submit="handleDismiss"
      @close="dismissOpen = false"
    />
  </div>
</template>
