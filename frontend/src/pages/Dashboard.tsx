import { useCallback, useEffect, useRef, useState } from 'react'
import type { ReactNode } from 'react'
import { Link } from 'react-router-dom'
import * as api from '../api/client'
import type { DashboardSummary, FeedEvent } from '../api/types'
import { useAuth } from '../auth/AuthContext'
import { useSSE } from '../hooks/useSSE'
import { BarRow } from '../components/BarRow'
import { DecisionBadge } from '../components/DecisionBadge'
import { ErrorPanel } from '../components/ErrorPanel'
import { FeedRow } from '../components/FeedRow'
import { LoadingPanel } from '../components/LoadingPanel'
import { PageHeader } from '../components/PageHeader'
import { RiskGauge } from '../components/RiskGauge'
import { StatusChip } from '../components/StatusChip'

const KILL_ORDER = ['Recon', 'Collection', 'Exfiltration', 'Lateral Movement', 'Privilege Abuse', 'Tool Abuse']
const KILL_COLORS: Record<string, string> = {
  Recon: '#22c55e',
  Collection: '#22d3ee',
  Exfiltration: '#ef4444',
  'Lateral Movement': '#f97316',
  'Privilege Abuse': '#ef4444',
  'Tool Abuse': '#f59e0b',
}

const DECISION_ORDER: { key: string; color: string }[] = [
  { key: 'ALLOWED', color: '#22c55e' },
  { key: 'RESTRICTED', color: '#f59e0b' },
  { key: 'PENDING_APPROVAL', color: '#22d3ee' },
  { key: 'BLOCKED', color: '#ef4444' },
]

function StatCard({ label, value, badge }: { label: string; value: string | number; badge?: ReactNode }) {
  return (
    <div className="rounded-xl border border-edge bg-panel px-4 py-3 min-w-[140px] flex-1">
      <div className="text-[10px] uppercase tracking-widest text-faint mb-1">{label}</div>
      <div className="text-xl font-semibold text-bright font-mono">{value}</div>
      {badge && <div className="mt-1">{badge}</div>}
    </div>
  )
}

function ThreatBadge({ level }: { level: string }) {
  const cls =
    level === 'HIGH'
      ? 'bg-danger/15 text-danger border-danger/40'
      : level === 'ELEVATED'
        ? 'bg-warn/15 text-warn border-warn/40'
        : 'bg-ok/15 text-ok border-ok/40'
  return (
    <span className={`inline-flex rounded border px-1.5 py-0.5 text-[11px] font-semibold ${cls}`}>
      {level}
    </span>
  )
}

