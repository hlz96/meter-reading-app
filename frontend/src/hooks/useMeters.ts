import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { meterApi } from '@/api/ledger'
import type { MeterFilter, MeterPayload } from '@/types/dto'
import { qk } from './keys'

export function useMeters(filter: MeterFilter = {}) {
  return useQuery({
    queryKey: qk.meters(filter),
    queryFn: () => meterApi.list(filter),
  })
}

export function useCreateMeter() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (payload: MeterPayload) => meterApi.create(payload),
    onSuccess: () => qc.invalidateQueries({ queryKey: qk.metersAll }),
  })
}

export function useUpdateMeter() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ id, payload }: { id: number; payload: MeterPayload }) =>
      meterApi.update(id, payload),
    onSuccess: () => qc.invalidateQueries({ queryKey: qk.metersAll }),
  })
}

export function useDeleteMeter() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (id: number) => meterApi.remove(id),
    onSuccess: () => qc.invalidateQueries({ queryKey: qk.metersAll }),
  })
}
