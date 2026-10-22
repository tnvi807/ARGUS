import { useCallback, useEffect, useState } from 'react'
import * as api from '../api/client'
import { apiErrorMessage } from '../api/client'
import type { ApprovalRequest, Factor } from '../api/types'
import { useAuth } from '../auth/AuthContext'
import { useSSE } from '../hooks/useSSE'
import { DecisionBadge } from '../components/DecisionBadge'
import { EmptyState } from '../components/EmptyState'
import { ErrorPanel } from '../components/ErrorPanel'
import { FactorList } from '../components/FactorList'
import { LoadingPanel } from '../components/LoadingPanel'
import { Modal } from '../components/Modal'
import { PageHeader } from '../components/PageHeader'
import { RiskPill } from '../components/RiskPill'
import { useToast } from '../components/Toast'

function parseFactors(json: string | null): Factor[] {
  if (!json) return []
  try {
    const parsed: unknown = JSON.parse(json)
    return Array.isArray(parsed) ? (parsed as Factor[]) : []
  } catch {
    return []
  }
}

function ApprovalCard({
  item,
  canAct,
  onDecide,
}: {
  item: ApprovalRequest
  canAct: boolean
  onDecide: (id: number, decision: 'APPROVE' | 'DENY', note?: string) => Promise<void>
}) {
  const [note, setNote] = useState('')
  const [confirmApprove, setConfirmApprove] = useState(false)
  const [busy, setBusy] = useState(false)
  const factors = parseFactors(item.factorsJson)

  const decide = async (decision: 'APPROVE' | 'DENY') => {
    setBusy(true)
    try {
      await onDecide(item.id!, decision, note.trim() || undefined)
    } catch {
      // onDecide already surfaced the error as a toast
    } finally {
      setBusy(false)
      setConfirmApprove(false)
    }
  }

  return (
    <div className="rounded-xl border border-edge bg-panel p-4 space-y-2">
      <div className="flex flex-wrap items-center gap-x-3 gap-y-1">
        <span className="text-bright font-semibold">{item.agentName || item.agentId}</span>
        <span className="rounded border border-edge bg-panel-2 px-1.5 py-0.5 text-[10px] uppercase text-dim">
          {item.kind}
        </span>
        {item.tool && (
          <span className="rounded border border-edge bg-panel-2 px-1.5 py-0.5 text-[10px] font-mono text-dim">
            {item.tool}
          </span>
        )}
        {item.category && (
          <span className="rounded border border-edge bg-panel-2 px-1.5 py-0.5 text-[10px] uppercase text-dim">
            {item.category}
          </span>
        )}
        <span className="flex-1" />
        <RiskPill score={item.riskScore} />
        <span className="font-mono text-[11px] text-faint">{new Date(item.ts).toLocaleString()}</span>
      </div>
      {item.target && (
        <div className="font-mono text-xs text-dim break-all">Target: {item.target}</div>
      )}
      {item.message && <div className="text-xs text-dim break-words">Message: {item.message}</div>}
      {item.reason && <div className="text-xs text-faint">{item.reason}</div>}
      {factors.length > 0 && (
        <div className="border-t border-edge pt-2">
          <FactorList factors={factors} />
        </div>
      )}
      {canAct && (
        <div className="flex flex-wrap items-center gap-2 border-t border-edge pt-3">
          <input
            value={note}
            onChange={(e) => setNote(e.target.value)}
            placeholder="Optional decision note…"
            maxLength={300}
            className="flex-1 min-w-[200px] rounded-md border border-edge bg-base px-3 py-1.5 text-xs text-bright"
          />
          <button
            type="button"
            onClick={() => setConfirmApprove(true)}
            disabled={busy}
            className="rounded-md border border-ok/50 bg-ok/10 px-3 py-1.5 text-xs text-ok font-semibold disabled:opacity-50"
          >
            Approve
          </button>
          <button
            type="button"
            onClick={() => void decide('DENY')}
            disabled={busy}
            className="rounded-md border border-danger/50 bg-danger/10 px-3 py-1.5 text-xs text-danger font-semibold disabled:opacity-50"
          >
            Deny
          </button>
        </div>
      )}
      <Modal open={confirmApprove} onClose={() => setConfirmApprove(false)} title="Confirm approval">
        <p className="text-sm text-dim mb-4">
          Approving <span className="font-mono text-bright">#{item.id}</span> for{' '}
          <span className="font-mono text-bright">{item.agentName}</span>{' '}
          <span className="text-danger font-semibold">executes the held action now</span> and records an
          audited administrator override. Continue?
        </p>
        <div className="flex justify-end gap-2">
          <button type="button" onClick={() => setConfirmApprove(false)} className="rounded-md border border-edge px-3 py-1.5 text-sm text-dim">
            Cancel
          </button>
          <button type="button" onClick={() => void decide('APPROVE')} disabled={busy} className="rounded-md border border-ok/50 bg-ok/15 px-3 py-1.5 text-sm text-ok font-semibold">
            Approve &amp; execute
          </button>
        </div>
      </Modal>
    </div>
  )
}

