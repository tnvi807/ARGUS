export function riskColor(score: number): string {
  if (score < 40) return '#22c55e'
  if (score < 60) return '#f59e0b'
  if (score < 70) return '#f97316'
  return '#ef4444'
}
