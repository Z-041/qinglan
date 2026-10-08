import { httpGet } from '@shared/api/http'

import type { Topic } from '@/types/post'

/** 启用中的话题字典，游客亦可读取（发布页与首页筛选共用） */
export function fetchTopics(): Promise<Topic[]> {
  return httpGet<Topic[]>('/topics')
}
