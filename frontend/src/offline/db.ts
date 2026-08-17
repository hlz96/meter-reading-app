import { openDB, type DBSchema, type IDBPDatabase } from 'idb'
import type { ReadingPayload } from '@/types/dto'

/**
 * 离线读数队列(前端 TRD §7.1)。仅存网络错误时未同步的读数;
 * clientUuid 为主键,后端 UNIQUE(org_id, client_uuid) 兜底幂等。
 */
export interface PendingReading extends ReadingPayload {
  clientUuid: string // 队列主键(必填)
  meterName?: string // 冗余,便于「待同步」列表展示
  periodName?: string
  queuedAt: number // 入队时间戳(排序用)
}

interface MeterDB extends DBSchema {
  pendingReadings: {
    key: string // clientUuid
    value: PendingReading
    indexes: { 'by-period': number }
  }
}

const DB_NAME = 'meter-reading'
const DB_VERSION = 1

let dbPromise: Promise<IDBPDatabase<MeterDB>> | null = null

function db(): Promise<IDBPDatabase<MeterDB>> {
  if (!dbPromise) {
    dbPromise = openDB<MeterDB>(DB_NAME, DB_VERSION, {
      upgrade(database) {
        const store = database.createObjectStore('pendingReadings', {
          keyPath: 'clientUuid',
        })
        store.createIndex('by-period', 'periodId')
      },
    })
  }
  return dbPromise
}

export async function enqueueReading(item: PendingReading): Promise<void> {
  const database = await db()
  await database.put('pendingReadings', item)
}

export async function getAllPending(): Promise<PendingReading[]> {
  const database = await db()
  const all = await database.getAll('pendingReadings')
  return all.sort((a, b) => a.queuedAt - b.queuedAt)
}

export async function countPending(): Promise<number> {
  const database = await db()
  return database.count('pendingReadings')
}

export async function removePending(clientUuids: string[]): Promise<void> {
  const database = await db()
  const tx = database.transaction('pendingReadings', 'readwrite')
  await Promise.all(clientUuids.map((id) => tx.store.delete(id)))
  await tx.done
}

export async function clearPending(): Promise<void> {
  const database = await db()
  await database.clear('pendingReadings')
}
