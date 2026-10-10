<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'

import { fetchTopics } from '@/api/topic'
import BaseButton from '@shared/components/base/BaseButton.vue'
import LoadMoreButton from '@shared/components/LoadMoreButton.vue'
import StatePanel from '@shared/components/StatePanel.vue'
import HomeSidebar from '@/components/HomeSidebar.vue'
import PostCard from '@/components/PostCard.vue'
import TopicFilter from '@/components/TopicFilter.vue'
import { useFeed } from '@/composables/useFeed'
import { useUserStore } from '@shared/stores/user'
import type { PostSort, Topic } from '@/types/post'

const userStore = useUserStore()

const { posts, topicId, sort, loading, loadingMore, hasMore, failed, load, setSort, refresh, loadMore, toggleLike } =
  useFeed()

/** 排序 Tab。热门口径由后端 `sort=hot` 计算，前端只负责下发参数 */
const SORT_TABS: { value: PostSort; label: string }[] = [
  { value: 'latest', label: '最新' },
  { value: 'hot', label: '热门' },
]

const topics = ref<Topic[]>([])
/**
 * 话题拉取是否失败过。失败时话题筛选整块消失，不提示就等于「本来就没有话题」——
 * 与发布页（同样以话题为前置）用同一套「提示 + 重试」口径。
 */
const topicsFailed = ref(false)

async function loadTopics(): Promise<void> {
  topicsFailed.value = false
  try {
    topics.value = await fetchTopics()
  } catch {
    // 话题筛选缺失不影响信息流本身，错误已由拦截器提示；这里只记状态位供页面给「重试」
    topicsFailed.value = true
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
  <section class="ql-shell py-6 max-lg:py-4">
    <!-- 信息流首页无视觉大标题，h1 仅服务于标题层级与屏幕阅读器地标 -->
    <h1 class="sr-only">最新帖子</h1>
    <!-- 话题横导航（取代原 hero 横幅）：话题是信息流的一级筛选，置于内容最上方 -->
    <div class="border-b border-line pb-3">
      <TopicFilter v-model="topicId" :topics="topics" />
    </div>

    <!-- 放在主列而非侧栏：侧栏在窄屏整块隐藏，提示放这里各宽度都看得见、只渲染一份 -->
    <p v-if="topicsFailed" class="mt-3 text-xs text-muted">
      话题没能加载出来
      <BaseButton class="ml-1" variant="ghost" size="sm" @click="loadTopics">重试</BaseButton>
    </p>

    <div class="mt-5 grid grid-cols-[minmax(0,1fr)_320px] gap-10 max-lg:(mt-4 grid-cols-1 gap-6)">
      <div class="min-w-0 self-start">
        <div
          v-if="!userStore.isAuthenticated"
          class="mb-4 flex flex-wrap items-center gap-3 rounded-lg border border-line bg-surface px-4 py-3"
        >
          <div class="min-w-0 flex-1">
            <p class="text-sm font-semibold text-ink">认证后即可匿名发帖与互动</p>
            <p class="mt-0.5 text-xs text-muted">游客可以浏览，发帖、评论与点赞需校园邮箱认证。</p>
          </div>
          <BaseButton as="RouterLink" size="sm" to="/login">去认证</BaseButton>
        </div>

        <!-- 排序 Tab：最新 / 热门。切 Tab 保留当前话题、重置到第一页（sort 由 useFeed 下发）。
             热门为后端现算分，翻页期间分数漂移可能漏/重某条——这是后端已知局限，前端不做去重 hack。 -->
        <div class="flex items-center gap-5 border-b border-line">
          <button
            v-for="tab in SORT_TABS"
            :key="tab.value"
            type="button"
            class="cursor-pointer border-b-2 pb-2 text-sm transition"
            :class="
              sort === tab.value
                ? 'border-primary-deep font-medium text-primary-deep'
                : 'border-transparent text-muted hover:text-primary-deep'
            "
            :aria-pressed="sort === tab.value"
            @click="setSort(tab.value)"
          >
            {{ tab.label }}
          </button>
        </div>

        <!-- 加载失败与「真的没有内容」由 StatePanel 分成两屏；请求错误信息只由 http
             拦截器的 toast 给出（AGENTS.md 第七章），这里不重复渲染 -->
        <StatePanel
          :state="loading ? 'loading' : failed ? 'failed' : posts.length === 0 ? 'empty' : 'ready'"
          top-space-class="mt-4"
          failed-title="内容没能加载出来"
          empty-title="这里还没有内容"
          empty-description="成为第一个发帖的人"
          @retry="refresh()"
        >
          <template #empty-action>
            <BaseButton v-if="userStore.isAuthenticated" as="RouterLink" size="sm" to="/publish">
              去发布
            </BaseButton>
          </template>

          <!-- 分割线列表：每条一行（话题 + 摘要 + 配图 + 互动数据），不再分头条大卡与紧凑行 -->
          <PostCard
            v-for="post in posts"
            :key="post.id"
            :post="post"
            @toggle-like="toggleLike(post)"
          />

          <LoadMoreButton
            v-if="!loading && posts.length"
            :has-more="hasMore"
            :loading="loadingMore"
            @load="loadMore"
          />
        </StatePanel>
      </div>

      <HomeSidebar v-model="topicId" :topics="topics" :authenticated="userStore.isAuthenticated" />
    </div>
  </section>
</template>
