import { useCallback, useEffect, useState } from 'react'
import * as api from '../api/client'
import type { FeedEvent } from '../api/types'
import { useAuth } from '../auth/AuthContext'
import { useSSE } from '../hooks/useSSE'
import { EmptyState } from '../components/EmptyState'
import { ErrorPanel } from '../components/ErrorPanel'
import { FeedRow } from '../components/FeedRow'
import { LoadingPanel } from '../components/LoadingPanel'
import { PageHeader } from '../components/PageHeader'
import { SearchInput } from '../components/SearchInput'

const DECISIONS = ['ALL', 'ALLOWED', 'RESTRICTED', 'PENDING_APPROVAL', 'BLOCKED'] as const

export function Feed() {
  const { hasPermission } = useAuth()
  const [decision, setDecision] = useState<string>('ALL')
  const [q, setQ] = useState('')
  const [events, setEvents] = useState<FeedEvent[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [freshIds, setFreshIds] = useState<Set<number>>(new Set())
  const [reloadKey, setReloadKey] = useState(0)

  useEffect(() => {
    let cancelled = false
    api
      .feed(decision === 'ALL' ? undefined : decision, q || undefined, 120)
      .then((data) => {
        if (cancelled) return
        setEvents(data)
        setError(null)
      })
      .catch((err) => {
        if (!cancelled) setError(err instanceof Error ? err.message : 'Failed to load feed')
      })
      .finally(() => {
        if (!cancelled) setLoading(false)
      })
    return () => {
      cancelled = true
    }
  }, [decision, q, reloadKey])

  useSSE(
    useCallback((name: string, data: unknown) => {
      if (name !== 'feed' || !data || typeof data !== 'object') return
      const evt = (data as { event?: FeedEvent }).event
      if (!evt) return
      setEvents((prev) => {
        if (prev.some((e) => e.id != null && e.id === evt.id)) return prev
        return [evt, ...prev].slice(0, 200)
      })
      if (evt.id != null) {
        const id = evt.id
        setFreshIds((prev) => new Set(prev).add(id))
        setTimeout(() => {
          setFreshIds((prev) => {
            const next = new Set(prev)
            next.delete(id)
            return next
          })
        }, 2500)
      }
    }, []),
    hasPermission('events.read'),
  )

  return (
    <div>
      <PageHeader
        title="Live Feed"
        description="Real-time agent traffic evaluated by the gateway"
        actions={
          <div className="flex items-center gap-2">
            <select
              value={decision}
              onChange={(e) => setDecision(e.target.value)}
              className="rounded-md border border-edge bg-panel px-2.5 py-1.5 text-sm text-bright focus:outline-none focus:border-accent/60"
            >
              {DECISIONS.map((d) => (
                <option key={d} value={d}>
                  {d.replace('_', ' ')}
                </option>
              ))}
            </select>
            <SearchInput onSearch={setQ} placeholder="Search feed…" />
          </div>
        }
      />
      {loading && events.length === 0 && <LoadingPanel label="Loading feed…" />}
      {error && <ErrorPanel message={error} onRetry={() => setReloadKey((k) => k + 1)} />}
      {!loading && !error && events.length === 0 && (
        <EmptyState title="No events" description="No traffic matches the current filters." />
      )}
      {events.length > 0 && (
        <div className="rounded-xl border border-edge bg-panel overflow-hidden">
          {events.map((evt) => (
            <FeedRow
              key={evt.id ?? `${evt.ts}-${evt.agentId}-${evt.riskScore}`}
              event={evt}
              highlight={evt.id != null && freshIds.has(evt.id)}
            />
          ))}
        </div>
      )}
    </div>
  )
}
