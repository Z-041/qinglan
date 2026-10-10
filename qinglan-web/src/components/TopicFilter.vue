<script setup lang="ts">
import type { Topic } from '@/types/post'

withDefaults(
  defineProps<{
    topics: Topic[]
    /** 首页需要「全部」筛选；发布页必须选定一个话题，故传 false */
    allowAll?: boolean
  }>(),
  { allowAll: true },
)

/** null 表示不筛选，即「全部」 */
const model = defineModel<number | null>({ default: null })

const CHIP_BASE =
  'inline-flex shrink-0 cursor-pointer items-center rounded-md border px-3 py-1.5 text-xs font-medium transition'
const CHIP_ON = 'border-primary-soft bg-primary-soft text-primary-deep'
const CHIP_OFF = 'border-line bg-surface text-muted hover:border-primary-soft hover:text-primary-deep'
</script>

<template>
  <div class="flex flex-wrap gap-2" role="group" aria-label="话题筛选">
    <button
      v-if="allowAll"
      type="button"
      :class="[CHIP_BASE, model === null ? CHIP_ON : CHIP_OFF]"
      :aria-pressed="model === null"
      @click="model = null"
    >
      全部
    </button>
    <button
      v-for="topic in topics"
      :key="topic.id"
      type="button"
      :class="[CHIP_BASE, model === topic.id ? CHIP_ON : CHIP_OFF]"
      :aria-pressed="model === topic.id"
      @click="model = topic.id"
    >
      {{ topic.name }}
    </button>
  </div>
</template>
