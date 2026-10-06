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

  /** 返回追加后的完整列表；同一地址只保留一份，顺带保证模板里 :key="url" 唯一 */
  async function addFiles(files: File[], current: string[]): Promise<string[]> {
    const remaining = MAX_IMAGES_PER_POST - current.length
    if (!files.length || remaining <= 0) {
      return current
    }
    const uploaded = [...current]
    uploading.value = true
    try {
      for (const file of files.slice(0, remaining)) {
        const { url } = await uploadImage(file)
        if (!uploaded.includes(url)) {
          uploaded.push(url)
        }
      }
    } catch {
      // 错误已由拦截器提示
    } finally {
      uploading.value = false
    }
    return uploaded
  }

  return { uploading, addFiles }
}
