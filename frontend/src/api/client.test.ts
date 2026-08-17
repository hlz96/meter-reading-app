import axios, { AxiosError, type AxiosAdapter } from 'axios'
import { beforeEach, describe, it, expect, vi } from 'vitest'
import { client, registerSessionExpiredHandler } from '@/api/client'
import { setTokens, getAccessToken, getRefreshToken } from '@/store/tokens'
import { ApiError, ErrorCode } from '@/types/error'

// 测试策略:替换 client.defaults.adapter,拦截器链照常执行,只把 HTTP 层换成可控 mock。
// refresh 走裸 axios.post,用 vi.spyOn(axios,'post') 拦截。

type MockResult =
  | { status: number; data: unknown; headers?: Record<string, string> }
  | { network: true }

let responder: (config: { url?: string; headers: { get(k: string): unknown } }, call: number) => MockResult
let callCount = 0
let onExpired: ReturnType<typeof vi.fn>

const mockAdapter: AxiosAdapter = async (config) => {
  const res = responder(config as never, callCount++)
  if ('network' in res) {
    // 无 response 的网络错误
    throw new AxiosError('Network Error', 'ERR_NETWORK', config as never, {})
  }
  const response = {
    data: res.data,
    status: res.status,
    statusText: '',
    headers: res.headers ?? {},
    config,
    request: {},
  } as never
  if (res.status >= 200 && res.status < 300) return response
  throw new AxiosError('Request failed', String(res.status), config as never, {}, response)
}

beforeEach(() => {
  client.defaults.adapter = mockAdapter
  callCount = 0
  responder = () => ({ status: 200, data: { code: 0, data: null } })
  onExpired = vi.fn()
  registerSessionExpiredHandler(onExpired)
})

describe('响应解包(2xx)', () => {
  it('code=0 → 剥出 data', async () => {
    responder = () => ({ status: 200, data: { code: 0, data: { id: 42 } } })
    const res = await client.get('/x')
    expect(res.data).toEqual({ id: 42 })
  })

  it('code≠0 → 抛 ApiError(带 code 与 message)', async () => {
    responder = () => ({ status: 200, data: { code: ErrorCode.CONFLICT, message: '重复' } })
    await expect(client.get('/x')).rejects.toMatchObject({
      code: ErrorCode.CONFLICT,
      message: '重复',
    })
  })

  it('非信封结构 → 原样返回', async () => {
    responder = () => ({ status: 200, data: 'plain-text' })
    const res = await client.get('/x')
    expect(res.data).toBe('plain-text')
  })

  it('blob 放行不解包', async () => {
    const blob = { fake: 'blob' }
    responder = () => ({ status: 200, data: blob })
    const res = await client.get('/export', { responseType: 'blob' })
    expect(res.data).toBe(blob)
  })
})

describe('错误漏斗(非 2xx)', () => {
  it('无响应 → NETWORK', async () => {
    responder = () => ({ network: true })
    await expect(client.get('/x')).rejects.toMatchObject({ code: ErrorCode.NETWORK })
    const err = await client.get('/x').catch((e) => e)
    expect(err).toBeInstanceOf(ApiError)
    expect(err.isNetwork).toBe(true)
  })

  it('403 带 body.code → 用业务码', async () => {
    responder = () => ({ status: 403, data: { code: ErrorCode.FORBIDDEN, message: '无权限' } })
    await expect(client.get('/x')).rejects.toMatchObject({
      code: ErrorCode.FORBIDDEN,
      message: '无权限',
    })
  })

  it('500 无 body.code → 回退 SERVER_ERROR', async () => {
    responder = () => ({ status: 500, data: undefined })
    await expect(client.get('/x')).rejects.toMatchObject({ code: ErrorCode.SERVER_ERROR })
  })
})

