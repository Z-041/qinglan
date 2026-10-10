<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'

import DismissReportsDialog from '@/components/admin/DismissReportsDialog.vue'
import ProposalReasonDialog from '@/components/admin/ProposalReasonDialog.vue'
import QueueShortcutHints from '@/components/admin/QueueShortcutHints.vue'
import QueueTable from '@/components/admin/QueueTable.vue'
import ReviewActionBar from '@/components/admin/ReviewActionBar.vue'
import ReviewContextPanel from '@/components/admin/ReviewContextPanel.vue'
import BaseButton from '@shared/components/base/BaseButton.vue'
import LoadMoreButton from '@shared/components/LoadMoreButton.vue'
import { useUserLabels } from '@shared/composables/useUserLabels'
import { REVIEW_ACTION_FLOW } from '@/composables/reviewActionFlow'
import { availableReviewActions, blockedByOpenProposal, REVIEW_ACTION_META } from '@/composables/reviewActionSet'
import { useDispositionFinisher } from '@/composables/useDispositionFinisher'
import { usePermissions } from '@/composables/usePermissions'
import { useQueuePolling } from '@/composables/useQueuePolling'
import { useQueueShortcuts, type QueueShortcut } from '@/composables/useQueueShortcuts'
import { useReviewActions } from '@/composables/useReviewActions'
import { useReviewContext } from '@/composables/useReviewContext'
import { useReviewQueue } from '@/composables/useReviewQueue'
import { useRowKeyboardNav } from '@/composables/useRowKeyboardNav'
import type { ReviewTargetType } from '@shared/types/review-common'
import {
  createQueueFilters,
  REPORT_REASONS,
  type AdminQueueItem,
  type ProposalDispositionPayload,
  type ReasonPayload,
  type ReviewAction,
  type ReviewQueueSort,
} from '@/types/review'

/**
 * 二期起转为「发起提案」的四个动作（P5 决策 D5）：不再直接处置，需委员会表决。
 * 动作的输入类别与文案元数据都在 `reviewActionFlow` / `reviewActionSet` 一处，视图不再各存一份。
 */

const { filters, filtersDirty, items, loading, loadingMore, hasMore, failed, refresh, loadMore, headKeys } =
  useReviewQueue()
