import { describe, it, expect } from 'vitest'
import {
  getAccessToken,
  getRefreshToken,
  setTokens,
  getIdentity,
  setIdentity,
  clearTokens,
} from '@/store/tokens'
import { STORAGE_KEYS } from '@/config'
import type { MeResult } from '@/types/dto'

const ID: MeResult = { userId: 1, orgId: 7, role: 'ADMIN' }

describe('tokens 读写', () => {
  it('未设置时 token 为 null', () => {
    expect(getAccessToken()).toBeNull()
    expect(getRefreshToken()).toBeNull()
  })

  it('setTokens 同时写 access 与 refresh', () => {
    setTokens('a1', 'r1')
    expect(getAccessToken()).toBe('a1')
    expect(getRefreshToken()).toBe('r1')
  })

  it('setIdentity / getIdentity 往返', () => {
    setIdentity(ID)
    expect(getIdentity()).toEqual(ID)
  })

  it('getIdentity 未设置 → null', () => {
    expect(getIdentity()).toBeNull()
  })

  it('getIdentity 遇到损坏 JSON 不抛异常,返回 null', () => {
    localStorage.setItem(STORAGE_KEYS.auth, '{不是合法json')
    expect(getIdentity()).toBeNull()
  })

  it('clearTokens 清空全部三项', () => {
    setTokens('a1', 'r1')
    setIdentity(ID)
    clearTokens()
    expect(getAccessToken()).toBeNull()
    expect(getRefreshToken()).toBeNull()
    expect(getIdentity()).toBeNull()
  })
})
