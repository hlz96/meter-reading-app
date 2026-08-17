import type { DownloadResult } from '@/api/export'

/** 触发浏览器下载 blob(前端 TRD §7.2)。 */
export function triggerDownload({ blob, filename }: DownloadResult): void {
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = filename
  document.body.appendChild(a)
  a.click()
  a.remove()
  // 释放对象 URL
  setTimeout(() => URL.revokeObjectURL(url), 1000)
}
