import { useCallback, useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import * as api from '../api/client'
import { apiErrorMessage } from '../api/client'
import type { AgentPolicyBody, AgentSummary, PolicyCatalog, PolicyTemplate } from '../api/types'
import { useAuth } from '../auth/AuthContext'
import { CheckList } from '../components/CheckList'
import { EmptyState } from '../components/EmptyState'
import { ErrorPanel } from '../components/ErrorPanel'
import { LoadingPanel } from '../components/LoadingPanel'
import { Modal } from '../components/Modal'
import { PageHeader } from '../components/PageHeader'
import { RiskGauge } from '../components/RiskGauge'
import { StatusChip } from '../components/StatusChip'
import { useToast } from '../components/Toast'

function IntegrityBar({ value }: { value: number }) {
  const color = value >= 85 ? '#22c55e' : value >= 60 ? '#f59e0b' : '#ef4444'
  return (
    <div className="flex items-center gap-2">
      <div className="flex-1 h-1.5 rounded-full bg-edge overflow-hidden">
        <div className="h-full rounded-full" style={{ width: `${value}%`, backgroundColor: color }} />
      </div>
      <span className="font-mono text-xs text-dim">{value}%</span>
    </div>
  )
}

function NewAgentModal({
  open,
  onClose,
  templates,
  catalog,
  onCreated,
}: {
  open: boolean
  onClose: () => void
  templates: PolicyTemplate[]
  catalog: PolicyCatalog | null
  onCreated: (a: AgentSummary) => void
}) {
  const toast = useToast()
  const [templateKey, setTemplateKey] = useState('')
  const [form, setForm] = useState<AgentPolicyBody>({
    id: '',
    name: '',
    icon: '',
    roleTitle: '',
    description: '',
    tools: [],
    allowed: [],
    restricted: [],
    restrictActions: [],
    clearance: 3,
  })
  const [busy, setBusy] = useState(false)

  const applyTemplate = (key: string) => {
    setTemplateKey(key)
    const t = templates.find((x) => x.key === key)
    if (!t) return
    setForm({
      id: t.key,
      name: t.name,
      icon: t.icon,
      roleTitle: t.roleTitle,
      description: t.description,
      tools: [...t.tools],
      allowed: [...t.allowed],
      restricted: [...t.restricted],
      restrictActions: [...t.restrictActions],
      clearance: t.clearance,
    })
  }

  const submit = async () => {
    setBusy(true)
    try {
      const created = await api.createAgent(form)
      toast.success(`Agent "${created.name}" registered`)
      onCreated(created)
      onClose()
    } catch (err) {
      toast.error(apiErrorMessage(err))
    } finally {
      setBusy(false)
    }
  }

  const set = <K extends keyof AgentPolicyBody>(key: K, value: AgentPolicyBody[K]) =>
    setForm((f) => ({ ...f, [key]: value }))

  return (
    <Modal open={open} onClose={onClose} title="Register new agent" wide>
      <div className="space-y-3">
        <div>
          <label className="block text-xs uppercase tracking-wide text-faint mb-1">Policy template</label>
          <select
            value={templateKey}
            onChange={(e) => applyTemplate(e.target.value)}
            className="w-full rounded-md border border-edge bg-base px-3 py-2 text-sm text-bright"
          >
            <option value="">— custom —</option>
            {templates.map((t) => (
              <option key={t.key} value={t.key}>
                {t.name} ({t.roleTitle})
              </option>
            ))}
          </select>
        </div>
        <div className="grid grid-cols-2 gap-3">
          <div>
            <label className="block text-xs uppercase tracking-wide text-faint mb-1">ID</label>
            <input
              value={form.id ?? ''}
              onChange={(e) => set('id', e.target.value)}
              placeholder="e.g. analyst-bot"
              className="w-full rounded-md border border-edge bg-base px-3 py-2 text-sm font-mono text-bright"
            />
          </div>
          <div>
            <label className="block text-xs uppercase tracking-wide text-faint mb-1">Name</label>
            <input
              value={form.name}
              onChange={(e) => set('name', e.target.value)}
              className="w-full rounded-md border border-edge bg-base px-3 py-2 text-sm text-bright"
            />
          </div>
          <div>
            <label className="block text-xs uppercase tracking-wide text-faint mb-1">Role title</label>
            <input
              value={form.roleTitle}
              onChange={(e) => set('roleTitle', e.target.value)}
              className="w-full rounded-md border border-edge bg-base px-3 py-2 text-sm text-bright"
            />
          </div>
          <div>
            <label className="block text-xs uppercase tracking-wide text-faint mb-1">Icon</label>
            <input
              value={form.icon ?? ''}
              onChange={(e) => set('icon', e.target.value)}
              maxLength={8}
              className="w-full rounded-md border border-edge bg-base px-3 py-2 text-sm text-bright"
            />
          </div>
        </div>
        <div>
          <label className="block text-xs uppercase tracking-wide text-faint mb-1">Clearance (1–5)</label>
          <input
            type="range"
            min={1}
            max={5}
            value={form.clearance}
            onChange={(e) => set('clearance', Number(e.target.value))}
            className="w-full accent-[#22d3ee]"
          />
          <div className="font-mono text-xs text-dim">{form.clearance}</div>
        </div>
        {catalog && (
          <div className="space-y-3">
            <CheckList
              title="Tools"
              options={catalog.tools.map((t) => ({ code: t }))}
              selected={form.tools}
              onChange={(next) => set('tools', next)}
            />
            <CheckList
              title="Allowed categories"
              options={catalog.categories.map((c) => ({ code: c.code, label: c.label }))}
              selected={form.allowed}
              onChange={(next) => set('allowed', next)}
            />
            <CheckList
              title="Restricted categories"
              options={catalog.categories.map((c) => ({ code: c.code, label: c.label }))}
              selected={form.restricted}
              onChange={(next) => set('restricted', next)}
            />
            <CheckList
              title="Restricted actions"
              options={catalog.restrictActions.map((r) => ({ code: r }))}
              selected={form.restrictActions}
              onChange={(next) => set('restrictActions', next)}
            />
          </div>
        )}
        <div className="flex justify-end gap-2 pt-2 border-t border-edge">
          <button type="button" onClick={onClose} className="rounded-md border border-edge px-3 py-1.5 text-sm text-dim hover:text-bright">
            Cancel
          </button>
          <button
            type="button"
            onClick={() => void submit()}
            disabled={busy || !form.id || !form.name || !form.roleTitle || form.tools.length === 0}
            className="rounded-md border border-accent/50 bg-accent/15 px-3 py-1.5 text-sm text-accent font-semibold disabled:opacity-50"
          >
            {busy ? 'Creating…' : 'Create agent'}
          </button>
        </div>
      </div>
    </Modal>
  )
}

export function Agents() {
  const { hasPermission } = useAuth()
  const toast = useToast()
  const [list, setList] = useState<AgentSummary[]>([])
  const [catalog, setCatalog] = useState<PolicyCatalog | null>(null)
  const [templates, setTemplates] = useState<PolicyTemplate[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [modalOpen, setModalOpen] = useState(false)
  const [reloadKey, setReloadKey] = useState(0)

  useEffect(() => {
    let cancelled = false
    Promise.all([api.agents(), api.policyCatalog(), api.policyTemplates()])
      .then(([a, c, t]) => {
        if (cancelled) return
        setList(a)
        setCatalog(c)
        setTemplates(t)
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

  const act = useCallback(
    async (a: AgentSummary, kind: 'pause' | 'resume' | 'isolate') => {
      try {
        const updated =
          kind === 'isolate'
            ? await api.isolate(a.id)
            : await api.agentStatus(a.id, kind === 'pause' ? 'PAUSED' : 'ACTIVE')
        setList((prev) => prev.map((x) => (x.id === updated.id ? updated : x)))
        toast.success(
          kind === 'isolate'
            ? `${a.name} isolated`
            : kind === 'pause'
              ? `${a.name} paused`
              : `${a.name} resumed`,
        )
      } catch (err) {
        toast.error(apiErrorMessage(err))
      }
    },
    [toast],
  )

  if (loading) return <LoadingPanel label="Loading agents…" />
  if (error) return <ErrorPanel message={error} onRetry={() => setReloadKey((k) => k + 1)} />

  return (
    <div>
      <PageHeader
        title="Agents"
        description="Registered autonomous agents and their gateway policies"
        actions={
          hasPermission('agents.write') ? (
            <button
              type="button"
              onClick={() => setModalOpen(true)}
              className="rounded-md border border-accent/50 bg-accent/15 px-3 py-1.5 text-sm text-accent font-semibold hover:bg-accent/25"
            >
              New agent
            </button>
          ) : null
        }
      />
      {list.length === 0 ? (
        <EmptyState title="No agents" description="Register an agent to start routing its actions through ARGUS." />
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-3 gap-4">
          {list.map((a) => (
            <div key={a.id} className="rounded-xl border border-edge bg-panel p-4 flex flex-col gap-3">
              <div className="flex items-start justify-between gap-2">
                <div className="flex items-center gap-2 min-w-0">
                  <span className="text-xl">{a.icon || '·'}</span>
                  <div className="min-w-0">
                    <Link to={`/agents/${a.id}`} className="text-bright font-semibold hover:text-accent block truncate">
                      {a.name}
                    </Link>
                    <div className="text-xs text-faint truncate">{a.roleTitle}</div>
                  </div>
                </div>
                <StatusChip status={a.status} />
              </div>
              <div className="flex items-center gap-2 text-xs text-dim">
                <span className="text-faint">Last risk</span>
                <RiskGauge value={a.lastRisk} width={80} />
                <span className="font-mono text-bright">{a.lastRisk}</span>
              </div>
              <IntegrityBar value={a.integrity} />
              <div className="flex items-center justify-between text-xs text-dim">
                <span>
                  Tools <span className="font-mono text-bright">{a.tools.length}</span>
                </span>
                <span>
                  Blocked resources <span className="font-mono text-bright">{a.blockedResources.length}</span>
                </span>
                <span>
                  Actions <span className="font-mono text-bright">{a.actions}</span>
                </span>
              </div>
              {hasPermission('alerts.act') && (
                <div className="flex gap-2 pt-1 border-t border-edge">
                  {a.status === 'ACTIVE' ? (
                    <button
                      type="button"
                      onClick={() => void act(a, 'pause')}
                      className="flex-1 rounded-md border border-edge px-2 py-1 text-xs text-dim hover:text-warn hover:border-warn/40"
                    >
                      Pause
                    </button>
                  ) : (
                    <button
                      type="button"
                      onClick={() => void act(a, 'resume')}
                      disabled={a.status === 'ISOLATED'}
                      className="flex-1 rounded-md border border-edge px-2 py-1 text-xs text-dim hover:text-ok hover:border-ok/40 disabled:opacity-40"
                    >
                      Resume
                    </button>
                  )}
                  <button
                    type="button"
                    onClick={() => void act(a, 'isolate')}
                    disabled={a.status === 'ISOLATED'}
                    className="flex-1 rounded-md border border-edge px-2 py-1 text-xs text-dim hover:text-danger hover:border-danger/40 disabled:opacity-40"
                  >
                    Isolate
                  </button>
                </div>
              )}
            </div>
          ))}
        </div>
      )}
      <NewAgentModal
        open={modalOpen}
        onClose={() => setModalOpen(false)}
        templates={templates}
        catalog={catalog}
        onCreated={(created) => setList((prev) => [...prev, created])}
      />
    </div>
  )
}
