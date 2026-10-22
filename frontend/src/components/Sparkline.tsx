export interface ChartPoint {
  label: string
  sens: number
}

function scalePoints(points: ChartPoint[], width: number, height: number, pad: number) {
  const n = points.length
  if (n === 0) return []
  const maxSens = Math.max(10, ...points.map((p) => p.sens))
  const innerW = width - pad * 2
  const innerH = height - pad * 2
  return points.map((p, i) => ({
    x: pad + (n === 1 ? innerW / 2 : (i / (n - 1)) * innerW),
    y: pad + innerH - (p.sens / maxSens) * innerH,
  }))
}

export function Sparkline({
  points,
  baseline,
  height = 28,
  width = 120,
}: {
  points: ChartPoint[]
  baseline?: number
  height?: number
  width?: number
}) {
  if (points.length === 0) {
    return <div className="text-faint text-xs" style={{ height }} />
  }
  const coords = scalePoints(points, width, height, 2)
  const path = coords.map((c, i) => `${i === 0 ? 'M' : 'L'}${c.x.toFixed(1)},${c.y.toFixed(1)}`).join(' ')
  const maxSens = Math.max(10, ...points.map((p) => p.sens))
  const baseY = baseline !== undefined ? 2 + (height - 4) - (baseline / maxSens) * (height - 4) : null
  return (
    <svg width={width} height={height} className="block" aria-hidden="true">
      {baseY !== null && (
        <line x1={2} x2={width - 2} y1={baseY} y2={baseY} stroke="#22d3ee" strokeWidth="1" strokeDasharray="3 3" opacity="0.6" />
      )}
      <path d={path} fill="none" stroke="#22d3ee" strokeWidth="1.5" strokeLinejoin="round" />
      {coords.length > 0 && (
        <circle cx={coords[coords.length - 1].x} cy={coords[coords.length - 1].y} r="2" fill="#22d3ee" />
      )}
    </svg>
  )
}

export function LineChart({
  points,
  baseline,
  height = 180,
  width = 560,
  maxSens = 10,
}: {
  points: ChartPoint[]
  baseline?: number
  height?: number
  width?: number
  maxSens?: number
}) {
  const padLeft = 34
  const padRight = 12
  const padTop = 12
  const padBottom = 22
  const n = points.length
  const effMax = Math.max(maxSens, ...points.map((p) => p.sens), baseline ?? 0)
  const innerW = width - padLeft - padRight
  const innerH = height - padTop - padBottom
  const xOf = (i: number) => padLeft + (n <= 1 ? innerW / 2 : (i / (n - 1)) * innerW)
  const yOf = (v: number) => padTop + innerH - (v / effMax) * innerH

  if (n === 0) {
    return <div className="text-faint text-sm py-8 text-center">No drift points recorded yet.</div>
  }

  const path = points.map((p, i) => `${i === 0 ? 'M' : 'L'}${xOf(i).toFixed(1)},${yOf(p.sens).toFixed(1)}`).join(' ')
  const ticks = [0, effMax / 2, effMax]
  const labelEvery = Math.max(1, Math.ceil(n / 8))

  return (
    <svg viewBox={`0 0 ${width} ${height}`} className="w-full" style={{ maxHeight: height + 40 }} role="img" aria-label="Sensitivity drift chart">
      {ticks.map((t) => (
        <g key={t}>
          <line x1={padLeft} x2={width - padRight} y1={yOf(t)} y2={yOf(t)} stroke="#1c2630" strokeWidth="1" />
          <text x={padLeft - 6} y={yOf(t) + 3} textAnchor="end" fontSize="9" fill="#5d7284" fontFamily="monospace">
            {t.toFixed(1)}
          </text>
        </g>
      ))}
      {baseline !== undefined && (
        <g>
          <line x1={padLeft} x2={width - padRight} y1={yOf(baseline)} y2={yOf(baseline)} stroke="#22d3ee" strokeWidth="1" strokeDasharray="4 3" />
          <text x={width - padRight} y={yOf(baseline) - 4} textAnchor="end" fontSize="9" fill="#22d3ee">
            baseline {baseline.toFixed(2)}
          </text>
        </g>
      )}
      <path d={path} fill="none" stroke="#22d3ee" strokeWidth="1.8" strokeLinejoin="round" />
      {points.map((p, i) => (
        <circle key={p.label} cx={xOf(i)} cy={yOf(p.sens)} r="2.5" fill="#0b0f14" stroke="#22d3ee" strokeWidth="1.5">
          <title>{`${p.label}: ${p.sens}`}</title>
        </circle>
      ))}
      {points.map((p, i) =>
        i % labelEvery === 0 || i === n - 1 ? (
          <text key={`lbl-${p.label}`} x={xOf(i)} y={height - 6} textAnchor="middle" fontSize="9" fill="#5d7284">
            {p.label}
          </text>
        ) : null,
      )}
    </svg>
  )
}
