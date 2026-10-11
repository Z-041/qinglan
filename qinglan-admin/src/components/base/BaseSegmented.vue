<script setup lang="ts">
/**
 * 分段单选控件（替代 el-radio-group + el-radio-button）：一组等权互斥选项，选中项高亮。
 * 视觉是「分段条」而非单选圆点——用于看板时间范围这类需要紧凑切换的场合。
 * @param options 选项；value 必填、label 为展示文案
 * @param label   可访问名（radiogroup），无可见分组标题时提供
 */
withDefaults(
  defineProps<{
    options: readonly { value: string; label: string }[]
    label?: string
    disabled?: boolean
  }>(),
  { label: '', disabled: false },
)

const model = defineModel<string>({ default: '' })
</script>

<template>
  <div
    role="radiogroup"
    :aria-label="label || undefined"
    class="inline-flex rounded-lg border border-line bg-canvas p-0.5"
  >
    <button
      v-for="option in options"
      :key="option.value"
      type="button"
      role="radio"
      :aria-checked="model === option.value"
      :disabled="disabled"
      class="cursor-pointer rounded-md px-3 py-1 text-sm transition disabled:cursor-not-allowed"
      :class="
        model === option.value
          ? 'bg-surface font-medium text-primary-deep shadow-sm'
          : 'text-muted hover:text-ink'
      "
      @click="model = option.value"
    >
      {{ option.label }}
    </button>
  </div>
</template>
