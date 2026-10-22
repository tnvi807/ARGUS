import type { Factor } from '../api/types'

export function FactorList({ factors }: { factors: Factor[] }) {
  if (!factors || factors.length === 0) {
    return <p className="text-faint text-xs">No risk factors recorded.</p>
  }
  return (
    <ul className="space-y-1.5">
      {factors.map((f, i) => (
        <li key={`${f.name}-${i}`} className="flex items-baseline gap-2 text-xs">
          <span
            className={`font-mono font-semibold shrink-0 ${f.weight >= 0 ? 'text-warn-2' : 'text-ok'}`}
          >
            {f.weight >= 0 ? `+${f.weight}` : f.weight}
          </span>
          <span className="text-bright shrink-0">{f.name}</span>
          <span className="text-dim truncate" title={f.detail}>
            {f.detail}
          </span>
        </li>
      ))}
    </ul>
  )
}
