import type { ReactNode } from 'react'

export function EmptyState({
  title,
  description,
  action,
}: {
  title: string
  description?: string
  action?: ReactNode
}) {
  return (
    <div className="rounded-xl border border-edge bg-panel px-6 py-12 text-center">
      <div className="text-bright font-semibold mb-1">{title}</div>
      {description && <p className="text-dim text-sm mb-3">{description}</p>}
      {action}
    </div>
  )
}
