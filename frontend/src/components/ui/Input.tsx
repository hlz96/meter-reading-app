import { clsx } from 'clsx'
import { forwardRef, type InputHTMLAttributes, type SelectHTMLAttributes } from 'react'

interface FieldProps {
  label?: string
  error?: string
  hint?: string
}

interface InputProps extends InputHTMLAttributes<HTMLInputElement>, FieldProps {}

export const Input = forwardRef<HTMLInputElement, InputProps>(function Input(
  { label, error, hint, className, ...rest },
  ref,
) {
  return (
    <label className="block">
      {label && <span className="mb-1 block text-sm font-medium text-gray-700">{label}</span>}
      <input
        ref={ref}
        className={clsx(
          'h-11 w-full rounded-lg border px-3 text-base outline-none',
          'focus:border-brand focus:ring-1 focus:ring-brand',
          error ? 'border-red-500' : 'border-gray-300',
          className,
        )}
        {...rest}
      />
      {error ? (
        <span className="mt-1 block text-sm text-red-600">{error}</span>
      ) : hint ? (
        <span className="mt-1 block text-sm text-gray-500">{hint}</span>
      ) : null}
    </label>
  )
})

interface SelectProps extends SelectHTMLAttributes<HTMLSelectElement>, FieldProps {
  options: { value: string | number; label: string }[]
  placeholder?: string
}

export const Select = forwardRef<HTMLSelectElement, SelectProps>(function Select(
  { label, error, hint, options, placeholder, className, ...rest },
  ref,
) {
  return (
    <label className="block">
      {label && <span className="mb-1 block text-sm font-medium text-gray-700">{label}</span>}
      <select
        ref={ref}
        className={clsx(
          'h-11 w-full rounded-lg border bg-white px-3 text-base outline-none',
          'focus:border-brand focus:ring-1 focus:ring-brand',
          error ? 'border-red-500' : 'border-gray-300',
          className,
        )}
        {...rest}
      >
        {placeholder !== undefined && <option value="">{placeholder}</option>}
        {options.map((o) => (
          <option key={o.value} value={o.value}>
            {o.label}
          </option>
        ))}
      </select>
      {error && <span className="mt-1 block text-sm text-red-600">{error}</span>}
    </label>
  )
})
