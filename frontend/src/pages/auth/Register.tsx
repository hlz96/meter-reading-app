import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { useMutation } from '@tanstack/react-query'
import { authApi } from '@/api/auth'
import { useAuthStore } from '@/store/auth'
import { Button } from '@/components/ui/Button'
import { Input } from '@/components/ui/Input'
import { toast } from '@/components/ui/Toast'
import { errorMessage } from '@/utils/errorMessage'
import { ApiError, ErrorCode } from '@/types/error'
import type { AuthResult } from '@/types/dto'

/**
 * 管理员自主注册并创建组织(注册流程说明 路径 A)。成员邀请为二期,首版只做此路径。
 * 骨架阶段 /auth/sms-code 会回传验证码,便于联调:点「获取验证码」后自动填入。
 */
export function RegisterPage() {
  const navigate = useNavigate()
  const login = useAuthStore((s) => s.login)
  const [phone, setPhone] = useState('')
  const [smsCode, setSmsCode] = useState('')
  const [password, setPassword] = useState('')
  const [orgName, setOrgName] = useState('')
  const [errors, setErrors] = useState<Record<string, string>>({})

  const smsMut = useMutation({
    mutationFn: () => authApi.smsCode({ phone, scene: 'REGISTER' }),
    onSuccess: (data) => {
      // 骨架阶段回传 code,自动填入
      if (data.code) setSmsCode(data.code)
      toast.success('验证码已获取')
    },
    onError: (e) => {
      if (e instanceof ApiError && e.code === ErrorCode.PARAM_INVALID) {
        setErrors((p) => ({ ...p, phone: e.message }))
      } else {
        toast.error(errorMessage(e))
      }
    },
  })

  const regMut = useMutation({
    mutationFn: () => authApi.register({ phone, smsCode, password, orgName }),
    onSuccess: (data: AuthResult) => {
      login(data)
      toast.success('注册成功')
      navigate('/', { replace: true })
    },
    onError: (e) => {
      if (e instanceof ApiError) {
        // 手机号已注册 → 3002 冲突;验证码无效 → 1002
        if (e.code === ErrorCode.CONFLICT) {
          setErrors((p) => ({ ...p, phone: e.message || '该手机号已注册' }))
          return
        }
        if (e.code === ErrorCode.SMS_CODE_INVALID) {
          setErrors((p) => ({ ...p, smsCode: '验证码有误或已过期,请重新获取' }))
          return
        }
        if (e.code === ErrorCode.PARAM_INVALID) {
          toast.error(e.message)
          return
        }
      }
      toast.error(errorMessage(e))
    },
  })

  const validate = (): boolean => {
    const next: Record<string, string> = {}
    if (!/^\d{11}$/.test(phone)) next.phone = '请输入 11 位手机号'
    if (password.length < 8 || password.length > 20) next.password = '密码需 8-20 位'
    if (!orgName.trim()) next.orgName = '请输入组织名称'
    setErrors(next)
    return Object.keys(next).length === 0
  }

  const submit = (e: React.FormEvent) => {
    e.preventDefault()
    if (validate()) regMut.mutate()
  }

  return (
    <div className="mx-auto flex min-h-full max-w-md flex-col justify-center px-6 py-12">
      <h1 className="mb-1 text-2xl font-bold text-gray-900">注册并创建组织</h1>
      <p className="mb-8 text-sm text-gray-500">首位使用者注册即成为管理员</p>

      <form className="space-y-4" onSubmit={submit}>
        <Input
          label="手机号"
          type="tel"
          inputMode="numeric"
          placeholder="11 位手机号"
          value={phone}
          error={errors.phone}
          onChange={(e) => setPhone(e.target.value.trim())}
        />
        <div className="flex items-end gap-2">
          <div className="flex-1">
            <Input
              label="验证码"
              inputMode="numeric"
              placeholder="短信验证码"
              value={smsCode}
              error={errors.smsCode}
              onChange={(e) => setSmsCode(e.target.value.trim())}
            />
          </div>
          <Button
            type="button"
            variant="secondary"
            className="mb-[1px]"
            loading={smsMut.isPending}
            disabled={!/^\d{11}$/.test(phone)}
            onClick={() => smsMut.mutate()}
          >
            获取验证码
          </Button>
        </div>
        <Input
          label="密码"
          type="password"
          placeholder="8-20 位密码"
          value={password}
          error={errors.password}
          onChange={(e) => setPassword(e.target.value)}
        />
        <Input
          label="组织名称"
          placeholder="如:阳光物业 / XX 园区"
          value={orgName}
          error={errors.orgName}
          onChange={(e) => setOrgName(e.target.value)}
        />
        <Button type="submit" block loading={regMut.isPending}>
          注册
        </Button>
      </form>

      <p className="mt-6 text-center text-sm text-gray-500">
        已有账号?
        <Link to="/login" className="text-brand">
          去登录
        </Link>
      </p>
    </div>
  )
}
