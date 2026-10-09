<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'

import BaseButton from '@shared/components/base/BaseButton.vue'
import BaseCard from '@shared/components/base/BaseCard.vue'
import { useAuth } from '@shared/composables/useAuth'
import { useUserLabels } from '@shared/composables/useUserLabels'
import { useUserStore } from '@shared/stores/user'

const userStore = useUserStore()
const auth = useAuth()
const { roleLabel, statusLabel } = useUserLabels()

const loading = ref(false)
const failed = ref(false)

/** 只展示对用户有意义的字段；内部数字 ID 等不面向用户 */
const rows = computed(() => {
  const profile = userStore.profile
  if (!profile) {
    return []
  }
  return [
    { key: '状态', value: statusLabel(profile.status) },
    { key: '信用分', value: String(profile.creditScore) },
    { key: '注册时间', value: new Date(profile.createdAt).toLocaleString() },
  ]
})

async function loadProfile(): Promise<void> {
  if (userStore.profile) {
    return
  }
  loading.value = true
  failed.value = false
  try {
    await auth.loadProfile()
  } catch {
    // 请求错误只由 http 拦截器 toast（AGENTS.md 第七章）；这里只置状态位，
    // 让页面给出「重试」出口，而不是失败后除标题外一片空白
    failed.value = true
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  void loadProfile()
})
</script>

<template>
  <section class="ql-shell py-8 max-lg:py-5">
    <h1 class="text-lg font-semibold text-ink">我的</h1>

    <p v-if="loading" class="mt-6 text-sm text-muted">加载中…</p>

    <!-- 失败态只在确实拿不到档案时出现，文案是状态说明；请求错误信息本身仍只由拦截器 toast -->
    <div
      v-else-if="failed && !userStore.profile"
      class="mt-6 rounded-xl border border-dashed border-line-strong px-5 py-10 text-center"
    >
      <p class="text-sm font-medium text-ink">资料没能加载出来</p>
      <BaseButton class="mt-4" variant="ghost" size="sm" @click="loadProfile()">重试</BaseButton>
    </div>

    <div
      v-else-if="userStore.profile"
      class="mt-4 grid grid-cols-[minmax(0,1fr)_320px] gap-10 max-lg:(grid-cols-1 gap-6)"
    >
      <div class="min-w-0 self-start">
        <BaseCard :padded="false">
          <!-- 去装饰：原来是渐变横幅 + 白色圆形光斑，属「AI 味」视觉；改为纯色块 -->
          <div class="h-20 rounded-t-xl bg-primary-soft" />

          <div class="px-5 pb-5">
            <div class="-mt-8 flex items-end gap-3">
              <span
                class="flex size-16 shrink-0 items-center justify-center rounded-2xl border-4 border-surface bg-primary-soft text-xl font-bold text-primary"
              >
                匿
              </span>
              <!-- 名称与角色标签同行：竖排时文字块会高出头像下半截、顶到上方色块上去 -->
              <div class="flex min-w-0 items-center gap-2 pb-1">
                <p class="truncate text-sm font-semibold text-ink">匿名用户</p>
                <span class="ql-tag shrink-0 bg-primary-soft text-primary-deep">
                  {{ roleLabel(userStore.profile.role) }}
                </span>
              </div>
            </div>

            <dl class="mt-5 divide-y divide-line">
              <div
                v-for="row in rows"
                :key="row.key"
                class="flex items-center justify-between gap-4 py-3"
              >
                <dt class="text-sm text-muted">{{ row.key }}</dt>
                <dd class="truncate text-sm font-medium text-ink">{{ row.value }}</dd>
              </div>
            </dl>
          </div>
        </BaseCard>

        <div class="ql-card mt-4 p-4">
          <p class="text-sm font-semibold text-ink">我的帖子</p>
          <p class="mt-1 text-xs leading-relaxed text-muted">
            查看自己发布的帖子，覆盖待审、已通过、已驳回与已下架全部状态；驳回与下架会展示原因。
          </p>
          <div class="mt-3 flex flex-wrap gap-2">
            <RouterLink
              class="ql-btn ql-btn-sm bg-primary-deep text-white shadow-sm hover:bg-primary-strong"
              to="/my-posts"
            >
              查看我的帖子
            </RouterLink>
            <RouterLink
              class="ql-btn ql-btn-sm border border-line bg-surface text-muted hover:border-primary-soft hover:text-primary-deep"
              to="/publish"
            >
              去发布
            </RouterLink>
          </div>
        </div>
      </div>

      <aside class="sticky top-24 flex flex-col gap-4 self-start max-lg:hidden">
        <div class="ql-card p-4">
          <p class="text-sm font-semibold text-ink">关于信用分</p>
          <p class="mt-2 text-xs leading-relaxed text-muted">
            信用分低于 60 无法发帖。违规内容被核实后会扣分，正常发帖与互动不扣分。
          </p>
        </div>
      </aside>
    </div>
  </section>
</template>
