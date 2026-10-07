<script setup lang="ts">
import { computed } from 'vue'

/**
 * 纯展示组件：只接收未读数、只负责渲染红点。
 *
 * 入口**必须是 `RouterLink` 而非 `<button>`**（P4 决策 D11）：`AppHeader.spec.ts`
 * 断言「已认证时组件内仅一个 `<button>`」（退出按钮），加按钮会打破该不变量。
 */
const props = withDefaults(defineProps<{ unreadCount?: number }>(), { unreadCount: 0 })

const badgeText = computed(() => (props.unreadCount > 99 ? '99+' : String(props.unreadCount)))
</script>

<template>
  <RouterLink
    class="relative inline-flex size-9 items-center justify-center rounded-lg text-muted transition hover:bg-primary-tint hover:text-primary-deep"
    to="/notifications"
    :aria-label="props.unreadCount > 0 ? `消息中心，${props.unreadCount} 条未读` : '消息中心'"
  >
    <svg
      viewBox="0 0 24 24"
      class="size-5"
      fill="none"
      stroke="currentColor"
      stroke-width="1.8"
      stroke-linecap="round"
      stroke-linejoin="round"
      aria-hidden="true"
    >
      <path d="M18 8a6 6 0 0 0-12 0c0 7-3 9-3 9h18s-3-2-3-9" />
      <path d="M13.7 21a2 2 0 0 1-3.4 0" />
    </svg>
    <span
      v-if="props.unreadCount > 0"
      aria-hidden="true"
      class="absolute -right-0.5 -top-0.5 inline-flex h-4 min-w-4 items-center justify-center rounded-full bg-danger px-1 text-[10px] font-medium leading-4 text-white"
    >
      {{ badgeText }}
    </span>
  </RouterLink>
</template>
