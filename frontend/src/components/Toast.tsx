import { createContext, useCallback, useContext, useMemo, useRef, useState } from 'react'
import type { ReactNode } from 'react'

export type ToastKind = 'success' | 'error' | 'info'

export interface ToastItem {
  id: number
  kind: ToastKind
  message: string
}

interface ToastApi {
  push: (kind: ToastKind, message: string) => void
  success: (message: string) => void
  error: (message: string) => void
  info: (message: string) => void
}

const ToastContext = createContext<ToastApi | null>(null)

const KIND_STYLES: Record<ToastKind, string> = {
  success: 'border-ok/50 text-ok',
  error: 'border-danger/50 text-danger',
  info: 'border-accent/50 text-accent',
}

export function ToastProvider({ children }: { children: ReactNode }) {
  const [items, setItems] = useState<ToastItem[]>([])
  const nextId = useRef(1)

  const remove = useCallback((id: number) => {
    setItems((prev) => prev.filter((t) => t.id !== id))
  }, [])

  const push = useCallback((kind: ToastKind, message: string) => {
    const id = nextId.current++
    setItems((prev) => [...prev.slice(-4), { id, kind, message }])
    setTimeout(() => remove(id), 5000)
  }, [remove])

  const api = useMemo<ToastApi>(
    () => ({
      push,
      success: (m: string) => push('success', m),
      error: (m: string) => push('error', m),
      info: (m: string) => push('info', m),
    }),
    [push],
  )

  return (
    <ToastContext.Provider value={api}>
      {children}
      <div className="fixed bottom-4 right-4 z-[60] flex flex-col gap-2 w-80 max-w-[calc(100vw-2rem)]">
        {items.map((t) => (
          <div
            key={t.id}
            className={`rounded-lg border bg-panel px-4 py-3 text-sm shadow-xl flex items-start gap-2 ${KIND_STYLES[t.kind]}`}
            role="status"
          >
            <span className="flex-1 text-bright font-medium break-words">{t.message}</span>
            <button
              type="button"
              onClick={() => remove(t.id)}
              aria-label="Dismiss"
              className="text-faint hover:text-bright leading-none"
            >
              ×
            </button>
          </div>
        ))}
      </div>
    </ToastContext.Provider>
  )
}

export function useToast(): ToastApi {
  const ctx = useContext(ToastContext)
  if (!ctx) {
    throw new Error('useToast must be used within ToastProvider')
  }
  return ctx
}
