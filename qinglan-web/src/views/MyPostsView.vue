<script setup lang="ts">
import { onMounted } from 'vue'

import BaseButton from '@shared/components/base/BaseButton.vue'
import LoadMoreButton from '@shared/components/LoadMoreButton.vue'
import StatePanel from '@shared/components/StatePanel.vue'
import AlertBox from '@/components/AlertBox.vue'
import PostImageGrid from '@/components/PostImageGrid.vue'
import PostMetaRow from '@/components/PostMetaRow.vue'
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

    <StatePanel
      :state="loading ? 'loading' : failed ? 'failed' : items.length === 0 ? 'empty' : 'ready'"
      failed-title="帖子没能加载出来"
      empty-title="你还没有发布过帖子"
      @retry="load()"
    >
      <template #empty-action>
        <BaseButton as="RouterLink" size="sm" to="/publish">去发布</BaseButton>
      </template>

      <ul class="mt-5 divide-y divide-line">
        <li v-for="item in items" :key="item.id" class="ql-row">
          <RouterLink :to="`/posts/${item.id}`" class="block px-4 pt-3.5 pb-3 max-lg:px-3">
            <PostMetaRow
              :topic-name="item.topicName"
              :anonymous-code="item.anonymousCode"
              :created-at="item.createdAt"
            >
              <template #leading>
                <span class="ql-tag shrink-0" :class="statusClass(item.status)">
                  {{ targetStatusLabel(item.status) }}
                </span>
              </template>
            </PostMetaRow>

            <p class="mt-2 line-clamp-2 text-sm leading-relaxed break-words text-ink">
              {{ item.content }}
            </p>

            <PostImageGrid class="mt-2" :urls="item.images" />

            <!-- 驳回与下架展示原因；其余状态（待审 / 已通过）没有原因可展示 -->
            <AlertBox
              v-if="(item.status === 'REJECTED' || item.status === 'TAKEN_DOWN') && reasonText(item)"
              class="mt-2"
            >
              {{ item.status === 'REJECTED' ? '驳回原因' : '下架原因' }}：{{ reasonText(item) }}
            </AlertBox>

            <div class="mt-2 flex items-center gap-4 text-xs text-muted">
              <span>赞 {{ item.likeCount }}</span>
              <span>评论 {{ item.commentCount }}</span>
            </div>
          </RouterLink>
        </li>
      </ul>

      <LoadMoreButton :has-more="hasMore" :loading="loadingMore" @load="loadMore" />
    </StatePanel>
  </section>
</template>
