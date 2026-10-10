<script setup lang="ts">
import BaseButton from '@shared/components/base/BaseButton.vue'
import SidebarCard from '@/components/SidebarCard.vue'
import type { Topic } from '@/types/post'

defineProps<{
  topics: Topic[]
  authenticated: boolean
}>()

/** null 表示「全部」，与 TopicFilter 同一契约 */
const model = defineModel<number | null>({ default: null })
</script>

<template>
  <!--
    侧栏容器（sticky / 断点隐藏 / 卡间距）由 TwoColumnLayout 的 #aside 插槽提供，
    本组件只出卡片，根为多根片段——故调用方必须把它放进 TwoColumnLayout 的 #aside，
    不要再自套 <aside>，否则页面里会出现嵌套 aside。
  -->
  <SidebarCard :title="authenticated ? '说说今天校园里的新鲜事' : '认证后即可匿名发帖'">
    <p class="mt-1 text-xs text-muted">
      {{ authenticated ? '匿名代号按帖生成，跨帖不可关联' : '发帖、评论与点赞需校园邮箱认证' }}
    </p>
    <BaseButton as="RouterLink" size="sm" class="mt-3" :to="authenticated ? '/publish' : '/login'">
      {{ authenticated ? '发布新帖' : '去认证' }}
    </BaseButton>
  </SidebarCard>

  <SidebarCard v-if="topics.length" title="话题">
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
  </SidebarCard>

  <SidebarCard title="社区须知">
    <ul class="mt-2 flex flex-col gap-1.5 text-xs text-muted">
      <li>匿名代号跨帖不可关联，同一人在不同帖子下代号不同</li>
      <li>发帖前先看话题规则，带联系方式的话题请谨慎</li>
      <li>发现违规可举报，管理员会逐条处理</li>
    </ul>
  </SidebarCard>
</template>
