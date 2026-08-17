import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { TopBar } from '@/components/layout/TopBar'
import {
  usePeriods,
  useCreatePeriod,
  useUpdatePeriod,
  useSettlePeriod,
} from '@/hooks/usePeriods'
import { useAuthStore } from '@/store/auth'
import { ROLE, PERIOD_STATUS } from '@/config/dict'
import { Button } from '@/components/ui/Button'
import { Input } from '@/components/ui/Input'
import { Modal } from '@/components/ui/Modal'
import { Badge } from '@/components/ui/Badge'
import { LoadingState, EmptyState, ErrorState } from '@/components/ui/States'
import { toast } from '@/components/ui/Toast'
import { errorMessage } from '@/utils/errorMessage'
import { ApiError, ErrorCode } from '@/types/error'
import { fmtDateRange, fmtMoney } from '@/utils/format'
import type { Period, PeriodPayload } from '@/types/dto'

export function PeriodListPage() {
  const role = useAuthStore((s) => s.role)
  const isAdmin = role === ROLE.ADMIN
  const canReport = role === ROLE.ADMIN || role === ROLE.VIEWER
  const q = usePeriods()
  const navigate = useNavigate()

  const [editing, setEditing] = useState<Period | null>(null)
  const [creating, setCreating] = useState(false)

  return (
    <div>
      <TopBar
        title="周期管理"
        back
        right={
          isAdmin ? (
            <button className="text-sm text-brand" onClick={() => setCreating(true)}>
              新增
            </button>
          ) : undefined
        }
      />

      {q.isLoading ? (
        <LoadingState />
      ) : q.isError ? (
        <ErrorState message={errorMessage(q.error)} onRetry={() => q.refetch()} />
      ) : !q.data || q.data.length === 0 ? (
        <EmptyState
          title="还没有抄表周期"
          description={isAdmin ? '新增一个周期开始抄表' : '等待管理员创建周期'}
          action={isAdmin ? <Button onClick={() => setCreating(true)}>新增周期</Button> : undefined}
        />
      ) : (
        <ul className="space-y-2 p-3">
          {q.data.map((p) => {
            const settled = p.status === PERIOD_STATUS.SETTLED
            return (
              <li key={p.id} className="rounded-xl bg-white p-4 shadow-sm">
                <div className="flex items-center justify-between">
                  <span className="font-medium text-gray-900">{p.name}</span>
                  <Badge tone={settled ? 'gray' : 'green'}>
                    {settled ? '已结算' : '进行中'}
                  </Badge>
                </div>
                <p className="mt-1 text-sm text-gray-500">
                  {fmtDateRange(p.startDate, p.endDate)}
                </p>
                <p className="mt-1 text-sm text-gray-500">
                  电价 {fmtMoney(p.elecPrice)} · 水价 {fmtMoney(p.waterPrice)}
                </p>
                <div className="mt-3 flex flex-wrap gap-2">
                  {canReport && (
                    <Button
                      size="sm"
                      variant="secondary"
                      onClick={() => navigate(`/report/summary/${p.id}`)}
                    >
                      汇总
                    </Button>
                  )}
                  {isAdmin && (
                    <>
                      <Button
                        size="sm"
                        variant="secondary"
                        onClick={() => navigate(`/audit/${p.id}`)}
                      >
                        审核
                      </Button>
                      {!settled && (
                        <>
                          <Button size="sm" variant="ghost" onClick={() => setEditing(p)}>
                            编辑
                          </Button>
                          <SettleButton period={p} />
                        </>
                      )}
                    </>
                  )}
                </div>
              </li>
            )
          })}
        </ul>
      )}

      {(creating || editing) && (
        <PeriodFormModal
          period={editing}
          onClose={() => {
            setCreating(false)
            setEditing(null)
          }}
        />
      )}
    </div>
  )
}

function SettleButton({ period }: { period: Period }) {
  const settle = useSettlePeriod()
  const [confirming, setConfirming] = useState(false)
  return (
    <>
      <Button size="sm" onClick={() => setConfirming(true)}>
        结算
      </Button>
      <Modal
        open={confirming}
        title="结算周期"
        onClose={() => setConfirming(false)}
        footer={
          <>
            <Button variant="secondary" block onClick={() => setConfirming(false)}>
              取消
            </Button>
            <Button
              block
              loading={settle.isPending}
              onClick={() =>
                settle.mutate(period.id, {
                  onSuccess: () => {
                    toast.success('已结算')
                    setConfirming(false)
                  },
                  onError: (e) => {
                    // 4003:有未审 / 缺费率
                    if (e instanceof ApiError && e.code === ErrorCode.PERIOD_NOT_SETTLEABLE) {
                      toast.error(e.message)
                    } else {
                      toast.error(errorMessage(e))
                    }
                    setConfirming(false)
                  },
                })
              }
            >
              确认结算
            </Button>
          </>
        }
      >
        <p className="text-sm text-gray-600">
          结算后「{period.name}」将不可再录入或修改读数。请确认所有读数均已审核、费率已填写。
        </p>
      </Modal>
    </>
  )
}

