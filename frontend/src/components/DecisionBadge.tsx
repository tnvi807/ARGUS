const STYLES: Record<string, { label: string; className: string }> = {
  ALLOWED: { label: 'ALLOWED', className: 'bg-ok/15 text-ok border-ok/40' },
  RESTRICTED: { label: 'RESTRICTED', className: 'bg-warn/15 text-warn border-warn/40' },
  PENDING_APPROVAL: { label: 'PENDING', className: 'bg-accent/15 text-accent border-accent/40' },
  BLOCKED: { label: 'BLOCKED', className: 'bg-danger/15 text-danger border-danger/40' },
}

export function DecisionBadge({ decision }: { decision: string }) {
  const style = STYLES[decision] ?? {
    label: decision || '—',
    className: 'bg-panel-2 text-dim border-edge',
  }
  return (
    <span
      className={`inline-flex items-center rounded border px-1.5 py-0.5 text-[11px] font-semibold tracking-wide ${style.className}`}
    >
      {style.label}
    </span>
  )
}
