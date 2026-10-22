const STYLES: Record<string, string> = {
  ACTIVE: 'bg-ok/15 text-ok border-ok/40',
  PAUSED: 'bg-warn/15 text-warn border-warn/40',
  ISOLATED: 'bg-danger/15 text-danger border-danger/40',
}

export function StatusChip({ status }: { status: string }) {
  return (
    <span
      className={`inline-flex items-center rounded border px-1.5 py-0.5 text-[11px] font-semibold tracking-wide ${STYLES[status] ?? 'bg-panel-2 text-dim border-edge'}`}
    >
      {status}
    </span>
  )
}
