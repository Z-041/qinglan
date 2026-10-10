<script setup lang="ts">
import { formatAbsoluteTime, formatRelativeTime, formatShortDateTime } from '@shared/composables/useRelativeTime'

/**
 * 统一的时间渲染：相对时间（刚刚 / 3 分钟前 / 10 月 1 日）+ 悬停看绝对时间。
 *
 * 语义化 `<time datetime>` 一处保证；底层 formatter 已锁定 zh-CN 与 24 小时制，
 * 不会随浏览器语言漂移。审计等需要「平时也看绝对时间」的场景用 absolute-short。
 */
const props = withDefaults(
  defineProps<{
    iso: string
    /** relative：相对文案（默认）；absolute-short：短绝对时间，悬停仍给完整时刻（审计日志） */
    variant?: 'relative' | 'absolute-short'
  }>(),
  { variant: 'relative' },
)
</script>

<template>
  <time :datetime="props.iso" :title="formatAbsoluteTime(props.iso)">
    {{
      props.variant === 'relative'
        ? formatRelativeTime(props.iso)
        : formatShortDateTime(props.iso)
    }}
  </time>
</template>
