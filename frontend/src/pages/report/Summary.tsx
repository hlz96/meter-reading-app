import { useMemo } from 'react'
import { useParams } from 'react-router-dom'
import { TopBar } from '@/components/layout/TopBar'
import { useSummary } from '@/hooks/useReports'
import { useCompanyMap } from '@/hooks/useCompanies'
import { METER_TYPE } from '@/config/dict'
import { LoadingState, EmptyState, ErrorState } from '@/components/ui/States'
import { errorMessage } from '@/utils/errorMessage'
import { companyName } from '@/utils/idToName'
import { fmtMoney, fmtUsage } from '@/utils/format'
import type { SummaryRow } from '@/types/dto'

interface CompanyGroup {
  companyId: number
  elec?: SummaryRow
  water?: SummaryRow
}

export function SummaryPage() {
  const { periodId } = useParams()
  const pid = Number(periodId)
  const q = useSummary(pid)
  const { map: companyMap } = useCompanyMap()

  // 每公司每类型一行 → 按公司归集为一卡片
  const groups = useMemo<CompanyGroup[]>(() => {
    const m = new Map<number, CompanyGroup>()
    q.data?.rows.forEach((r) => {
      const g = m.get(r.companyId) || { companyId: r.companyId }
      if (r.type === METER_TYPE.ELEC) g.elec = r
      else if (r.type === METER_TYPE.WATER) g.water = r
      m.set(r.companyId, g)
    })
    return [...m.values()]
  }, [q.data])

  return (
    <div>
      <TopBar title="用量汇总" back />

      {q.isLoading ? (
        <LoadingState />
      ) : q.isError ? (
        <ErrorState message={errorMessage(q.error)} onRetry={() => q.refetch()} />
      ) : !q.data || groups.length === 0 ? (
        <EmptyState title="该周期暂无汇总数据" description="尚无已审核通过的读数" />
      ) : (
        <div className="p-3">
          {q.data.pendingCount > 0 && (
            <div className="mb-3 rounded-lg bg-amber-50 px-3 py-2 text-sm text-amber-800">
              仍有 {q.data.pendingCount} 条待审核读数,未计入下方汇总。
            </div>
          )}

          <ul className="space-y-2">
            {groups.map((g) => (
              <li key={g.companyId} className="rounded-xl bg-white p-4 shadow-sm">
                <p className="mb-2 font-medium text-gray-900">
                  {companyName(companyMap, g.companyId)}
                </p>
                <div className="grid grid-cols-2 gap-3 text-sm">
                  <TypeCell label="电" tone="amber" row={g.elec} type={METER_TYPE.ELEC} />
                  <TypeCell label="水" tone="blue" row={g.water} type={METER_TYPE.WATER} />
                </div>
              </li>
            ))}
          </ul>
        </div>
      )}
    </div>
  )
}

function TypeCell({
  label,
  tone,
  row,
  type,
}: {
  label: string
  tone: 'amber' | 'blue'
  row: SummaryRow | undefined
  type: number
}) {
  const border = tone === 'amber' ? 'border-amber-200' : 'border-blue-200'
  return (
    <div className={`rounded-lg border ${border} p-2`}>
      <p className="text-xs text-gray-500">{label}</p>
      <p className="mt-0.5 font-medium text-gray-900">
        {row ? fmtUsage(row.usage, type) : '—'}
      </p>
      <p className="text-xs text-gray-500">{row ? fmtMoney(row.fee) : '—'}</p>
    </div>
  )
}
