<script setup lang="ts">
import RelativeTime from '@shared/components/RelativeTime.vue'

/**
 * 帖子元信息行：可选话题标签 + 可选「我」 + 匿名代号 + 相对时间（靠右）。
 *
 * 信息流列表行、详情头、「我的帖子」三处同构（评论行的代号在前且有强调样式，语义不同，
 * 不并入）。#leading 插槽供「我的帖子」放状态标签。
 */
withDefaults(
  defineProps<{
    anonymousCode: string
    createdAt: string
    mine?: boolean
    /** 话题名；无话题场景（如纯列表）不传 */
    topicName?: string
  }>(),
  { mine: false, topicName: '' },
)
</script>

<template>
  <div class="flex items-center gap-2">
    <slot name="leading" />
    <span v-if="topicName" class="ql-tag shrink-0 bg-primary-soft text-primary-deep">
      {{ topicName }}
    </span>
    <span v-if="mine" class="ql-tag shrink-0 bg-primary-tint text-primary-deep">我</span>
    <span class="truncate text-xs text-muted">{{ anonymousCode }}</span>
    <RelativeTime class="ml-auto shrink-0 text-xs text-muted" :iso="createdAt" />
  </div>
</template>
