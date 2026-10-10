<script setup lang="ts">
import CountBadge from '@shared/components/base/CountBadge.vue'
import BaseIcon from '@shared/components/base/BaseIcon.vue'

/**
 * 纯展示组件：只接收未读数、只负责渲染红点。
 *
 * 入口**必须是 `RouterLink` 而非 `<button>`**（P4 决策 D11）：`AppHeader.spec.ts`
 * 断言「已认证时组件内仅一个 `<button>`」（退出按钮），加按钮会打破该不变量。
 */
const props = withDefaults(defineProps<{ unreadCount?: number }>(), { unreadCount: 0 })
</script>

<template>
  <RouterLink
    class="relative inline-flex size-9 items-center justify-center rounded-lg text-muted transition hover:bg-primary-tint hover:text-primary-deep"
    to="/notifications"
    :aria-label="props.unreadCount > 0 ? `消息中心，${props.unreadCount} 条未读` : '消息中心'"
  >
    <BaseIcon name="bell" class="size-5" />
    <CountBadge class="-right-0.5 -top-0.5 absolute" :count="props.unreadCount" />
  </RouterLink>
</template>
