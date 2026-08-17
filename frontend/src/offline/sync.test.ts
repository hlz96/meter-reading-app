import { beforeEach, describe, it, expect, vi } from 'vitest'
import { syncPendingReadings } from '@/offline/sync'
import { enqueueReading, getAllPending, clearPending, type PendingReading } from '@/offline/db'
import { readingApi } from '@/api/reading'
import { ApiError, ErrorCode } from '@/types/error'
import type { BatchResult } from '@/types/dto'

vi.mock('@/api/reading', () => ({
  readingApi: { submitBatch: vi.fn() },
}))

const submitBatch = vi.mocked(readingApi.submitBatch)

const mk = (uuid: string, queuedAt: number): PendingReading => ({
  clientUuid: uuid,
  meterId: 1,
  periodId: 1,
  currReading: 10,
  queuedAt,
})

const ok = (n: number): BatchResult => ({ successCount: n, failCount: 0, errors: [] })

beforeEach(async () => {
  await clearPending()
  submitBatch.mockReset()
})

describe('syncPendingReadings', () => {
  it('无待同步 → 返回 null,不调接口', async () => {
    const r = await syncPendingReadings()
    expect(r).toBeNull()
    expect(submitBatch).not.toHaveBeenCalled()
  })

  it('全部成功 → 上传后从队列清空,聚合计数', async () => {
    await enqueueReading(mk('u1', 1))
    await enqueueReading(mk('u2', 2))
    submitBatch.mockResolvedValueOnce(ok(2))

    const r = await syncPendingReadings()
    expect(r).toEqual({ successCount: 2, failCount: 0, errors: [] })
    expect(await getAllPending()).toEqual([])
    expect(submitBatch).toHaveBeenCalledTimes(1)
  })

  it('按 clientUuid/字段映射为 payload 提交', async () => {
    await enqueueReading({ ...mk('u1', 1), photoUrl: 'p', remark: 'r' })
    submitBatch.mockResolvedValueOnce(ok(1))

    await syncPendingReadings()
    const sent = submitBatch.mock.calls[0][0]
    expect(sent).toHaveLength(1)
    expect(sent[0]).toMatchObject({
      meterId: 1,
      periodId: 1,
      currReading: 10,
      clientUuid: 'u1',
      photoUrl: 'p',
      remark: 'r',
    })
  })

  it('后端逐条受理:整批移除,失败明细进 errors', async () => {
    await enqueueReading(mk('u1', 1))
    await enqueueReading(mk('u2', 2))
    submitBatch.mockResolvedValueOnce({ successCount: 1, failCount: 1, errors: ['u2 异常读数'] })

    const r = await syncPendingReadings()
    expect(r?.successCount).toBe(1)
    expect(r?.failCount).toBe(1)
    expect(r?.errors).toContain('u2 异常读数')
    // 逐条独立事务+幂等:整批已受理,队列清空,不重试
    expect(await getAllPending()).toEqual([])
  })

  it('超过 500 分批(501 条 → 2 批:500 + 1)', async () => {
    for (let i = 0; i < 501; i++) await enqueueReading(mk(`u${i}`, i))
    submitBatch.mockResolvedValueOnce(ok(500)).mockResolvedValueOnce(ok(1))

    const r = await syncPendingReadings()
    expect(submitBatch).toHaveBeenCalledTimes(2)
    expect(submitBatch.mock.calls[0][0]).toHaveLength(500)
    expect(submitBatch.mock.calls[1][0]).toHaveLength(1)
    expect(r?.successCount).toBe(501)
    expect(await getAllPending()).toEqual([])
  })

  it('网络中断:当前批保留、停止后续批、追加提示', async () => {
    for (let i = 0; i < 501; i++) await enqueueReading(mk(`u${i}`, i))
    // 第 1 批网络错误 → break,第 2 批不再发
    submitBatch.mockRejectedValueOnce(new ApiError(ErrorCode.NETWORK, '断网'))

    const r = await syncPendingReadings()
    expect(submitBatch).toHaveBeenCalledTimes(1)
    expect(r?.errors.some((e) => e.includes('网络中断'))).toBe(true)
    // 全部 501 条都保留(第 1 批未删,第 2 批未发)
    expect(await getAllPending()).toHaveLength(501)
  })

  it('非网络错误(如鉴权)向上抛,不吞', async () => {
    await enqueueReading(mk('u1', 1))
    submitBatch.mockRejectedValueOnce(new ApiError(ErrorCode.TOKEN_EXPIRED, '登录失效'))

    await expect(syncPendingReadings()).rejects.toMatchObject({ code: ErrorCode.TOKEN_EXPIRED })
    // 抛出前未删,数据保留
    expect(await getAllPending()).toHaveLength(1)
  })
})