describe('请求拦截:Bearer 注入', () => {
  it('已登录且非免登录端点 → 带 Authorization', async () => {
    setTokens('tok-abc', 'r1')
    let seen: unknown
    responder = (cfg) => {
      seen = cfg.headers.get('Authorization')
      return { status: 200, data: { code: 0, data: null } }
    }
    await client.get('/protected')
    expect(seen).toBe('Bearer tok-abc')
  })

  it('免登录端点 → 不注入', async () => {
    setTokens('tok-abc', 'r1')
    let seen: unknown = 'unset'
    responder = (cfg) => {
      seen = cfg.headers.get('Authorization')
      return { status: 200, data: { code: 0, data: null } }
    }
    await client.post('/auth/login', {})
    expect(seen == null).toBe(true)
  })
})

describe('401 单飞 refresh + 重放', () => {
  it('refresh 成功 → 重放一次并返回数据,新 token 落库', async () => {
    setTokens('old-a', 'old-r')
    const postSpy = vi.spyOn(axios, 'post').mockResolvedValue({
      data: { code: 0, data: { accessToken: 'new-a', refreshToken: 'new-r' } },
    } as never)

    // call#0 → 401,call#1(重放)→ 200
    responder = (_cfg, call) =>
      call === 0
        ? { status: 401, data: { code: ErrorCode.UNAUTHORIZED } }
        : { status: 200, data: { code: 0, data: { ok: true } } }

    const res = await client.get('/protected')
    expect(res.data).toEqual({ ok: true })
    expect(postSpy).toHaveBeenCalledTimes(1)
    expect(getAccessToken()).toBe('new-a')
    expect(getRefreshToken()).toBe('new-r')
    expect(onExpired).not.toHaveBeenCalled()
  })

  it('并发 401 只触发一次 refresh(单飞)', async () => {
    setTokens('old-a', 'old-r')
    const postSpy = vi.spyOn(axios, 'post').mockResolvedValue({
      data: { code: 0, data: { accessToken: 'new-a', refreshToken: 'new-r' } },
    } as never)

    // 前两次调用(两个并发请求的首发)都 401,之后重放都 200
    responder = (_cfg, call) =>
      call < 2
        ? { status: 401, data: { code: ErrorCode.UNAUTHORIZED } }
        : { status: 200, data: { code: 0, data: { ok: true } } }

    const [a, b] = await Promise.all([client.get('/a'), client.get('/b')])
    expect(a.data).toEqual({ ok: true })
    expect(b.data).toEqual({ ok: true })
    expect(postSpy).toHaveBeenCalledTimes(1)
  })

  it('refresh 失败 → 触发 sessionExpired 并抛 TOKEN_EXPIRED', async () => {
    setTokens('old-a', 'old-r')
    vi.spyOn(axios, 'post').mockRejectedValue(new Error('refresh 500'))
    responder = () => ({ status: 401, data: { code: ErrorCode.UNAUTHORIZED } })

    await expect(client.get('/protected')).rejects.toMatchObject({
      code: ErrorCode.TOKEN_EXPIRED,
    })
    expect(onExpired).toHaveBeenCalledTimes(1)
  })

  it('无 refresh token → 直接会话失效,不调 refresh 接口', async () => {
    // 未 setTokens,refresh token 为 null
    const postSpy = vi.spyOn(axios, 'post')
    responder = () => ({ status: 401, data: { code: ErrorCode.UNAUTHORIZED } })

    await expect(client.get('/protected')).rejects.toMatchObject({
      code: ErrorCode.TOKEN_EXPIRED,
    })
    expect(postSpy).not.toHaveBeenCalled()
    expect(onExpired).toHaveBeenCalledTimes(1)
  })

  it('免登录端点 401 → 不触发 refresh', async () => {
    setTokens('old-a', 'old-r')
    const postSpy = vi.spyOn(axios, 'post')
    responder = () => ({ status: 401, data: { code: ErrorCode.UNAUTHORIZED, message: '账号或密码错误' } })

    await expect(client.post('/auth/login', {})).rejects.toBeInstanceOf(ApiError)
    expect(postSpy).not.toHaveBeenCalled()
  })
})
