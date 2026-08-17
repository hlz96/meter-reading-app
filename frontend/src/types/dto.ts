// 后端 record 的 TS 镜像(前端 TRD §2)。字段名/类型严格对齐后端 DTO。
// 金额/读数后端为 BigDecimal,JSON 序列化为 number;前端一律用 number。

/** 统一响应体 {code, message, data}(ApiResponse)。 */
export interface ApiEnvelope<T> {
  code: number
  message: string
  data: T
}

// ---- auth ----

/** AuthResponse。 */
export interface AuthResult {
  userId: number
  orgId: number
  role: string
  accessToken: string
  refreshToken: string
  expiresIn: number
}

/** LoginRequest。 */
export interface LoginPayload {
  phone: string
  password: string
}

/** RegisterRequest。 */
export interface RegisterPayload {
  phone: string
  smsCode?: string
  password: string
  orgName: string
}

/** SmsCodeRequest。 */
export interface SmsCodePayload {
  phone: string
  scene?: string
}

/** SmsCodeResponse(骨架阶段回传 code 便于联调)。 */
export interface SmsCodeResult {
  code: string
  expiresInSeconds: number
}

/** GET /auth/me 返回 {userId, orgId, role}。 */
export interface MeResult {
  userId: number
  orgId: number
  role: string
}

// ---- ledger ----

/** CompanyResponse。 */
export interface Company {
  id: number
  name: string
  contact: string | null
  phone: string | null
  remark: string | null
}

/** CompanyRequest。 */
export interface CompanyPayload {
  name: string
  contact?: string
  phone?: string
  remark?: string
}

/** MeterResponse。 */
export interface Meter {
  id: number
  companyId: number
  name: string
  type: number // 1电 2水
  initialReading: number
  ratio: number
  location: string | null
  status: number // 1启用 0停用
}

/** MeterRequest。 */
export interface MeterPayload {
  companyId: number
  name: string
  type: number
  initialReading: number
  ratio: number
  location?: string
  status?: number
}

/** 表计列表筛选参数。 */
export interface MeterFilter {
  companyId?: number
  type?: number
  status?: number
}

// ---- reading / period ----

/** PeriodResponse。 */
export interface Period {
  id: number
  name: string
  startDate: string | null // LocalDate → "YYYY-MM-DD"
  endDate: string | null
  elecPrice: number | null
  waterPrice: number | null
  status: number // 1进行中 2已结算
}

/** PeriodRequest。 */
export interface PeriodPayload {
  name: string
  startDate?: string | null
  endDate?: string | null
  elecPrice?: number | null
  waterPrice?: number | null
}

/** TaskItem。 */
export interface TaskItem {
  meterId: number
  meterName: string
  companyId: number
  type: number
  done: boolean
  currReading: number | null
  usageAmount: number | null
  auditStatus: number | null
}

/** TaskResponse。 */
export interface TaskOverview {
  periodId: number
  total: number
  doneCount: number
  items: TaskItem[]
}

/** ReadingResponse。 */
export interface Reading {
  id: number
  meterId: number
  periodId: number
  prevReading: number
  currReading: number
  usageAmount: number
  isAbnormal: boolean
  abnormalType: string | null
  auditStatus: number
  auditRemark: string | null
}

/** ReadingRequest。 */
export interface ReadingPayload {
  meterId: number
  periodId: number
  currReading: number
  photoUrl?: string
  clientUuid?: string
  remark?: string
}

/** BatchResult。 */
export interface BatchResult {
  successCount: number
  failCount: number
  errors: string[]
}

/** 读数列表筛选。 */
export interface ReadingFilter {
  periodId: number
  auditStatus?: number
  companyId?: number
}

// ---- report ----

/** SummaryResponse.Row。 */
export interface SummaryRow {
  companyId: number
  type: number
  usage: number
  fee: number | null // null=未定价
}

/** SummaryResponse。 */
export interface Summary {
  periodId: number
  pendingCount: number
  rows: SummaryRow[]
}

/** DunningResponse.Row。 */
export interface DunningRow {
  companyId: number
  elecUsage: number
  elecFee: number | null
  waterUsage: number
  waterFee: number | null
  totalFee: number
}

/** DunningResponse。 */
export interface Dunning {
  periodId: number
  pendingCount: number
  rows: DunningRow[]
}
