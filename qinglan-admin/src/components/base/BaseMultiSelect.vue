<script setup lang="ts">
import { computed } from 'vue'

import BaseCheckbox from './BaseCheckbox.vue'
import BasePopover from './BasePopover.vue'

/**
 * 多选下拉（替代 el-select multiple + collapse-tags）：触发器显示已选摘要，点开是复选列表。
 *
 * 勾选**不关闭**面板（由 BasePopover 只在点外部 / ESC 时关闭），便于连续勾多个；
 * 一个都不选时显示 placeholder（对应「不按该项过滤」）。
 *
 * @param options 选项；value 进筛选参数，label 为展示文案
 */
const props = withDefaults(
  defineProps<{
    options: readonly { value: string; label: string }[]
    /** 一项都没选时的占位文案 */
    placeholder?: string
    ariaLabel?: string
  }>(),
  { placeholder: '全部' },
)

const model = defineModel<string[]>({ default: () => [] })

const selected = computed(() => new Set(model.value))

const summary = computed(() =>
  model.value.length === 0
    ? props.placeholder
    : props.options
        .filter((option) => selected.value.has(option.value))
        .map((option) => option.label)
        .join('、'),
)

function toggle(value: string): void {
  model.value = selected.value.has(value)
    ? model.value.filter((item) => item !== value)
    : [...model.value, value]
}
</script>

<template>
  <BasePopover :width="280" align="start">
    <template #trigger="{ toggle: togglePanel }">
      <button
        type="button"
        class="ql-field flex w-full cursor-pointer items-center justify-between gap-2 text-left"
        :aria-label="ariaLabel"
        @click="togglePanel"
      >
        <span class="truncate" :class="model.length ? 'text-ink' : 'text-muted'">{{ summary }}</span>
      </button>
    </template>

    <ul class="flex max-h-64 flex-col overflow-y-auto">
      <li v-for="option in options" :key="option.value">
        <BaseCheckbox
          class="w-full rounded-lg px-2 py-1.5 hover:bg-primary-tint"
          :model-value="selected.has(option.value)"
          @update:model-value="toggle(option.value)"
        >
          {{ option.label }}
        </BaseCheckbox>
      </li>
    </ul>
  </BasePopover>
</template>
