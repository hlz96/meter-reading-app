import { Link, useNavigate } from 'react-router-dom'
import { TopBar } from '@/components/layout/TopBar'
import { useAuthStore } from '@/store/auth'
import { usePeriods } from '@/hooks/usePeriods'
import { ROLE, ROLE_LABEL, PERIOD_STATUS } from '@/config/dict'
import { Badge } from '@/components/ui/Badge'
import { Button } from '@/components/ui/Button'
import { LoadingState, ErrorState } from '@/components/ui/States'
import { fmtDateRange } from '@/utils/format'
import { toast } from '@/components/ui/Toast'

export function HomePage() {
  const role = useAuthStore((s) => s.role)
  const logout = useAuthStore((s) => s.logout)
  const navigate = useNavigate()
  const periods = usePeriods()

  const isAdmin = role === ROLE.ADMIN
  const canRead = role === ROLE.ADMIN || role === ROLE.READER
  const canReport = role === ROLE.ADMIN || role === ROLE.VIEWER

  // 最近的进行中周期,作为快捷入口
  const activePeriod = periods.data?.find((p) => p.status === PERIOD_STATUS.OPEN)

  return (
    <div>
      <TopBar
        title="首页"
        right={
          <button
            className="text-sm text-gray-500"
            onClick={() => {
              logout()
              navigate('/login', { replace: true })
            }}
          >
            退出
          </button>
        }
      />

      <div className="space-y-4 p-4">
        <div className="rounded-xl bg-white p-4 shadow-sm">
          <div className="flex items-center justify-between">
            <span className="text-sm text-gray-500">当前身份</span>
            <Badge tone="blue">{role ? ROLE_LABEL[role] : '—'}</Badge>
          </div>
        </div>

        {/* 快捷功能 */}
        <div className="grid grid-cols-2 gap-3">
          <QuickCard to="/ledger/companies" icon="🏢" label="公司台账" />
          <QuickCard to="/ledger/meters" icon="🔢" label="表计台账" />
          <QuickCard to="/periods" icon="📅" label="周期管理" />
          {canRead && <QuickCard to="/reading" icon="✍️" label="抄表录入" />}
          {canReport && <QuickCard to="/report" icon="📊" label="汇总催单" />}
        </div>

        {/* 当前周期 */}
        <section>
          <h2 className="mb-2 px-1 text-sm font-medium text-gray-500">当前周期</h2>
          {periods.isLoading ? (
            <LoadingState />
          ) : periods.isError ? (
            <ErrorState onRetry={() => periods.refetch()} />
          ) : activePeriod ? (
            <div className="rounded-xl bg-white p-4 shadow-sm">
              <div className="flex items-center justify-between">
                <span className="font-medium text-gray-900">{activePeriod.name}</span>
                <Badge tone="green">进行中</Badge>
              </div>
              <p className="mt-1 text-sm text-gray-500">
                {fmtDateRange(activePeriod.startDate, activePeriod.endDate)}
              </p>
              <div className="mt-3 flex gap-2">
                {canRead && (
                  <Button
                    size="sm"
                    onClick={() => navigate(`/reading/${activePeriod.id}/tasks`)}
                  >
                    去抄表
                  </Button>
                )}
                {isAdmin && (
                  <Button
                    size="sm"
                    variant="secondary"
                    onClick={() => navigate(`/audit/${activePeriod.id}`)}
                  >
                    去审核
                  </Button>
                )}
                {canReport && (
                  <Button
                    size="sm"
                    variant="secondary"
                    onClick={() => navigate(`/report/summary/${activePeriod.id}`)}
                  >
                    看汇总
                  </Button>
                )}
              </div>
            </div>
          ) : (
            <div className="rounded-xl bg-white p-4 text-sm text-gray-500 shadow-sm">
              暂无进行中的周期。
              {isAdmin ? (
                <Link to="/periods" className="ml-1 text-brand">
                  去创建
                </Link>
              ) : (
                <button
                  className="ml-1 text-brand"
                  onClick={() => toast.info('请联系管理员创建周期')}
                >
                  联系管理员
                </button>
              )}
            </div>
          )}
        </section>
      </div>
    </div>
  )
}

function QuickCard({ to, icon, label }: { to: string; icon: string; label: string }) {
  return (
    <Link
      to={to}
      className="flex flex-col items-center gap-1 rounded-xl bg-white p-4 shadow-sm active:bg-gray-50"
    >
      <span className="text-2xl">{icon}</span>
      <span className="text-sm text-gray-700">{label}</span>
    </Link>
  )
}
