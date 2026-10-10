<script setup lang="ts">
import { formatAbsoluteTime, formatRelativeTime } from '@shared/composables/useRelativeTime'
import type { PostSummary } from '@/types/post'

/**
 * 信息流统一列表行（阶段 2：feature / compact 双变体合并）。
 *
 * 结构：话题 + 代号 + 相对时间一行 → 内容摘要 → 配图 → 互动数据一行。
 * 互动条（点赞按钮）必须在链接**之外**：`<a>` 的内容模型不允许出现 `<button>`。
 */
defineProps<{ post: PostSummary }>()

const emit = defineEmits<{ 'toggle-like': [] }>()
</script>

<template>
  <article class="ql-row">
    <RouterLink :to="`/posts/${post.id}`" class="block px-4 pt-3.5 pb-2 max-lg:px-3">
      <div class="flex items-center gap-2">
        <span class="ql-tag shrink-0 bg-primary-soft text-primary-deep">{{ post.topicName }}</span>
        <span v-if="post.mine" class="ql-tag shrink-0 bg-primary-tint text-primary-deep">我</span>
        <span class="truncate text-xs text-muted">{{ post.anonymousCode }}</span>
        <time
          class="ml-auto shrink-0 text-xs text-muted"
          :datetime="post.createdAt"
          :title="formatAbsoluteTime(post.createdAt)"
        >
          {{ formatRelativeTime(post.createdAt) }}
        </time>
      </div>

      <p class="mt-2 line-clamp-2 text-sm leading-relaxed break-words text-ink">{{ post.content }}</p>

      <div v-if="post.images.length" class="mt-2 grid grid-cols-4 gap-1.5">
        <img
          v-for="url in post.images"
          :key="url"
          :src="url"
          alt="帖子配图"
          loading="lazy"
          class="aspect-square w-full rounded border border-line object-cover"
        />
      </div>
    </RouterLink>

    <div class="flex items-center gap-4 px-4 pb-3 max-lg:px-3">
      <button
        type="button"
        class="flex cursor-pointer items-center gap-1 text-xs transition"
        :class="post.liked ? 'text-primary-deep' : 'text-muted hover:text-primary-deep'"
        :aria-pressed="post.liked"
        :aria-label="post.liked ? '取消点赞' : '点赞'"
        @click="emit('toggle-like')"
      >
        <svg
          viewBox="0 0 24 24"
          :fill="post.liked ? 'currentColor' : 'none'"
          stroke="currentColor"
          stroke-width="1.8"
          class="size-4"
          aria-hidden="true"
        >
          <path
            d="M12 20.3 4.8 13a4.6 4.6 0 0 1 6.5-6.5l.7.7.7-.7A4.6 4.6 0 0 1 19.2 13Z"
            stroke-linejoin="round"
          />
        </svg>
        <span>{{ post.likeCount }}</span>
      </button>

      <span class="flex items-center gap-1 text-xs text-muted">
        <svg
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          stroke-width="1.8"
          class="size-4"
          aria-hidden="true"
        >
          <path d="M21 12a8 8 0 0 1-11.6 7.1L4 20.5l1.4-5.4A8 8 0 1 1 21 12Z" stroke-linejoin="round" />
        </svg>
        <span>{{ post.commentCount }}</span>
      </span>
    </div>
  </article>
</template>
