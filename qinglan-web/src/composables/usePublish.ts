import { computed, ref } from 'vue'

import { createPost } from '@/api/post'
import { fetchTopics } from '@/api/topic'
import type { CreatePostRequest, PostDetail, Topic } from '@/types/post'

/** 中国大陆手机号。联系方式应填在独立字段（设计方案 4.3），出现在正文里即提示违规 */
const PHONE_PATTERN = /1[3-9]\d{9}/

/**
 * 发布页：话题与规则、正文、联系方式、图片。
 *
 * 客户端的违规提示只是体验层（降低违规率），真正的拦截在服务端（设计方案第四章）。
 */
export function usePublish() {
  const topics = ref<Topic[]>([])
  const topicId = ref<number | null>(null)
  const content = ref('')
  const contact = ref('')
  const images = ref<string[]>([])

  const submitting = ref(false)
  /**
   * 话题拉取是否失败过。话题是发帖的前置：失败时不提示就等于「选择话题」永远空白、
   * 发布按钮永远灰着却不说为什么。
   */
  const topicsFailed = ref(false)

  const selectedTopic = computed(
    () => topics.value.find((topic) => topic.id === topicId.value) ?? null,
  )

  /** 命中时禁止发布，并提示把联系方式挪到独立字段 */
  const blockedReason = computed(() =>
    PHONE_PATTERN.test(content.value) ? '正文里出现了手机号，请填写到下方「联系方式」栏' : null,
  )

  /** 二手 / 失物等话题要求必填联系方式（topic.requires_contact） */
  const contactRequired = computed(() => selectedTopic.value?.requiresContact ?? false)

  const canSubmit = computed(
    () =>
      Boolean(topicId.value) &&
      content.value.trim().length > 0 &&
      (!contactRequired.value || contact.value.trim().length > 0) &&
      blockedReason.value === null,
  )

  async function loadTopics(): Promise<void> {
    topicsFailed.value = false
    try {
      topics.value = await fetchTopics()
    } catch {
      // 错误已由拦截器提示；这里只记状态位，供页面给出「重试」
      topicsFailed.value = true
    }
  }

  /** 发布成功返回详情，失败返回 null（错误已由拦截器提示） */
  async function submit(): Promise<PostDetail | null> {
    if (!canSubmit.value || submitting.value || topicId.value === null) {
      return null
    }
    submitting.value = true
    const payload: CreatePostRequest = {
      topicId: topicId.value,
      content: content.value.trim(),
      contact: contact.value.trim() ? contact.value.trim() : null,
      images: images.value,
    }
    try {
      return await createPost(payload)
    } catch {
      return null
    } finally {
      submitting.value = false
    }
  }

  return {
    topics,
    topicId,
    content,
    contact,
    images,
    submitting,
    topicsFailed,
    selectedTopic,
    blockedReason,
    contactRequired,
    canSubmit,
    loadTopics,
    submit,
  }
}
