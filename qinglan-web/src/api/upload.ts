import { httpPost, httpPostForm } from '@shared/api/http'

import type { ImageUrlRefreshItem, UploadedImage } from '@/types/post'

/**
 * 上传图片，返回可填进发帖请求的地址。
 *
 * 未绑定上传属私有：后端返回的是**签名 URL**（带 exp/sig），发帖页预览（浏览器 `<img>` 不带令牌）
 * 才能即刻取到图。提交发帖时后端按 objectKey 归一（忽略签名查询串），落库的是干净地址。
 * 类型与大小由服务端按文件头字节校验（不信任文件名与 Content-Type）。
 */
export function uploadImage(file: File): Promise<UploadedImage> {
  const form = new FormData()
  form.append('file', file)
  return httpPostForm<UploadedImage>('/uploads/images', form)
}

/**
 * 批量换取「当前用户可直接使用」的图片地址（草稿恢复等场景）。
 *
 * 草稿里存的是原 URL（私有图在 `<img>` 请求里不带令牌、取不到图）；恢复时用它换取当前可用的地址。
 * 无权访问的项 `available=false` 且 `usableUrl=null`，调用方据此提示「需重新上传」。
 */
export function refreshImageUrls(urls: string[]): Promise<ImageUrlRefreshItem[]> {
  return httpPost<ImageUrlRefreshItem[]>('/uploads/image-urls', { urls })
}
