import { ref } from 'vue'

import { uploadImage } from '@/api/upload'

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
    const remaining = MAX_IMAGES_PER_POST - existing.length
    if (!files.length || remaining <= 0) {
      return []
    }
    const added: string[] = []
    uploading.value = true
    try {
      for (const file of files.slice(0, remaining)) {
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

  return { uploading, addFiles }
}
