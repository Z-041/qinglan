<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'

import PendingPostTable from '@/components/admin/PendingPostTable.vue'
import QueueShortcutHints from '@/components/admin/QueueShortcutHints.vue'
import ReviewContextPanel from '@/components/admin/ReviewContextPanel.vue'
import BaseButton from '@shared/components/base/BaseButton.vue'
import { useDispositionFinisher } from '@/composables/useDispositionFinisher'
import { usePendingPostQueue } from '@/composables/usePendingPostQueue'
import { usePermissions } from '@/composables/usePermissions'
import { useQueuePolling } from '@/composables/useQueuePolling'
import { useQueueShortcuts, type QueueShortcut } from '@/composables/useQueueShortcuts'
import { useReviewActions } from '@/composables/useReviewActions'
import { useReviewContext } from '@/composables/useReviewContext'
import { useRowKeyboardNav } from '@/composables/useRowKeyboardNav'
import type { AdminPendingPostItem } from '@/types/review'

/** 本页只有「通过 / 驳回」两个动作，按键映射据此固定（方案：按各页实际动作集适配） */
type PendingAction = 'approve' | 'reject'

const SHORTCUTS: readonly QueueShortcut<PendingAction>[] = [
  { key: '1', action: 'approve', label: '通过', permission: 'POST_REVIEW' },
  { key: '2', action: 'reject', label: '驳回', permission: 'POST_REVIEW' },
]

const { items, loading, loadingMore, hasMore, failed, refresh, loadMore, headKeys } = usePendingPostQueue()
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
const { submitting, approvePost, rejectPost } = useReviewActions()

const panelOpen = ref(false)
/** 驳回理由弹窗（ElMessageBox）打开期间计入「处置进行中」，轮询不得替换列表 */
const reasonPromptOpen = ref(false)

/** 键盘行焦点：↑/↓ 移动，面板随焦点行自动加载 */
const {
  activeItem,
  activeKey,
  focused,
  focus: focusList,
  handleFocusOut,
  handleArrowKeys,
  setActive,
  clamp,
} = useRowKeyboardNav<AdminPendingPostItem>(
  () => items.value,
  (row) => String(row.postId),
)

const shortcuts = useQueueShortcuts<PendingAction>(SHORTCUTS)

const { can } = usePermissions()

const busy = computed(() => submitting.value || reasonPromptOpen.value)

const { hasNewContent, applyNow } = useQueuePolling({
  fetchHeadKeys: headKeys,
  getDisplayedKeys: () => items.value.map((row) => String(row.postId)),
  isBusy: () => busy.value,
  onRefresh: refresh,
})

onMounted(() => {
  void refresh()
})

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
  void open({ targetType: 'POST', targetId: row.postId })
}

function handleSelect(row: AdminPendingPostItem): void {
  setActive(items.value.indexOf(row))
  selectActive()
}

/** ↑/↓ 移焦点（未聚焦不响应），数字键触发通过 / 驳回 */
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
  if (action === 'approve') {
    event.preventDefault()
    void handleApprove()
  } else if (action === 'reject') {
    event.preventDefault()
    void handleReject()
  }
}

/**
 * 处置成功后的收尾：清理、提示、刷新队列，并把焦点推进到下一条（连续处置）。
 *
 * 本页无弹窗可关（理由弹窗的开关在自身 finally 里复位），故不传 cleanup；
 * 也不刷新上下文：面板随焦点行切换会自动 `open()`，旧的上下文已被新目标覆盖。
 */
const { finish } = useDispositionFinisher({
  refresh,
  advance: () => {
    clamp()
    selectActive()
  },
})

async function handleApprove(): Promise<void> {
  if (!target.value) {
    return
  }
  if (await approvePost(target.value.targetId)) {
    await finish('已通过，帖子对外可见')
  }
}

/** 驳回必须填理由：理由会随通知发给作者，也会写入审计日志 */
async function handleReject(): Promise<void> {
  if (!target.value) {
    return
  }
  let reason: string
  reasonPromptOpen.value = true
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
  } finally {
    reasonPromptOpen.value = false
  }
  if (await rejectPost(target.value.targetId, { reason })) {
    await finish('已驳回，仅作者可见')
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
      <PendingPostTable
        :items="items"
        :loading="loading"
        :failed="failed"
        :active-key="activeKey"
        @handle="handleSelect"
      />

      <div v-if="hasMore" class="border-t border-line px-3 py-3 text-center">
        <BaseButton variant="ghost" size="sm" :loading="loadingMore" @click="loadMore">加载更多</BaseButton>
      </div>
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
      <!-- 只用「通过 / 驳回」两个动作，因此不挂 ReviewActionBar：那条处置栏还会给出
           「下架 / 隐藏联系方式 / 举报不成立」，而待审帖从未对外可见，下架会触发非法状态迁移。
           与快捷键同口径：无 POST_REVIEW 则不渲染（AGENTS.md 第九章） -->
      <div v-if="context && can('POST_REVIEW')" class="flex flex-wrap gap-2">
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
