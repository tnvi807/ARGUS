import { useCallback, useEffect, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import * as api from '../api/client'
import { apiErrorMessage, fieldErrorsOf } from '../api/client'
import type { AgentPolicyBody, AgentSummary, DriftView, PolicyCatalog } from '../api/types'
import { useAuth } from '../auth/AuthContext'
import { CheckList } from '../components/CheckList'
import { ErrorPanel } from '../components/ErrorPanel'
import { LineChart } from '../components/Sparkline'
import { LoadingPanel } from '../components/LoadingPanel'
import { Modal } from '../components/Modal'
import { PageHeader } from '../components/PageHeader'
import { RiskGauge } from '../components/RiskGauge'
import { StatusChip } from '../components/StatusChip'
import { useToast } from '../components/Toast'

const TABS = ['Overview', 'Policy', 'Drift'] as const
type Tab = (typeof TABS)[number]

function verdictStyle(verdict: string): string {
  if (verdict.includes('Stable')) return 'border-ok/40 bg-ok/10 text-ok'
  if (verdict.includes('Drifting')) return 'border-warn/40 bg-warn/10 text-warn'
  if (verdict.includes('Compromised')) return 'border-danger/40 bg-danger/10 text-danger'
  return 'border-edge bg-panel-2 text-dim'
}

function StatBox({ label, value }: { label: string; value: string | number }) {
  return (
    <div className="rounded-lg border border-edge bg-panel-2 px-3 py-2">
      <div className="text-[10px] uppercase tracking-wide text-faint">{label}</div>
      <div className="font-mono text-bright text-lg">{value}</div>
    </div>
  )
}

export function AgentDetail() {
  const { id = '' } = useParams()
  const navigate = useNavigate()
  const toast = useToast()
  const { hasPermission } = useAuth()
  const [agent, setAgent] = useState<AgentSummary | null>(null)
  const [drift, setDrift] = useState<DriftView | null>(null)
  const [catalog, setCatalog] = useState<PolicyCatalog | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [tab, setTab] = useState<Tab>('Overview')
  const [policy, setPolicy] = useState<AgentPolicyBody | null>(null)
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})
  const [saving, setSaving] = useState(false)
  const [confirmDelete, setConfirmDelete] = useState(false)
  const [reloadKey, setReloadKey] = useState(0)

  useEffect(() => {
    let cancelled = false
    Promise.all([api.agent(id), api.agentDrift(id), api.policyCatalog().catch(() => null)])
      .then(([a, d, c]) => {
        if (cancelled) return
        setAgent(a)
        setDrift(d)
        setCatalog(c)
        setError(null)
        setPolicy({
          id: a.id,
          name: a.name,
          icon: a.icon,
          roleTitle: a.roleTitle,
          description: a.description,
          tools: [...a.tools],
          allowed: [...a.allowed],
          restricted: [...a.restricted],
          restrictActions: [...a.restrictActions],
          clearance: a.clearance,
          baselineRate: a.baselineRate,
          baselineOffHours: a.baselineOffHours,
          baselineAvgSens: a.baselineAvgSens,
        })
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
  }, [id, reloadKey])

  const act = useCallback(
    async (kind: 'pause' | 'resume' | 'isolate' | 'restore') => {
      try {
        const updated =
          kind === 'isolate'
            ? await api.isolate(id)
            : kind === 'restore'
              ? await api.restore(id)
              : await api.agentStatus(id, kind === 'pause' ? 'PAUSED' : 'ACTIVE')
        setAgent(updated)
        const verb =
          kind === 'isolate' ? 'isolated' : kind === 'restore' ? 'restored' : kind === 'pause' ? 'paused' : 'resumed'
        toast.success(`Agent ${verb} — ${updated.name}`)
      } catch (err) {
        toast.error(apiErrorMessage(err))
      }
    },
    [id, toast],
  )

  const savePolicy = async () => {
    if (!policy) return
    setSaving(true)
    setFieldErrors({})
    try {
      const updated = await api.updateAgent(id, policy)
      setAgent(updated)
      toast.success('Policy saved')
      setReloadKey((k) => k + 1)
    } catch (err) {
      setFieldErrors(fieldErrorsOf(err))
      toast.error(apiErrorMessage(err))
    } finally {
      setSaving(false)
    }
  }

  const doDelete = async () => {
    try {
      await api.deleteAgent(id)
      toast.success('Agent deleted')
      navigate('/agents')
    } catch (err) {
      toast.error(apiErrorMessage(err))
    } finally {
      setConfirmDelete(false)
    }
  }

  if (loading) return <LoadingPanel label="Loading agent…" />
  if (error) return <ErrorPanel message={error} onRetry={() => setReloadKey((k) => k + 1)} />
  if (!agent || !policy) return null

  const set = <K extends keyof AgentPolicyBody>(key: K, value: AgentPolicyBody[K]) =>
    setPolicy((p) => (p ? { ...p, [key]: value } : p))

  return (
    <div>
      <PageHeader
        title={`${agent.icon || ''} ${agent.name}`}
        description={agent.roleTitle}
        actions={
          <div className="flex flex-wrap items-center gap-2">
            <StatusChip status={agent.status} />
            <span className="text-xs text-faint">
              Clearance <span className="font-mono text-bright">{agent.clearance}</span>/5
            </span>
            <RiskGauge value={agent.lastRisk} width={80} />
            {hasPermission('alerts.act') && agent.status === 'ACTIVE' && (
              <button type="button" onClick={() => void act('pause')} className="rounded-md border border-edge px-2.5 py-1 text-xs text-dim hover:text-warn hover:border-warn/40">
                Pause
              </button>
            )}
            {hasPermission('alerts.act') && agent.status === 'PAUSED' && (
              <button type="button" onClick={() => void act('resume')} className="rounded-md border border-edge px-2.5 py-1 text-xs text-dim hover:text-ok hover:border-ok/40">
                Resume
              </button>
            )}
            {hasPermission('alerts.act') && (
              <button type="button" onClick={() => void act('isolate')} disabled={agent.status === 'ISOLATED'} className="rounded-md border border-edge px-2.5 py-1 text-xs text-dim hover:text-danger hover:border-danger/40 disabled:opacity-40">
                Isolate
              </button>
            )}
            {hasPermission('admin.manage') && agent.status === 'ISOLATED' && (
              <button type="button" onClick={() => void act('restore')} className="rounded-md border border-accent/50 bg-accent/10 px-2.5 py-1 text-xs text-accent">
                Restore
              </button>
            )}
            {hasPermission('policies.write') && (
              <button type="button" onClick={() => setConfirmDelete(true)} className="rounded-md border border-danger/40 px-2.5 py-1 text-xs text-danger hover:bg-danger/10">
                Delete
              </button>
            )}
          </div>
        }
      />

      <div className="flex gap-1 border-b border-edge mb-4">
        {TABS.map((t) => (
          <button
            key={t}
            type="button"
            onClick={() => setTab(t)}
            className={`px-4 py-2 text-sm border-b-2 -mb-px ${
              tab === t ? 'border-accent text-accent' : 'border-transparent text-dim hover:text-bright'
            }`}
          >
            {t}
          </button>
        ))}
      </div>

      {tab === 'Overview' && (
        <div className="space-y-4">
          {agent.description && <p className="text-dim text-sm">{agent.description}</p>}
          <div className="grid grid-cols-2 md:grid-cols-3 xl:grid-cols-6 gap-3">
            <StatBox label="Actions" value={agent.actions} />
            <StatBox label="File reads" value={agent.fileReads} />
            <StatBox label="API calls" value={agent.apiCalls} />
            <StatBox label="Exports" value={agent.exports} />
            <StatBox label="Off-hours" value={agent.offHours} />
            <StatBox label="Avg sensitivity" value={agent.avgSensitivity} />
          </div>
          <div className="rounded-xl border border-edge bg-panel p-4">
            <div className="text-xs uppercase tracking-widest text-faint mb-3">Baseline metrics</div>
            <div className="grid grid-cols-1 md:grid-cols-3 gap-3 text-sm">
              <div>
                <span className="text-faint">Rate budget</span>{' '}
                <span className="font-mono text-bright">{agent.baselineRate}/hr</span>
              </div>
              <div>
                <span className="text-faint">Off-hours budget</span>{' '}
                <span className="font-mono text-bright">{agent.baselineOffHours}</span>
              </div>
              <div>
                <span className="text-faint">Avg sensitivity baseline</span>{' '}
                <span className="font-mono text-bright">{agent.baselineAvgSens}</span>
              </div>
            </div>
          </div>
          <div className="rounded-xl border border-edge bg-panel p-4">
            <div className="text-xs uppercase tracking-widest text-faint mb-2">
              Blocked resources <span className="font-mono">({agent.blockedResources.length})</span>
            </div>
            {agent.blockedResources.length === 0 ? (
              <p className="text-faint text-sm">No quarantined resources.</p>
            ) : (
              <div className="flex flex-wrap gap-2">
                {agent.blockedResources.map((r) => (
                  <span key={r} className="rounded border border-danger/40 bg-danger/10 px-2 py-0.5 text-xs text-danger font-mono">
                    {r}
                  </span>
                ))}
              </div>
            )}
          </div>
          <div className="rounded-xl border border-edge bg-panel p-4">
            <div className="text-xs uppercase tracking-widest text-faint mb-2">Tools</div>
            <div className="flex flex-wrap gap-2">
              {agent.tools.map((t) => (
                <span key={t} className="rounded border border-edge bg-panel-2 px-2 py-0.5 text-xs font-mono text-dim">
                  {t}
                </span>
              ))}
            </div>
          </div>
        </div>
      )}

      {tab === 'Policy' && (
        <div className="rounded-xl border border-edge bg-panel p-4 space-y-4 max-w-3xl">
          <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
            <div>
              <label className="block text-xs uppercase tracking-wide text-faint mb-1">Name</label>
              <input
                value={policy.name}
                onChange={(e) => set('name', e.target.value)}
                className="w-full rounded-md border border-edge bg-base px-3 py-2 text-sm text-bright"
              />
              {fieldErrors.name && <p className="text-xs text-danger mt-1">{fieldErrors.name}</p>}
            </div>
            <div>
              <label className="block text-xs uppercase tracking-wide text-faint mb-1">Role title</label>
              <input
                value={policy.roleTitle}
                onChange={(e) => set('roleTitle', e.target.value)}
                className="w-full rounded-md border border-edge bg-base px-3 py-2 text-sm text-bright"
              />
              {fieldErrors.roleTitle && <p className="text-xs text-danger mt-1">{fieldErrors.roleTitle}</p>}
            </div>
          </div>
          <div>
            <label className="block text-xs uppercase tracking-wide text-faint mb-1">Description</label>
            <textarea
              value={policy.description ?? ''}
              onChange={(e) => set('description', e.target.value)}
              rows={2}
              className="w-full rounded-md border border-edge bg-base px-3 py-2 text-sm text-bright"
            />
          </div>
          <div>
            <label className="block text-xs uppercase tracking-wide text-faint mb-1">
              Clearance <span className="font-mono text-bright">{policy.clearance}</span>/5
            </label>
            <input
              type="range"
              min={1}
              max={5}
              value={policy.clearance}
              onChange={(e) => set('clearance', Number(e.target.value))}
              className="w-full accent-[#22d3ee]"
            />
            {fieldErrors.clearance && <p className="text-xs text-danger mt-1">{fieldErrors.clearance}</p>}
          </div>
          <div className="grid grid-cols-1 md:grid-cols-3 gap-3">
            <div>
              <label className="block text-xs uppercase tracking-wide text-faint mb-1">Baseline rate /hr</label>
              <input
                type="number"
                step="0.1"
                value={policy.baselineRate ?? ''}
                onChange={(e) => set('baselineRate', e.target.value === '' ? null : Number(e.target.value))}
                className="w-full rounded-md border border-edge bg-base px-3 py-2 text-sm font-mono text-bright"
              />
            </div>
            <div>
              <label className="block text-xs uppercase tracking-wide text-faint mb-1">Baseline off-hours</label>
              <input
                type="number"
                step="0.1"
                value={policy.baselineOffHours ?? ''}
                onChange={(e) => set('baselineOffHours', e.target.value === '' ? null : Number(e.target.value))}
                className="w-full rounded-md border border-edge bg-base px-3 py-2 text-sm font-mono text-bright"
              />
            </div>
            <div>
              <label className="block text-xs uppercase tracking-wide text-faint mb-1">Baseline avg sensitivity</label>
              <input
                type="number"
                step="0.1"
                value={policy.baselineAvgSens ?? ''}
                onChange={(e) => set('baselineAvgSens', e.target.value === '' ? null : Number(e.target.value))}
                className="w-full rounded-md border border-edge bg-base px-3 py-2 text-sm font-mono text-bright"
              />
            </div>
          </div>
          {catalog && (
            <div className="space-y-3">
              <CheckList
                title="Tools"
                options={catalog.tools.map((t) => ({ code: t }))}
                selected={policy.tools}
                onChange={(next) => set('tools', next)}
              />
              <CheckList
                title="Allowed categories"
                options={catalog.categories.map((c) => ({ code: c.code, label: c.label }))}
                selected={policy.allowed}
                onChange={(next) => set('allowed', next)}
              />
              <CheckList
                title="Restricted categories"
                options={catalog.categories.map((c) => ({ code: c.code, label: c.label }))}
                selected={policy.restricted}
                onChange={(next) => set('restricted', next)}
              />
              <CheckList
                title="Restricted actions"
                options={catalog.restrictActions.map((r) => ({ code: r }))}
                selected={policy.restrictActions}
                onChange={(next) => set('restrictActions', next)}
              />
            </div>
          )}
          {fieldErrors.tools && <p className="text-xs text-danger">{fieldErrors.tools}</p>}
          {fieldErrors.allowed && <p className="text-xs text-danger">{fieldErrors.allowed}</p>}
          {fieldErrors.restricted && <p className="text-xs text-danger">{fieldErrors.restricted}</p>}
          <div className="flex justify-end gap-2 border-t border-edge pt-3">
            <button
              type="button"
              onClick={() =>
                setPolicy({
                  id: agent.id,
                  name: agent.name,
                  icon: agent.icon,
                  roleTitle: agent.roleTitle,
                  description: agent.description,
                  tools: [...agent.tools],
                  allowed: [...agent.allowed],
                  restricted: [...agent.restricted],
                  restrictActions: [...agent.restrictActions],
                  clearance: agent.clearance,
                  baselineRate: agent.baselineRate,
                  baselineOffHours: agent.baselineOffHours,
                  baselineAvgSens: agent.baselineAvgSens,
                })
              }
              className="rounded-md border border-edge px-3 py-1.5 text-sm text-dim hover:text-bright"
            >
              Reset
            </button>
            <button
              type="button"
              onClick={() => void savePolicy()}
              disabled={saving}
              className="rounded-md border border-accent/50 bg-accent/15 px-3 py-1.5 text-sm text-accent font-semibold disabled:opacity-50"
            >
              {saving ? 'Saving…' : 'Save policy'}
            </button>
          </div>
        </div>
      )}

      {tab === 'Drift' && drift && (
        <div className="space-y-4">
          <div className={`rounded-lg border px-4 py-3 text-sm font-semibold ${verdictStyle(drift.verdict)}`}>
            {drift.verdict}
          </div>
          <div className="rounded-xl border border-edge bg-panel p-4">
            <div className="text-xs uppercase tracking-widest text-faint mb-3">Sensitivity drift</div>
            <LineChart points={drift.points} baseline={drift.baselineAvgSens} />
          </div>
          <div className="grid grid-cols-2 md:grid-cols-5 gap-3">
            <StatBox label="Slope" value={drift.slope.toFixed(3)} />
            <StatBox label="Integrity" value={`${drift.integrity}%`} />
            <StatBox label="Off-hours %" value={`${drift.offHoursPct.toFixed(1)}%`} />
            <StatBox label="Novel categories" value={drift.novelCategories} />
            <StatBox label="Exfil attempts" value={drift.exfiltrationAttempts} />
          </div>
          <div className="rounded-xl border border-edge bg-panel p-4 text-sm space-y-1">
            <div>
              <span className="text-faint">Baseline avg sensitivity</span>{' '}
              <span className="font-mono text-bright">{drift.baselineAvgSens.toFixed(2)}</span>
            </div>
            <div>
              <span className="text-faint">Current avg sensitivity</span>{' '}
              <span className="font-mono text-bright">{drift.currentAvgSens.toFixed(2)}</span>
            </div>
            <div>
              <span className="text-faint">Actions observed</span>{' '}
              <span className="font-mono text-bright">{drift.actionCount}</span>
            </div>
            <div>
              <Link to="/graph" className="text-accent hover:underline text-xs">
                Open trust graph for this agent →
              </Link>
            </div>
          </div>
        </div>
      )}

      <Modal open={confirmDelete} onClose={() => setConfirmDelete(false)} title="Delete agent">
        <p className="text-sm text-dim mb-4">
          Permanently delete <span className="font-mono text-bright">{agent.name}</span> ({agent.id}) and its
          policy? This cannot be undone.
        </p>
        <div className="flex justify-end gap-2">
          <button type="button" onClick={() => setConfirmDelete(false)} className="rounded-md border border-edge px-3 py-1.5 text-sm text-dim">
            Cancel
          </button>
          <button type="button" onClick={() => void doDelete()} className="rounded-md border border-danger/50 bg-danger/15 px-3 py-1.5 text-sm text-danger font-semibold">
            Delete
          </button>
        </div>
      </Modal>
    </div>
  )
}
