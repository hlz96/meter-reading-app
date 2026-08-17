import { readingApi } from '@/api/reading'
import { BATCH_LIMIT } from '@/config'
import { ApiError } from '@/types/error'
import type { BatchResult, ReadingPayload } from '@/types/dto'
import { getAllPending, removePending } from './db'
import { useOfflineQueue } from '@/store/offlineQueue'

/**
 * 排空离线队列 → /readings/batch(前端 TRD §7.1)。
 * clientUuid 幂等保证重复上传安全。成功的条目从 db 删除;网络错误保留待下次重试。
 * 返回聚合的 BatchResult;无待同步返回 null。
 */
export async function syncPendingReadings(): Promise<BatchResult | null> {
  const queue = useOfflineQueue.getState()
  const pending = await getAllPending()
  if (pending.length === 0) return null

  queue.setSyncing(true)
  try {
    let successCount = 0
    let failCount = 0
    const errors: string[] = []

    // 分批(后端上限 500)
    for (let i = 0; i < pending.length; i += BATCH_LIMIT) {
      const slice = pending.slice(i, i + BATCH_LIMIT)
      const items: ReadingPayload[] = slice.map((p) => ({
        meterId: p.meterId,
        periodId: p.periodId,
        currReading: p.currReading,
        photoUrl: p.photoUrl,
        clientUuid: p.clientUuid,
        remark: p.remark,
      }))

      try {
        const res = await readingApi.submitBatch(items)
        successCount += res.successCount
        failCount += res.failCount
        errors.push(...res.errors)
        // 整批已被后端受理(逐条独立事务 + 幂等):无论单条成败都可从队列移除,
        // 失败明细已在 errors 中反馈给用户,重试同一条无意义(会再次失败)。
        await removePending(slice.map((p) => p.clientUuid))
      } catch (e) {
        // 网络错误:整批保留,停止后续批次,下次网络恢复再试
        if (e instanceof ApiError && e.isNetwork) {
          errors.push('网络中断,剩余读数保留待下次同步')
          break
        }
        throw e
      }
    }

    await queue.refresh()
    return { successCount, failCount, errors }
  } finally {
    queue.setSyncing(false)
  }
}

/** 注册网络恢复自动同步(在 App 挂载时调用一次)。 */
export function registerAutoSync(onSynced?: (r: BatchResult | null) => void): () => void {
  const handler = () => {
    syncPendingReadings()
      .then((r) => onSynced?.(r))
      .catch(() => {
        /* 静默:下次 online 或手动再试 */
      })
  }
  window.addEventListener('online', handler)
  return () => window.removeEventListener('online', handler)
}
