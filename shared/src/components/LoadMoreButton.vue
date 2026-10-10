<script setup lang="ts">
import BaseButton from './base/BaseButton.vue'

/**
 * 游标列表统一的「加载更多」尾部。hasMore 为 false 时不渲染。
 * 两种外观对应既有两处场景：plain 为学生端居中轻量按钮，table 为管理端列表的分隔行；
 * 交互完全一致（在途时按钮转圈防连点）。
 * hint 用于按钮上方的条件提示（如「筛选条件已修改，请先查询」），disallow 时按钮禁用。
 */
withDefaults(
  defineProps<{
    hasMore: boolean
    loading?: boolean
    disabled?: boolean
    hint?: string
    variant?: 'plain' | 'table'
  }>(),
  { loading: false, disabled: false, hint: '', variant: 'plain' },
)

const emit = defineEmits<{ load: [] }>()
</script>

<template>
  <div
    v-if="hasMore"
    :class="variant === 'table' ? 'border-t border-line px-3 py-3 text-center' : 'mt-4 flex flex-col items-center gap-2'"
  >
    <p v-if="hint" class="text-xs text-muted">
      {{ hint }}
    </p>
    <BaseButton
      variant="ghost"
      size="sm"
      :disabled="disabled"
      :loading="loading"
      @click="emit('load')"
    >
      加载更多
    </BaseButton>
  </div>
</template>