const {
  target,
  context,
  history,
  loading: contextLoading,
  historyLoading,
  failed: contextFailed,
  historyFailed,
  open,
  close,
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
const { reportReasonLabel, targetTypeLabel } = useUserLabels()

/** 撤销已作出的处置（恢复可见 / 撤销隐藏）仅超管：前端据此隐藏按钮，后端注解才是防线 */
const canRevoke = computed(() => can('DISPOSITION_REVOKE'))

/** 下拉控件用字符串：'' 表示「不限」，与 null 互转集中在此，模板不各转一遍 */
const targetTypeFilter = computed<string>({
  get: () => filters.value.targetType ?? '',
  set: (value) => {
    filters.value.targetType = value === '' ? null : (value as ReviewTargetType)
  },
})

const SORT_OPTIONS: { value: ReviewQueueSort; label: string }[] = [
  { value: 'REPORT_COUNT_DESC', label: '举报数优先' },
  { value: 'FIRST_REPORTED_ASC', label: '等得最久优先' },
]

/** 重置筛选并重查（回到第一页）；watch 会接管刷新，这里直接改表单值即可 */
function resetFilters(): void {
  filters.value = createQueueFilters()
}

const panelOpen = ref(false)
const dismissOpen = ref(false)
const proposalOpen = ref(false)
/** 理由弹窗（ElMessageBox）打开期间也计入「处置进行中」，轮询不得在此期间替换列表 */
const reasonPromptOpen = ref(false)
/** 本次发起的提案动作；决定 ProposalReasonDialog 的标题与提交后调哪个接口 */
const proposalAction = ref<ReviewAction | null>(null)

/** 键盘行焦点：↑/↓ 移动，面板随焦点行自动加载（方案「行焦点」） */
const {
  activeItem,
  activeKey,
  focused,
  focus: focusList,
  handleFocusOut,
  handleArrowKeys,
  setActive,
  clamp,
  reset: resetFocus,
} = useRowKeyboardNav<AdminQueueItem>(
  () => items.value,
  (row) => `${row.targetType}-${row.targetId}`,
)

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

const proposalMeta = computed(() => {
  const action = proposalAction.value
  if (!action) {
    return { title: '', confirmText: '' }
  }
  const title = REVIEW_ACTION_META[action].dialogTitle ?? REVIEW_ACTION_META[action].label
  return { title, confirmText: title }
})

/**
 * 一键处置快捷键：只取「当前焦点目标可用且本账号有权限」的动作。
 * 有进行中提案时，会撞 409 的发起类动作既不渲染也不响应。
 */
const shortcuts = useQueueShortcuts<ReviewAction>(
  computed<readonly QueueShortcut<ReviewAction>[]>(() => {
    const current = context.value
    if (!current) {
      return []
    }
    return availableReviewActions({
      targetType: current.targetType,
      targetStatus: current.targetStatus,
      canRevoke: canRevoke.value,
    })
      .filter((action) => !blockedByOpenProposal(action.key, selectedProposal.value !== null))
      .map((action) => ({
        key: action.shortcut,
        action: action.key,
        label: action.label,
        permission: action.permission,
      }))
  }),
)

/** 处置进行中：弹窗打开或提交中。轮询在此期间只挂角标，绝不替换列表 */
const busy = computed(
  () => submitting.value || proposalOpen.value || dismissOpen.value || reasonPromptOpen.value,
)

const { hasNewContent, applyNow, clear: clearNewContent } = useQueuePolling({
  fetchHeadKeys: headKeys,
  getDisplayedKeys: () => items.value.map((row) => `${row.targetType}-${row.targetId}`),
  isBusy: () => busy.value,
  onRefresh: refresh,
})

onMounted(() => {
  void refresh()
})

/**
 * 筛选变化：重置游标回第一页（refresh 内部已重置），并同步重置行焦点与面板——
 * 换了筛选口径后旧的焦点行已不在结果集里，面板继续挂着前一条会误导处置。
 * 旧的「有新内容」角标也不对应当前口径，一并清除。
 */
watch(
  filters,
  () => {
    panelOpen.value = false
    resetFocus()
    clearNewContent()
    void refresh()
  },
  { deep: true },
)

/** 面板关闭时清空上下文，避免下次打开先闪出上一个目标的旧数据 */
watch(panelOpen, (open) => {
  if (!open) {
    close()
  }
})

/** 把焦点行选中并加载其上下文面板 */
function selectActive(): void {
  const row = activeItem.value
  if (!row) {
    panelOpen.value = false
    return
  }
  panelOpen.value = true
  void open({ targetType: row.targetType, targetId: row.targetId })
}

function handleSelect(row: AdminQueueItem): void {
  setActive(items.value.indexOf(row))
  selectActive()
}

/**
 * 列表容器上的按键：↑/↓ 移焦点（未聚焦时一概不响应），数字键触发对应动作。
 * 弹窗（Teleport 到 body）与搜索框的按键不会冒泡到这里，故抢不走输入。
 */
function onKeydown(event: KeyboardEvent): void {
  if (handleArrowKeys(event)) {
    event.preventDefault()
    selectActive()
    return
  }
  if (!focused.value) {
    return
  }
  const action = shortcuts.actionOf(event.key)
  if (action) {
    event.preventDefault()
    void handleAction(action)
  }
}

/** 单人处置执行器（输入自由文本理由）：动作 → 执行器 */
const REASON_EXECUTORS: Partial<
  Record<ReviewAction, (targetId: number, payload: ReasonPayload) => Promise<boolean>>
> = {
  reject: rejectPost,
  restore: restorePost,
  'show-contact': showPostContact,
}

/** 发起提案执行器（输入提案载荷）：动作 → 执行器 */
const PROPOSAL_EXECUTORS: Partial<
  Record<ReviewAction, (targetId: number, payload: ProposalDispositionPayload) => Promise<boolean>>
> = {
  'take-down': takeDownPost,
  'hide-contact': hidePostContact,
  uphold: upholdReports,
  'remove-comment': removeComment,
}

/** 处置成功后的统一收尾：关弹窗、提示、刷新队列，并把焦点推进到下一条（连续处置） */
const { finish } = useDispositionFinisher({
  cleanup: () => {
    proposalOpen.value = false
    dismissOpen.value = false
    proposalAction.value = null
  },
  refresh,
  advance: () => {
    // 被处置的条目已从队列消失，同一焦点位即下一条；越界则夹回末行
    clamp()
    selectActive()
  },
})

/** 二次确认 + 填理由；用户取消返回 null（不是错误，静默返回） */
async function promptReason(title: string): Promise<string | null> {
  reasonPromptOpen.value = true
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
  } finally {
    reasonPromptOpen.value = false
  }
}

