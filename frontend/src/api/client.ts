import axios, {
  AxiosError,
  type AxiosInstance,
  type AxiosResponse,
  type InternalAxiosRequestConfig,
} from 'axios'
import { API_BASE, PUBLIC_PATHS } from '@/config'
import { ApiError, ErrorCode } from '@/types/error'
import type { ApiEnvelope, AuthResult } from '@/types/dto'
import {
  clearTokens,
  getAccessToken,
  getRefreshToken,
  setTokens,
} from '@/store/tokens'

/**
 * axios 实例 + 拦截器(前端 TRD §4)。
 * - 请求:注入 Bearer(免登录端点跳过)。
 * - 响应成功(2xx):blob 放行;否则读 body.code,0 返回 data,非 0 抛 ApiError。
 * - 响应错误(非 2xx):从 err.response.data 组 ApiError;无 body 视为网络错误(NETWORK)。
 * - 401:单飞 refresh + 重放一次。
 */
export const client: AxiosInstance = axios.create({
  baseURL: API_BASE,
  timeout: 20_000,
})

/** 会话失效回调(由 App 注册,跳 /login 并记 returnTo)。解耦 client 与 router。 */
let onSessionExpired: (() => void) | null = null
export function registerSessionExpiredHandler(fn: () => void): void {
  onSessionExpired = fn
}
function handleSessionExpired(): void {
  clearTokens()
  if (onSessionExpired) onSessionExpired()
  else window.location.assign('/login')
}

function isPublicPath(url = ''): boolean {
  return PUBLIC_PATHS.some((p) => url.startsWith(p))
}

// ---- 请求拦截:注入 Bearer ----
client.interceptors.request.use((cfg: InternalAxiosRequestConfig) => {
  if (!isPublicPath(cfg.url)) {
    const token = getAccessToken()
    if (token) cfg.headers.set('Authorization', `Bearer ${token}`)
  }
  return cfg
})

// ---- 单飞 refresh ----
let refreshing: Promise<string> | null = null

/** 用裸 axios 调 refresh(不走本实例拦截器,避免递归)。成功写回新 access+refresh。 */
async function doRefresh(): Promise<string> {
  const refreshToken = getRefreshToken()
  if (!refreshToken) throw new ApiError(ErrorCode.TOKEN_EXPIRED, '登录已失效')

  const resp = await axios.post<ApiEnvelope<AuthResult>>(
    `${API_BASE}/auth/refresh`,
    { refreshToken },
    { timeout: 20_000 },
  )
  const body = resp.data
  if (body.code !== ErrorCode.OK || !body.data) {
    // refresh 返回业务错误(如 2002)→ 视为会话失效
    throw new ApiError(body.code, body.message || '登录已失效')
  }
  // 滚动发放:access + refresh 都要存
  setTokens(body.data.accessToken, body.data.refreshToken)
  return body.data.accessToken
}

function refreshOnce(): Promise<string> {
  if (!refreshing) {
    refreshing = doRefresh().finally(() => {
      refreshing = null
    })
  }
  return refreshing
}

type RetriableConfig = InternalAxiosRequestConfig & { _retried?: boolean }

// ---- 响应拦截:解包 + 错误漏斗 + 401 重放 ----
client.interceptors.response.use(
  (resp: AxiosResponse) => {
    // blob 放行(导出),错误分支在 export 层按 content-type 判定
    if (resp.config.responseType === 'blob') return resp

    const body = resp.data as ApiEnvelope<unknown>
    // 后端契约:成功 = 2xx 且 code===0
    if (body && typeof body.code === 'number') {
      if (body.code === ErrorCode.OK) {
        resp.data = body.data
        return resp
      }
      throw new ApiError(body.code, body.message || '请求失败')
    }
    // 非信封结构(极少数),原样返回
    return resp
  },
  async (error: AxiosError) => {
    const response = error.response
    const config = error.config as RetriableConfig | undefined

    // 无响应 → 网络错误(交给离线队列判定)
    if (!response || !config) {
      return Promise.reject(new ApiError(ErrorCode.NETWORK, '网络连接失败,请检查网络'))
    }

    const bodyData = response.data as ApiEnvelope<unknown> | undefined
    const bizCode = bodyData?.code
    const bizMsg = bodyData?.message

    // 401:单飞 refresh + 重放一次(refresh/login 自身或已重试过的请求不再触发)
    if (
      response.status === 401 &&
      !config._retried &&
      !isPublicPath(config.url)
    ) {
      try {
        const newToken = await refreshOnce()
        config._retried = true
        config.headers.set('Authorization', `Bearer ${newToken}`)
        return client.request(config)
      } catch {
        handleSessionExpired()
        return Promise.reject(
          new ApiError(ErrorCode.TOKEN_EXPIRED, '登录已失效,请重新登录'),
        )
      }
    }

    // 401 重试后仍失败 / refresh 端点 401
    if (response.status === 401) {
      handleSessionExpired()
      return Promise.reject(
        new ApiError(bizCode ?? ErrorCode.UNAUTHORIZED, bizMsg || '登录已失效'),
      )
    }

    // 其余非 2xx(400 校验、403 无权限、500 等):按 body {code,message} 组 ApiError
    return Promise.reject(
      new ApiError(
        bizCode ?? ErrorCode.SERVER_ERROR,
        bizMsg || error.message || '请求失败',
      ),
    )
  },
)
