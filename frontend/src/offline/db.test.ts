import { beforeEach, describe, it, expect } from 'vitest'
import {
  enqueueReading,
  getAllPending,
  countPending,
  removePending,
  clearPending,
  type PendingReading,
} from '@/offline/db'

// fake-indexeddb 由 setup.ts 的 'fake-indexeddb/auto' 提供。
// db.ts 的 dbPromise 是模块级单例:数据跨用例残留,故每例先 clearPending。

const mk = (uuid: string, queuedAt: number, periodId = 1): PendingReading => ({
  clientUuid: uuid,
  meterId: 100,
  periodId,
  currReading: 12.5,
  queuedAt,
})

beforeEach(async () => {
  await clearPending()
})

describe('offline db', () => {
  it('空库计数为 0', async () => {
    expect(await countPending()).toBe(0)
    expect(await getAllPending()).toEqual([])
  })

  it('enqueue 后可查询并计数', async () => {
    await enqueueReading(mk('u1', 1000))
    expect(await countPending()).toBe(1)
    const all = await getAllPending()
    expect(all).toHaveLength(1)
    expect(all[0].clientUuid).toBe('u1')
  })

  it('相同 clientUuid put 覆盖(幂等,不重复)', async () => {
    await enqueueReading(mk('u1', 1000))
    await enqueueReading({ ...mk('u1', 1000), currReading: 99 })
    expect(await countPending()).toBe(1)
    expect((await getAllPending())[0].currReading).toBe(99)
  })

  it('getAllPending 按 queuedAt 升序', async () => {
    await enqueueReading(mk('late', 3000))
    await enqueueReading(mk('early', 1000))
    await enqueueReading(mk('mid', 2000))
    expect((await getAllPending()).map((p) => p.clientUuid)).toEqual(['early', 'mid', 'late'])
  })

  it('removePending 批量删除指定 uuid', async () => {
    await enqueueReading(mk('u1', 1000))
    await enqueueReading(mk('u2', 2000))
    await enqueueReading(mk('u3', 3000))
    await removePending(['u1', 'u3'])
    expect((await getAllPending()).map((p) => p.clientUuid)).toEqual(['u2'])
  })

  it('removePending 删不存在的 uuid 不报错', async () => {
    await enqueueReading(mk('u1', 1000))
    await removePending(['nope'])
    expect(await countPending()).toBe(1)
  })

  it('clearPending 清空', async () => {
    await enqueueReading(mk('u1', 1000))
    await enqueueReading(mk('u2', 2000))
    await clearPending()
    expect(await countPending()).toBe(0)
  })
})
