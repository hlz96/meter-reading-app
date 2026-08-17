import { client } from './client'
import type {
  BatchResult,
  Period,
  PeriodPayload,
  Reading,
  ReadingFilter,
  ReadingPayload,
  TaskOverview,
} from '@/types/dto'

export const periodApi = {
  list: () => client.get<Period[]>('/periods').then((r) => r.data),
  tasks: (id: number) =>
    client.get<TaskOverview>(`/periods/${id}/tasks`).then((r) => r.data),
  create: (payload: PeriodPayload) =>
    client.post<Period>('/periods', payload).then((r) => r.data),
  update: (id: number, payload: PeriodPayload) =>
    client.put<Period>(`/periods/${id}`, payload).then((r) => r.data),
  settle: (id: number) =>
    client.post<Period>(`/periods/${id}/settle`).then((r) => r.data),
}

export const readingApi = {
  list: (filter: ReadingFilter) =>
    client
      .get<Reading[]>('/readings', {
        params: {
          periodId: filter.periodId,
          auditStatus: filter.auditStatus,
          companyId: filter.companyId,
        },
      })
      .then((r) => r.data),

  submit: (payload: ReadingPayload) =>
    client.post<Reading>('/readings', payload).then((r) => r.data),

  submitBatch: (items: ReadingPayload[]) =>
    client
      .post<BatchResult>('/readings/batch', { items })
      .then((r) => r.data),

  update: (id: number, payload: ReadingPayload) =>
    client.put<Reading>(`/readings/${id}`, payload).then((r) => r.data),

  audit: (id: number, approved: boolean, remark?: string) =>
    client
      .post<Reading>(`/readings/${id}/audit`, { approved, remark })
      .then((r) => r.data),

  auditBatch: (ids: number[], approved: boolean, remark?: string) =>
    client
      .post<{ processed: number }>('/readings/audit/batch', { ids, approved, remark })
      .then((r) => r.data),
}
