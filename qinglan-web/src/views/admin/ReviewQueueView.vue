<script setup lang="ts">
import { onMounted } from 'vue'

import QueueTable from '@/components/admin/QueueTable.vue'
import BaseButton from '@/components/base/BaseButton.vue'
import { useReviewQueue } from '@/composables/useReviewQueue'

const { items, loading, loadingMore, hasMore, refresh, loadMore } = useReviewQueue()

onMounted(() => {
  void refresh()
})
</script>

<template>
  <div class="flex flex-col gap-5">
    <div class="flex items-center justify-between">
      <h1 class="text-lg font-semibold text-ink">审核队列</h1>
      <BaseButton variant="ghost" size="sm" :loading="loading" @click="refresh">刷新</BaseButton>
    </div>

    <section class="ql-card overflow-hidden">
      <QueueTable :items="items" :loading="loading" />

      <div v-if="hasMore" class="border-t border-line px-3 py-3 text-center">
        <BaseButton variant="ghost" size="sm" :loading="loadingMore" @click="loadMore">加载更多</BaseButton>
      </div>
    </section>
  </div>
</template>