/** 按输入类别数据驱动分发；执行器映射与文案元数据见文件上方与 reviewActionFlow / reviewActionSet */
async function handleAction(action: ReviewAction): Promise<void> {
  const current = target.value
  if (!current) {
    return
  }
  const targetId = current.targetId
  const meta = REVIEW_ACTION_META[action]

  switch (REVIEW_ACTION_FLOW[action]) {
    case 'direct':
      if (await approvePost(targetId)) {
        await finish(meta.successText)
      }
      return
    case 'reason': {
      const reason = await promptReason(meta.dialogTitle ?? meta.label)
      if (reason === null) {
        return
      }
      const executor = REASON_EXECUTORS[action]
      if (executor && (await executor(targetId, { reason }))) {
        await finish(meta.successText)
      }
      return
    }
    case 'dialog':
      // 「举报不成立」走专用弹窗；其余四个是发起提案（P5 决策 D5）
      if (action === 'dismiss') {
        dismissOpen.value = true
      } else {
        proposalAction.value = action
        proposalOpen.value = true
      }
      return
  }
}

async function handleProposalSubmit(payload: ProposalDispositionPayload): Promise<void> {
  const current = target.value
  const action = proposalAction.value
  if (!current || !action) {
    return
  }
  const executor = PROPOSAL_EXECUTORS[action]
  if (executor && (await executor(current.targetId, payload))) {
    await finish(REVIEW_ACTION_META[action].successText)
  }
}

async function handleDismiss(reason: string): Promise<void> {
  const current = target.value
  if (!current) {
    return
  }
  const payload = { targetType: current.targetType, targetId: current.targetId, reason }
  if (await dismissReports(payload)) {
    await finish(REVIEW_ACTION_META.dismiss.successText)
  }
}
</script>

<template>
  <div class="flex flex-col gap-5">
    <div class="flex items-center justify-between">
      <h1 class="text-lg font-semibold text-ink">审核队列</h1>
      <BaseButton variant="ghost" size="sm" :loading="loading" @click="refresh">刷新</BaseButton>
    </div>

    <!-- 筛选一律作为查询参数传后端（不前端本地过滤），切换后 watch 会重置游标回第一页 -->
    <section class="ql-card flex flex-wrap items-end gap-3 p-4">
      <label class="flex flex-col gap-1">
        <span class="ql-label">举报理由</span>
        <el-select v-model="filters.reasons" multiple collapse-tags class="w-[280px]" placeholder="全部理由">
          <el-option
            v-for="reason in REPORT_REASONS"
            :key="reason"
            :label="reportReasonLabel(reason)"
            :value="reason"
          />
        </el-select>
      </label>

      <label class="flex flex-col gap-1">
        <span class="ql-label">目标类型</span>
        <el-select v-model="targetTypeFilter" class="w-[130px]" placeholder="全部">
          <el-option label="全部类型" value="" />
          <el-option :label="targetTypeLabel('POST')" value="POST" />
          <el-option :label="targetTypeLabel('COMMENT')" value="COMMENT" />
        </el-select>
      </label>

      <label class="flex flex-col gap-1">
        <span class="ql-label">排序</span>
        <el-select v-model="filters.sort" class="w-[160px]">
          <el-option
            v-for="option in SORT_OPTIONS"
            :key="option.value"
            :label="option.label"
            :value="option.value"
          />
        </el-select>
      </label>

      <label class="flex items-center gap-2 pb-2.5">
        <el-switch v-model="filters.overdueOnly" />
        <span class="text-sm text-ink">只看超时</span>
      </label>

      <div class="pb-1.5">
        <BaseButton variant="ghost" size="sm" @click="resetFilters">重置</BaseButton>
      </div>
    </section>

    <!-- 轮询到列表有变化时提示；处置进行中只挂角标，点击后再合并刷新 -->
    <button
      v-if="hasNewContent"
      type="button"
      class="cursor-pointer rounded-lg border border-warning bg-warning-soft px-3 py-2 text-left text-sm text-warning"
      @click="applyNow"
    >
      列表有新内容，点击刷新
    </button>

    <QueueShortcutHints :bindings="shortcuts.bindings.value" />

    <section
      class="ql-card overflow-clip outline-none focus-visible:ring-2 focus-visible:ring-primary-soft"
      tabindex="0"
      @focusin="focusList"
      @focusout="handleFocusOut"
      @keydown="onKeydown"
    >
      <QueueTable
        :items="items"
        :loading="loading"
        :failed="failed"
        :active-key="activeKey"
        @handle="handleSelect"
      />

      <LoadMoreButton
        variant="table"
        :has-more="hasMore"
        :loading="loadingMore"
        :disabled="filtersDirty"
        :hint="filtersDirty ? '筛选条件已修改，正在重新加载，请稍候再继续加载' : ''"
        @load="loadMore"
      />
    </section>

    <ReviewContextPanel
      v-model:open="panelOpen"
      :context="context"
      :history="history"
      :loading="contextLoading"
      :history-loading="historyLoading"
      :failed="contextFailed"
      :history-failed="historyFailed"
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
