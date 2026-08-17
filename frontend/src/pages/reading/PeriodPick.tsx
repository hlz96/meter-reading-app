import { useNavigate } from 'react-router-dom'
import { TopBar } from '@/components/layout/TopBar'
import { usePeriods } from '@/hooks/usePeriods'
import { PERIOD_STATUS } from '@/config/dict'
import { Badge } from '@/components/ui/Badge'
import { LoadingState, EmptyState, ErrorState } from '@/components/ui/States'
import { errorMessage } from '@/utils/errorMessage'
import { fmtDateRange } from '@/utils/format'

/** 抄表第一步:选周期(前端 TRD §6)。已结算周期不可录入,置灰。 */
export function PeriodPickPage() {
  const q = usePeriods()
  const navigate = useNavigate()

  return (
    <div>
      <TopBar title="选择周期" back />

      {q.isLoading ? (
        <LoadingState />
      ) : q.isError ? (
        <ErrorState message={errorMessage(q.error)} onRetry={() => q.refetch()} />
      ) : !q.data || q.data.length === 0 ? (
        <EmptyState title="暂无可抄表的周期" description="等待管理员创建周期" />
      ) : (
        <ul className="space-y-2 p-3">
          {q.data.map((p) => {
            const settled = p.status === PERIOD_STATUS.SETTLED
            return (
              <li key={p.id}>
                <button
                  disabled={settled}
                  onClick={() => navigate(`/reading/${p.id}/tasks`)}
                  className="flex w-full items-center justify-between rounded-xl bg-white p-4 text-left shadow-sm active:bg-gray-50 disabled:opacity-60"
                >
                  <div>
                    <p className="font-medium text-gray-900">{p.name}</p>
                    <p className="mt-1 text-sm text-gray-500">
                      {fmtDateRange(p.startDate, p.endDate)}
                    </p>
                  </div>
                  {settled ? (
                    <Badge tone="gray">已结算</Badge>
                  ) : (
                    <span className="text-gray-400">›</span>
                  )}
                </button>
              </li>
            )
          })}
        </ul>
      )}
    </div>
  )
}
