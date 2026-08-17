import { useState } from 'react'
import { useParams } from 'react-router-dom'
import { TopBar } from '@/components/layout/TopBar'
import { useDunning } from '@/hooks/useReports'
import { useCompanyMap } from '@/hooks/useCompanies'
import { LoadingState, EmptyState, ErrorState } from '@/components/ui/States'
import { errorMessage } from '@/utils/errorMessage'
import { companyName } from '@/utils/idToName'
import { fmtMoney, fmtUsage } from '@/utils/format'
import { exportApi } from '@/api/export'
import { triggerDownload } from '@/utils/download'
import { toast } from '@/components/ui/Toast'
import { METER_TYPE } from '@/config/dict'
import { Spinner } from '@/components/ui/Spinner'

export function DunningPage() {
  const { periodId } = useParams()
  const pid = Number(periodId)
  const q = useDunning(pid)
  const { map: companyMap } = useCompanyMap()
  const [exporting, setExporting] = useState<number | null>(null)

  const onExport = async (companyId: number) => {
    setExporting(companyId)
    try {
      const res = await exportApi.dunningByCompany(pid, companyId)
      triggerDownload(res)
      toast.success('已导出')
    } catch (e) {
      toast.error(errorMessage(e))
    } finally {
      setExporting(null)
    }
  }

  return (
    <div>
      <TopBar title="催缴清单" back />

      {q.isLoading ? (
        <LoadingState />
      ) : q.isError ? (
        <ErrorState message={errorMessage(q.error)} onRetry={() => q.refetch()} />
      ) : !q.data || q.data.rows.length === 0 ? (
        <EmptyState title="该周期暂无催缴数据" description="尚无已审核通过的读数" />
      ) : (
        <div className="p-3">
          {q.data.pendingCount > 0 && (
            <div className="mb-3 rounded-lg bg-amber-50 px-3 py-2 text-sm text-amber-800">
              仍有 {q.data.pendingCount} 条待审核读数,未计入催缴。
            </div>
          )}

          <ul className="space-y-2">
            {q.data.rows.map((r) => (
              <li key={r.companyId} className="rounded-xl bg-white p-4 shadow-sm">
                <div className="flex items-center justify-between">
                  <span className="font-medium text-gray-900">
                    {companyName(companyMap, r.companyId)}
                  </span>
                  <button
                    className="flex items-center gap-1 text-sm text-brand disabled:opacity-50"
                    disabled={exporting === r.companyId}
                    onClick={() => onExport(r.companyId)}
                  >
                    {exporting === r.companyId && <Spinner className="h-4 w-4" />}
                    导出
                  </button>
                </div>

                <div className="mt-2 grid grid-cols-2 gap-x-4 gap-y-1 text-sm">
                  <Line label="电量" value={fmtUsage(r.elecUsage, METER_TYPE.ELEC)} />
                  <Line label="电费" value={fmtMoney(r.elecFee)} />
                  <Line label="水量" value={fmtUsage(r.waterUsage, METER_TYPE.WATER)} />
                  <Line label="水费" value={fmtMoney(r.waterFee)} />
                </div>

                <div className="mt-2 flex items-center justify-between border-t border-gray-100 pt-2">
                  <span className="text-sm text-gray-500">合计</span>
                  <span className="text-base font-semibold text-gray-900">
                    {fmtMoney(r.totalFee, '¥0.00')}
                  </span>
                </div>
              </li>
            ))}
          </ul>
        </div>
      )}
    </div>
  )
}

function Line({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex justify-between">
      <span className="text-gray-500">{label}</span>
      <span className="text-gray-900">{value}</span>
    </div>
  )
}
