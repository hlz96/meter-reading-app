import { clsx } from 'clsx'
import type { ReactNode } from 'react'

type Tone = 'gray' | 'blue' | 'green' | 'red' | 'amber'

const TONE: Record<Tone, string> = {
  gray: 'bg-gray-100 text-gray-700',
  blue: 'bg-blue-100 text-blue-700',
  green: 'bg-green-100 text-green-700',
  red: 'bg-red-100 text-red-700',
  amber: 'bg-amber-100 text-amber-800',
}

export function Badge({ tone = 'gray', children }: { tone?: Tone; children: ReactNode }) {
  return (
    <span
      className={clsx(
        'inline-flex items-center rounded-full px-2 py-0.5 text-xs font-medium',
        TONE[tone],
      )}
    >
      {children}
    </span>
  )
}

/** 审核状态徽标(1待审 2通过 3驳回)。 */
export function AuditBadge({ status }: { status: number | null | undefined }) {
  if (status == null) return null
  const map: Record<number, { tone: Tone; label: string }> = {
    1: { tone: 'amber', label: '待审核' },
    2: { tone: 'green', label: '已通过' },
    3: { tone: 'red', label: '已驳回' },
  }
  const it = map[status]
  if (!it) return null
  return <Badge tone={it.tone}>{it.label}</Badge>
}
