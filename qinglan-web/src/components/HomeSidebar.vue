<script setup lang="ts">
import type { Topic } from '@/types/post'

defineProps<{
  topics: Topic[]
  authenticated: boolean
}>()

/** null 表示「全部」，与 TopicFilter 同一契约 */
const model = defineModel<number | null>({ default: null })
</script>

<template>
  <aside class="sticky top-24 flex flex-col gap-4 self-start max-lg:hidden">
    <div class="ql-card p-4">
      <p class="text-sm font-semibold text-ink">
        {{ authenticated ? '说说今天校园里的新鲜事' : '认证后即可匿名发帖' }}
      </p>
      <p class="mt-1 text-xs text-muted">
        {{ authenticated ? '匿名代号按帖生成，跨帖不可关联' : '发帖、评论与点赞需校园邮箱认证' }}
      </p>
      <RouterLink
        class="ql-btn ql-btn-sm mt-3 bg-primary-deep text-white shadow-sm hover:bg-primary-strong"
        :to="authenticated ? '/publish' : '/login'"
      >
        {{ authenticated ? '发布新帖' : '去认证' }}
      </RouterLink>
    </div>

    <div v-if="topics.length" class="ql-card p-4">
      <p class="text-sm font-semibold text-ink">话题</p>
      <nav class="mt-2 flex flex-col" aria-label="话题筛选">
        <button
          type="button"
          class="cursor-pointer rounded-lg px-2 py-1.5 text-left text-sm transition"
          :class="
            model === null
              ? 'bg-primary-soft font-medium text-primary-deep'
              : 'text-muted hover:bg-primary-tint hover:text-ink'
          "
          :aria-pressed="model === null"
          @click="model = null"
        >
          全部
        </button>
        <button
          v-for="topic in topics"
          :key="topic.id"
          type="button"
          class="cursor-pointer rounded-lg px-2 py-1.5 text-left text-sm transition"
          :class="
            model === topic.id
              ? 'bg-primary-soft font-medium text-primary-deep'
              : 'text-muted hover:bg-primary-tint hover:text-ink'
          "
          :aria-pressed="model === topic.id"
          @click="model = topic.id"
        >
          {{ topic.name }}
        </button>
      </nav>
    </div>

    <div class="ql-card p-4">
      <p class="text-sm font-semibold text-ink">社区须知</p>
      <ul class="mt-2 flex flex-col gap-1.5 text-xs text-muted">
        <li>匿名代号跨帖不可关联，同一人在不同帖子下代号不同</li>
        <li>发帖前先看话题规则，带联系方式的话题请谨慎</li>
        <li>发现违规可举报，管理员会逐条处理</li>
      </ul>
    </div>
  </aside>
</template>