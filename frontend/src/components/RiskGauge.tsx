import { riskColor } from '../lib/risk'

export function RiskGauge({
  value,
  width = 96,
  height = 6,
}: {
  value: number
  width?: number
  height?: number
}) {
  const clamped = Math.max(0, Math.min(100, value))
  return (
    <div
      className="rounded-full bg-edge overflow-hidden"
      style={{ width, height }}
      title={`Risk ${clamped}/100`}
    >
      <div className="h-full rounded-full" style={{ width: `${clamped}%`, backgroundColor: riskColor(clamped) }} />
    </div>
  )
}
