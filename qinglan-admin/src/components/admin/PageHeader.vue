<script setup lang="ts">
import BaseButton from '@shared/components/base/BaseButton.vue'

/**
 * 管理端页面统一页头：左侧标题、右侧操作区。
 *
 * 传 refreshing 即得到标准「刷新」按钮（在途转圈，避免连点）；额外动作（导出、新建、
 * 权限提示等）放 #extra，渲染在刷新按钮之前。副标题不进组件——各页说明文案长短与结构
 * 差异大，留在页面里更直观。
 */
withDefaults(
  defineProps<{
    title: string
    /** 传 boolean 渲染标准刷新按钮；不传（undefined）则右侧完全交给 #extra */
    refreshing?: boolean
  }>(),
  { refreshing: undefined },
)

defineEmits<{ refresh: [] }>()
</script>

<template>
  <div class="flex items-center justify-between gap-4">
    <h1 class="text-lg font-semibold text-ink">{{ title }}</h1>
    <div v-if="$slots.extra || refreshing !== undefined" class="flex items-center gap-2">
      <slot name="extra" />
      <BaseButton
        v-if="refreshing !== undefined"
        variant="ghost"
        size="sm"
        :loading="refreshing"
        @click="$emit('refresh')"
      >
        刷新
      </BaseButton>
    </div>
  </div>
</template>
