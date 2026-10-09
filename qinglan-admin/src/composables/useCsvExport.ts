import type { BlobDownload } from '@shared/api/http'
import { useSubmit } from '@shared/composables/useSubmit'

/** 两位补零，供兜底文件名的时间戳拼接 */
function pad(value: number): string {
  return String(value).padStart(2, '0')
}

/** 拿不到后端文件名时的兜底：`<前缀>-YYYYMMDD-HHmmss.csv` */
export function timestampedFileName(base: string, now: Date = new Date()): string {
  const stamp =
    `${now.getFullYear()}${pad(now.getMonth() + 1)}${pad(now.getDate())}` +
    `-${pad(now.getHours())}${pad(now.getMinutes())}${pad(now.getSeconds())}`
  return `${base}-${stamp}.csv`
}

/** 触发浏览器保存 Blob；用完即释放 object URL，避免长会话里内存泄漏 */
export function saveBlob(blob: Blob, fileName: string): void {
  const url = URL.createObjectURL(blob)
  const anchor = document.createElement('a')
  anchor.href = url
  anchor.download = fileName
  anchor.rel = 'noopener'
  document.body.appendChild(anchor)
  anchor.click()
  anchor.remove()
  URL.revokeObjectURL(url)
}

/**
 * CSV 导出（阶段 B）：把「带令牌取 Blob + 处理后端 JSON 错误 + 落盘」收在一处，
 * 各页面只提供「请求函数 + 兜底文件名」，不各写一遍。
 *
 * <p>错误（含导出超限 QL-400-014）已由 http 拦截器统一弹「明确原因」，这里只负责结束 loading，
 * 不再重复提示（AGENTS.md 第七章：请求错误只有一个出口）。</p>
 */
export function useCsvExport() {
  const { submitting, run } = useSubmit()

  /** 成功返回 true；失败返回 false（错误已由拦截器提示） */
  async function download(
    request: () => Promise<BlobDownload>,
    fallbackBase: string,
  ): Promise<boolean> {
    return run(async () => {
      const { blob, fileName } = await request()
      // 文件名以后端 Content-Disposition 为准；拿不到才用带时间戳的兜底名
      saveBlob(blob, fileName ?? timestampedFileName(fallbackBase))
    })
  }

  // 对外仍叫 exporting（本页语义），实现与其它提交态共用 useSubmit
  return { exporting: submitting, download }
}
