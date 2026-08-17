import { describe, it, expect } from 'vitest'
import { errorMessage } from '@/utils/errorMessage'
import { ApiError, ErrorCode } from '@/types/error'

describe('errorMessage', () => {
  it('ApiError 带 message 时优先用 message', () => {
    expect(errorMessage(new ApiError(ErrorCode.FORBIDDEN, '自定义文案'))).toBe('自定义文案')
  })

  it('ApiError 无 message 时按码回退默认文案', () => {
    expect(errorMessage(new ApiError(ErrorCode.NETWORK, ''))).toBe('网络连接失败,请检查网络')
    expect(errorMessage(new ApiError(ErrorCode.FORBIDDEN, ''))).toBe('无权限执行此操作')
    expect(errorMessage(new ApiError(ErrorCode.CROSS_ORG, ''))).toBe('越权访问')
    expect(errorMessage(new ApiError(ErrorCode.SERVER_ERROR, ''))).toBe('服务器错误,请稍后重试')
  })

  it('未知码且无 message → 请求失败', () => {
    expect(errorMessage(new ApiError(9999, ''))).toBe('请求失败')
  })

  it('普通 Error → 用 error.message', () => {
    expect(errorMessage(new Error('boom'))).toBe('boom')
  })

  it('非 Error 值 → 未知错误', () => {
    expect(errorMessage('字符串')).toBe('未知错误')
    expect(errorMessage(null)).toBe('未知错误')
    expect(errorMessage(undefined)).toBe('未知错误')
  })
})

describe('ApiError 语义 getter', () => {
  it('isNetwork 仅 NETWORK 码', () => {
    expect(new ApiError(ErrorCode.NETWORK, '').isNetwork).toBe(true)
    expect(new ApiError(ErrorCode.SERVER_ERROR, '').isNetwork).toBe(false)
  })

  it('isAuth 覆盖 UNAUTHORIZED 与 TOKEN_EXPIRED', () => {
    expect(new ApiError(ErrorCode.UNAUTHORIZED, '').isAuth).toBe(true)
    expect(new ApiError(ErrorCode.TOKEN_EXPIRED, '').isAuth).toBe(true)
    expect(new ApiError(ErrorCode.FORBIDDEN, '').isAuth).toBe(false)
  })

  it('isForbidden 覆盖 FORBIDDEN 与 CROSS_ORG', () => {
    expect(new ApiError(ErrorCode.FORBIDDEN, '').isForbidden).toBe(true)
    expect(new ApiError(ErrorCode.CROSS_ORG, '').isForbidden).toBe(true)
    expect(new ApiError(ErrorCode.UNAUTHORIZED, '').isForbidden).toBe(false)
  })

  it('保留 code 与 name', () => {
    const e = new ApiError(ErrorCode.CONFLICT, '冲突')
    expect(e.code).toBe(ErrorCode.CONFLICT)
    expect(e.name).toBe('ApiError')
    expect(e).toBeInstanceOf(Error)
  })
})
