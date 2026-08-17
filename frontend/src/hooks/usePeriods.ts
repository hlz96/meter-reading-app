import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { periodApi } from '@/api/reading'
import type { PeriodPayload } from '@/types/dto'
import { qk } from './keys'

export function usePeriods() {
  return useQuery({ queryKey: qk.periods, queryFn: periodApi.list })
}

export function useTasks(periodId: number | undefined) {
  return useQuery({
    queryKey: qk.tasks(periodId ?? 0),
    queryFn: () => periodApi.tasks(periodId as number),
    enabled: !!periodId,
  })
}

export function useCreatePeriod() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (payload: PeriodPayload) => periodApi.create(payload),
    onSuccess: () => qc.invalidateQueries({ queryKey: qk.periods }),
  })
}

export function useUpdatePeriod() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ id, payload }: { id: number; payload: PeriodPayload }) =>
      periodApi.update(id, payload),
    onSuccess: () => qc.invalidateQueries({ queryKey: qk.periods }),
  })
}

export function useSettlePeriod() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (id: number) => periodApi.settle(id),
    onSuccess: (_data, id) => {
      qc.invalidateQueries({ queryKey: qk.periods })
      qc.invalidateQueries({ queryKey: qk.summary(id) })
      qc.invalidateQueries({ queryKey: qk.dunning(id) })
    },
  })
}
