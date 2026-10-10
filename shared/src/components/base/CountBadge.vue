<script setup lang="ts">
/**
 * 数字角标（未读 / 待办计数）：超上限显示「{max}+」，count <= 0 不渲染。
 *
 * 纯装饰：所在按钮的 aria-label 必须自行带上数量；角标本身 aria-hidden，避免读屏
 * 「按钮、12」与「12 条未读」重复朗读。
 * 不含定位类（absolute/right/top 由调用方按各自图标容器给，避免两处既有位置被强行统一）；
 * 两档尺寸只对应既有两处消费点的原始规格，不新增视觉变体。
 */
const props = withDefaults(
  defineProps<{ count: number; max?: number; size?: 'sm' | 'md' }>(),
  { max: 99, size: 'sm' },
)
</script>

<template>
  <span
    v-if="props.count > 0"
    aria-hidden="true"
    class="inline-flex items-center justify-center rounded-full bg-danger px-1 font-medium text-white"
    :class="
      props.size === 'sm'
        ? 'h-4 min-w-4 text-[10px] leading-4'
        : 'h-[18px] min-w-[18px] text-[11px] leading-[18px]'
    "
  >
    {{ props.count > props.max ? `${props.max}+` : props.count }}
  </span>
</template>
