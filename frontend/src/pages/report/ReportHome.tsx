import { useNavigate } from 'react-router-dom'
import { TopBar } from '@/components/layout/TopBar'
import { usePeriods } from '@/hooks/usePeriods'
import { PERIOD_STATUS } from '@/config/dict'
import { Badge } from '@/components/ui/Badge'
import { Button } from '@/components/ui/Button'
import { LoadingState, EmptyState, ErrorState } from '@/components/ui/States'
import { errorMessage } from '@/utils/errorMessage'
import { fmtDateRange } from '@/utils/format'

/** 报表入口:选周期看汇总/催单(ADMIN·VIEWER)。 */
export function ReportHomePage() {
  const q = usePeriods()
  const navigate = useNavigate()

  return (
    <div>
      <TopBar title="报表" back />

      {q.isLoading ? (
        <LoadingState />
      ) : q.isError ? (
        <ErrorState message={errorMessage(q.error)} onRetry={() => q.refetch()} />
      ) : !q.data || q.data.length === 0 ? (
        <EmptyState title="暂无周期数据" />
      ) : (
        <ul className="space-y-2 p-3">
          {q.data.map((p) => (
            <li key={p.id} className="rounded-xl bg-white p-4 shadow-sm">
              <div className="flex items-center justify-between">
                <span className="font-medium text-gray-900">{p.name}</span>
                <Badge tone={p.status === PERIOD_STATUS.SETTLED ? 'gray' : 'green'}>
                  {p.status === PERIOD_STATUS.SETTLED ? '已结算' : '进行中'}
                </Badge>
              </div>
              <p className="mt-1 text-sm text-gray-500">
                {fmtDateRange(p.startDate, p.endDate)}
              </p>
              <div className="mt-3 flex gap-2">
                <Button
                  size="sm"
                  variant="secondary"
                  onClick={() => navigate(`/report/summary/${p.id}`)}
                >
                  用量汇总
                </Button>
                <Button
                  size="sm"
                  variant="secondary"
                  onClick={() => navigate(`/report/dunning/${p.id}`)}
                >
                  催缴清单
                </Button>
              </div>
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}
