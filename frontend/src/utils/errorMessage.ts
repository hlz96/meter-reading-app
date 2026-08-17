import { ApiError, ErrorCode } from '@/types/error'

/** ApiError → 用户可读文案(前端 TRD §4.5)。未知错误回退到 error.message。 */
export function errorMessage(e: unknown): string {
  if (e instanceof ApiError) {
    // 大多数情况后端 message 已足够友好,直接用;个别码补充默认文案
    if (e.message) return e.message
    switch (e.code) {
      case ErrorCode.NETWORK:
        return '网络连接失败,请检查网络'
      case ErrorCode.FORBIDDEN:
        return '无权限执行此操作'
      case ErrorCode.CROSS_ORG:
        return '越权访问'
      case ErrorCode.SERVER_ERROR:
        return '服务器错误,请稍后重试'
      default:
        return '请求失败'
    }
  }
  if (e instanceof Error) return e.message
  return '未知错误'
}
