<script setup lang="ts">
import { useUserStore } from '@/stores/user'

const userStore = useUserStore()

const highlights = [
  { no: '01', title: '仅限本校', desc: '校园邮箱认证，非本校成员无法进入，社区更干净。' },
  { no: '02', title: '匿名发帖', desc: '每个帖子一个独立代号，跨帖无法被关联到同一个人。' },
  { no: '03', title: '自主治理', desc: '举报、申诉与信用分共同维护秩序，规则由社区把关。' },
]
</script>

<template>
  <section class="py-8 sm:py-14">
    <div class="text-center">
      <span class="ql-badge border border-primary-soft bg-surface text-primary-deep">
        <span class="size-1.5 rounded-full bg-primary" />
        仅限本校认证学生
      </span>

      <h1 class="ql-title-gradient mt-6 text-4xl font-bold tracking-tight sm:text-5xl">青栏</h1>

      <p class="mx-auto mt-4 max-w-sm text-sm leading-relaxed text-muted sm:max-w-md sm:text-base">
        说真话，不露脸。发帖、讨论、互助，都在这一个校园里。
      </p>

      <div class="mt-8 flex flex-col items-center justify-center gap-3 sm:flex-row">
        <p v-if="userStore.isAuthenticated" class="text-sm text-muted">你已完成认证，可以开始浏览与发帖。</p>
        <RouterLink
          v-else
          class="ql-btn ql-btn-md w-full bg-primary-deep text-white shadow-sm hover:bg-primary-strong hover:shadow-md sm:w-auto"
          to="/login"
        >
          使用校园邮箱认证
        </RouterLink>
      </div>
    </div>

    <ul class="mx-auto mt-14 grid max-w-3xl gap-4 text-left sm:grid-cols-3">
      <li v-for="item in highlights" :key="item.no" class="ql-card-interactive group overflow-hidden p-5">
        <span
          class="inline-flex size-9 items-center justify-center rounded-lg bg-primary-soft text-sm font-semibold text-primary-deep"
        >
          {{ item.no }}
        </span>
        <h2 class="mt-4 text-sm font-semibold text-ink">{{ item.title }}</h2>
        <p class="mt-1.5 text-xs leading-relaxed text-muted">{{ item.desc }}</p>
        <span
          class="absolute inset-x-0 bottom-0 h-0.5 origin-left scale-x-0 bg-gradient-to-r from-primary to-primary-deep transition group-hover:scale-x-100"
          aria-hidden="true"
        />
      </li>
    </ul>
  </section>
</template>
