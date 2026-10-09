<script setup lang="ts">
import { computed } from 'vue'

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
    <template #reference>
      <button
        type="button"
        class="relative flex cursor-pointer items-center rounded-lg px-2 py-1.5 text-muted transition hover:bg-primary-tint hover:text-ink"
        aria-label="待办提醒"
      >
        <!-- 铃铛图标用 currentColor，不引入图标库、不硬编码色值 -->
        <svg
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          stroke-width="1.8"
          stroke-linecap="round"
          stroke-linejoin="round"
          class="h-5 w-5"
          aria-hidden="true"
        >
          <path d="M18 8a6 6 0 1 0-12 0c0 7-3 9-3 9h18s-3-2-3-9" />
          <path d="M13.7 21a2 2 0 0 1-3.4 0" />
        </svg>
        <span
          v-if="total > 0"
          class="absolute -right-1 -top-1 min-w-[18px] rounded-full bg-danger px-1 text-center text-[11px] font-medium leading-[18px] text-white"
        >
          {{ total > 99 ? '99+' : total }}
        </span>
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
