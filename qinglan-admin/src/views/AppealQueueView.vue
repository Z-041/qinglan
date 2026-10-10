<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'

import AppealReviewDialog from '@/components/admin/AppealReviewDialog.vue'
import AppealTable from '@/components/admin/AppealTable.vue'
import PageHeader from '@/components/admin/PageHeader.vue'
import QueueShortcutHints from '@/components/admin/QueueShortcutHints.vue'
import LoadMoreButton from '@shared/components/LoadMoreButton.vue'
import { useAppealQueue } from '@/composables/useAppealQueue'
import { useDispositionFinisher } from '@/composables/useDispositionFinisher'
import { usePermissions } from '@/composables/usePermissions'
import { useQueuePolling } from '@/composables/useQueuePolling'
import { useQueueShortcuts, type QueueShortcut } from '@/composables/useQueueShortcuts'
import { useReviewActions } from '@/composables/useReviewActions'
import { useRowKeyboardNav } from '@/composables/useRowKeyboardNav'
import type { AdminAppealItem } from '@/types/review'

/** 本页只有「通过申诉 / 维持原判」两个动作，按键映射据此固定 */
type AppealAction = 'accept' | 'reject'

const SHORTCUTS: readonly QueueShortcut<AppealAction>[] = [
  { key: '1', action: 'accept', label: '通过申诉', permission: 'APPEAL_REVIEW' },
  { key: '2', action: 'reject', label: '维持原判', permission: 'APPEAL_REVIEW' },
]

const { items, loading, loadingMore, hasMore, failed, refresh, loadMore, headKeys } = useAppealQueue()
const { submitting, acceptAppeal, rejectAppeal } = useReviewActions()

const dialogOpen = ref(false)
const current = ref<AdminAppealItem | null>(null)
/** 打开弹窗时预置的结论：快捷键 1 预置「通过」、2 预置「维持原判」 */
const dialogApproved = ref(true)

/** 键盘行焦点：↑/↓ 移动高亮；本页无上下文面板，动作由数字键或点击发起 */
const {
  activeItem,
  activeKey,
  focused,
  focus: focusList,
  handleFocusOut,
  handleArrowKeys,
  setActive,
  clamp,
} = useRowKeyboardNav<AdminAppealItem>(
  () => items.value,
  (row) => String(row.appealId),
)

const shortcuts = useQueueShortcuts<AppealAction>(SHORTCUTS)

/** 与快捷键同口径：无 APPEAL_REVIEW 则「复审」按钮不渲染（AGENTS.md 第九章） */
const { can } = usePermissions()

const busy = computed(() => submitting.value || dialogOpen.value)

const { hasNewContent, applyNow } = useQueuePolling({
  fetchHeadKeys: headKeys,
  getDisplayedKeys: () => items.value.map((row) => String(row.appealId)),
  isBusy: () => busy.value,
  onRefresh: refresh,
})

onMounted(() => {
  void refresh()
})

function openReview(row: AdminAppealItem, presetApproved: boolean): void {
  setActive(items.value.indexOf(row))
  current.value = row
  dialogApproved.value = presetApproved
  dialogOpen.value = true
}

/** ↑/↓ 移焦点（未聚焦不响应），数字键按预置结论打开复审弹窗 */
function onKeydown(event: KeyboardEvent): void {
  if (handleArrowKeys(event)) {
    event.preventDefault()
    return
  }
  if (!focused.value) {
    return
  }
  const action = shortcuts.actionOf(event.key)
  const row = activeItem.value
  if (!action || !row) {
    return
  }
  event.preventDefault()
  openReview(row, action === 'accept')
}

/**
 * 处置成功后的收尾：关弹窗、提示、刷新队列，并夹回焦点。
 * 本页无上下文面板，故不推进选中（无 selectActive）。
 */
const { finish } = useDispositionFinisher({
  cleanup: () => {
    dialogOpen.value = false
    current.value = null
  },
  refresh,
  advance: () => {
    // 连续处置：被复审的条目已从队列消失，同一焦点位即下一条；越界则夹回末行
    clamp()
  },
})

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
  await finish(approved ? '已通过申诉，内容恢复可见' : '已维持原判')
}
</script>

<template>
  <div class="flex flex-col gap-5">
    <PageHeader title="申诉复审" :refreshing="loading" @refresh="refresh" />

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
      <AppealTable
        :items="items"
        :loading="loading"
        :failed="failed"
        :active-key="activeKey"
        :can-review="can('APPEAL_REVIEW')"
        @review="openReview($event, true)"
      />

      <LoadMoreButton variant="table" :has-more="hasMore" :loading="loadingMore" @load="loadMore" />
    </section>

    <AppealReviewDialog
      :open="dialogOpen"
      :submitting="submitting"
      :initial-approved="dialogApproved"
      @submit="handleSubmit"
      @close="dialogOpen = false"
    />
  </div>
</template>
