<script setup lang="ts">
/**
 * 自绘页签条（替代 el-tabs + el-tab-pane 的头部）。
 *
 * 只做「页签条」这一件展示职责：渲染 tab 按钮、维护选中态（v-model）。
 * **面板内容由调用方用 v-show 自行切换**——本项目只有审核上下文面板一处用页签，
 * 若把面板也托管进来就得引入 provide/inject 或动态插槽，属为单一调用点过度设计。
 *
 * @param tabs 页签定义；name 作为 v-model 的值，label 为展示文案
 */
withDefaults(
  defineProps<{
    tabs: readonly { name: string; label: string }[]
    /** 可访问名（tablist）；无可见分组标题时提供 */
    ariaLabel?: string
  }>(),
  { ariaLabel: '' },
)

const model = defineModel<string>({ default: '' })
</script>

<template>
  <div role="tablist" :aria-label="ariaLabel || undefined" class="flex gap-4 border-b border-line">
    <button
      v-for="tab in tabs"
      :key="tab.name"
      type="button"
      role="tab"
      :aria-selected="model === tab.name"
      class="-mb-px cursor-pointer border-b-2 px-1 pb-2 text-sm transition"
      :class="
        model === tab.name
          ? 'border-primary-deep font-medium text-primary-deep'
          : 'border-transparent text-muted hover:text-ink'
      "
      @click="model = tab.name"
    >
      {{ tab.label }}
    </button>
  </div>
</template>
