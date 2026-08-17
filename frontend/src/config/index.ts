// 全局配置与常量(前端 TRD §2)。

/** API base,同源部署默认 /api/v1。 */
export const API_BASE = import.meta.env.VITE_API_BASE || '/api/v1'

/** localStorage key。 */
export const STORAGE_KEYS = {
  accessToken: 'mr_access_token',
  refreshToken: 'mr_refresh_token',
  auth: 'mr_auth', // 序列化的 {userId, orgId, role}
} as const

/** 免登录端点(不注入 token,401 不触发 refresh)。 */
export const PUBLIC_PATHS = [
  '/auth/login',
  '/auth/register',
  '/auth/refresh',
  '/auth/sms-code',
  '/ping',
]

/** 离线批量上限(后端 /readings/batch ≤ 500)。 */
export const BATCH_LIMIT = 500
