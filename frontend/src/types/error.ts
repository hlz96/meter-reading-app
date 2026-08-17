// 错误码 → 语义(前端 TRD §4.5)。统一 ApiError,页面只 catch 一种错误类型。

export const ErrorCode = {
  OK: 0,
  PARAM_INVALID: 1001,
  SMS_CODE_INVALID: 1002,
  UNAUTHORIZED: 2001,
  TOKEN_EXPIRED: 2002,
  FORBIDDEN: 2003,
  CROSS_ORG: 2004,
  NOT_FOUND: 3001,
  CONFLICT: 3002,
  INVITATION_INVALID: 3003,
  IMPORT_PARSE_FAIL: 4001,
  READING_ABNORMAL: 4002,
  PERIOD_NOT_SETTLEABLE: 4003,
  SERVER_ERROR: 5000,
  /** 前端合成:网络不可达(离线队列判定依据)。 */
  NETWORK: -1,
} as const

export type ErrorCodeValue = (typeof ErrorCode)[keyof typeof ErrorCode]

/**
 * 业务/网络错误统一类型。code 来自后端 {code} 或前端合成的 NETWORK。
 */
export class ApiError extends Error {
  readonly code: number

  constructor(code: number, message: string) {
    super(message)
    this.name = 'ApiError'
    this.code = code
  }

  /** 网络不可达(无 HTTP 响应):离线队列据此入队。 */
  get isNetwork(): boolean {
    return this.code === ErrorCode.NETWORK
  }

  /** 未登录 / token 过期。 */
  get isAuth(): boolean {
    return this.code === ErrorCode.UNAUTHORIZED || this.code === ErrorCode.TOKEN_EXPIRED
  }

  /** 无权限 / 越权(不跳登录,仅 toast)。 */
  get isForbidden(): boolean {
    return this.code === ErrorCode.FORBIDDEN || this.code === ErrorCode.CROSS_ORG
  }
}
