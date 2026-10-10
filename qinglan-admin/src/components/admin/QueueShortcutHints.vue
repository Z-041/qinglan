<script setup lang="ts">
import type { ShortcutHint } from '@/composables/useQueueShortcuts'

/**
 * 一键处置快捷键提示条。只展示当前账号有权限、且当前动作集里可用的绑定
 * （绑定已由 `useQueueShortcuts` 过滤），因此不会提示出按不动的键。
 */
defineProps<{ bindings: readonly ShortcutHint[] }>()
</script>

<template>
  <p v-if="bindings.length" class="flex flex-wrap items-center gap-x-3 gap-y-1 text-xs text-muted">
    <span>快捷键（先点击列表）：</span>
    <span v-for="binding in bindings" :key="binding.key" class="inline-flex items-center gap-1">
      <kbd
        class="rounded border border-line bg-primary-tint px-1.5 py-0.5 font-mono text-[11px] text-primary-deep"
      >
        {{ binding.key }}
      </kbd>
      {{ binding.label }}
    </span>
  </p>
</template>
