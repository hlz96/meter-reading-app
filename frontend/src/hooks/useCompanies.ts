import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { companyApi } from '@/api/ledger'
import type { Company, CompanyPayload } from '@/types/dto'
import { qk } from './keys'

export function useCompanies() {
  return useQuery({ queryKey: qk.companies, queryFn: companyApi.list })
}

/** 便捷:companies 的 id→name map(列表/汇总显示公司名)。 */
export function useCompanyMap() {
  const q = useCompanies()
  const map = new Map<number, string>()
  q.data?.forEach((c: Company) => map.set(c.id, c.name))
  return { map, ...q }
}

export function useCreateCompany() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (payload: CompanyPayload) => companyApi.create(payload),
    onSuccess: () => qc.invalidateQueries({ queryKey: qk.companies }),
  })
}

export function useUpdateCompany() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ id, payload }: { id: number; payload: CompanyPayload }) =>
      companyApi.update(id, payload),
    onSuccess: () => qc.invalidateQueries({ queryKey: qk.companies }),
  })
}

export function useDeleteCompany() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (id: number) => companyApi.remove(id),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: qk.companies })
      qc.invalidateQueries({ queryKey: qk.metersAll })
    },
  })
}
