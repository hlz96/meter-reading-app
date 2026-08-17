import type { MeterFilter, ReadingFilter } from '@/types/dto'

/** react-query key 工厂(前端 TRD §2)。集中定义便于精准失效。 */
export const qk = {
  me: ['me'] as const,
  companies: ['companies'] as const,
  meters: (filter: MeterFilter = {}) => ['meters', filter] as const,
  metersAll: ['meters'] as const,
  periods: ['periods'] as const,
  tasks: (periodId: number) => ['tasks', periodId] as const,
  readings: (filter: ReadingFilter) => ['readings', filter] as const,
  readingsByPeriod: (periodId: number) => ['readings', { periodId }] as const,
  summary: (periodId: number) => ['summary', periodId] as const,
  dunning: (periodId: number) => ['dunning', periodId] as const,
}
