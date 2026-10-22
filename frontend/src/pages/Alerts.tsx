import { useCallback, useEffect, useState } from 'react'
import * as api from '../api/client'
import { apiErrorMessage } from '../api/client'
import type { AlertView, ContainmentAction } from '../api/types'
import { useAuth } from '../auth/AuthContext'
import { useSSE } from '../hooks/useSSE'
import { EmptyState } from '../components/EmptyState'
import { ErrorPanel } from '../components/ErrorPanel'
import { FactorList } from '../components/FactorList'
import { LoadingPanel } from '../components/LoadingPanel'
import { Modal } from '../components/Modal'
import { PageHeader } from '../components/PageHeader'
import { RiskPill } from '../components/RiskPill'
import { useToast } from '../components/Toast'

const KIND_COLORS: Record<string, string> = {
  BLOCK_RESOURCE: 'border-danger/40 bg-danger/10 text-danger',
  PAUSE_AGENT: 'border-warn/40 bg-warn/10 text-warn',
  RESUME_AGENT: 'border-ok/40 bg-ok/10 text-ok',
  ISOLATE_AGENT: 'border-danger/40 bg-danger/10 text-danger',
  RESTORE_AGENT: 'border-accent/40 bg-accent/10 text-accent',
  REVOKE_TOOL: 'border-warn-2/40 bg-warn-2/10 text-warn-2',
}

