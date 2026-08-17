import { create } from 'zustand'
import { countPending } from '@/offline/db'

/**
 * 离线队列 UI 状态(前端 TRD §2/§7.1)。只保存待同步计数与同步中标记;
 * 实际数据在 IndexedDB。计数变更后调 refresh() 从 db 重新读。
 */
interface OfflineQueueState {
  pendingCount: number
  syncing: boolean
  refresh: () => Promise<void>
  setSyncing: (v: boolean) => void
}

export const useOfflineQueue = create<OfflineQueueState>((set) => ({
  pendingCount: 0,
  syncing: false,
  refresh: async () => {
    const n = await countPending()
    set({ pendingCount: n })
  },
  setSyncing: (v) => set({ syncing: v }),
}))
