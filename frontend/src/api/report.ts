import { client } from './client'
import type { Dunning, Summary } from '@/types/dto'

export const reportApi = {
  summary: (periodId: number) =>
    client
      .get<Summary>('/reports/summary', { params: { periodId } })
      .then((r) => r.data),

  dunning: (periodId: number) =>
    client.get<Dunning>(`/dunning/${periodId}`).then((r) => r.data),
}
