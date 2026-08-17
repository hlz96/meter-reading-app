// 枚举字典(镜像后端语义,前端 TRD §4.5 / §5)。集中定义避免各页面散落魔法数字。

/** 表计类型:1 电表 2 水表(MeterRequest.type / SummaryRow.type)。 */
export const METER_TYPE = { ELEC: 1, WATER: 2 } as const
export const METER_TYPE_LABEL: Record<number, string> = {
  1: '电表',
  2: '水表',
}

/** 表计状态:1 启用 0 停用。 */
export const METER_STATUS = { ENABLED: 1, DISABLED: 0 } as const
export const METER_STATUS_LABEL: Record<number, string> = {
  1: '启用',
  0: '停用',
}

/** 周期状态:1 进行中 2 已结算(PeriodService)。 */
export const PERIOD_STATUS = { OPEN: 1, SETTLED: 2 } as const
export const PERIOD_STATUS_LABEL: Record<number, string> = {
  1: '进行中',
  2: '已结算',
}

/** 审核状态:1 待审 2 通过 3 驳回(ReadingService)。 */
export const AUDIT_STATUS = { PENDING: 1, APPROVED: 2, REJECTED: 3 } as const
export const AUDIT_STATUS_LABEL: Record<number, string> = {
  1: '待审核',
  2: '已通过',
  3: '已驳回',
}

/** 读数异常类型(ReadingService:BACKWARD 倒退 / SPIKE 突增)。 */
export const ABNORMAL_TYPE_LABEL: Record<string, string> = {
  BACKWARD: '读数倒退',
  SPIKE: '用量突增',
}

/** 角色(org/entity/Role)。 */
export const ROLE = { ADMIN: 'ADMIN', READER: 'READER', VIEWER: 'VIEWER' } as const
export const ROLE_LABEL: Record<string, string> = {
  ADMIN: '管理员',
  READER: '抄表员',
  VIEWER: '查看者',
}
export type Role = (typeof ROLE)[keyof typeof ROLE]
