import { useParams, useNavigate } from 'react-router-dom'
import { TopBar } from '@/components/layout/TopBar'
import { useTasks } from '@/hooks/usePeriods'
import { useCompanyMap } from '@/hooks/useCompanies'
import { METER_TYPE, METER_TYPE_LABEL } from '@/config/dict'
import { Badge, AuditBadge } from '@/components/ui/Badge'
import { LoadingState, EmptyState, ErrorState } from '@/components/ui/States'
import { errorMessage } from '@/utils/errorMessage'
import { companyName } from '@/utils/idToName'
import { fmtUsage } from '@/utils/format'
import { SyncBanner } from './SyncBanner'
import type { TaskItem } from '@/types/dto'

export function TaskListPage() {
  const { periodId } = useParams()
  const pid = Number(periodId)
  const q = useTasks(pid)
  const { map: companyMap } = useCompanyMap()
  const navigate = useNavigate()

  return (
    <div>
      <TopBar
        title="待抄清单"
        back
        right={
          q.data ? (
            <span className="text-sm text-gray-500">
              {q.data.doneCount}/{q.data.total}
            </span>
          ) : undefined
        }
      />

      <SyncBanner periodId={pid} />

      {q.isLoading ? (
        <LoadingState />
      ) : q.isError ? (
        <ErrorState message={errorMessage(q.error)} onRetry={() => q.refetch()} />
      ) : !q.data || q.data.items.length === 0 ? (
        <EmptyState title="该周期暂无需抄的表计" description="请检查表计台账是否已启用" />
      ) : (
        <ul className="divide-y divide-gray-100">
          {q.data.items.map((it) => (
            <li key={it.meterId}>
              <button
                onClick={() => navigate(`/reading/${pid}/meter/${it.meterId}`)}
                className="flex w-full items-center gap-3 bg-white px-4 py-3 text-left active:bg-gray-50"
              >
                <div className="min-w-0 flex-1">
                  <div className="flex items-center gap-2">
                    <span className="truncate font-medium text-gray-900">{it.meterName}</span>
                    <Badge tone={it.type === METER_TYPE.ELEC ? 'amber' : 'blue'}>
                      {METER_TYPE_LABEL[it.type]}
                    </Badge>
                  </div>
                  <p className="truncate text-sm text-gray-500">
                    {companyName(companyMap, it.companyId)}
                    {it.done && it.usageAmount != null
                      ? ` · 用量 ${fmtUsage(it.usageAmount, it.type)}`
                      : ''}
                  </p>
                </div>
                <div className="flex shrink-0 flex-col items-end gap-1">
                  {it.done ? (
                    <>
                      <TaskDoneMark item={it} />
                      <AuditBadge status={it.auditStatus} />
                    </>
                  ) : (
                    <Badge tone="gray">待抄</Badge>
                  )}
                </div>
              </button>
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}

function TaskDoneMark({ item }: { item: TaskItem }) {
  return (
    <span className="text-sm text-gray-700">
      本期 {item.currReading != null ? Number(item.currReading) : '—'}
    </span>
  )
}