function PeriodFormModal({ period, onClose }: { period: Period | null; onClose: () => void }) {
  const isEdit = !!period
  const create = useCreatePeriod()
  const update = useUpdatePeriod()
  const [form, setForm] = useState({
    name: period?.name ?? '',
    startDate: period?.startDate ?? '',
    endDate: period?.endDate ?? '',
    elecPrice: period?.elecPrice != null ? String(period.elecPrice) : '',
    waterPrice: period?.waterPrice != null ? String(period.waterPrice) : '',
  })
  const [errors, setErrors] = useState<Record<string, string>>({})
  const pending = create.isPending || update.isPending

  const build = (): PeriodPayload | null => {
    const next: Record<string, string> = {}
    if (!form.name.trim()) next.name = '请输入周期名称'
    const elec = form.elecPrice === '' ? null : Number(form.elecPrice)
    const water = form.waterPrice === '' ? null : Number(form.waterPrice)
    if (elec != null && (Number.isNaN(elec) || elec < 0)) next.elecPrice = '电价需 ≥ 0'
    if (water != null && (Number.isNaN(water) || water < 0)) next.waterPrice = '水价需 ≥ 0'
    if (form.startDate && form.endDate && form.startDate > form.endDate)
      next.endDate = '结束日期不能早于开始日期'
    setErrors(next)
    if (Object.keys(next).length > 0) return null
    return {
      name: form.name.trim(),
      startDate: form.startDate || null,
      endDate: form.endDate || null,
      elecPrice: elec,
      waterPrice: water,
    }
  }

  const submit = () => {
    const payload = build()
    if (!payload) return
    const onErr = (e: unknown) => {
      if (e instanceof ApiError && e.code === ErrorCode.CONFLICT) {
        setErrors((p) => ({ ...p, name: e.message || '周期名已存在' }))
      } else {
        toast.error(errorMessage(e))
      }
    }
    if (isEdit) {
      update.mutate(
        { id: period!.id, payload },
        {
          onSuccess: () => {
            toast.success('已保存')
            onClose()
          },
          onError: onErr,
        },
      )
    } else {
      create.mutate(payload, {
        onSuccess: () => {
          toast.success('已新增')
          onClose()
        },
        onError: onErr,
      })
    }
  }

  return (
    <Modal
      open
      title={isEdit ? '编辑周期' : '新增周期'}
      onClose={onClose}
      footer={
        <>
          <Button variant="secondary" block onClick={onClose}>
            取消
          </Button>
          <Button block loading={pending} onClick={submit}>
            保存
          </Button>
        </>
      }
    >
      <div className="space-y-3">
        <Input
          label="周期名称"
          placeholder="如:2026年8月"
          value={form.name}
          error={errors.name}
          onChange={(e) => setForm({ ...form, name: e.target.value })}
        />
        <div className="grid grid-cols-2 gap-3">
          <Input
            label="开始日期"
            type="date"
            value={form.startDate}
            onChange={(e) => setForm({ ...form, startDate: e.target.value })}
          />
          <Input
            label="结束日期"
            type="date"
            value={form.endDate}
            error={errors.endDate}
            onChange={(e) => setForm({ ...form, endDate: e.target.value })}
          />
        </div>
        <div className="grid grid-cols-2 gap-3">
          <Input
            label="电价(元/度)"
            type="number"
            inputMode="decimal"
            placeholder="未定价可留空"
            value={form.elecPrice}
            error={errors.elecPrice}
            onChange={(e) => setForm({ ...form, elecPrice: e.target.value })}
          />
          <Input
            label="水价(元/吨)"
            type="number"
            inputMode="decimal"
            placeholder="未定价可留空"
            value={form.waterPrice}
            error={errors.waterPrice}
            onChange={(e) => setForm({ ...form, waterPrice: e.target.value })}
          />
        </div>
      </div>
    </Modal>
  )
}