function IncidentCard({
  alert,
  canAct,
  onAction,
}: {
  alert: AlertView
  canAct: boolean
  onAction: (id: number, action: string, value?: string) => Promise<void>
}) {
  const [valuePrompt, setValuePrompt] = useState<null | 'BLOCK_RESOURCE' | 'REVOKE_TOOL'>(null)
  const [value, setValue] = useState('')
  const [busy, setBusy] = useState(false)

  const run = async (action: string, v?: string) => {
    setBusy(true)
    try {
      await onAction(alert.id!, action, v)
      setValuePrompt(null)
    } catch {
      // onAction already surfaced the error as a toast
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="rounded-xl border border-edge bg-panel p-4 space-y-2">
      <div className="flex flex-wrap items-center gap-x-3 gap-y-1">
        <RiskPill score={alert.riskScore} />
        <span className="text-bright font-semibold">
          {alert.agentIcon && <span className="mr-1">{alert.agentIcon}</span>}
          {alert.agentName || alert.agentId}
        </span>
        <span className="rounded border border-edge bg-panel-2 px-1.5 py-0.5 text-[10px] uppercase text-dim">
          {alert.kind}
        </span>
        {alert.tool && (
          <span className="rounded border border-edge bg-panel-2 px-1.5 py-0.5 text-[10px] font-mono text-dim">
            {alert.tool}
          </span>
        )}
        {alert.category && (
          <span className="rounded border border-edge bg-panel-2 px-1.5 py-0.5 text-[10px] uppercase text-dim">
            {alert.category}
          </span>
        )}
        <span className="flex-1" />
        <span className="font-mono text-[11px] text-faint">{new Date(alert.ts).toLocaleString()}</span>
      </div>
      {alert.target && <div className="font-mono text-xs text-dim break-all">{alert.target}</div>}
      {alert.message && <div className="text-xs text-dim break-words">{alert.message}</div>}
      <div className="text-xs text-faint">{alert.reason}</div>
      {alert.factors?.length > 0 && (
        <div className="border-t border-edge pt-2">
          <FactorList factors={alert.factors} />
        </div>
      )}
      {alert.containment && (
        <div className="rounded-md border border-warn/30 bg-warn/5 px-3 py-2 text-xs text-warn">
          Containment: {alert.containment}
        </div>
      )}
      {canAct && (
        <div className="flex flex-wrap gap-2 border-t border-edge pt-3">
          <button
            type="button"
            onClick={() => void run('MARK_REVIEWED')}
            disabled={busy}
            className="rounded-md border border-edge px-2.5 py-1 text-xs text-dim hover:text-ok hover:border-ok/40 disabled:opacity-40"
          >
            Mark reviewed
          </button>
          <button
            type="button"
            onClick={() => void run('PAUSE_AGENT')}
            disabled={busy}
            className="rounded-md border border-edge px-2.5 py-1 text-xs text-dim hover:text-warn hover:border-warn/40 disabled:opacity-40"
          >
            Pause agent
          </button>
          <button
            type="button"
            onClick={() => void run('RESUME_AGENT')}
            disabled={busy}
            className="rounded-md border border-edge px-2.5 py-1 text-xs text-dim hover:text-ok hover:border-ok/40 disabled:opacity-40"
          >
            Resume agent
          </button>
          <button
            type="button"
            onClick={() => {
              setValue(alert.category ?? '')
              setValuePrompt('BLOCK_RESOURCE')
            }}
            disabled={busy}
            className="rounded-md border border-edge px-2.5 py-1 text-xs text-dim hover:text-danger hover:border-danger/40 disabled:opacity-40"
          >
            Block resource
          </button>
          <button
            type="button"
            onClick={() => {
              setValue(alert.tool ?? '')
              setValuePrompt('REVOKE_TOOL')
            }}
            disabled={busy || !alert.tool}
            className="rounded-md border border-edge px-2.5 py-1 text-xs text-dim hover:text-warn-2 hover:border-warn-2/40 disabled:opacity-40"
          >
            Revoke tool
          </button>
        </div>
      )}
      <Modal
        open={valuePrompt !== null}
        onClose={() => setValuePrompt(null)}
        title={valuePrompt === 'BLOCK_RESOURCE' ? 'Block resource' : 'Revoke tool'}
      >
        <label className="block text-xs uppercase tracking-wide text-faint mb-1">
          {valuePrompt === 'BLOCK_RESOURCE' ? 'Data category code' : 'Tool name'}
        </label>
        <input
          value={value}
          onChange={(e) => setValue(e.target.value)}
          className="w-full rounded-md border border-edge bg-base px-3 py-2 text-sm font-mono text-bright mb-4"
        />
        <div className="flex justify-end gap-2">
          <button type="button" onClick={() => setValuePrompt(null)} className="rounded-md border border-edge px-3 py-1.5 text-sm text-dim">
            Cancel
          </button>
          <button
            type="button"
            onClick={() => void run(valuePrompt!, value.trim() || undefined)}
            disabled={busy || !value.trim()}
            className="rounded-md border border-danger/50 bg-danger/15 px-3 py-1.5 text-sm text-danger font-semibold disabled:opacity-50"
          >
            Apply
          </button>
        </div>
      </Modal>
    </div>
  )
}

export function Alerts() {
  const { hasPermission } = useAuth()
  const toast = useToast()
  const canAct = hasPermission('alerts.act')
  const [open, setOpen] = useState<AlertView[]>([])
  const [history, setHistory] = useState<ContainmentAction[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [reloadKey, setReloadKey] = useState(0)

  useEffect(() => {
    let cancelled = false
    Promise.all([api.alerts('OPEN'), api.containments()])
      .then(([a, c]) => {
        if (cancelled) return
        setOpen(a)
        setHistory(c)
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
  }, [reloadKey])

  useSSE(
    useCallback((name: string) => {
      if (name === 'alert' || name === 'containment') {
        setReloadKey((k) => k + 1)
      }
    }, []),
    hasPermission('events.read'),
  )

  const onAction = async (id: number, action: string, value?: string) => {
    try {
      const updated = await api.playbook(id, action, value)
      setOpen((prev) => prev.map((x) => (x.id === updated.id ? updated : x)))
      toast.success(`Playbook ${action} applied to alert #${id}`)
      if (action === 'MARK_REVIEWED') {
        setTimeout(() => setReloadKey((k) => k + 1), 300)
      }
    } catch (err) {
      toast.error(apiErrorMessage(err))
      throw err
    }
  }

  if (loading) return <LoadingPanel label="Loading alerts…" />
  if (error) return <ErrorPanel message={error} onRetry={() => setReloadKey((k) => k + 1)} />

  return (
    <div className="space-y-8">
      <div>
        <PageHeader title="Alerts" description="Open security incidents with one-click containment playbooks" />
        {open.length === 0 ? (
          <EmptyState title="No open incidents" description="All alerts are reviewed or the queue is empty." />
        ) : (
          <div className="space-y-3">
            {open.map((alert) => (
              <IncidentCard key={alert.id} alert={alert} canAct={canAct} onAction={onAction} />
            ))}
          </div>
        )}
      </div>

      <div>
        <div className="text-xs uppercase tracking-widest text-faint mb-3">Containment history</div>
        {history.length === 0 ? (
          <EmptyState title="No containment actions" description="Playbook executions will appear here." />
        ) : (
          <div className="rounded-xl border border-edge bg-panel overflow-x-auto">
            <table className="w-full text-sm">
              <thead>
                <tr className="text-left text-[10px] uppercase tracking-wide text-faint border-b border-edge">
                  <th className="px-4 py-2 font-normal">Time</th>
                  <th className="px-2 py-2 font-normal">Agent</th>
                  <th className="px-2 py-2 font-normal">Kind</th>
                  <th className="px-2 py-2 font-normal">Detail</th>
                  <th className="px-2 py-2 font-normal">Trigger</th>
                  <th className="px-2 py-2 font-normal">Actor</th>
                  <th className="px-4 py-2 font-normal">Active</th>
                </tr>
              </thead>
              <tbody>
                {history.map((c) => (
                  <tr key={c.id} className="border-b border-edge last:border-b-0">
                    <td className="px-4 py-2 font-mono text-xs text-faint whitespace-nowrap">
                      {new Date(c.ts).toLocaleString()}
                    </td>
                    <td className="px-2 py-2 text-bright">{c.agentName || c.agentId}</td>
                    <td className="px-2 py-2">
                      <span className={`rounded border px-1.5 py-0.5 text-[10px] font-semibold ${KIND_COLORS[c.kind] ?? 'border-edge bg-panel-2 text-dim'}`}>
                        {c.kind}
                      </span>
                    </td>
                    <td className="px-2 py-2 text-dim max-w-[280px] truncate" title={c.detail}>
                      {c.detail}
                    </td>
                    <td className="px-2 py-2 text-xs text-faint max-w-[160px] truncate" title={c.trigger ?? ''}>
                      {c.trigger ?? '—'}
                    </td>
                    <td className="px-2 py-2 text-xs text-dim">{c.actor ?? '—'}</td>
                    <td className="px-4 py-2 text-xs">
                      <span className={c.active ? 'text-ok' : 'text-faint'}>{c.active ? 'yes' : 'no'}</span>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </div>
  )
}
