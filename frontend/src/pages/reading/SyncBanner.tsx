import { useQueryClient } from '@tanstack/react-query'
import { useOfflineQueue } from '@/store/offlineQueue'
import { syncPendingReadings } from '@/offline/sync'
import { qk } from '@/hooks/keys'
import { toast } from '@/components/ui/Toast'
import { Spinner } from '@/components/ui/Spinner'

/** 「待同步 N 条」横幅 + 手动立即同步(前端 TRD §7.1)。无待同步时不渲染。 */
export function SyncBanner({ periodId }: { periodId: number }) {
  const pendingCount = useOfflineQueue((s) => s.pendingCount)
  const syncing = useOfflineQueue((s) => s.syncing)
  const qc = useQueryClient()

  if (pendingCount === 0) return null

  const onSync = async () => {
    try {
      const res = await syncPendingReadings()
      if (res) {
        if (res.failCount > 0) {
          toast.error(`同步完成:成功 ${res.successCount} 条,失败 ${res.failCount} 条`)
        } else {
          toast.success(`已同步 ${res.successCount} 条`)
        }
        qc.invalidateQueries({ queryKey: qk.tasks(periodId) })
        qc.invalidateQueries({ queryKey: ['readings'] })
      }
    } catch {
      toast.error('同步失败,请检查网络后重试')
    }
  }

  return (
    <div className="flex items-center justify-between gap-2 bg-amber-50 px-4 py-2 text-sm text-amber-800">
      <span className="flex items-center gap-2">
        {syncing && <Spinner className="h-4 w-4" />}
        待同步 {pendingCount} 条读数
      </span>
      <button
        className="rounded-md bg-amber-600 px-3 py-1 text-white disabled:opacity-50"
        disabled={syncing}
        onClick={onSync}
      >
        立即同步
      </button>
    </div>
  )
}
