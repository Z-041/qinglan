<script setup lang="ts">
/**
 * 自绘复选框（替代 el-checkbox）。原生 `<input type="checkbox">` 保证键盘与读屏语义，
 * 只重着色，不再引第三方控件。文案可走 `label` 或默认插槽（管理端仅 PC，无需触屏放大）。
 */
withDefaults(
  defineProps<{
    /** 文案；也可用默认插槽给富文本 */
    label?: string
    name?: string
    disabled?: boolean
    /** 可访问名：无可见文案时提供 */
    ariaLabel?: string
  }>(),
  { label: '', disabled: false },
)

const model = defineModel<boolean>({ default: false })
</script>

<template>
  <label class="inline-flex cursor-pointer items-center gap-2 text-sm text-ink">
    <input
      v-model="model"
      type="checkbox"
      :name="name"
      :disabled="disabled"
      :aria-label="ariaLabel"
      class="size-4 shrink-0 cursor-pointer accent-primary-deep disabled:cursor-not-allowed"
    />
    <span v-if="label || $slots.default"><slot>{{ label }}</slot></span>
  </label>
</template>
