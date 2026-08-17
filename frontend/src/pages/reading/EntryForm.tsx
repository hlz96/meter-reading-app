import { useMemo, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { TopBar } from '@/components/layout/TopBar'
import { useMeters } from '@/hooks/useMeters'
import { useReadings, useSubmitReading } from '@/hooks/useReadings'
import { METER_TYPE, METER_TYPE_LABEL, ABNORMAL_TYPE_LABEL } from '@/config/dict'
import { Button } from '@/components/ui/Button'
import { Input } from '@/components/ui/Input'
import { Badge, AuditBadge } from '@/components/ui/Badge'
import { LoadingState, ErrorState } from '@/components/ui/States'
import { toast } from '@/components/ui/Toast'
import { errorMessage } from '@/utils/errorMessage'
import { ApiError, ErrorCode } from '@/types/error'
import { fmtNum, fmtUsage } from '@/utils/format'
import { enqueueReading } from '@/offline/db'
import { useOfflineQueue } from '@/store/offlineQueue'
import type { Reading, ReadingPayload } from '@/types/dto'

/**
 * 抄表录入(前端 TRD §6 核心)。在线优先 POST /readings(submit 端点按表+周期 upsert,
 * 覆盖即重新审核);仅网络错误入 IndexedDB 队列。倒退实时提示 + 用量预览。
 */
export function EntryFormPage() {
  const { periodId, meterId } = useParams()
  const pid = Number(periodId)
  const mid = Number(meterId)
  const navigate = useNavigate()

  const metersQ = useMeters({})
  const meter = metersQ.data?.find((m) => m.id === mid)

  // 读取本表在本周期的既有读数(拿到权威 prevReading + 是否已抄)
  const readingsQ = useReadings(
    { periodId: pid, companyId: meter?.companyId },
    !!meter,
  )
  const existing: Reading | undefined = readingsQ.data?.find((r) => r.meterId === mid)

  const [curr, setCurr] = useState('')
  const [remark, setRemark] = useState('')
  const [result, setResult] = useState<Reading | null>(null)

  const submit = useSubmitReading()
  const refreshQueue = useOfflineQueue((s) => s.refresh)

  // 参考上期读数:既有记录的 prevReading > 表计初始读数(与后端兜底一致)
  const prevRef = existing?.prevReading ?? meter?.initialReading ?? 0
  const ratio = meter?.ratio ?? 1

  const currNum = curr === '' ? null : Number(curr)
  const invalid = currNum !== null && (Number.isNaN(currNum) || currNum < 0)
  const backward = currNum !== null && !invalid && currNum < prevRef
  const usagePreview = useMemo(() => {
    if (currNum === null || invalid) return null
    if (backward) return 0
    return (currNum - prevRef) * ratio
  }, [currNum, invalid, backward, prevRef, ratio])

  if (metersQ.isLoading || readingsQ.isLoading) return <LoadingState />
  if (metersQ.isError)
    return <ErrorState message={errorMessage(metersQ.error)} onRetry={() => metersQ.refetch()} />
  if (!meter)
    return <ErrorState message="表计不存在或不在可抄范围" />

  const doSubmit = async () => {
    if (currNum === null || invalid) {
      toast.error('请输入有效的本期读数(≥0)')
      return
    }
    const payload: ReadingPayload = {
      meterId: mid,
      periodId: pid,
      currReading: currNum,
      clientUuid: crypto.randomUUID(),
      remark: remark.trim() || undefined,
    }

    submit.mutate(payload, {
      onSuccess: (data) => {
        setResult(data)
        if (data.isAbnormal) {
          toast.info('已保存,但读数被标记为异常,请留意')
        } else {
          toast.success('已提交')
        }
      },
      onError: async (e) => {
        // 仅网络错误入离线队列;业务错误直接提示
        if (e instanceof ApiError && e.isNetwork) {
          await enqueueReading({
            ...payload,
            clientUuid: payload.clientUuid!,
            meterName: meter.name,
            queuedAt: Date.now(),
          })
          await refreshQueue()
          toast.info('网络不可用,已离线保存,待同步')
          navigate(`/reading/${pid}/tasks`)
          return
        }
        if (e instanceof ApiError && e.code === ErrorCode.CONFLICT) {
          toast.error(e.message || '该周期已结算,不能录入')
          return
        }
        if (e instanceof ApiError && e.isForbidden) {
          toast.error('无权为该公司抄表')
          return
        }
        toast.error(errorMessage(e))
      },
    })
  }

  return (
    <div>
      <TopBar title="抄表录入" back />

      <div className="space-y-4 p-4">
        {/* 表计信息 */}
        <div className="rounded-xl bg-white p-4 shadow-sm">
          <div className="flex items-center gap-2">
            <span className="font-medium text-gray-900">{meter.name}</span>
            <Badge tone={meter.type === METER_TYPE.ELEC ? 'amber' : 'blue'}>
              {METER_TYPE_LABEL[meter.type]}
            </Badge>
            {existing && <AuditBadge status={existing.auditStatus} />}
          </div>
          <p className="mt-2 text-sm text-gray-500">
            上期读数 <span className="font-medium text-gray-700">{fmtNum(prevRef)}</span> · 倍率{' '}
            {fmtNum(ratio)}
            {meter.location ? ` · ${meter.location}` : ''}
          </p>
          {existing && (
            <p className="mt-1 text-sm text-gray-500">
              本期已抄 {fmtNum(existing.currReading)},重新提交将覆盖并重新审核。
            </p>
          )}
        </div>

        {/* 录入 */}
        <div className="rounded-xl bg-white p-4 shadow-sm">
          <Input
            label="本期读数"
            type="number"
            inputMode="decimal"
            autoFocus
            placeholder="请输入当前表盘读数"
            value={curr}
            error={invalid ? '读数需为 ≥ 0 的数字' : undefined}
            onChange={(e) => setCurr(e.target.value)}
          />

          {/* 实时提示 */}
          {backward && (
            <div className="mt-2 rounded-lg bg-red-50 px-3 py-2 text-sm text-red-700">
              本期读数低于上期({fmtNum(prevRef)}),疑似读数倒退,用量将按 0 计。请核对。
            </div>
          )}
          {usagePreview !== null && !backward && (
            <p className="mt-2 text-sm text-gray-600">
              预计用量 <span className="font-medium">{fmtUsage(usagePreview, meter.type)}</span>
            </p>
          )}

          <div className="mt-3">
            <Input
              label="备注(可选)"
              value={remark}
              onChange={(e) => setRemark(e.target.value)}
            />
          </div>

          <Button
            block
            className="mt-4"
            loading={submit.isPending}
            disabled={currNum === null || invalid}
            onClick={doSubmit}
          >
            提交读数
          </Button>
        </div>

        {/* 提交结果 */}
        {result && (
          <div className="rounded-xl bg-white p-4 shadow-sm">
            <div className="flex items-center justify-between">
              <span className="text-sm text-gray-500">提交结果</span>
              {result.isAbnormal ? (
                <Badge tone="red">
                  {result.abnormalType
                    ? ABNORMAL_TYPE_LABEL[result.abnormalType] || '异常'
                    : '异常'}
                </Badge>
              ) : (
                <Badge tone="green">正常</Badge>
              )}
            </div>
            <p className="mt-2 text-sm text-gray-700">
              用量 {fmtUsage(result.usageAmount, meter.type)}(上期 {fmtNum(result.prevReading)} →
              本期 {fmtNum(result.currReading)})
            </p>
            <div className="mt-3 flex gap-2">
              <Button
                variant="secondary"
                block
                onClick={() => navigate(`/reading/${pid}/tasks`)}
              >
                返回清单
              </Button>
            </div>
          </div>
        )}
      </div>
    </div>
  )
}
