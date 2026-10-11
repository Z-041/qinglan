<script setup lang="ts">
/**
 * 自绘开关（替代 el-switch）。用 `<button role="switch">` 而非原生 checkbox：
 * 开关的语义是「即时生效的toggle」，ARIA 明确推荐 role="switch" + aria-checked。
 */
const props = withDefaults(
  defineProps<{
    label?: string
    /** 可访问名：无可见文案时提供（管理端多为筛选行为，文案在旁边的 span 里） */
    ariaLabel?: string
    disabled?: boolean
  }>(),
  { label: '', disabled: false },
)

const model = defineModel<boolean>({ default: false })
const emit = defineEmits<{ change: [value: boolean] }>()

function toggle(): void {
  if (props.disabled) {
    return
  }
  model.value = !model.value
  emit('change', model.value)
}
</script>

<template>
  <button
    type="button"
    role="switch"
    :aria-checked="model"
    :aria-label="ariaLabel || label || undefined"
    :disabled="disabled"
    class="relative inline-flex h-6 w-11 shrink-0 cursor-pointer items-center rounded-full border transition disabled:(cursor-not-allowed opacity-60)"
    :class="model ? 'border-primary-deep bg-primary-deep' : 'border-line bg-canvas'"
    @click="toggle"
  >
    <span
      class="size-4 rounded-full bg-surface shadow-sm transition"
      :class="model ? 'translate-x-6' : 'translate-x-1'"
    />
  </button>
</template>
