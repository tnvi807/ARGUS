import { useCallback, useEffect, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import * as api from '../api/client'
import { ApiRequestError, apiErrorMessage } from '../api/client'
import type {
  ActionSubmitBody,
  AgentSummary,
  DemoCatalogEntry,
  FeedEvent,
  PolicyCatalog,
  SubmitView,
} from '../api/types'
import { useAuth } from '../auth/AuthContext'
import { useSSE } from '../hooks/useSSE'
import { DecisionBadge } from '../components/DecisionBadge'
import { EmptyState } from '../components/EmptyState'
import { ErrorPanel } from '../components/ErrorPanel'
import { FactorList } from '../components/FactorList'
import { FeedRow } from '../components/FeedRow'
import { LoadingPanel } from '../components/LoadingPanel'
import { Modal } from '../components/Modal'
import { PageHeader } from '../components/PageHeader'
import { RiskPill } from '../components/RiskPill'
import { useToast } from '../components/Toast'

function ManualSubmit({
  agentsList,
  catalog,
  onSubmitted,
}: {
  agentsList: AgentSummary[]
  catalog: PolicyCatalog | null
  onSubmitted: (view: SubmitView) => void
}) {
  const toast = useToast()
  const [form, setForm] = useState<ActionSubmitBody>({
    agentId: '',
    kind: 'read',
    tool: '',
    category: '',
    target: '',
    message: '',
    toAgent: '',
  })
  const [busy, setBusy] = useState(false)

  const set = <K extends keyof ActionSubmitBody>(key: K, value: string) =>
    setForm((f) => ({ ...f, [key]: value }))

  const submit = async () => {
    setBusy(true)
    try {
      const view = await api.submitAction({
        agentId: form.agentId,
        kind: form.kind,
        tool: form.tool || null,
        category: form.category || null,
        target: form.target || null,
        message: form.message || null,
        toAgent: form.toAgent || null,
      })
      onSubmitted(view)
      toast.success(`Action evaluated: ${view.decision} (risk ${view.riskScore})`)
    } catch (err) {
      toast.error(apiErrorMessage(err))
    } finally {
      setBusy(false)
    }
  }

  const selectCls = 'w-full rounded-md border border-edge bg-base px-3 py-2 text-sm text-bright'
  const inputCls = selectCls + ' font-mono'

  return (
    <div className="rounded-xl border border-edge bg-panel p-4 space-y-3">
      <div className="text-xs uppercase tracking-widest text-faint">Manual gateway submission</div>
      <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
        <div>
          <label className="block text-xs text-faint mb-1">Agent</label>
          <select value={form.agentId} onChange={(e) => set('agentId', e.target.value)} className={selectCls}>
            <option value="">— select —</option>
            {agentsList.map((a) => (
              <option key={a.id} value={a.id}>
                {a.name} ({a.id})
              </option>
            ))}
          </select>
        </div>
        <div>
          <label className="block text-xs text-faint mb-1">Kind</label>
          <select value={form.kind} onChange={(e) => set('kind', e.target.value)} className={selectCls}>
            {(catalog?.actionKinds ?? ['read']).map((k) => (
              <option key={k} value={k}>
                {k}
              </option>
            ))}
          </select>
        </div>
        <div>
          <label className="block text-xs text-faint mb-1">Tool</label>
          <select value={form.tool ?? ''} onChange={(e) => set('tool', e.target.value)} className={selectCls}>
            <option value="">— none —</option>
            {(catalog?.tools ?? []).map((t) => (
              <option key={t} value={t}>
                {t}
              </option>
            ))}
          </select>
        </div>
        <div>
          <label className="block text-xs text-faint mb-1">Category</label>
          <select value={form.category ?? ''} onChange={(e) => set('category', e.target.value)} className={selectCls}>
            <option value="">— none —</option>
            {(catalog?.categories ?? []).map((c) => (
              <option key={c.code} value={c.code}>
                {c.label} (sens {c.sensitivity})
              </option>
            ))}
          </select>
        </div>
        <div>
          <label className="block text-xs text-faint mb-1">Target</label>
          <input value={form.target ?? ''} onChange={(e) => set('target', e.target.value)} className={inputCls} />
        </div>
        <div>
          <label className="block text-xs text-faint mb-1">To agent</label>
          <input value={form.toAgent ?? ''} onChange={(e) => set('toAgent', e.target.value)} className={inputCls} />
        </div>
      </div>
      <div>
        <label className="block text-xs text-faint mb-1">Message</label>
        <textarea value={form.message ?? ''} onChange={(e) => set('message', e.target.value)} rows={2} className={selectCls} />
      </div>
      <div className="flex justify-end">
        <button
          type="button"
          onClick={() => void submit()}
          disabled={busy || !form.agentId}
          className="rounded-md border border-accent/50 bg-accent/15 px-4 py-1.5 text-sm text-accent font-semibold disabled:opacity-50"
        >
          {busy ? 'Submitting…' : 'Submit action'}
        </button>
      </div>
    </div>
  )
}

export function DemoLab() {
  const { hasPermission } = useAuth()
  const toast = useToast()
  const navigate = useNavigate()
  const canRun = hasPermission('demos.run')
  const canSubmit = hasPermission('actions.submit')
  const canSeeEvents = hasPermission('events.read')
  const canControlSim = hasPermission('sim.control')
  const [demos, setDemos] = useState<Record<string, DemoCatalogEntry>>({})
  const [running, setRunning] = useState(false)
  const [autopilot, setAutopilotState] = useState<boolean | null>(null)
  const [catalog, setCatalog] = useState<PolicyCatalog | null>(null)
  const [agentsList, setAgentsList] = useState<AgentSummary[]>([])
  const [mirror, setMirror] = useState<FeedEvent[]>([])
  const [result, setResult] = useState<SubmitView | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [confirmReset, setConfirmReset] = useState(false)
  const [resetBusy, setResetBusy] = useState(false)
  const [reloadKey, setReloadKey] = useState(0)

  useEffect(() => {
    let cancelled = false
    Promise.all([
      api.demos().catch(() => ({ demos: {} as Record<string, DemoCatalogEntry>, running: false })),
      api.policyCatalog().catch(() => null),
      api.agents().catch(() => [] as AgentSummary[]),
      canSeeEvents ? api.feed(undefined, undefined, 20).catch(() => [] as FeedEvent[]) : Promise.resolve([] as FeedEvent[]),
      api.simStatus().catch(() => ({ autopilot: false })),
    ])
      .then(([d, c, a, f, s]) => {
        if (cancelled) return
        setDemos(d.demos)
        setRunning(d.running)
        setCatalog(c)
        setAgentsList(a)
        setMirror(f)
        setAutopilotState(s.autopilot)
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
  }, [canSeeEvents, reloadKey])

  useSSE(
    useCallback((name: string, data: unknown) => {
      if (name !== 'feed' || !data || typeof data !== 'object') return
      const evt = (data as { event?: FeedEvent }).event
      if (!evt) return
      setMirror((prev) => [evt, ...prev].slice(0, 20))
    }, []),
    canSeeEvents,
  )

  const run = async (key: string, type: string) => {
    if (type === 'analysis') {
      navigate('/analysis')
      return
    }
    try {
      const res = await api.runDemo(key)
      if (res.started) {
        toast.success(`Demo started: ${demos[key]?.title ?? key}`)
        setRunning(true)
      } else {
        toast.info(res.message || 'Another demo is still running')
      }
    } catch (err) {
      if (err instanceof ApiRequestError && err.status === 409) {
        toast.info(err.body?.message || 'Another demo is still running')
      } else {
        toast.error(apiErrorMessage(err))
      }
    }
  }

  const toggleAutopilot = async () => {
    try {
      const res = await api.setAutopilot(!(autopilot ?? false))
      setAutopilotState(res.autopilot)
      toast.success(`Ambient autopilot ${res.autopilot ? 'enabled' : 'disabled'}`)
    } catch (err) {
      toast.error(apiErrorMessage(err))
    }
  }

  const doReset = async () => {
    setResetBusy(true)
    try {
      const res = await api.resetDemo()
      toast.success(`Reset complete — ${res.eventsCleared} events cleared, ${res.agentsSeeded} agents reseeded`)
      setConfirmReset(false)
      setRunning(false)
      setMirror([])
      setResult(null)
      setReloadKey((k) => k + 1)
    } catch (err) {
      toast.error(apiErrorMessage(err))
    } finally {
      setResetBusy(false)
    }
  }

  if (loading) return <LoadingPanel label="Loading demo lab…" />
  if (error) return <ErrorPanel message={error} onRetry={() => setReloadKey((k) => k + 1)} />

  return (
    <div className="space-y-6">
      <PageHeader
        title="Demo Lab"
        description="Scripted attack simulations executed against the live gateway pipeline"
        actions={
          <div className="flex items-center gap-2">
            {canControlSim && autopilot !== null && (
              <button
                type="button"
                onClick={() => void toggleAutopilot()}
                className={`rounded-md border px-3 py-1.5 text-sm font-semibold ${
                  autopilot
                    ? 'border-ok/50 bg-ok/10 text-ok'
                    : 'border-edge text-dim hover:text-bright'
                }`}
                title="Ambient autopilot traffic keeps the live feed populated"
              >
                Autopilot {autopilot ? 'ON' : 'OFF'}
              </button>
            )}
            {canRun ? (
              <button
                type="button"
                onClick={() => setConfirmReset(true)}
                className="rounded-md border border-danger/40 px-3 py-1.5 text-sm text-danger hover:bg-danger/10"
              >
                Reset demo data
              </button>
            ) : null}
          </div>
        }
      />

      {running && (
        <div className="rounded-md border border-warn/40 bg-warn/10 px-3 py-2 text-sm text-warn">
          A demo simulation is currently running…
        </div>
      )}

      <div className="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-3 gap-3">
        {Object.entries(demos).map(([key, demo]) => (
          <div key={key} className="rounded-xl border border-edge bg-panel p-4 flex flex-col gap-2">
            <div className="flex items-start justify-between gap-2">
              <div className="text-bright font-semibold text-sm">{demo.title}</div>
              <span
                className={`shrink-0 rounded border px-1.5 py-0.5 text-[10px] uppercase tracking-wide ${
                  demo.type === 'analysis'
                    ? 'border-accent/40 bg-accent/10 text-accent'
                    : 'border-edge bg-panel-2 text-dim'
                }`}
              >
                {demo.type}
              </span>
            </div>
            <p className="text-xs text-dim flex-1">{demo.description}</p>
            <button
              type="button"
              onClick={() => void run(key, demo.type)}
              disabled={!canRun}
              title={canRun ? 'Run this demo' : 'Requires demos.run permission'}
              className="self-start rounded-md border border-accent/50 bg-accent/10 px-3 py-1 text-xs text-accent font-semibold hover:bg-accent/20 disabled:opacity-40"
            >
              {demo.type === 'analysis' ? 'Open Analysis' : 'Run demo'}
            </button>
          </div>
        ))}
      </div>

      {canSubmit && (
        <ManualSubmit
          agentsList={agentsList}
          catalog={catalog}
          onSubmitted={(view) => setResult(view)}
        />
      )}

      {result && (
        <div className="rounded-xl border border-accent/40 bg-panel p-4 space-y-2">
          <div className="flex flex-wrap items-center gap-x-3 gap-y-1">
            <span className="text-xs uppercase tracking-widest text-faint">Gateway result</span>
            <RiskPill score={result.riskScore} />
            <DecisionBadge decision={result.decision} />
            <span className="text-xs text-faint">agent {result.agentId}</span>
            <span className="flex-1" />
            {result.alertId != null && (
              <Link to="/alerts" className="text-xs text-accent hover:underline">
                Alert #{result.alertId} →
              </Link>
            )}
            {result.approvalId != null && (
              <Link to="/approvals" className="text-xs text-accent hover:underline">
                Approval #{result.approvalId} →
              </Link>
            )}
          </div>
          <p className="text-sm text-dim">{result.reason}</p>
          <div className="text-xs text-faint">
            Executed: <span className={result.executed ? 'text-ok' : 'text-warn'}>{result.executed ? 'yes' : 'no'}</span>
            {result.executionDetail && <> — {result.executionDetail}</>}
          </div>
          <FactorList factors={result.factors} />
        </div>
      )}

      <div>
        <div className="text-xs uppercase tracking-widest text-faint mb-3">Live mirror (last 20 events)</div>
        {mirror.length === 0 ? (
          <EmptyState title="No traffic yet" description="Run a demo or submit an action to see events here." />
        ) : (
          <div className="rounded-xl border border-edge bg-panel overflow-hidden">
            {mirror.map((evt) => (
              <FeedRow key={evt.id ?? `${evt.ts}-${evt.agentId}`} event={evt} />
            ))}
          </div>
        )}
      </div>

      <Modal open={confirmReset} onClose={() => setConfirmReset(false)} title="Reset demo data">
        <p className="text-sm text-dim mb-4">
          This wipes all demo traffic, alerts, approvals and containments, then reseeds the agent fleet.
          The audit trail is preserved. Continue?
        </p>
        <div className="flex justify-end gap-2">
          <button type="button" onClick={() => setConfirmReset(false)} className="rounded-md border border-edge px-3 py-1.5 text-sm text-dim">
            Cancel
          </button>
          <button
            type="button"
            onClick={() => void doReset()}
            disabled={resetBusy}
            className="rounded-md border border-danger/50 bg-danger/15 px-3 py-1.5 text-sm text-danger font-semibold disabled:opacity-50"
          >
            {resetBusy ? 'Resetting…' : 'Reset'}
          </button>
        </div>
      </Modal>
    </div>
  )
}
