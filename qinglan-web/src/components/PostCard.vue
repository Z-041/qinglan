<script setup lang="ts">
import BaseIcon from '@shared/components/base/BaseIcon.vue'
import LikeButton from '@/components/LikeButton.vue'
import PostImageGrid from '@/components/PostImageGrid.vue'
import PostMetaRow from '@/components/PostMetaRow.vue'
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
      <PostMetaRow
        :topic-name="post.topicName"
        :mine="post.mine"
        :anonymous-code="post.anonymousCode"
        :created-at="post.createdAt"
      />

      <p class="mt-2 line-clamp-2 text-sm leading-relaxed break-words text-ink">{{ post.content }}</p>

      <PostImageGrid class="mt-2" :urls="post.images" />
    </RouterLink>

    <div class="flex items-center gap-4 px-4 pb-3 max-lg:px-3">
      <LikeButton
        :liked="post.liked"
        :count="post.likeCount"
        :label="post.liked ? '取消点赞' : '点赞'"
        @toggle="emit('toggle-like')"
      />

      <span class="flex items-center gap-1 text-xs text-muted">
        <BaseIcon name="comment" class="size-4" />
        <span>{{ post.commentCount }}</span>
      </span>
    </div>
  </article>
</template>
