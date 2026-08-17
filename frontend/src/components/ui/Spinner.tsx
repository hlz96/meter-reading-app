import { clsx } from 'clsx'

export function Spinner({ className }: { className?: string }) {
  return (
    <span
      className={clsx(
        'inline-block animate-spin rounded-full border-2 border-brand border-t-transparent',
        className || 'h-6 w-6',
      )}
      role="status"
      aria-label="加载中"
    />
  )
}

/** 页面级 loading 占位。 */
export function LoadingState({ text = '加载中…' }: { text?: string }) {
  return (
    <div className="flex flex-col items-center justify-center gap-3 py-16 text-gray-500">
      <Spinner className="h-8 w-8" />
      <span className="text-sm">{text}</span>
    </div>
  )
}
