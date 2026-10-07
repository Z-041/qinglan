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
  <article v-if="isCompact" class="group relative transition hover:bg-primary-tint">
    <RouterLink :to="`/posts/${props.post.id}`" class="block px-4 py-3 max-lg:py-3.5">
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

          <div class="mt-2 flex items-center gap-4">
            <!-- 这个按钮在 RouterLink（<a>）内部：不加 .prevent.stop 的话，点「赞」会冒泡到锚点，
                 既触发 router.push 跳进详情页，浏览器也可能执行 <a> 的默认跳转。
                 .stop 拦冒泡、.prevent 取消默认行为，两者都要，否则仍会导航。 -->
            <button
              type="button"
              class="flex cursor-pointer items-center gap-1 text-xs transition"
              :class="props.post.liked ? 'text-primary-deep' : 'text-muted hover:text-primary-deep'"
              :aria-pressed="props.post.liked"
              @click.prevent.stop="emit('toggle-like')"
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
      </div>
    </RouterLink>
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
