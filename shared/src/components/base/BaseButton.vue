<script setup lang="ts">
import { computed } from 'vue'

import BaseSpinner from './BaseSpinner.vue'

type Variant = 'primary' | 'ghost' | 'ghost-danger' | 'danger'
type Size = 'md' | 'sm' | 'xs'

const props = withDefaults(
  defineProps<{
    variant?: Variant
    size?: Size
    block?: boolean
    disabled?: boolean
    loading?: boolean
    /** 原生 button 的 type，默认 button 以避免在表单内意外触发提交 */
    htmlType?: 'button' | 'submit'
  }>(),
  { variant: 'primary', size: 'md', block: false, disabled: false, loading: false, htmlType: 'button' },
)

const emit = defineEmits<{ click: [event: MouseEvent] }>()

const VARIANT_CLASS: Record<Variant, string> = {
  // 填充底用 deep 而非 primary：白字对比度 6.0:1，达标（见 styles/main.css）
  primary: 'bg-primary-deep text-white shadow-sm hover:bg-primary-strong hover:shadow-md',
  ghost: 'border border-line bg-transparent text-muted hover:border-primary hover:bg-primary-tint hover:text-primary-deep',
  // 描边危险态：表格行内的封禁 / 撤回 / 收回等，平时中性、悬停转危险色（区别于实心 danger 的高警示）
  'ghost-danger': 'border border-line bg-transparent text-muted hover:border-danger hover:text-danger-deep',
  // 悬停用 danger-deep 而非透明度：降低不透明度会同时淡掉白字，反而拉低对比度（见 styles/main.css）
  danger: 'bg-danger text-white shadow-sm hover:bg-danger-deep hover:shadow-md',
}

const SIZE_CLASS: Record<Size, string> = {
  md: 'ql-btn-md',
  sm: 'ql-btn-sm',
  xs: 'ql-btn-xs',
}

const classes = computed(() => [
  'ql-btn',
  VARIANT_CLASS[props.variant],
  SIZE_CLASS[props.size],
  props.block ? 'w-full' : '',
])
</script>

<template>
  <button
    :type="htmlType"
    class="cursor-pointer"
    :class="classes"
    :disabled="disabled || loading"
    @click="emit('click', $event)"
  >
    <BaseSpinner v-if="loading" size-class="size-3.5" />
    <slot />
  </button>
</template>
