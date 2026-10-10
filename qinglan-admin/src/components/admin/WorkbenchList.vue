<script setup lang="ts">
import QueueShortcutHints from '@/components/admin/QueueShortcutHints.vue'
import LoadMoreButton from '@shared/components/LoadMoreButton.vue'

/**
 * 审核工作台三页（审核队列 / 待审帖子 / 申诉复审）共用的列表骨架：
 * 轮询「有新内容」提示条 + 快捷键提示 + **可聚焦的列表容器** + 加载更多。
 *
 * 收的是三处逐字重复的两样东西：
 *  1. 提示条与快捷键提示的渲染；
 *  2. 列表容器的键盘契约——`tabindex="0"` + 焦点环 + focusin/focusout/keydown 三个入口，
 *     三页必须完全一致，否则「先点列表再按数字键」的交互会在某一页失效。
 *
 * 只做外壳：行焦点、轮询判定、连续处置收尾仍由各页自己的 composable 持有
 * （`useRowKeyboardNav` / `useQueuePolling` / `useDispositionFinisher`），
 * 本组件把 DOM 事件原样上抛，不接管任何业务状态。
 */
withDefaults(
  defineProps<{
    /** 轮询检测到列表有变化 */
    hasNewContent?: boolean
    hasMore: boolean
    loadingMore?: boolean
    /** 本页可用快捷键绑定（已按权限过滤），仅用于提示条 */
    shortcuts?: readonly { key: string; label: string }[]
    /** 加载更多上方的条件提示（如「筛选条件已修改，请先查询」） */
    loadMoreHint?: string
    /** 加载更多禁用（与 loadMoreHint 搭配，如筛选脏态） */
    loadMoreDisabled?: boolean
  }>(),
  { hasNewContent: false, loadingMore: false, shortcuts: () => [], loadMoreHint: '', loadMoreDisabled: false },
)

const emit = defineEmits<{
  focusin: [event: FocusEvent]
  focusout: [event: FocusEvent]
  keydown: [event: KeyboardEvent]
  load: []
  'apply-now': []
}>()
</script>

<template>
  <!-- 多根片段：三块在页面里是同级兄弟（提示条 / 提示 / 列表卡），保持原有 DOM 结构与间距节奏 -->
  <button
    v-if="hasNewContent"
    type="button"
    class="cursor-pointer rounded-lg border border-warning bg-warning-soft px-3 py-2 text-left text-sm text-warning"
    @click="emit('apply-now')"
  >
    列表有新内容，点击刷新
  </button>

  <QueueShortcutHints :bindings="shortcuts" />

  <section
    class="ql-card overflow-clip outline-none focus-visible:ring-2 focus-visible:ring-primary-soft"
    tabindex="0"
    @focusin="emit('focusin', $event)"
    @focusout="emit('focusout', $event)"
    @keydown="emit('keydown', $event)"
  >
    <slot />
    <LoadMoreButton
      variant="table"
      :has-more="hasMore"
      :loading="loadingMore"
      :disabled="loadMoreDisabled"
      :hint="loadMoreHint"
      @load="emit('load')"
    />
  </section>
</template>
