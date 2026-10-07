<script setup lang="ts">
import { computed } from 'vue'

import type { PostSummary } from '@/types/post'

/**
 * variant 默认 'feature'：既有 5 条单测都不传 variant，
 * 默认值必须与改造前的完整卡片渲染一致，否则断言全崩。
 */
const props = withDefaults(defineProps<{ post: PostSummary; variant?: 'feature' | 'compact' }>(), {
  variant: 'feature',
})

const emit = defineEmits<{ 'toggle-like': [] }>()

const isCompact = computed(() => props.variant === 'compact')

/** 紧凑行左侧的视觉锚点：话题名首字（不引入头像，保持匿名） */
const topicInitial = computed(() => props.post.topicName.slice(0, 1))
</script>

<template>
  <!-- 互动条必须在链接**之外**：<a> 的内容模型不允许出现 <button> 这类交互元素。
       结构合法后就不再需要靠 .stop/.prevent 兜住「点赞连带跳转」，行为由结构保证。
       代价是底部互动条不再属于链接、不再是可点区域（T-21 记过这笔取舍）。 -->
  <article v-if="isCompact" class="group relative flex flex-col transition hover:bg-primary-tint">
    <RouterLink :to="`/posts/${props.post.id}`" class="px-4 pt-3 pb-2 max-lg:pt-3.5">
      <div class="flex gap-3">
        <span
          class="flex size-9 shrink-0 items-center justify-center rounded-lg bg-primary-soft text-sm font-semibold text-primary-deep"
        >
          {{ topicInitial }}
        </span>

        <div class="min-w-0 flex-1">
          <div class="flex items-center gap-2">
            <span class="ql-badge bg-primary-soft text-primary-deep">{{ props.post.topicName }}</span>
            <span class="truncate text-xs text-muted">{{ props.post.anonymousCode }}</span>
            <span v-if="props.post.mine" class="ql-badge shrink-0 bg-primary-tint text-primary-deep">我</span>
            <time class="ml-auto shrink-0 text-xs text-muted" :datetime="props.post.createdAt">
              {{ new Date(props.post.createdAt).toLocaleString() }}
            </time>
          </div>

          <p class="mt-2 line-clamp-2 text-sm leading-relaxed break-words text-ink">
            {{ props.post.content }}
          </p>

          <div v-if="props.post.images.length" class="mt-2 grid grid-cols-4 gap-1.5">
            <img
              v-for="url in props.post.images"
              :key="url"
              :src="url"
              alt="帖子配图"
              loading="lazy"
              class="aspect-square w-full rounded-md border border-line object-cover"
            />
          </div>
        </div>
      </div>
    </RouterLink>

    <div class="flex items-center gap-3 px-4 pb-3 max-lg:pb-3.5">
      <!-- 占位块与话题首字同宽（size-9 + gap-3），让互动条对齐上面正文的起始位置 -->
      <span class="size-9 shrink-0" aria-hidden="true" />
      <div class="flex items-center gap-4">
        <button
          type="button"
          class="flex cursor-pointer items-center gap-1 text-xs transition"
          :class="props.post.liked ? 'text-primary-deep' : 'text-muted hover:text-primary-deep'"
          :aria-pressed="props.post.liked"
          @click="emit('toggle-like')"
        >
          <svg
            viewBox="0 0 24 24"
            :fill="props.post.liked ? 'currentColor' : 'none'"
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
          <span>{{ props.post.likeCount }}</span>
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
          <span>{{ props.post.commentCount }}</span>
        </span>
      </div>
    </div>
  </article>

  <article v-else class="ql-card-interactive overflow-hidden">
    <RouterLink :to="`/posts/${props.post.id}`" class="block p-5">
      <div class="flex items-center gap-2">
        <span class="ql-badge bg-primary-soft text-primary-deep">{{ props.post.topicName }}</span>
        <span class="truncate text-xs text-muted">{{ props.post.anonymousCode }}</span>
        <span v-if="props.post.mine" class="ql-badge shrink-0 bg-primary-tint text-primary-deep">我</span>
      </div>

      <p class="mt-3 line-clamp-4 text-[15px] leading-relaxed break-words text-ink">
        {{ props.post.content }}
      </p>

      <div
        v-if="props.post.images.length"
        class="mt-3 grid gap-1.5"
        :class="props.post.images.length === 1 ? 'grid-cols-1' : 'grid-cols-3'"
      >
        <img
          v-for="url in props.post.images"
          :key="url"
          :src="url"
          alt="帖子配图"
          loading="lazy"
          class="w-full rounded-lg border border-line object-cover"
          :class="props.post.images.length === 1 ? 'max-h-80' : 'aspect-square'"
        />
      </div>
    </RouterLink>

    <footer class="flex items-center gap-4 border-t border-line px-5 py-2.5">
      <button
        type="button"
        class="flex cursor-pointer items-center gap-1 text-xs transition"
        :class="props.post.liked ? 'text-primary-deep' : 'text-muted hover:text-primary-deep'"
        :aria-pressed="props.post.liked"
        @click="emit('toggle-like')"
      >
        <svg
          viewBox="0 0 24 24"
          :fill="props.post.liked ? 'currentColor' : 'none'"
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
        <span>{{ props.post.likeCount }}</span>
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
        <span>{{ props.post.commentCount }}</span>
      </span>

      <time class="ml-auto text-xs text-muted" :datetime="props.post.createdAt">
        {{ new Date(props.post.createdAt).toLocaleString() }}
      </time>
    </footer>
  </article>
</template>
