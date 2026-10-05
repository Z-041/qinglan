import { httpPostForm } from './http'

import type { UploadedImage } from '@/types/post'

/**
 * 上传图片，返回可填进发帖请求的地址。
 * 类型与大小由服务端按文件头字节校验（不信任文件名与 Content-Type）。
 */
export function uploadImage(file: File): Promise<UploadedImage> {
  const form = new FormData()
  form.append('file', file)
  return httpPostForm<UploadedImage>('/uploads/images', form)
}
