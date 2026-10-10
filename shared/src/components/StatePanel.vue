<script setup lang="ts">
import BaseButton from './base/BaseButton.vue'

/**
 * 列表 / 详情统一的四态包装：加载中 / 加载失败（带重试）/ 空态 / 正常内容。
 *
 * 失败与空态必须分成两屏——一次网络故障不能被说成「这里还没有内容」。
 * 请求错误的具体信息不在这里渲染（统一由 http 拦截器 toast），这里只放状态说明与重试出口。
 *
 * 用法：
 *   <StatePanel :state="state" failed-title="内容没能加载出来" empty-title="还没有内容"
 *               @retry="load">
 *     正常内容…
 *     <template #empty-action><RouterLink to="/publish">去发布</RouterLink></template>
 *   </StatePanel>
 */
withDefaults(
  defineProps<{
    state: 'loading' | 'failed' | 'empty' | 'ready'
    loadingText?: string
    failedTitle?: string
    emptyTitle?: string
    emptyDescription?: string
    /** 失败 / 空态盒的顶部间距，默认 mt-6（ql-container 页面节奏）；gap 容器内传 'mt-0' */
    topSpaceClass?: string
  }>(),
  {
    loadingText: '加载中…',
    failedTitle: '内容没能加载出来',
    emptyTitle: '暂无内容',
    emptyDescription: '',
    topSpaceClass: 'mt-6',
  },
)

const emit = defineEmits<{ retry: [] }>()
</script>

<template>
  <p v-if="state === 'loading'" class="py-8 text-center text-sm text-muted">
    {{ loadingText }}
  </p>

  <div
    v-else-if="state === 'failed'"
    class="rounded-xl border border-dashed border-line-strong px-5 py-10 text-center"
    :class="topSpaceClass"
  >
    <p class="text-sm font-medium text-ink">{{ failedTitle }}</p>
    <BaseButton class="mt-4" variant="ghost" size="sm" @click="emit('retry')">重试</BaseButton>
  </div>

  <div
    v-else-if="state === 'empty'"
    class="rounded-xl border border-dashed border-line-strong px-5 py-10 text-center"
    :class="topSpaceClass"
  >
    <p class="text-sm font-medium text-ink">{{ emptyTitle }}</p>
    <p v-if="emptyDescription" class="mt-1 text-xs text-muted">{{ emptyDescription }}</p>
    <div v-if="$slots['empty-action']" class="mt-4">
      <slot name="empty-action" />
    </div>
  </div>

  <slot v-else />
</template>
