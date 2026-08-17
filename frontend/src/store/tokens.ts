// 纯 localStorage token 读写(无 React/Zustand 依赖),供 axios 拦截器与 auth store 共用,
// 避免 client ↔ store 循环依赖(前端 TRD §4.1)。

import { STORAGE_KEYS } from '@/config'
import type { MeResult } from '@/types/dto'

export function getAccessToken(): string | null {
  return localStorage.getItem(STORAGE_KEYS.accessToken)
}

export function getRefreshToken(): string | null {
  return localStorage.getItem(STORAGE_KEYS.refreshToken)
}

/** 后端滚动发放:access + refresh 都要存。 */
export function setTokens(accessToken: string, refreshToken: string): void {
  localStorage.setItem(STORAGE_KEYS.accessToken, accessToken)
  localStorage.setItem(STORAGE_KEYS.refreshToken, refreshToken)
}

export function getIdentity(): MeResult | null {
  const raw = localStorage.getItem(STORAGE_KEYS.auth)
  if (!raw) return null
  try {
    return JSON.parse(raw) as MeResult
  } catch {
    return null
  }
}

export function setIdentity(id: MeResult): void {
  localStorage.setItem(STORAGE_KEYS.auth, JSON.stringify(id))
}

export function clearTokens(): void {
  localStorage.removeItem(STORAGE_KEYS.accessToken)
  localStorage.removeItem(STORAGE_KEYS.refreshToken)
  localStorage.removeItem(STORAGE_KEYS.auth)
}
