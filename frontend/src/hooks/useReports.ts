import { useQuery } from '@tanstack/react-query'
import { reportApi } from '@/api/report'
import { qk } from './keys'

export function useSummary(periodId: number | undefined) {
  return useQuery({
    queryKey: qk.summary(periodId ?? 0),
    queryFn: () => reportApi.summary(periodId as number),
    enabled: !!periodId,
  })
}

export function useDunning(periodId: number | undefined) {
  return useQuery({
    queryKey: qk.dunning(periodId ?? 0),
    queryFn: () => reportApi.dunning(periodId as number),
    enabled: !!periodId,
  })
}