export function Dashboard() {
  const { hasPermission } = useAuth()
  const [summary, setSummary] = useState<DashboardSummary | null>(null)
  const [timeline, setTimeline] = useState<FeedEvent[]>([])
  const [error, setError] = useState<string | null>(null)
  const [loading, setLoading] = useState(true)
  const [reloadKey, setReloadKey] = useState(0)
  const debounceRef = useRef<ReturnType<typeof setTimeout> | undefined>(undefined)

  useEffect(() => {
    let cancelled = false
    api
      .dashboard()
      .then((s) => {
        if (cancelled) return
        setSummary(s)
        setTimeline(s.timeline || [])
        setError(null)
      })
      .catch((err) => {
        if (!cancelled) setError(err instanceof Error ? err.message : 'Failed to load dashboard')
      })
      .finally(() => {
        if (!cancelled) setLoading(false)
      })
    return () => {
      cancelled = true
      if (debounceRef.current) clearTimeout(debounceRef.current)
    }
  }, [reloadKey])

  const scheduleRefresh = useCallback(() => {
    if (debounceRef.current) clearTimeout(debounceRef.current)
    debounceRef.current = setTimeout(() => setReloadKey((k) => k + 1), 5000)
  }, [])

  useSSE(
    useCallback((name: string, data: unknown) => {
      if (name === 'feed' && data && typeof data === 'object') {
        const evt = (data as { event?: FeedEvent }).event
        if (evt) {
          setTimeline((prev) => [evt, ...prev].slice(0, 12))
        }
      }
      if (name === 'feed' || name === 'alert' || name === 'agent') {
        scheduleRefresh()
      }
    }, [scheduleRefresh]),
    hasPermission('events.read'),
  )

  if (loading && !summary) return <LoadingPanel label="Loading dashboard…" />
  if (error) return <ErrorPanel message={error} onRetry={() => setReloadKey((k) => k + 1)} />
  if (!summary) return null

  const decisionTotal = DECISION_ORDER.reduce((sum, d) => sum + (summary.decisions24h[d.key] ?? 0), 0)
  const killEntries = KILL_ORDER.filter((k) => (summary.killChain24h[k] ?? 0) > 0)
  const killMax = Math.max(1, ...killEntries.map((k) => summary.killChain24h[k] ?? 0))

  return (
    <div>
      <PageHeader title="Security Operations Dashboard" description={`Generated ${new Date(summary.generatedAt).toLocaleString()}`} />
      <div className="grid grid-cols-2 md:grid-cols-3 xl:grid-cols-6 gap-3 mb-6">
        <StatCard label="Threat Level" value="—" badge={<ThreatBadge level={summary.threatLevel} />} />
        <StatCard label="Open Incidents" value={summary.openIncidents} />
        <StatCard label="Pending Approvals" value={summary.alerts.pendingApprovals} />
        <StatCard label="Actions 24h" value={summary.traffic.requests24h} />
        <StatCard label="Avg Risk 24h" value={summary.traffic.avgRisk24h} />
        <StatCard
          label="Active Agents"
          value={`${summary.agents.active}/${summary.agents.registered}`}
        />
      </div>

      <div className="grid grid-cols-1 xl:grid-cols-3 gap-4 mb-6">
        <div className="rounded-xl border border-edge bg-panel p-4 xl:col-span-2">
          <div className="text-xs uppercase tracking-widest text-faint mb-3">Decisions (24h)</div>
          {decisionTotal === 0 ? (
            <p className="text-faint text-sm">No decisions recorded in the last 24 hours.</p>
          ) : (
            <>
              <div className="flex h-4 rounded overflow-hidden bg-edge mb-3">
                {DECISION_ORDER.map((d) => {
                  const v = summary.decisions24h[d.key] ?? 0
                  if (v === 0) return null
                  return (
                    <div
                      key={d.key}
                      style={{ width: `${(v / decisionTotal) * 100}%`, backgroundColor: d.color }}
                      title={`${d.key}: ${v}`}
                    />
                  )
                })}
              </div>
              <div className="flex flex-wrap gap-x-5 gap-y-2">
                {DECISION_ORDER.map((d) => (
                  <div key={d.key} className="flex items-center gap-2 text-sm">
                    <span className="h-2.5 w-2.5 rounded-sm" style={{ backgroundColor: d.color }} />
                    <span className="text-dim">{d.key.replace('_', ' ')}</span>
                    <span className="font-mono text-bright">{summary.decisions24h[d.key] ?? 0}</span>
                  </div>
                ))}
              </div>
            </>
          )}
        </div>
        <div className="rounded-xl border border-edge bg-panel p-4">
          <div className="text-xs uppercase tracking-widest text-faint mb-3">Kill Chain (24h)</div>
          {killEntries.length === 0 ? (
            <p className="text-faint text-sm">No kill-chain activity.</p>
          ) : (
            <div className="space-y-2">
              {killEntries.map((k) => (
                <BarRow key={k} label={k} value={summary.killChain24h[k] ?? 0} max={killMax} color={KILL_COLORS[k] ?? '#22d3ee'} />
              ))}
            </div>
          )}
        </div>
      </div>

      <div className="grid grid-cols-1 xl:grid-cols-2 gap-4">
        <div className="rounded-xl border border-edge bg-panel overflow-hidden">
          <div className="px-4 py-3 border-b border-edge text-xs uppercase tracking-widest text-faint">
            Risk Board — Top 6 Agents
          </div>
          <table className="w-full text-sm">
            <thead>
              <tr className="text-left text-[10px] uppercase tracking-wide text-faint border-b border-edge">
                <th className="px-4 py-2 font-normal">Agent</th>
                <th className="px-2 py-2 font-normal">Status</th>
                <th className="px-2 py-2 font-normal">Risk</th>
                <th className="px-2 py-2 font-normal">Integrity</th>
                <th className="px-4 py-2 font-normal text-right">Blocked</th>
              </tr>
            </thead>
            <tbody>
              {summary.riskBoard.map((row) => (
                <tr key={row.id} className="border-b border-edge last:border-b-0 hover:bg-panel-2/60">
                  <td className="px-4 py-2">
                    <Link to={`/agents/${row.id}`} className="flex items-center gap-2 text-bright hover:text-accent">
                      <span>{row.icon || '·'}</span>
                      <span className="truncate max-w-[140px]">{row.name}</span>
                    </Link>
                  </td>
                  <td className="px-2 py-2"><StatusChip status={row.status} /></td>
                  <td className="px-2 py-2">
                    <div className="flex items-center gap-2">
                      <RiskGauge value={row.lastRisk} width={56} />
                      <span className="font-mono text-xs text-dim">{row.lastRisk}</span>
                    </div>
                  </td>
                  <td className="px-2 py-2 font-mono text-xs text-dim">{row.integrity}%</td>
                  <td className="px-4 py-2 font-mono text-xs text-dim text-right">{row.blockedResources}</td>
                </tr>
              ))}
              {summary.riskBoard.length === 0 && (
                <tr>
                  <td colSpan={5} className="px-4 py-6 text-center text-faint text-sm">No agents registered.</td>
                </tr>
              )}
            </tbody>
          </table>
        </div>

        <div className="rounded-xl border border-edge bg-panel overflow-hidden">
          <div className="px-4 py-3 border-b border-edge flex items-center justify-between">
            <span className="text-xs uppercase tracking-widest text-faint">Recent Timeline</span>
            <Link to="/feed" className="text-xs text-accent hover:underline">Open feed →</Link>
          </div>
          <div className="max-h-[420px] overflow-y-auto">
            {timeline.length === 0 ? (
              <p className="px-4 py-8 text-center text-faint text-sm">No recent activity.</p>
            ) : (
              timeline.slice(0, 12).map((evt) => (
                <FeedRow key={evt.id ?? `${evt.ts}-${evt.agentId}-${evt.riskScore}`} event={evt} />
              ))
            )}
          </div>
        </div>
      </div>

      <div className="mt-4 flex items-center gap-3 text-xs text-faint">
        <span>Traffic: {summary.traffic.requestsPerMinute}/min · {summary.traffic.requestsPerHour}/hour</span>
        <span>·</span>
        <span>
          Isolated: {summary.agents.isolated} · Paused: {summary.agents.paused}
        </span>
        <span>·</span>
        <span>
          Decisions:{' '}
          {DECISION_ORDER.map((d) => (
            <DecisionBadge key={d.key} decision={d.key} />
          ))}
        </span>
      </div>
    </div>
  )
}
