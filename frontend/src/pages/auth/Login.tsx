import { useState } from 'react'
import { Link, useLocation, useNavigate } from 'react-router-dom'
import { useMutation } from '@tanstack/react-query'
import { authApi } from '@/api/auth'
import { useAuthStore } from '@/store/auth'
import { Button } from '@/components/ui/Button'
import { Input } from '@/components/ui/Input'
import { toast } from '@/components/ui/Toast'
import { errorMessage } from '@/utils/errorMessage'
import type { AuthResult } from '@/types/dto'

export function LoginPage() {
  const navigate = useNavigate()
  const location = useLocation()
  const login = useAuthStore((s) => s.login)
  const [phone, setPhone] = useState('')
  const [password, setPassword] = useState('')

  const returnTo = (location.state as { returnTo?: string } | null)?.returnTo || '/'

  const mut = useMutation({
    mutationFn: () => authApi.login({ phone, password }),
    onSuccess: (data: AuthResult) => {
      login(data)
      navigate(returnTo, { replace: true })
    },
    // 后端把「手机号/密码错误」合并为 2001 防枚举,统一提示
    onError: () => toast.error('手机号或密码错误'),
  })

  const submit = (e: React.FormEvent) => {
    e.preventDefault()
    if (!phone || !password) {
      toast.error('请输入手机号和密码')
      return
    }
    mut.mutate()
  }

  return (
    <div className="mx-auto flex min-h-full max-w-md flex-col justify-center px-6 py-12">
      <h1 className="mb-1 text-2xl font-bold text-gray-900">水电表抄表管理</h1>
      <p className="mb-8 text-sm text-gray-500">登录以继续</p>

      <form className="space-y-4" onSubmit={submit}>
        <Input
          label="手机号"
          type="tel"
          inputMode="numeric"
          autoComplete="username"
          placeholder="11 位手机号"
          value={phone}
          onChange={(e) => setPhone(e.target.value.trim())}
        />
        <Input
          label="密码"
          type="password"
          autoComplete="current-password"
          placeholder="8-20 位密码"
          value={password}
          onChange={(e) => setPassword(e.target.value)}
        />
        <Button type="submit" block loading={mut.isPending}>
          登录
        </Button>
      </form>

      <p className="mt-6 text-center text-sm text-gray-500">
        还没有账号?
        <Link to="/register" className="text-brand">
          注册新组织
        </Link>
      </p>
      {mut.isError && (
        <p className="mt-2 text-center text-sm text-red-600">{errorMessage(mut.error)}</p>
      )}
    </div>
  )
}
