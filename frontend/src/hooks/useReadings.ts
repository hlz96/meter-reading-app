import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { readingApi } from '@/api/reading'
import type { ReadingFilter, ReadingPayload } from '@/types/dto'
import { qk } from './keys'

export function useReadings(filter: ReadingFilter, enabled = true) {
  return useQuery({
    queryKey: qk.readings(filter),
    queryFn: () => readingApi.list(filter),
    enabled: enabled && !!filter.periodId,
  })
}

/** 提交/修正后失效该周期的 tasks + readings + 汇总/催单。 */
function invalidatePeriod(
  qc: ReturnType<typeof useQueryClient>,
  periodId: number,
) {
  qc.invalidateQueries({ queryKey: qk.tasks(periodId) })
  qc.invalidateQueries({ queryKey: ['readings'] })
  qc.invalidateQueries({ queryKey: qk.summary(periodId) })
  qc.invalidateQueries({ queryKey: qk.dunning(periodId) })
}

export function useSubmitReading() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (payload: ReadingPayload) => readingApi.submit(payload),
    onSuccess: (_data, vars) => invalidatePeriod(qc, vars.periodId),
  })
}

export function useUpdateReading() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ id, payload }: { id: number; payload: ReadingPayload }) =>
      readingApi.update(id, payload),
    onSuccess: (_data, vars) => invalidatePeriod(qc, vars.payload.periodId),
  })
}

export function useAuditReading(periodId: number) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({
      id,
      approved,
      remark,
    }: {
      id: number
      approved: boolean
      remark?: string
    }) => readingApi.audit(id, approved, remark),
    onSuccess: () => invalidatePeriod(qc, periodId),
  })
}

export function useAuditBatch(periodId: number) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({
      ids,
      approved,
      remark,
    }: {
      ids: number[]
      approved: boolean
      remark?: string
    }) => readingApi.auditBatch(ids, approved, remark),
    onSuccess: () => invalidatePeriod(qc, periodId),
  })
}
