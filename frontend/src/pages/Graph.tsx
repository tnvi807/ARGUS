import { useCallback, useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import * as api from '../api/client'
import { apiErrorMessage } from '../api/client'
import type { BlastRadius, GraphData, GraphNode } from '../api/types'
import { EmptyState } from '../components/EmptyState'
import { ErrorPanel } from '../components/ErrorPanel'
import { LoadingPanel } from '../components/LoadingPanel'
import { PageHeader } from '../components/PageHeader'
import { StatusChip } from '../components/StatusChip'
import { useToast } from '../components/Toast'

const SIZE = 640

function statusStroke(status: string): string {
  if (status === 'ACTIVE') return '#22c55e'
  if (status === 'PAUSED') return '#f59e0b'
  if (status === 'ISOLATED') return '#ef4444'
  return '#8aa0b2'
}

function nodeRadius(node: GraphNode): number {
  return 18 + Math.min(28, node.lastRisk * 0.28)
}

export function Graph() {
  const toast = useToast()
  const [data, setData] = useState<GraphData | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [selected, setSelected] = useState<GraphNode | null>(null)
  const [blastResult, setBlastResult] = useState<BlastRadius | null>(null)
  const [blastBusy, setBlastBusy] = useState(false)
  const [reloadKey, setReloadKey] = useState(0)

  useEffect(() => {
    let cancelled = false
    api
      .graph()
      .then((g) => {
        if (!cancelled) {
          setData(g)
          setError(null)
        }
      })
      .catch((err) => {
        if (!cancelled) setError(apiErrorMessage(err))
      })
      .finally(() => {
        if (!cancelled) setLoading(false)
      })
    return () => {
      cancelled = true
    }
  }, [reloadKey])

  const computeBlast = useCallback(async () => {
    if (!selected) return
    setBlastBusy(true)
    try {
      const result = await api.blast(selected.id)
      setBlastResult(result)
    } catch (err) {
      toast.error(apiErrorMessage(err))
    } finally {
      setBlastBusy(false)
    }
  }, [selected, toast])

  if (loading) return <LoadingPanel label="Loading trust graph…" />
  if (error) return <ErrorPanel message={error} onRetry={() => setReloadKey((k) => k + 1)} />
  if (!data) return null

  const n = data.nodes.length
  const cx = SIZE / 2
  const cy = SIZE / 2
  const radius = Math.min(cx, cy) - 70
  const positions = new Map<string, { x: number; y: number }>()
  data.nodes.forEach((node, i) => {
    const angle = (i / Math.max(1, n)) * Math.PI * 2 - Math.PI / 2
    positions.set(node.id, { x: cx + radius * Math.cos(angle), y: cy + radius * Math.sin(angle) })
  })

  const nameOf = (id: string) => data.nodes.find((x) => x.id === id)?.name ?? id

  return (
    <div>
      <PageHeader
        title="Trust Graph"
        description="Agent-to-agent communication, flagged chains and blast-radius prediction"
        actions={
          <button
            type="button"
            onClick={() => setReloadKey((k) => k + 1)}
            className="rounded-md border border-edge px-3 py-1.5 text-sm text-dim hover:text-bright"
          >
            Refresh
          </button>
        }
      />
      {n === 0 ? (
        <EmptyState title="No agents" description="The graph appears once agents are registered." />
      ) : (
        <div className="grid grid-cols-1 xl:grid-cols-3 gap-4">
          <div className="xl:col-span-2 rounded-xl border border-edge bg-panel p-3">
            <svg viewBox={`0 0 ${SIZE} ${SIZE}`} className="w-full" role="img" aria-label="Agent trust graph">
              {data.edges.map((e) => {
                const from = positions.get(e.from)
                const to = positions.get(e.to)
                if (!from || !to) return null
                return (
                  <line
                    key={`${e.from}->${e.to}`}
                    x1={from.x}
                    y1={from.y}
                    x2={to.x}
                    y2={to.y}
                    stroke={e.flagged ? '#ef4444' : '#2a3a4a'}
                    strokeWidth={Math.min(6, 0.6 + e.count * 0.5)}
                    opacity={e.flagged ? 0.9 : 0.6}
                  >
                    <title>{`${e.from} → ${e.to} (${e.count})\n${e.lastMessage}`}</title>
                  </line>
                )
              })}
              {data.nodes.map((node) => {
                const pos = positions.get(node.id)
                if (!pos) return null
                const r = nodeRadius(node)
                const isSelected = selected?.id === node.id
                return (
                  <g
                    key={node.id}
                    transform={`translate(${pos.x},${pos.y})`}
                    onClick={() => {
                      setSelected(node)
                      setBlastResult(null)
                    }}
                    style={{ cursor: 'pointer' }}
                  >
                    <circle
                      r={r}
                      fill={isSelected ? '#17212b' : '#131a21'}
                      stroke={statusStroke(node.status)}
                      strokeWidth={isSelected ? 3 : 1.5}
                    />
                    <text textAnchor="middle" y={-2} fontSize={Math.max(12, r * 0.6)}>
                      {node.icon || node.name.slice(0, 1)}
                    </text>
                    <text textAnchor="middle" y={r + 14} fontSize="11" fill="#8aa0b2">
                      {node.name}
                    </text>
                    <text textAnchor="middle" y={r + 27} fontSize="9" fill="#5d7284" fontFamily="monospace">
                      risk {node.lastRisk}
                    </text>
                    <title>{`${node.name} (${node.role})\nstatus ${node.status} · risk ${node.lastRisk} · integrity ${node.integrity}%`}</title>
                  </g>
                )
              })}
            </svg>
          </div>

          <div className="space-y-4">
            {selected && (
              <div className="rounded-xl border border-accent/40 bg-panel p-4 space-y-3">
                <div className="flex items-center justify-between gap-2">
                  <div className="flex items-center gap-2">
                    <span className="text-xl">{selected.icon || '·'}</span>
                    <div>
                      <div className="text-bright font-semibold">{selected.name}</div>
                      <div className="text-xs text-faint">{selected.role}</div>
                    </div>
                  </div>
                  <StatusChip status={selected.status} />
                </div>
                <div className="text-xs text-dim space-y-1">
                  <div>
                    Last risk <span className="font-mono text-bright">{selected.lastRisk}</span> · Integrity{' '}
                    <span className="font-mono text-bright">{selected.integrity}%</span>
                  </div>
                  <div>
                    Tools:{' '}
                    <span className="font-mono">{selected.tools.join(', ') || '—'}</span>
                  </div>
                  <div>
                    Blocked:{' '}
                    <span className="font-mono">{selected.blockedResources.join(', ') || '—'}</span>
                  </div>
                </div>
                <div className="flex gap-2">
                  <Link
                    to={`/agents/${selected.id}`}
                    className="rounded-md border border-edge px-2.5 py-1 text-xs text-dim hover:text-accent"
                  >
                    Open agent
                  </Link>
                  <button
                    type="button"
                    onClick={() => void computeBlast()}
                    disabled={blastBusy}
                    className="rounded-md border border-danger/50 bg-danger/10 px-2.5 py-1 text-xs text-danger font-semibold disabled:opacity-50"
                  >
                    {blastBusy ? 'Computing…' : 'Compute blast radius'}
                  </button>
                </div>
                {blastResult && (
                  <div className="border-t border-edge pt-3 space-y-2 text-sm">
                    <div className="text-xs uppercase tracking-wide text-faint">
                      At risk <span className="font-mono text-danger">{blastResult.atRisk}</span> agents
                    </div>
                    <div className="flex flex-wrap gap-1.5">
                      {blastResult.infected.map((id) => (
                        <span
                          key={id}
                          className={`rounded border px-1.5 py-0.5 text-[11px] font-mono ${
                            id === blastResult.start
                              ? 'border-danger/50 bg-danger/10 text-danger'
                              : 'border-warn/40 bg-warn/10 text-warn'
                          }`}
                        >
                          {nameOf(id)}
                        </span>
                      ))}
                    </div>
                    {blastResult.paths.length > 0 && (
                      <div>
                        <div className="text-xs uppercase tracking-wide text-faint mb-1">Paths</div>
                        <ul className="space-y-0.5 text-xs text-dim">
                          {blastResult.paths.map((p, i) => (
                            <li key={`${p.from}-${p.to}-${i}`} className="font-mono">
                              {nameOf(p.from)} → {nameOf(p.to)}{' '}
                              <span className="text-faint">d{p.depth}</span>
                              {p.tool && <span className="text-faint"> via {p.tool}</span>}
                            </li>
                          ))}
                        </ul>
                      </div>
                    )}
                    {blastResult.predictions.length > 0 && (
                      <div>
                        <div className="text-xs uppercase tracking-wide text-faint mb-1">Predictions</div>
                        <ul className="space-y-1 text-xs text-dim list-disc pl-4">
                          {blastResult.predictions.map((p, i) => (
                            <li key={i}>{p}</li>
                          ))}
                        </ul>
                      </div>
                    )}
                  </div>
                )}
              </div>
            )}

            <div className="rounded-xl border border-edge bg-panel p-4">
              <div className="text-xs uppercase tracking-widest text-faint mb-3">Suspicious chains</div>
              {data.suspiciousChains.length === 0 ? (
                <p className="text-faint text-sm">No flagged agent-to-agent chains.</p>
              ) : (
                <ul className="space-y-2">
                  {data.suspiciousChains.map((c, i) => (
                    <li key={i} className="rounded-md border border-danger/30 bg-danger/5 px-3 py-2 text-xs">
                      <div className="font-mono text-danger">
                        {nameOf(c.from)} → {nameOf(c.to)}
                      </div>
                      <div className="text-dim mt-0.5 break-words">{c.message}</div>
                      <div className="text-faint font-mono mt-0.5">{new Date(c.ts).toLocaleString()}</div>
                    </li>
                  ))}
                </ul>
              )}
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
