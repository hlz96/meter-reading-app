import { client } from './client'
import { ApiError, ErrorCode } from '@/types/error'
import type { ApiEnvelope } from '@/types/dto'

/**
 * 导出下载(前端 TRD §7.2)。responseType:'blob',拦截器对 blob 放行;
 * 若响应 content-type 含 json 说明是错误信封 → 解析出 ApiError。
 * 返回 {blob, filename}(filename 从 Content-Disposition 取)。
 */
export interface DownloadResult {
  blob: Blob
  filename: string
}

async function download(url: string, fallbackName: string): Promise<DownloadResult> {
  const resp = await client.get(url, { responseType: 'blob' })
  const blob = resp.data as Blob
  const contentType = String(resp.headers['content-type'] || '')

  // blob 但实为 JSON 错误信封:读出来解析成 ApiError
  if (contentType.includes('json')) {
    const text = await blob.text()
    try {
      const env = JSON.parse(text) as ApiEnvelope<unknown>
      throw new ApiError(env.code ?? ErrorCode.SERVER_ERROR, env.message || '导出失败')
    } catch (e) {
      if (e instanceof ApiError) throw e
      throw new ApiError(ErrorCode.SERVER_ERROR, '导出失败')
    }
  }

  return { blob, filename: parseFilename(String(resp.headers['content-disposition'] || ''), fallbackName) }
}

/** 解析 Content-Disposition:优先 filename*=UTF-8''xxx,回退 filename="xxx"。 */
function parseFilename(disposition: string, fallback: string): string {
  if (!disposition) return fallback
  const star = /filename\*=UTF-8''([^;]+)/i.exec(disposition)
  if (star?.[1]) {
    try {
      return decodeURIComponent(star[1])
    } catch {
      /* ignore */
    }
  }
  const plain = /filename="?([^";]+)"?/i.exec(disposition)
  return plain?.[1] || fallback
}

export const exportApi = {
  /** 首版只接催单导出(按公司)。台账/读数导出二期。 */
  dunningByCompany: (periodId: number, companyId: number) =>
    download(
      `/dunning/${periodId}/company/${companyId}/export`,
      `催缴单_${periodId}_${companyId}.xlsx`,
    ),
}
