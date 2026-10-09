import { ref } from 'vue'

import { refreshImageUrls, uploadImage } from '@/api/upload'
import { pushToast } from '@shared/composables/useToast'

/** 与后端 qinglan.post.max-images-per-post 保持一致 */
export const MAX_IMAGES_PER_POST = 9

/**
 * 图片上传编排：逐个上传、成功即追加，中途失败时已传成功的仍保留。
 *
 * 放在 composable 而非组件里，是为了让 ImagePicker 保持纯展示——components 只负责外观与
 * 交互，不直接依赖 src/api（AGENTS.md 第七章）。
 */
export function useImageUpload() {
  const uploading = ref(false)

  /**
   * 上传并返回**本次新增**的图片地址（不含传入前的已有项）。
   *
   * 刻意不返回「合并后的完整列表」：那要按调用时的快照拼装，`await` 回来再整份赋值，
   * 会把等待期间用户对列表的改动一起抹掉——上传中删掉的图会「复活」，紧接着再选一次会丢掉
   * 上一次刚传好的结果。合并交给调用方，在拿到返回值后基于**当时的**列表追加。
   */
  async function addFiles(files: File[], existing: string[]): Promise<string[]> {
    if (!files.length) {
      return []
    }
    const remaining = MAX_IMAGES_PER_POST - existing.length
    if (remaining <= 0) {
      // 已满还选图：必须说一声，否则用户以为图传上去了，直到发帖才发现少图
      pushToast(`最多只能上传 ${MAX_IMAGES_PER_POST} 张图片`, 'error')
      return []
    }
    const accepted = files.slice(0, remaining)
    if (files.length > remaining) {
      // 超出的部分不静默丢弃：明确告知忽略了几张
      pushToast(
        `最多 ${MAX_IMAGES_PER_POST} 张图片，已忽略超出的 ${files.length - remaining} 张`,
        'error',
      )
    }
    const added: string[] = []
    uploading.value = true
    try {
      for (const file of accepted) {
        const { url } = await uploadImage(file)
        // 同一次选择内去重；跨次重复由调用方按当时的列表判断
        if (!added.includes(url) && !existing.includes(url)) {
          added.push(url)
        }
      }
    } catch {
      // 错误已由拦截器提示
    } finally {
      uploading.value = false
    }
    return added
  }

  return { uploading, addFiles, refresh }
}

/**
 * 草稿恢复时批量换取当前可用的图片地址（私有图在 `<img>` 请求里不带令牌、草稿里的原 URL 取不到图）。
 * 返回可用地址与「换取不到」的张数——后者须由调用方提示用户重新上传，不要静默破图。
 *
 * 请求失败不在此吞掉：由拦截器统一 toast，异常继续上抛，调用方据此决定是否保留原值。
 */
async function refresh(urls: string[]): Promise<{ images: string[]; missing: number }> {
  if (!urls.length) {
    return { images: [], missing: 0 }
  }
  const items = await refreshImageUrls(urls)
  const images: string[] = []
  let missing = 0
  for (const item of items) {
    if (item.available && item.usableUrl) {
      images.push(item.usableUrl)
    } else {
      missing += 1
    }
  }
  return { images, missing }
}
