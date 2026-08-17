import { useState } from 'react'
import { TopBar } from '@/components/layout/TopBar'
import {
  useCompanies,
  useCreateCompany,
  useDeleteCompany,
  useUpdateCompany,
} from '@/hooks/useCompanies'
import { useAuthStore } from '@/store/auth'
import { ROLE } from '@/config/dict'
import { Button } from '@/components/ui/Button'
import { Input } from '@/components/ui/Input'
import { Modal } from '@/components/ui/Modal'
import { LoadingState, EmptyState, ErrorState } from '@/components/ui/States'
import { toast } from '@/components/ui/Toast'
import { errorMessage } from '@/utils/errorMessage'
import { ApiError, ErrorCode } from '@/types/error'
import type { Company, CompanyPayload } from '@/types/dto'

export function CompanyListPage() {
  const role = useAuthStore((s) => s.role)
  const isAdmin = role === ROLE.ADMIN
  const q = useCompanies()

  const [editing, setEditing] = useState<Company | null>(null)
  const [creating, setCreating] = useState(false)

  return (
    <div>
      <TopBar
        title="公司台账"
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
          title="还没有公司,先建一个"
          action={
            isAdmin ? <Button onClick={() => setCreating(true)}>新增公司</Button> : undefined
          }
        />
      ) : (
        <ul className="divide-y divide-gray-100">
          {q.data.map((c) => (
            <li key={c.id} className="flex items-center gap-3 bg-white px-4 py-3">
              <div className="min-w-0 flex-1">
                <p className="truncate font-medium text-gray-900">{c.name}</p>
                <p className="truncate text-sm text-gray-500">
                  {c.contact || '无联系人'}
                  {c.phone ? ` · ${c.phone}` : ''}
                </p>
              </div>
              {isAdmin && (
                <div className="flex shrink-0 gap-3">
                  <button className="text-sm text-brand" onClick={() => setEditing(c)}>
                    编辑
                  </button>
                  <DeleteButton company={c} />
                </div>
              )}
            </li>
          ))}
        </ul>
      )}

      {(creating || editing) && (
        <CompanyFormModal
          company={editing}
          onClose={() => {
            setCreating(false)
            setEditing(null)
          }}
        />
      )}
    </div>
  )
}

function DeleteButton({ company }: { company: Company }) {
  const del = useDeleteCompany()
  const [confirming, setConfirming] = useState(false)

  const onDelete = () => {
    del.mutate(company.id, {
      onSuccess: () => {
        toast.success('已删除')
        setConfirming(false)
      },
      onError: (e) => {
        // 有表计的公司禁删 → 3002
        if (e instanceof ApiError && e.code === ErrorCode.CONFLICT) {
          toast.error(e.message || '该公司下有表计,不能删除')
        } else {
          toast.error(errorMessage(e))
        }
        setConfirming(false)
      },
    })
  }

  return (
    <>
      <button className="text-sm text-red-600" onClick={() => setConfirming(true)}>
        删除
      </button>
      <Modal
        open={confirming}
        title="删除公司"
        onClose={() => setConfirming(false)}
        footer={
          <>
            <Button variant="secondary" block onClick={() => setConfirming(false)}>
              取消
            </Button>
            <Button variant="danger" block loading={del.isPending} onClick={onDelete}>
              确认删除
            </Button>
          </>
        }
      >
        <p className="text-sm text-gray-600">
          确认删除「{company.name}」?若该公司下已有表计将无法删除。
        </p>
      </Modal>
    </>
  )
}

function CompanyFormModal({
  company,
  onClose,
}: {
  company: Company | null
  onClose: () => void
}) {
  const isEdit = !!company
  const create = useCreateCompany()
  const update = useUpdateCompany()
  const [form, setForm] = useState<CompanyPayload>({
    name: company?.name || '',
    contact: company?.contact || '',
    phone: company?.phone || '',
    remark: company?.remark || '',
  })
  const [nameError, setNameError] = useState('')

  const pending = create.isPending || update.isPending

  const onError = (e: unknown) => {
    // 同名冲突 → 3002,就地表单红字
    if (e instanceof ApiError && e.code === ErrorCode.CONFLICT) {
      setNameError(e.message || '公司名已存在')
    } else {
      toast.error(errorMessage(e))
    }
  }

  const submit = () => {
    if (!form.name.trim()) {
      setNameError('请输入公司名称')
      return
    }
    setNameError('')
    if (isEdit) {
      update.mutate(
        { id: company!.id, payload: form },
        {
          onSuccess: () => {
            toast.success('已保存')
            onClose()
          },
          onError,
        },
      )
    } else {
      create.mutate(form, {
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
      title={isEdit ? '编辑公司' : '新增公司'}
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
          label="公司名称"
          value={form.name}
          error={nameError}
          onChange={(e) => setForm({ ...form, name: e.target.value })}
        />
        <Input
          label="联系人"
          value={form.contact}
          onChange={(e) => setForm({ ...form, contact: e.target.value })}
        />
        <Input
          label="联系电话"
          type="tel"
          value={form.phone}
          onChange={(e) => setForm({ ...form, phone: e.target.value })}
        />
        <Input
          label="备注"
          value={form.remark}
          onChange={(e) => setForm({ ...form, remark: e.target.value })}
        />
      </div>
    </Modal>
  )
}
