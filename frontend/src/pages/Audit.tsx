import { useCallback, useEffect, useState } from 'react'
import { Fragment } from 'react'
import * as api from '../api/client'
import { apiErrorMessage } from '../api/client'
import type { AuditPage, AuditView } from '../api/types'
import { useAuth } from '../auth/AuthContext'
import { DecisionBadge } from '../components/DecisionBadge'
import { ErrorPanel } from '../components/ErrorPanel'
import { FactorList } from '../components/FactorList'
import { LoadingPanel } from '../components/LoadingPanel'
import { PageHeader } from '../components/PageHeader'
import { SearchInput } from '../components/SearchInput'
import { useToast } from '../components/Toast'

export function Audit() {
  const { hasPermission } = useAuth()
  const toast = useToast()
  const [q, setQ] = useState('')
  const [page, setPage] = useState(0)
  const [data, setData] = useState<AuditPage | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [expanded, setExpanded] = useState<Set<number>>(new Set())
  const [reloadKey, setReloadKey] = useState(0)

  useEffect(() => {
    let cancelled = false
    api
      .audit(q || undefined, page, 25)
      .then((result) => {
        if (cancelled) return
        setData(result)
        setError(null)
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
  }, [q, page, reloadKey])

  const onSearch = useCallback((value: string) => {
    setPage(0)
    setQ(value)
  }, [])

  const exportFile = async (format: 'json' | 'csv') => {
    try {
      await api.exportAudit(format, q || undefined)
      toast.success(`Audit export (${format.toUpperCase()}) downloaded`)
    } catch (err) {
      toast.error(apiErrorMessage(err))
    }
  }

  const toggle = (id: number) => {
    setExpanded((prev) => {
      const next = new Set(prev)
      if (next.has(id)) next.delete(id)
      else next.add(id)
      return next
    })
  }

  return (
    <div>
      <PageHeader
        title="Audit Trail"
        description="Append-only compliance log of every gateway decision and administrative action"
        actions={
          <>
            {hasPermission('audit.export') && (
              <>
                <button
                  type="button"
                  onClick={() => void exportFile('json')}
                  className="rounded-md border border-edge px-3 py-1.5 text-sm text-dim hover:text-accent hover:border-accent/40"
                >
                  Export JSON
                </button>
                <button
                  type="button"
                  onClick={() => void exportFile('csv')}
                  className="rounded-md border border-edge px-3 py-1.5 text-sm text-dim hover:text-accent hover:border-accent/40"
                >
                  Export CSV
                </button>
              </>
            )}
            <SearchInput onSearch={onSearch} placeholder="Search audit…" />
          </>
        }
      />

      {loading && !data && <LoadingPanel label="Loading audit…" />}
      {error && <ErrorPanel message={error} onRetry={() => setReloadKey((k) => k + 1)} />}

      {data && (
        <>
          <div className="rounded-xl border border-edge bg-panel overflow-x-auto">
            <table className="w-full text-sm">
              <thead>
                <tr className="text-left text-[10px] uppercase tracking-wide text-faint border-b border-edge">
                  <th className="px-4 py-2 font-normal">Time</th>
                  <th className="px-2 py-2 font-normal">Actor</th>
                  <th className="px-2 py-2 font-normal">Agent</th>
                  <th className="px-2 py-2 font-normal">Kind</th>
                  <th className="px-2 py-2 font-normal">Description</th>
                  <th className="px-2 py-2 font-normal">Target</th>
                  <th className="px-2 py-2 font-normal">Decision</th>
                  <th className="px-2 py-2 font-normal">Score</th>
                  <th className="px-4 py-2 font-normal">Reason</th>
                </tr>
              </thead>
              <tbody>
                {data.items.map((row: AuditView) => (
                  <Fragment key={row.id}>
                    <tr
                      onClick={() => row.id != null && row.factors?.length && toggle(row.id)}
                      className={`border-b border-edge hover:bg-panel-2/60 ${row.factors?.length ? 'cursor-pointer' : ''}`}
                    >
                      <td className="px-4 py-2 font-mono text-xs text-faint whitespace-nowrap">
                        {new Date(row.ts).toLocaleString()}
                      </td>
                      <td className="px-2 py-2 text-dim">{row.actor ?? '—'}</td>
                      <td className="px-2 py-2 text-bright">{row.agent ?? '—'}</td>
                      <td className="px-2 py-2 font-mono text-xs text-dim">{row.kind}</td>
                      <td className="px-2 py-2 text-dim max-w-[280px] truncate" title={row.description}>
                        {row.description}
                      </td>
                      <td className="px-2 py-2 font-mono text-xs text-dim max-w-[140px] truncate" title={row.target ?? ''}>
                        {row.target ?? '—'}
                      </td>
                      <td className="px-2 py-2">
                        <DecisionBadge decision={row.decision} />
                      </td>
                      <td className="px-2 py-2 font-mono text-xs text-dim">{row.score ?? '—'}</td>
                      <td className="px-4 py-2 text-xs text-faint max-w-[200px] truncate" title={row.reason ?? ''}>
                        {row.reason ?? '—'}
                      </td>
                    </tr>
                    {row.id != null && expanded.has(row.id) && (
                      <tr className="border-b border-edge bg-panel-2/40">
                        <td colSpan={9} className="px-4 py-3">
                          <FactorList factors={row.factors || []} />
                        </td>
                      </tr>
                    )}
                  </Fragment>
                ))}
                {data.items.length === 0 && (
                  <tr>
                    <td colSpan={9} className="px-4 py-8 text-center text-faint text-sm">
                      No audit entries match the search.
                    </td>
                  </tr>
                )}
              </tbody>
            </table>
          </div>

          <div className="flex items-center justify-between mt-3 text-sm">
            <span className="text-faint">
              Page <span className="font-mono text-dim">{data.page + 1}</span> of{' '}
              <span className="font-mono text-dim">{Math.max(1, data.totalPages)}</span> ·{' '}
              <span className="font-mono text-dim">{data.totalElements}</span> entries
            </span>
            <div className="flex gap-2">
              <button
                type="button"
                onClick={() => setPage((p) => Math.max(0, p - 1))}
                disabled={data.page === 0 || loading}
                className="rounded-md border border-edge px-3 py-1 text-xs text-dim disabled:opacity-40 hover:text-bright"
              >
                Prev
              </button>
              <button
                type="button"
                onClick={() => setPage((p) => p + 1)}
                disabled={data.page >= data.totalPages - 1 || loading}
                className="rounded-md border border-edge px-3 py-1 text-xs text-dim disabled:opacity-40 hover:text-bright"
              >
                Next
              </button>
            </div>
          </div>
        </>
      )}
    </div>
  )
}
