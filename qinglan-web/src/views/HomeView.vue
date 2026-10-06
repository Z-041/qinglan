<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'

import { fetchTopics } from '@/api/topic'
import BaseButton from '@/components/base/BaseButton.vue'
import HomeSidebar from '@/components/HomeSidebar.vue'
import PostCard from '@/components/PostCard.vue'
import TopicFilter from '@/components/TopicFilter.vue'
import { useFeed } from '@/composables/useFeed'
import { useUserStore } from '@/stores/user'
import type { Topic } from '@/types/post'

const userStore = useUserStore()

const { posts, topicId, loading, loadingMore, hasMore, load, refresh, loadMore, toggleLike } =
  useFeed()

const topics = ref<Topic[]>([])

/** 首条作头条大卡，其余进紧凑行列表；切换话题与加载更多后自动重算（追加不影响头条） */
const featurePost = computed(() => posts.value[0])
const compactPosts = computed(() => posts.value.slice(1))

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
  <section class="ql-shell py-8 max-lg:py-5">
    <div
      class="flex items-center justify-between gap-8 rounded-2xl bg-gradient-to-br from-primary to-primary-deep px-8 py-8 text-white max-lg:(flex-col items-start gap-5 px-5 py-6)"
    >
      <div class="min-w-0">
        <h1 class="text-2xl font-semibold tracking-tight max-sm:text-lg">校园里的事，匿名说出来</h1>
        <p class="mt-2 max-w-[560px] text-sm text-primary-soft">
          本校学生认证 · 匿名代号跨帖不可关联
        </p>
      </div>
      <RouterLink
        class="ql-btn ql-btn-md shrink-0 bg-surface text-primary-deep hover:bg-primary-tint"
        :to="userStore.isAuthenticated ? '/publish' : '/login'"
      >
        {{ userStore.isAuthenticated ? '发布新帖' : '去认证' }}
      </RouterLink>
    </div>

    <div class="mt-8 grid grid-cols-[minmax(0,1fr)_320px] gap-10 max-lg:(mt-6 grid-cols-1 gap-6)">
      <div class="min-w-0 self-start">
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

        <!-- 窄屏才落下话题 chips；PC 由右栏话题导航承担同一职责 -->
        <div class="mb-4 hidden max-lg:block">
          <TopicFilter v-model="topicId" :topics="topics" />
        </div>

        <p v-if="loading" class="py-8 text-center text-sm text-muted">加载中…</p>

        <div
          v-else-if="!posts.length"
          class="rounded-xl border border-dashed border-line-strong px-5 py-10 text-center"
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

        <template v-else>
          <PostCard :post="featurePost" variant="feature" @toggle-like="toggleLike(featurePost)" />

          <div v-if="compactPosts.length" class="ql-card mt-4 divide-y divide-line overflow-hidden">
            <PostCard
              v-for="post in compactPosts"
              :key="post.id"
              :post="post"
              variant="compact"
              @toggle-like="toggleLike(post)"
            />
          </div>
        </template>

        <div v-if="!loading && posts.length && hasMore" class="mt-4 flex justify-center">
          <BaseButton variant="ghost" size="sm" :loading="loadingMore" @click="loadMore">
            加载更多
          </BaseButton>
        </div>
      </div>

      <HomeSidebar v-model="topicId" :topics="topics" :authenticated="userStore.isAuthenticated" />
    </div>
  </section>
</template>
