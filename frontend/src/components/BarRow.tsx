export function BarRow({
  label,
  value,
  max,
  color = '#22d3ee',
}: {
  label: string
  value: number
  max: number
  color?: string
}) {
  const pct = max > 0 ? Math.min(100, (value / max) * 100) : 0
  return (
    <div className="flex items-center gap-3 text-sm">
      <span className="w-36 shrink-0 text-dim truncate" title={label}>
        {label}
      </span>
      <div className="flex-1 h-2.5 rounded-full bg-edge overflow-hidden">
        <div className="h-full rounded-full" style={{ width: `${pct}%`, backgroundColor: color }} />
      </div>
      <span className="font-mono text-xs text-bright w-8 text-right">{value}</span>
    </div>
  )
}
