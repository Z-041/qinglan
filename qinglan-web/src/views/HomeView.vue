<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'

import { fetchTopics } from '@/api/topic'
import BaseButton from '@/components/base/BaseButton.vue'
import PostCard from '@/components/PostCard.vue'
import TopicFilter from '@/components/TopicFilter.vue'
import { useFeed } from '@/composables/useFeed'
import { useUserStore } from '@/stores/user'
import type { Topic } from '@/types/post'

const userStore = useUserStore()

const { posts, topicId, loading, loadingMore, hasMore, load, refresh, loadMore, toggleLike } =
  useFeed()

const topics = ref<Topic[]>([])

async function loadTopics(): Promise<void> {
  try {
    topics.value = await fetchTopics()
  } catch {
    // 话题筛选缺失不影响信息流本身，错误已由拦截器提示
  }
}

onMounted(() => {
  void load(null)
  void loadTopics()
})

// 话题变更后回到第一页；refresh 只读取 topicId，不会反向写它
watch(topicId, () => {
  void refresh()
})
</script>

<template>
  <section class="py-2">
    <div
      v-if="!userStore.isAuthenticated"
      class="ql-card mb-4 flex flex-wrap items-center gap-3 p-4"
    >
      <div class="min-w-0 flex-1">
        <p class="text-sm font-semibold text-ink">认证后即可匿名发帖与互动</p>
        <p class="mt-0.5 text-xs text-muted">游客可以浏览，发帖、评论与点赞需校园邮箱认证。</p>
      </div>
      <RouterLink
        class="ql-btn ql-btn-sm bg-primary-deep text-white shadow-sm hover:bg-primary-strong"
        to="/login"
      >
        去认证
      </RouterLink>
    </div>

    <TopicFilter v-model="topicId" :topics="topics" />

    <p v-if="loading" class="mt-8 text-center text-sm text-muted">加载中…</p>

    <div
      v-else-if="!posts.length"
      class="mt-6 rounded-xl border border-dashed border-line-strong px-5 py-10 text-center"
    >
      <p class="text-sm font-medium text-ink">这里还没有内容</p>
      <p class="mt-1 text-xs text-muted">成为第一个发帖的人</p>
      <RouterLink
        v-if="userStore.isAuthenticated"
        class="ql-btn ql-btn-sm mt-4 bg-primary-deep text-white shadow-sm hover:bg-primary-strong"
        to="/publish"
      >
        去发布
      </RouterLink>
    </div>

    <ul v-else class="mt-4 flex flex-col gap-3">
      <li v-for="post in posts" :key="post.id">
        <PostCard :post="post" @toggle-like="toggleLike(post)" />
      </li>
    </ul>

    <div v-if="!loading && posts.length && hasMore" class="mt-4 flex justify-center">
      <BaseButton variant="ghost" size="sm" :loading="loadingMore" @click="loadMore">
        加载更多
      </BaseButton>
    </div>
  </section>
</template>
