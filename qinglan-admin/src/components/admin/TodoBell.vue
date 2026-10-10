<script setup lang="ts">
import { computed } from 'vue'

import CountBadge from '@shared/components/base/CountBadge.vue'
import BaseIcon from '@shared/components/base/BaseIcon.vue'
import type { TodoGroup } from '@/composables/useTodoCounts'

const props = defineProps<{
  /** 已按权限与字段存在性裁剪过的分组；空数组时铃铛只显示图标 */
  groups: TodoGroup[]
}>()

const emit = defineEmits<{ select: [group: TodoGroup] }>()

/** 角标总数：各可见分组计数之和 */
const total = computed(() => props.groups.reduce((sum, group) => sum + group.count, 0))
</script>

<template>
  <el-popover trigger="click" placement="bottom-end" :width="210">
    <!-- 轮询会让总数变化：视觉隐藏的 live region 负责把变化播报给屏幕阅读器；
         按钮自身的 aria-label 也带数量，保证聚焦时读到的是完整语义 -->
    <span class="sr-only" aria-live="polite">{{ total > 0 ? `待办提醒，${total} 项待处理` : '' }}</span>
    <template #reference>
      <button
        type="button"
        class="relative flex cursor-pointer items-center rounded-lg px-2 py-1.5 text-muted transition hover:bg-primary-tint hover:text-ink"
        :aria-label="total > 0 ? `待办提醒，${total} 项待处理` : '待办提醒，暂无待办'"
      >
        <!-- 铃铛图标与角标统一走 shared BaseIcon / CountBadge（currentColor 着色，不硬编码色值） -->
        <BaseIcon name="bell" class="size-5" />
        <CountBadge class="-right-1 -top-1 absolute" size="md" :count="total" />
      </button>
    </template>

    <p v-if="!props.groups.length" class="px-2 py-1 text-sm text-muted">暂无待办</p>
    <ul v-else class="flex flex-col">
      <li v-for="group in props.groups" :key="group.key">
        <button
          type="button"
          class="flex w-full cursor-pointer items-center justify-between rounded-lg px-2 py-2 text-sm text-ink transition hover:bg-primary-tint"
          @click="emit('select', group)"
        >
          <span>{{ group.label }}</span>
          <span
            class="ql-badge"
            :class="group.count > 0 ? 'bg-primary-soft text-primary-deep' : 'bg-canvas text-muted'"
          >
            {{ group.count }}
          </span>
        </button>
      </li>
    </ul>
  </el-popover>
</template>
