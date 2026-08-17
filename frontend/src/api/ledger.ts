import { client } from './client'
import type {
  Company,
  CompanyPayload,
  Meter,
  MeterFilter,
  MeterPayload,
} from '@/types/dto'

export const companyApi = {
  list: () => client.get<Company[]>('/companies').then((r) => r.data),
  create: (payload: CompanyPayload) =>
    client.post<Company>('/companies', payload).then((r) => r.data),
  update: (id: number, payload: CompanyPayload) =>
    client.put<Company>(`/companies/${id}`, payload).then((r) => r.data),
  remove: (id: number) => client.delete<void>(`/companies/${id}`).then((r) => r.data),
}

export const meterApi = {
  list: (filter: MeterFilter = {}) =>
    client
      .get<Meter[]>('/meters', {
        params: {
          companyId: filter.companyId,
          type: filter.type,
          status: filter.status,
        },
      })
      .then((r) => r.data),
  create: (payload: MeterPayload) =>
    client.post<Meter>('/meters', payload).then((r) => r.data),
  update: (id: number, payload: MeterPayload) =>
    client.put<Meter>(`/meters/${id}`, payload).then((r) => r.data),
  remove: (id: number) => client.delete<void>(`/meters/${id}`).then((r) => r.data),
}
