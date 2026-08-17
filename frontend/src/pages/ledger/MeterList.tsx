import { useState } from 'react'
import { TopBar } from '@/components/layout/TopBar'
import { useMeters, useCreateMeter, useUpdateMeter, useDeleteMeter } from '@/hooks/useMeters'
import { useCompanyMap } from '@/hooks/useCompanies'
import { useAuthStore } from '@/store/auth'
import {
  ROLE,
  METER_TYPE,
  METER_TYPE_LABEL,
  METER_STATUS,
  METER_STATUS_LABEL,
} from '@/config/dict'
import { Button } from '@/components/ui/Button'
import { Input, Select } from '@/components/ui/Input'
import { Modal } from '@/components/ui/Modal'
import { Badge } from '@/components/ui/Badge'
import { LoadingState, EmptyState, ErrorState } from '@/components/ui/States'
import { toast } from '@/components/ui/Toast'
import { errorMessage } from '@/utils/errorMessage'
import { ApiError, ErrorCode } from '@/types/error'
import { companyName } from '@/utils/idToName'
import { fmtNum } from '@/utils/format'
import type { Meter, MeterFilter, MeterPayload } from '@/types/dto'

export function MeterListPage() {
  const role = useAuthStore((s) => s.role)
  const isAdmin = role === ROLE.ADMIN
  const [filter, setFilter] = useState<MeterFilter>({})
  const q = useMeters(filter)
  const { map: companyMap, data: companies } = useCompanyMap()

  const [editing, setEditing] = useState<Meter | null>(null)
  const [creating, setCreating] = useState(false)

  const companyOptions = (companies || []).map((c) => ({ value: c.id, label: c.name }))

  return (
    <div>
      <TopBar
        title="表计台账"
        back
        right={
          isAdmin ? (
            <button
              className="text-sm text-brand"
              onClick={() => setCreating(true)}
              disabled={!companies || companies.length === 0}
            >
              新增
            </button>
          ) : undefined
        }
      />

      {/* 三筛选 */}
      <div className="grid grid-cols-3 gap-2 bg-white p-3">
        <Select
          placeholder="全部公司"
          options={companyOptions}
          value={filter.companyId ?? ''}
          onChange={(e) =>
            setFilter({ ...filter, companyId: e.target.value ? Number(e.target.value) : undefined })
          }
        />
        <Select
          placeholder="全部类型"
          options={[
            { value: METER_TYPE.ELEC, label: '电表' },
            { value: METER_TYPE.WATER, label: '水表' },
          ]}
          value={filter.type ?? ''}
          onChange={(e) =>
            setFilter({ ...filter, type: e.target.value ? Number(e.target.value) : undefined })
          }
        />
        <Select
          placeholder="全部状态"
          options={[
            { value: METER_STATUS.ENABLED, label: '启用' },
            { value: METER_STATUS.DISABLED, label: '停用' },
          ]}
          value={filter.status ?? ''}
          onChange={(e) =>
            setFilter({ ...filter, status: e.target.value ? Number(e.target.value) : undefined })
          }
        />
      </div>

      {q.isLoading ? (
        <LoadingState />
      ) : q.isError ? (
        <ErrorState message={errorMessage(q.error)} onRetry={() => q.refetch()} />
      ) : !q.data || q.data.length === 0 ? (
        <EmptyState
          title="没有符合条件的表计"
          action={
            isAdmin && companies && companies.length > 0 ? (
              <Button onClick={() => setCreating(true)}>新增表计</Button>
            ) : !companies || companies.length === 0 ? (
              <span className="text-sm text-gray-500">请先创建公司</span>
            ) : undefined
          }
        />
      ) : (
        <ul className="divide-y divide-gray-100">
          {q.data.map((m) => (
            <li key={m.id} className="flex items-center gap-3 bg-white px-4 py-3">
              <div className="min-w-0 flex-1">
                <div className="flex items-center gap-2">
                  <span className="truncate font-medium text-gray-900">{m.name}</span>
                  <Badge tone={m.type === METER_TYPE.ELEC ? 'amber' : 'blue'}>
                    {METER_TYPE_LABEL[m.type]}
                  </Badge>
                  {m.status === METER_STATUS.DISABLED && <Badge tone="gray">停用</Badge>}
                </div>
                <p className="truncate text-sm text-gray-500">
                  {companyName(companyMap, m.companyId)} · 倍率 {fmtNum(m.ratio)} ·{' '}
                  {m.location || '无位置'}
                </p>
              </div>
              {isAdmin && (
                <div className="flex shrink-0 gap-3">
                  <button className="text-sm text-brand" onClick={() => setEditing(m)}>
                    编辑
                  </button>
                  <DeleteMeterButton meter={m} />
                </div>
              )}
            </li>
          ))}
        </ul>
      )}

      {(creating || editing) && (
        <MeterFormModal
          meter={editing}
          companyOptions={companyOptions}
          defaultCompanyId={filter.companyId}
          onClose={() => {
            setCreating(false)
            setEditing(null)
          }}
        />
      )}
    </div>
  )
}