export function Approvals() {
  const { hasPermission } = useAuth()
  const toast = useToast()
  const canAct = hasPermission('approvals.act')
  const [items, setItems] = useState<ApprovalRequest[]>([])
  const [filter, setFilter] = useState<'PENDING' | 'ALL'>('PENDING')
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [reloadKey, setReloadKey] = useState(0)

  useEffect(() => {
    let cancelled = false
    api
      .approvals(filter === 'PENDING' ? 'PENDING' : undefined)
      .then((data) => {
        if (cancelled) return
        setItems(data)
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
  }, [filter, reloadKey])

  useSSE(
    useCallback((name: string) => {
      if (name === 'approval') {
        setReloadKey((k) => k + 1)
      }
    }, []),
    hasPermission('events.read'),
  )

  const onDecide = async (id: number, decision: 'APPROVE' | 'DENY', note?: string) => {
    try {
      const updated = await api.decide(id, decision, note)
      setItems((prev) => prev.map((x) => (x.id === updated.id ? updated : x)))
      toast.success(`Approval #${id} ${decision === 'APPROVE' ? 'approved' : 'denied'}`)
    } catch (err) {
      toast.error(apiErrorMessage(err))
      throw err
    }
  }

  if (loading && items.length === 0) return <LoadingPanel label="Loading approvals…" />
  if (error) return <ErrorPanel message={error} onRetry={() => setReloadKey((k) => k + 1)} />

  const pending = items.filter((a) => a.status === 'PENDING')
  const decided = items.filter((a) => a.status !== 'PENDING')
  const shown = filter === 'PENDING' ? pending : items

  return (
    <div>
      <PageHeader
        title="Approval Queue"
        description="Held gateway actions awaiting an administrator decision"
        actions={
          <select
            value={filter}
            onChange={(e) => setFilter(e.target.value as 'PENDING' | 'ALL')}
            className="rounded-md border border-edge bg-panel px-2.5 py-1.5 text-sm text-bright"
          >
            <option value="PENDING">PENDING</option>
            <option value="ALL">ALL</option>
          </select>
        }
      />

      {shown.length === 0 ? (
        <EmptyState title="Queue empty" description="No approval requests match this filter." />
      ) : filter === 'PENDING' ? (
        <div className="space-y-3">
          {pending.map((item) => (
            <ApprovalCard key={item.id} item={item} canAct={canAct} onDecide={onDecide} />
          ))}
        </div>
      ) : (
        <div className="space-y-3">
          {shown.map((item) =>
            item.status === 'PENDING' ? (
              <ApprovalCard key={item.id} item={item} canAct={canAct} onDecide={onDecide} />
            ) : (
              <div key={item.id} className="rounded-xl border border-edge bg-panel p-4 space-y-2 opacity-80">
                <div className="flex flex-wrap items-center gap-x-3 gap-y-1 text-sm">
                  <span className="text-bright font-semibold">{item.agentName || item.agentId}</span>
                  <span className="rounded border border-edge bg-panel-2 px-1.5 py-0.5 text-[10px] uppercase text-dim">
                    {item.kind}
                  </span>
                  <DecisionBadge decision={item.status === 'APPROVED' ? 'ALLOWED' : 'BLOCKED'} />
                  <span className="flex-1" />
                  <RiskPill score={item.riskScore} />
                  <span className="font-mono text-[11px] text-faint">#{item.id}</span>
                </div>
                {item.target && <div className="font-mono text-xs text-dim break-all">{item.target}</div>}
                <div className="text-xs text-dim">
                  Decided by <span className="text-bright">{item.decidedBy ?? '—'}</span>
                  {item.decidedAt && (
                    <span className="text-faint"> at {new Date(item.decidedAt).toLocaleString()}</span>
                  )}
                </div>
                {item.decisionNote && <div className="text-xs text-faint">Note: {item.decisionNote}</div>}
                {item.executionDetail && (
                  <div className="text-xs text-dim border-t border-edge pt-2 break-words">
                    {item.executionDetail}
                  </div>
                )}
              </div>
            ),
          )}
        </div>
      )}

      {filter === 'PENDING' && decided.length > 0 && (
        <div className="mt-8">
          <div className="text-xs uppercase tracking-widest text-faint mb-3">
            Recently decided <span className="font-mono">({decided.length})</span>
          </div>
          <div className="space-y-2">
            {decided.slice(0, 10).map((item) => (
              <div key={item.id} className="rounded-lg border border-edge bg-panel px-4 py-2.5 flex flex-wrap items-center gap-x-3 gap-y-1 text-xs">
                <span className="text-bright">{item.agentName || item.agentId}</span>
                <span className="font-mono text-dim">{item.kind}</span>
                <DecisionBadge decision={item.status === 'APPROVED' ? 'ALLOWED' : 'BLOCKED'} />
                <span className="text-faint">by {item.decidedBy ?? '—'}</span>
                {item.decisionNote && <span className="text-faint truncate max-w-[240px]">{item.decisionNote}</span>}
                <span className="flex-1" />
                <span className="font-mono text-faint">#{item.id}</span>
              </div>
            ))}
          </div>
        </div>
      )}
    </div>
  )
}
