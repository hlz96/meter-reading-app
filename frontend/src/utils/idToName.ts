import type { Company } from '@/types/dto'

/** id→name 映射(前端 TRD §6:列表/汇总用 companies map 显示公司名)。 */
export function buildIdNameMap(companies: Company[] | undefined): Map<number, string> {
  const map = new Map<number, string>()
  companies?.forEach((c) => map.set(c.id, c.name))
  return map
}

/** 取公司名,缺失回退为「公司#id」。 */
export function companyName(map: Map<number, string>, id: number): string {
  return map.get(id) || `公司#${id}`
}