function DeleteMeterButton({ meter }: { meter: Meter }) {
  const del = useDeleteMeter()
  const [confirming, setConfirming] = useState(false)
  return (
    <>
      <button className="text-sm text-red-600" onClick={() => setConfirming(true)}>
        删除
      </button>
      <Modal
        open={confirming}
        title="删除表计"
        onClose={() => setConfirming(false)}
        footer={
          <>
            <Button variant="secondary" block onClick={() => setConfirming(false)}>
              取消
            </Button>
            <Button
              variant="danger"
              block
              loading={del.isPending}
              onClick={() =>
                del.mutate(meter.id, {
                  onSuccess: () => {
                    toast.success('已删除')
                    setConfirming(false)
                  },
                  onError: (e) => {
                    toast.error(errorMessage(e))
                    setConfirming(false)
                  },
                })
              }
            >
              确认删除
            </Button>
          </>
        }
      >
        <p className="text-sm text-gray-600">确认删除表计「{meter.name}」?</p>
      </Modal>
    </>
  )
}

function MeterFormModal({
  meter,
  companyOptions,
  defaultCompanyId,
  onClose,
}: {
  meter: Meter | null
  companyOptions: { value: number; label: string }[]
  defaultCompanyId?: number
  onClose: () => void
}) {
  const isEdit = !!meter
  const create = useCreateMeter()
  const update = useUpdateMeter()
  const [form, setForm] = useState({
    companyId: meter?.companyId ?? defaultCompanyId ?? companyOptions[0]?.value ?? 0,
    name: meter?.name ?? '',
    type: meter?.type ?? METER_TYPE.ELEC,
    initialReading: meter ? String(meter.initialReading) : '0',
    ratio: meter ? String(meter.ratio) : '1',
    location: meter?.location ?? '',
    status: meter?.status ?? METER_STATUS.ENABLED,
  })
  const [errors, setErrors] = useState<Record<string, string>>({})
  const pending = create.isPending || update.isPending

  const validate = (): MeterPayload | null => {
    const next: Record<string, string> = {}
    if (!form.companyId) next.companyId = '请选择公司'
    if (!form.name.trim()) next.name = '请输入表计名称'
    const initial = Number(form.initialReading)
    const ratio = Number(form.ratio)
    if (Number.isNaN(initial) || initial < 0) next.initialReading = '初始读数需 ≥ 0'
    if (Number.isNaN(ratio) || ratio <= 0) next.ratio = '倍率需 > 0'
    setErrors(next)
    if (Object.keys(next).length > 0) return null
    return {
      companyId: Number(form.companyId),
      name: form.name.trim(),
      type: Number(form.type),
      initialReading: initial,
      ratio,
      location: form.location.trim() || undefined,
      status: Number(form.status),
    }
  }

  const onError = (e: unknown) => {
    if (e instanceof ApiError && e.code === ErrorCode.CONFLICT) {
      setErrors((p) => ({ ...p, name: e.message || '同公司下表计名重复' }))
    } else {
      toast.error(errorMessage(e))
    }
  }

  const submit = () => {
    const payload = validate()
    if (!payload) return
    if (isEdit) {
      update.mutate(
        { id: meter!.id, payload },
        {
          onSuccess: () => {
            toast.success('已保存')
            onClose()
          },
          onError,
        },
      )
    } else {
      create.mutate(payload, {
        onSuccess: () => {
          toast.success('已新增')
          onClose()
        },
        onError,
      })
    }
  }

  return (
    <Modal
      open
      title={isEdit ? '编辑表计' : '新增表计'}
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
        <Select
          label="所属公司"
          options={companyOptions}
          placeholder="请选择公司"
          value={form.companyId || ''}
          error={errors.companyId}
          onChange={(e) => setForm({ ...form, companyId: Number(e.target.value) })}
        />
        <Input
          label="表计名称"
          value={form.name}
          error={errors.name}
          onChange={(e) => setForm({ ...form, name: e.target.value })}
        />
        <Select
          label="类型"
          options={[
            { value: METER_TYPE.ELEC, label: '电表' },
            { value: METER_TYPE.WATER, label: '水表' },
          ]}
          value={form.type}
          onChange={(e) => setForm({ ...form, type: Number(e.target.value) })}
        />
        <div className="grid grid-cols-2 gap-3">
          <Input
            label="初始读数"
            type="number"
            inputMode="decimal"
            value={form.initialReading}
            error={errors.initialReading}
            onChange={(e) => setForm({ ...form, initialReading: e.target.value })}
          />
          <Input
            label="倍率"
            type="number"
            inputMode="decimal"
            value={form.ratio}
            error={errors.ratio}
            onChange={(e) => setForm({ ...form, ratio: e.target.value })}
          />
        </div>
        <Input
          label="安装位置"
          value={form.location}
          onChange={(e) => setForm({ ...form, location: e.target.value })}
        />
        <Select
          label="状态"
          options={[
            { value: METER_STATUS.ENABLED, label: METER_STATUS_LABEL[1] },
            { value: METER_STATUS.DISABLED, label: METER_STATUS_LABEL[0] },
          ]}
          value={form.status}
          onChange={(e) => setForm({ ...form, status: Number(e.target.value) })}
        />
      </div>
    </Modal>
  )
}
