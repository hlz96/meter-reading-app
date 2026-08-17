import { Navigate, useLocation } from 'react-router-dom'
import type { ReactNode } from 'react'
import { useAuthStore } from '@/store/auth'
import { toast } from '@/components/ui/Toast'

/** 未登录 → /login,记录 returnTo(前端 TRD §5)。 */
export function RequireAuth({ children }: { children: ReactNode }) {
  const isAuthed = useAuthStore((s) => s.isAuthed)
  const location = useLocation()
  if (!isAuthed) {
    return <Navigate to="/login" replace state={{ returnTo: location.pathname }} />
  }
  return <>{children}</>
}

/** 角色不符 → 回首页 + toast(不跳登录,前端 TRD §5)。 */
export function RequireRole({
  roles,
  children,
}: {
  roles: string[]
  children: ReactNode
}) {
  const role = useAuthStore((s) => s.role)
  if (!role || !roles.includes(role)) {
    toast.error('无权限访问该页面')
    return <Navigate to="/" replace />
  }
  return <>{children}</>
}
