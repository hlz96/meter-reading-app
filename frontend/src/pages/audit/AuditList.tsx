import { useMemo, useState } from 'react'
import { useParams } from 'react-router-dom'
import { TopBar } from '@/components/layout/TopBar'
import { useReadings, useAuditReading, useAuditBatch } from '@/hooks/useReadings'
import { useMeters } from '@/hooks/useMeters'
import { AUDIT_STATUS, ABNORMAL_TYPE_LABEL } from '@/config/dict'
import { Button } from '@/components/ui/Button'
import { Badge } from '@/components/ui/Badge'
import { Modal } from '@/components/ui/Modal'
import { Input } from '@/components/ui/Input'
import { LoadingState, EmptyState, ErrorState } from '@/components/ui/States'
import { toast } from '@/components/ui/Toast'
import { errorMessage } from '@/utils/errorMessage'
import { fmtNum, fmtUsage } from '@/utils/format'
import type { Reading } from '@/types/dto'

/** 审核清单(ADMIN only,前端 TRD §6)。默认看待审核(auditStatus=1)。 */
export function AuditListPage() {
  const { periodId } = useParams()
  const pid = Number(periodId)
  const q = useReadings({ periodId: pid, auditStatus: AUDIT_STATUS.PENDING })
  const metersQ = useMeters({})

  const meterName = useMemo(() => {
    const m = new Map<number, string>()
    metersQ.data?.forEach((it) => m.set(it.id, it.name))
    return m
  }, [metersQ.data])

  const audit = useAuditReading(pid)
  const auditBatch = useAuditBatch(pid)

  const [selected, setSelected] = useState<Set<number>>(new Set())
  const [rejecting, setRejecting] = useState<Reading | null>(null)

  const items = q.data || []
  const allSelected = items.length > 0 && selected.size === items.length

  const toggle = (id: number) => {
    setSelected((prev) => {
      const next = new Set(prev)
      if (next.has(id)) next.delete(id)
      else next.add(id)
      return next
    })
  }
  const toggleAll = () => {
    setSelected(allSelected ? new Set() : new Set(items.map((r) => r.id)))
  }

  const approveOne = (r: Reading) =>
    audit.mutate(
      { id: r.id, approved: true },
      {
        onSuccess: () => toast.success('已通过'),
        onError: (e) => toast.error(errorMessage(e)),
      },
    )

  const batchApprove = () => {
    if (selected.size === 0) return
    auditBatch.mutate(
      { ids: [...selected], approved: true },
      {
        onSuccess: (res) => {
          toast.success(`已通过 ${res.processed} 条`)
          setSelected(new Set())
        },
        onError: (e) => toast.error(errorMessage(e)),
      },
    )
  }

  return (
    <div>
      <TopBar
        title="读数审核"
        back
        right={
          items.length > 0 ? (
            <button className="text-sm text-brand" onClick={toggleAll}>
              {allSelected ? '取消全选' : '全选'}
            </button>
          ) : undefined
        }
      />

      {q.isLoading ? (
        <LoadingState />
      ) : q.isError ? (
        <ErrorState message={errorMessage(q.error)} onRetry={() => q.refetch()} />
      ) : items.length === 0 ? (
        <EmptyState title="没有待审核的读数" description="所有读数均已处理" />
      ) : (
        <>
          <ul className="divide-y divide-gray-100 pb-20">
            {items.map((r) => (
              <li key={r.id} className="flex items-start gap-3 bg-white px-4 py-3">
                <input
                  type="checkbox"
                  className="mt-1 h-5 w-5"
                  checked={selected.has(r.id)}
                  onChange={() => toggle(r.id)}
                />
                <div className="min-w-0 flex-1">
                  <div className="flex items-center gap-2">
                    <span className="truncate font-medium text-gray-900">
                      {meterName.get(r.meterId) || `表#${r.meterId}`}
                    </span>
                    {r.isAbnormal && (
                      <Badge tone="red">
                        {r.abnormalType
                          ? ABNORMAL_TYPE_LABEL[r.abnormalType] || '异常'
                          : '异常'}
                      </Badge>
                    )}
                  </div>
                  <p className="text-sm text-gray-500">
                    上期 {fmtNum(r.prevReading)} → 本期 {fmtNum(r.currReading)} · 用量{' '}
                    {fmtUsage(r.usageAmount)}
                  </p>
                  <div className="mt-2 flex gap-2">
                    <Button size="sm" onClick={() => approveOne(r)} loading={audit.isPending}>
                      通过
                    </Button>
                    <Button size="sm" variant="danger" onClick={() => setRejecting(r)}>
                      驳回
                    </Button>
                  </div>
                </div>
              </li>
            ))}
          </ul>

          {/* 批量操作条 */}
          {selected.size > 0 && (
            <div className="fixed inset-x-0 bottom-16 z-30 mx-auto flex max-w-md items-center justify-between gap-2 border-t border-gray-200 bg-white px-4 py-3">
              <span className="text-sm text-gray-600">已选 {selected.size} 条</span>
              <Button loading={auditBatch.isPending} onClick={batchApprove}>
                批量通过
              </Button>
            </div>
          )}
        </>
      )}

      {rejecting && (
        <RejectModal
          reading={rejecting}
          meterName={meterName.get(rejecting.meterId) || `表#${rejecting.meterId}`}
          onClose={() => setRejecting(null)}
          onReject={(remark) =>
            audit.mutate(
              { id: rejecting.id, approved: false, remark },
              {
                onSuccess: () => {
                  toast.success('已驳回')
                  setRejecting(null)
                },
                onError: (e) => toast.error(errorMessage(e)),
              },
            )
          }
          pending={audit.isPending}
        />
      )}
    </div>
  )
}

function RejectModal({
  reading,
  meterName,
  onClose,
  onReject,
  pending,
}: {
  reading: Reading
  meterName: string
  onClose: () => void
  onReject: (remark: string) => void
  pending: boolean
}) {
  const [remark, setRemark] = useState('')
  return (
    <Modal
      open
      title="驳回读数"
      onClose={onClose}
      footer={
        <>
          <Button variant="secondary" block onClick={onClose}>
            取消
          </Button>
          <Button variant="danger" block loading={pending} onClick={() => onReject(remark)}>
            确认驳回
          </Button>
        </>
      }
    >
      <p className="mb-3 text-sm text-gray-600">
        驳回「{meterName}」的读数(本期 {fmtNum(reading.currReading)})。抄表员需重新录入。
      </p>
      <Input
        label="驳回原因(可选)"
        placeholder="如:读数明显偏大,请复核"
        value={remark}
        onChange={(e) => setRemark(e.target.value)}
      />
    </Modal>
  )
}
