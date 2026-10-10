<script setup lang="ts">
/**
 * 帖子配图统一渲染（信息流缩略图 / 详情大图两种口径）。
 *
 * - thumb：四列小方块，用于信息流列表行与「我的帖子」；
 * - detail：单图按原比例限高、多图三列方块，用于详情页。
 *
 * 外距（mt-*）由调用方通过 class 传入，避免把页面节奏写进组件；单根元素故 class 会落到网格容器上。
 */
withDefaults(
  defineProps<{
    urls: string[]
    variant?: 'thumb' | 'detail'
    alt?: string
  }>(),
  { variant: 'thumb', alt: '帖子配图' },
)
</script>

<template>
  <div
    v-if="urls.length"
    class="grid gap-1.5"
    :class="variant === 'thumb' ? 'grid-cols-4' : urls.length === 1 ? 'grid-cols-1' : 'grid-cols-3'"
  >
    <img
      v-for="url in urls"
      :key="url"
      :src="url"
      :alt="alt"
      loading="lazy"
      class="w-full border border-line object-cover"
      :class="
        variant === 'thumb'
          ? 'aspect-square rounded'
          : urls.length === 1
            ? 'max-h-96 rounded-lg'
            : 'aspect-square rounded-lg'
      "
    />
  </div>
</template>
