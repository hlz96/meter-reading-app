import { useNavigate } from 'react-router-dom'

interface TopBarProps {
  title: string
  back?: boolean
  right?: React.ReactNode
}

export function TopBar({ title, back, right }: TopBarProps) {
  const navigate = useNavigate()
  return (
    <header className="sticky top-0 z-20 flex h-12 items-center border-b border-gray-200 bg-white px-3">
      {back ? (
        <button
          className="mr-1 -ml-1 flex h-9 w-9 items-center justify-center text-gray-600"
          onClick={() => navigate(-1)}
          aria-label="返回"
        >
          <span className="text-xl">‹</span>
        </button>
      ) : (
        <span className="w-2" />
      )}
      <h1 className="flex-1 truncate text-center text-base font-semibold text-gray-900">
        {title}
      </h1>
      <div className="flex min-w-[2rem] items-center justify-end">{right}</div>
    </header>
  )
}
