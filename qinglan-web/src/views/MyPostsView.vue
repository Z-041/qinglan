<script setup lang="ts">
import { onMounted } from 'vue'

import BaseButton from '@shared/components/base/BaseButton.vue'
import { formatAbsoluteTime, formatRelativeTime } from '@shared/composables/useRelativeTime'
import { useUserLabels } from '@shared/composables/useUserLabels'
import { useMyPosts } from '@/composables/useMyPosts'
import type { MyPostItem, PostStatus } from '@/types/post'

const { items, loading, loadingMore, hasMore, failed, load, loadMore } = useMyPosts()
const { targetStatusLabel, reviewReasonText } = useUserLabels()

/** 状态徽标配色：已通过用主色，驳回 / 下架用危险色，其余（待审等）用中性色 */
function statusClass(status: PostStatus): string {
  if (status === 'APPROVED') {
    return 'bg-primary-soft text-primary-deep'
  }
  if (status === 'REJECTED' || status === 'TAKEN_DOWN') {
    return 'bg-danger-soft text-danger-deep'
  }
  return 'border border-line bg-surface text-muted'
}

/**
 * 处置原因：驳回存的是审核员自由文本，原样展示；下架存的是「违规类型码[: 说明]」，
 * 需经 `reviewReasonText` 映射（与详情页同一口径）。
 */
function reasonText(item: MyPostItem): string | null {
  return item.status === 'TAKEN_DOWN' ? reviewReasonText(item.reviewReason) : item.reviewReason
}

onMounted(() => {
  void load()
})
</script>

<template>
  <section class="ql-shell py-8 max-lg:py-5">
    <div class="flex items-center justify-between gap-4">
      <h1 class="text-lg font-semibold text-ink">我的帖子</h1>
      <RouterLink class="text-xs text-muted transition hover:text-primary-deep" to="/profile">
        返回我的
      </RouterLink>
    </div>

    <p v-if="loading" class="mt-6 text-center text-sm text-muted">加载中…</p>

    <!-- 加载失败与「真的没有帖子」分成两屏：文案是状态说明，请求错误信息仍只由 http 拦截器 toast -->
    <div
      v-else-if="failed"
      class="mt-6 rounded-xl border border-dashed border-line-strong px-5 py-10 text-center"
    >
      <p class="text-sm font-medium text-ink">帖子没能加载出来</p>
      <BaseButton class="mt-4" variant="ghost" size="sm" @click="load()">重试</BaseButton>
    </div>

    <div
      v-else-if="!items.length"
      class="mt-6 rounded-xl border border-dashed border-line-strong px-5 py-10 text-center"
    >
      <p class="text-sm font-medium text-ink">你还没有发布过帖子</p>
      <RouterLink
        class="ql-btn ql-btn-sm mt-4 bg-primary-deep text-white shadow-sm hover:bg-primary-strong"
        to="/publish"
      >
        去发布
      </RouterLink>
    </div>

    <template v-else>
      <ul class="mt-5 divide-y divide-line">
        <li v-for="item in items" :key="item.id" class="ql-row">
          <RouterLink :to="`/posts/${item.id}`" class="block px-4 pt-3.5 pb-3 max-lg:px-3">
            <div class="flex items-center gap-2">
              <span
                class="ql-tag shrink-0"
                :class="statusClass(item.status)"
              >
                {{ targetStatusLabel(item.status) }}
              </span>
              <span class="ql-tag shrink-0 bg-primary-soft text-primary-deep">{{ item.topicName }}</span>
              <span class="truncate text-xs text-muted">{{ item.anonymousCode }}</span>
              <time
                class="ml-auto shrink-0 text-xs text-muted"
                :datetime="item.createdAt"
                :title="formatAbsoluteTime(item.createdAt)"
              >
                {{ formatRelativeTime(item.createdAt) }}
              </time>
            </div>

            <p class="mt-2 line-clamp-2 text-sm leading-relaxed break-words text-ink">
              {{ item.content }}
            </p>

            <div v-if="item.images.length" class="mt-2 grid grid-cols-4 gap-1.5">
              <img
                v-for="url in item.images"
                :key="url"
                :src="url"
                alt="帖子配图"
                loading="lazy"
                class="aspect-square w-full rounded border border-line object-cover"
              />
            </div>

            <!-- 驳回与下架展示原因；其余状态（待审 / 已通过）没有原因可展示 -->
            <p
              v-if="(item.status === 'REJECTED' || item.status === 'TAKEN_DOWN') && reasonText(item)"
              class="mt-2 rounded-lg bg-danger-soft px-3 py-2 text-xs text-danger-deep"
            >
              {{ item.status === 'REJECTED' ? '驳回原因' : '下架原因' }}：{{ reasonText(item) }}
            </p>

            <div class="mt-2 flex items-center gap-4 text-xs text-muted">
              <span>赞 {{ item.likeCount }}</span>
              <span>评论 {{ item.commentCount }}</span>
            </div>
          </RouterLink>
        </li>
      </ul>

      <div v-if="hasMore" class="mt-4 flex justify-center">
        <BaseButton variant="ghost" size="sm" :loading="loadingMore" @click="loadMore">
          加载更多
        </BaseButton>
      </div>
    </template>
  </section>
</template>
