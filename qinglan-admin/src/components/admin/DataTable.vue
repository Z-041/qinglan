<script setup lang="ts">
import BaseButton from '@shared/components/base/BaseButton.vue'

/**
 * 管理端表格统一外壳：固定表头 + 空 / 加载 / 失败占位行（失败态带内联「重试」，与 StatePanel 同取向）。
 *
 * 9 张表（5 个表组件 + 4 处页面内联表）此前各自复制同一套 `<table>` 外壳、吸顶表头类与
 * 「加载中 / 加载失败 / 暂无…」三态占位行，colspan 还要手工维护。此处只收敛这三处外壳，
 * **列定义与行内容仍留在各表内**——列数、单元格形态（缩略图 / 徽标 / 行内按钮）差异大，
 * 强行配置化会把差异藏进一堆插槽里，得不偿失。
 *
 * 表头吸顶依赖外层容器不用 overflow-hidden（会另建滚动容器、令 sticky 失效），
 * 各视图统一用 `ql-card overflow-clip`。
 */
withDefaults(
  defineProps<{
    /** 行数据；仅用于判断是否渲染占位行 */
    items: readonly unknown[]
    /** 占位列跨列数，须与表头列数一致 */
    colspan: number
    loading?: boolean
    failed?: boolean
    /** 无数据时的提示（加载中 / 加载失败时不会用到） */
    emptyText?: string
  }>(),
  { loading: false, failed: false, emptyText: '暂无数据' },
)

/** 失败态的内联「重试」按钮；调用方（或其外层表组件的根就是本组件，会把监听透传下来）接它 */
const emit = defineEmits<{ retry: [] }>()
</script>

<template>
  <table class="w-full border-collapse text-sm" :aria-busy="loading ? 'true' : undefined">
    <thead class="sticky top-0 z-10 bg-surface">
      <tr class="border-b border-line text-left text-xs text-muted">
        <slot name="head" />
      </tr>
    </thead>
    <tbody>
      <slot />
      <tr v-if="!items.length">
        <td :colspan="colspan" class="px-3 py-10 text-center text-sm text-muted">
          <template v-if="loading">加载中…</template>
          <template v-else-if="failed">
            加载失败
            <BaseButton class="ml-1" variant="ghost" size="sm" @click="emit('retry')">重试</BaseButton>
          </template>
          <template v-else>{{ emptyText }}</template>
        </td>
      </tr>
    </tbody>
  </table>
</template>
