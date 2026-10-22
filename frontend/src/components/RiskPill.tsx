import { riskColor } from '../lib/risk'

export function RiskPill({ score }: { score: number }) {
  const color = riskColor(score)
  return (
    <span
      className="font-mono text-[11px] font-semibold rounded px-1.5 py-0.5 border"
      style={{ color, borderColor: `${color}66`, backgroundColor: `${color}1f` }}
    >
      {score}
    </span>
  )
}
