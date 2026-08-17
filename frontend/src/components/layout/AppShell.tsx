import { useEffect } from 'react'
import { Outlet, useNavigate } from 'react-router-dom'
import { TabBar } from './TabBar'
import { registerSessionExpiredHandler } from '@/api/client'
import { registerAutoSync } from '@/offline/sync'
import { useOfflineQueue } from '@/store/offlineQueue'
import { useAuthStore } from '@/store/auth'
import { authApi } from '@/api/auth'
import { toast } from '@/components/ui/Toast'

/**
 * 应用外壳:注册会话失效跳转、离线自动同步、启动时用 /auth/me 校准身份;
 * 底部固定 TabBar,内容区留出 TabBar 高度。
 */
export function AppShell() {
  const navigate = useNavigate()
  const refreshQueue = useOfflineQueue((s) => s.refresh)
  const syncIdentity = useAuthStore((s) => s.syncIdentity)
  const logout = useAuthStore((s) => s.logout)

  useEffect(() => {
    registerSessionExpiredHandler(() => {
      logout()
      navigate('/login', { replace: true, state: { returnTo: location.pathname } })
    })
  }, [navigate, logout])

  useEffect(() => {
    // 初次计数 + 网络恢复自动同步
    refreshQueue()
    const off = registerAutoSync((r) => {
      if (r && r.successCount > 0) toast.success(`已同步 ${r.successCount} 条读数`)
    })
    return off
  }, [refreshQueue])

  useEffect(() => {
    // 用 /auth/me 校准角色(可能被后端变更);失败交给拦截器处理
    authApi.me().then(syncIdentity).catch(() => undefined)
  }, [syncIdentity])

  return (
    <div className="mx-auto flex min-h-full max-w-md flex-col">
      <main className="flex-1 pb-16">
        <Outlet />
      </main>
      <TabBar />
    </div>
  )
}
