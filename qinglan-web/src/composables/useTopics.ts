import { ref } from 'vue'

import { fetchTopics } from '@/api/topic'
import type { Topic } from '@/types/post'

/**
 * 话题列表 + 拉取失败状态。首页（话题筛选）与发布页（话题是发帖前置）的这段逻辑逐字相同：
 * 失败时不提示就等于「本来就没有话题」，或「发布按钮永远灰着却不说为什么」，故两处都要给
 * 「重试」出口。抽到一处，避免两页各写一份、改了一边漏另一边。
 *
 * 请求错误本身仍只由 http 拦截器 toast；这里只记状态位供页面渲染「重试」。
 */
export function useTopics() {
  const topics = ref<Topic[]>([])
  const topicsFailed = ref(false)

  async function loadTopics(): Promise<void> {
    topicsFailed.value = false
    try {
      topics.value = await fetchTopics()
    } catch {
      topicsFailed.value = true
    }
  }

  return { topics, topicsFailed, loadTopics }
}
