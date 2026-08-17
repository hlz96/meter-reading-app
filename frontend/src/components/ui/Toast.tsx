import { useEffect } from 'react'
import { create } from 'zustand'
import { clsx } from 'clsx'

type ToastKind = 'success' | 'error' | 'info'

interface ToastItem {
  id: number
  kind: ToastKind
  message: string
}

interface ToastState {
  toasts: ToastItem[]
  push: (kind: ToastKind, message: string) => void
  remove: (id: number) => void
}

let seq = 1

const useToastStore = create<ToastState>((set) => ({
  toasts: [],
  push: (kind, message) =>
    set((s) => ({ toasts: [...s.toasts, { id: seq++, kind, message }] })),
  remove: (id) => set((s) => ({ toasts: s.toasts.filter((t) => t.id !== id) })),
}))

/** 命令式 toast API,供任意模块(含非组件)调用。 */
export const toast = {
  success: (m: string) => useToastStore.getState().push('success', m),
  error: (m: string) => useToastStore.getState().push('error', m),
  info: (m: string) => useToastStore.getState().push('info', m),
}

const KIND_STYLE: Record<ToastKind, string> = {
  success: 'bg-green-600',
  error: 'bg-red-600',
  info: 'bg-gray-800',
}

function ToastView({ item }: { item: ToastItem }) {
  const remove = useToastStore((s) => s.remove)
  useEffect(() => {
    const t = setTimeout(() => remove(item.id), 3000)
    return () => clearTimeout(t)
  }, [item.id, remove])

  return (
    <div
      className={clsx(
        'pointer-events-auto rounded-lg px-4 py-2 text-sm text-white shadow-lg',
        KIND_STYLE[item.kind],
      )}
      role="alert"
      onClick={() => remove(item.id)}
    >
      {item.message}
    </div>
  )
}

/** 挂在根组件的 toast 容器。 */
export function ToastHost() {
  const toasts = useToastStore((s) => s.toasts)
  return (
    <div className="pointer-events-none fixed inset-x-0 top-4 z-50 flex flex-col items-center gap-2 px-4">
      {toasts.map((t) => (
        <ToastView key={t.id} item={t} />
      ))}
    </div>
  )
}
