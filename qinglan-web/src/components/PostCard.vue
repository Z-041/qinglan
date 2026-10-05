<script setup lang="ts">
import type { PostSummary } from '@/types/post'

const props = defineProps<{ post: PostSummary }>()

const emit = defineEmits<{ 'toggle-like': [] }>()
</script>

<template>
  <article class="ql-card-interactive overflow-hidden">
    <RouterLink :to="`/posts/${props.post.id}`" class="block p-4 sm:p-5">
      <div class="flex items-center gap-2">
        <span class="ql-badge bg-primary-soft text-primary-deep">{{ props.post.topicName }}</span>
        <span class="truncate text-xs text-muted">{{ props.post.anonymousCode }}</span>
        <span v-if="props.post.mine" class="ql-badge shrink-0 bg-primary-tint text-primary-deep">我</span>
      </div>

      <p class="mt-3 line-clamp-3 text-sm leading-relaxed break-words text-ink">
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
          :class="props.post.images.length === 1 ? 'max-h-72' : 'aspect-square'"
        />
      </div>
    </RouterLink>

    <footer class="flex items-center gap-4 border-t border-line px-4 py-2.5 sm:px-5">
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
