import { NavLink } from 'react-router-dom'
import { clsx } from 'clsx'
import { useAuthStore } from '@/store/auth'
import { ROLE } from '@/config/dict'

interface TabDef {
  to: string
  label: string
  icon: string
  roles?: string[] // 缺省=全角色可见
}

// 底部导航(前端 TRD §5.2:READER 隐藏汇总/催单;VIEWER 不显示抄表录入入口)。
const TABS: TabDef[] = [
  { to: '/', label: '首页', icon: '🏠' },
  { to: '/ledger/companies', label: '台账', icon: '📒' },
  { to: '/reading', label: '抄表', icon: '✍️', roles: [ROLE.ADMIN, ROLE.READER] },
  { to: '/report', label: '报表', icon: '📊', roles: [ROLE.ADMIN, ROLE.VIEWER] },
]

export function TabBar() {
  const role = useAuthStore((s) => s.role)
  const visible = TABS.filter((t) => !t.roles || (role && t.roles.includes(role)))

  return (
    <nav className="fixed inset-x-0 bottom-0 z-30 border-t border-gray-200 bg-white pb-[env(safe-area-inset-bottom)]">
      <div className="mx-auto flex max-w-md">
        {visible.map((t) => (
          <NavLink
            key={t.to}
            to={t.to}
            end={t.to === '/'}
            className={({ isActive }) =>
              clsx(
                'flex flex-1 flex-col items-center gap-0.5 py-2 text-xs',
                isActive ? 'text-brand' : 'text-gray-500',
              )
            }
          >
            <span className="text-xl">{t.icon}</span>
            {t.label}
          </NavLink>
        ))}
      </div>
    </nav>
  )
}
