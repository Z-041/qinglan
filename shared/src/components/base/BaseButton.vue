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
    /**
     * 换成链接等其它元素：传原生标签名（'a'）或**已全局注册的组件名**（'RouterLink'）——字符串
     * 由 Vue 解析，因此调用方无需 import，shared 也不必反向依赖 vue-router。不传则渲染 `<button>`。
     * 非 button 时不输出 `type` / `disabled`（链接不接受这两个属性），其余 props 落到根元素。
     * 收窄为字面量联合：拼错（如 'Routerlink'）在 typecheck 即报错，而不是静默渲染成未知元素。
     */
    as?: 'button' | 'a' | 'RouterLink'
  }>(),
  { variant: 'primary', size: 'md', block: false, disabled: false, loading: false, htmlType: 'button' },
)

const emit = defineEmits<{ click: [event: MouseEvent] }>()

const tag = computed(() => props.as ?? 'button')
/** 仅原生 button 才带 type / disabled：链接类元素接受它们会造成无效属性与错误语义 */
const isButton = computed(() => props.as === undefined || props.as === 'button')

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
  <component
    :is="tag"
    :type="isButton ? htmlType : undefined"
    class="cursor-pointer"
    :class="classes"
    :aria-busy="isButton && loading ? 'true' : undefined"
    :disabled="isButton ? disabled || loading : undefined"
    @click="emit('click', $event)"
  >
    <BaseSpinner v-if="loading" size-class="size-3.5" />
    <slot />
  </component>
</template>
