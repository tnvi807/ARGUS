import { useEffect, useRef, useState } from 'react'

export function SearchInput({
  onSearch,
  placeholder = 'Search…',
  debounceMs = 300,
}: {
  onSearch: (q: string) => void
  placeholder?: string
  debounceMs?: number
}) {
  const [value, setValue] = useState('')
  const onSearchRef = useRef(onSearch)
  useEffect(() => {
    onSearchRef.current = onSearch
  }, [onSearch])

  useEffect(() => {
    const t = setTimeout(() => onSearchRef.current(value.trim()), debounceMs)
    return () => clearTimeout(t)
  }, [value, debounceMs])

  return (
    <div className="relative">
      <svg
        className="absolute left-2.5 top-1/2 -translate-y-1/2 h-3.5 w-3.5 text-faint pointer-events-none"
        viewBox="0 0 24 24"
        fill="none"
        stroke="currentColor"
        strokeWidth="2"
        aria-hidden="true"
      >
        <circle cx="11" cy="11" r="7" />
        <path d="M20 20l-3.5-3.5" />
      </svg>
      <input
        type="search"
        value={value}
        onChange={(e) => setValue(e.target.value)}
        placeholder={placeholder}
        className="w-56 rounded-md border border-edge bg-panel pl-8 pr-3 py-1.5 text-sm text-bright placeholder:text-faint focus:outline-none focus:border-accent/60"
      />
    </div>
  )
}
