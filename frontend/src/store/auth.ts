import { create } from 'zustand'
import type { AuthResult, MeResult } from '@/types/dto'
import {
  clearTokens,
  getIdentity,
  setIdentity,
  setTokens,
} from './tokens'

/**
 * 会话状态(前端 TRD §2/§4.1)。token 存 localStorage,身份(role/orgId)镜像到内存态供守卫/导航用。
 * 首版单组织:orgId 固定取登录返回值。
 */
interface AuthState {
  userId: number | null
  orgId: number | null
  role: string | null
  isAuthed: boolean
  login: (auth: AuthResult) => void
  /** 用 /auth/me 校准身份(role 可能因后端变更)。 */
  syncIdentity: (me: MeResult) => void
  logout: () => void
}

const initial = getIdentity()

export const useAuthStore = create<AuthState>((set) => ({
  userId: initial?.userId ?? null,
  orgId: initial?.orgId ?? null,
  role: initial?.role ?? null,
  isAuthed: !!initial,

  login: (auth) => {
    setTokens(auth.accessToken, auth.refreshToken)
    const id: MeResult = { userId: auth.userId, orgId: auth.orgId, role: auth.role }
    setIdentity(id)
    set({ ...id, isAuthed: true })
  },

  syncIdentity: (me) => {
    setIdentity(me)
    set({ userId: me.userId, orgId: me.orgId, role: me.role, isAuthed: true })
  },

  logout: () => {
    clearTokens()
    set({ userId: null, orgId: null, role: null, isAuthed: false })
  },
}))

/** 便捷选择器:当前角色。 */
export const useRole = () => useAuthStore((s) => s.role)
