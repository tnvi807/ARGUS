import { useCallback, useEffect, useState } from 'react'
import * as api from '../api/client'
import { apiErrorMessage } from '../api/client'
import type { AdminUserView, RoleView } from '../api/types'
import { useAuth } from '../auth/AuthContext'
import { ErrorPanel } from '../components/ErrorPanel'
import { LoadingPanel } from '../components/LoadingPanel'
import { Modal } from '../components/Modal'
import { PageHeader } from '../components/PageHeader'
import { useToast } from '../components/Toast'

const USERNAME_RE = /^[a-z0-9][a-z0-9._-]{2,31}$/

function passwordProblems(password: string): string | null {
  if (password.length < 12) return 'Password must be at least 12 characters'
  if (!/[A-Za-z]/.test(password) || !/\d/.test(password)) {
    return 'Password must contain at least a letter and a digit'
  }
  return null
}

function RoleChips({ roles }: { roles: string[] }) {
  return (
    <div className="flex flex-wrap gap-1">
      {roles.map((r) => (
        <span key={r} className="rounded border border-accent/40 bg-accent/10 px-1.5 py-0.5 text-[10px] font-semibold text-accent">
          {r}
        </span>
      ))}
    </div>
  )
}

function CreateUserModal({
  open,
  onClose,
  allRoles,
  onCreated,
}: {
  open: boolean
  onClose: () => void
  allRoles: string[]
  onCreated: () => void
}) {
  const toast = useToast()
  const [username, setUsername] = useState('')
  const [displayName, setDisplayName] = useState('')
  const [password, setPassword] = useState('')
  const [roles, setRoles] = useState<string[]>(['VIEWER'])
  const [busy, setBusy] = useState(false)

  const submit = async () => {
    if (!USERNAME_RE.test(username.trim().toLowerCase())) {
      toast.error('Username must be lowercase letters, digits, dot, underscore or dash (3-32 chars)')
      return
    }
    const pwProblem = passwordProblems(password)
    if (pwProblem) {
      toast.error(pwProblem)
      return
    }
    if (roles.length === 0) {
      toast.error('Select at least one role')
      return
    }
    setBusy(true)
    try {
      const created = await api.adminCreateUser({
        username: username.trim().toLowerCase(),
        password,
        displayName: displayName.trim() || undefined,
        roles,
      })
      toast.success(`User ${created.username} created`)
      setUsername('')
      setDisplayName('')
      setPassword('')
      setRoles(['VIEWER'])
      onCreated()
      onClose()
    } catch (err) {
      toast.error(apiErrorMessage(err))
    } finally {
      setBusy(false)
    }
  }

  return (
    <Modal open={open} onClose={onClose} title="Create user">
      <div className="space-y-3">
        <div>
          <label className="block text-xs uppercase tracking-wide text-faint mb-1">Username</label>
          <input
            value={username}
            onChange={(e) => setUsername(e.target.value)}
            className="w-full rounded-md border border-edge bg-base px-3 py-2 text-sm font-mono text-bright"
          />
        </div>
        <div>
          <label className="block text-xs uppercase tracking-wide text-faint mb-1">Display name</label>
          <input
            value={displayName}
            onChange={(e) => setDisplayName(e.target.value)}
            className="w-full rounded-md border border-edge bg-base px-3 py-2 text-sm text-bright"
          />
        </div>
        <div>
          <label className="block text-xs uppercase tracking-wide text-faint mb-1">Password</label>
          <input
            type="password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            className="w-full rounded-md border border-edge bg-base px-3 py-2 text-sm text-bright"
          />
          <p className="text-[11px] text-faint mt-1">Min 12 characters, at least one letter and one digit.</p>
        </div>
        <div>
          <label className="block text-xs uppercase tracking-wide text-faint mb-1">Roles</label>
          <div className="flex flex-wrap gap-3">
            {allRoles.map((r) => (
              <label key={r} className="flex items-center gap-1.5 text-sm text-dim cursor-pointer">
                <input
                  type="checkbox"
                  checked={roles.includes(r)}
                  onChange={() =>
                    setRoles((prev) => (prev.includes(r) ? prev.filter((x) => x !== r) : [...prev, r]))
                  }
                  className="accent-[#22d3ee]"
                />
                {r}
              </label>
            ))}
          </div>
        </div>
        <div className="flex justify-end gap-2 pt-2 border-t border-edge">
          <button type="button" onClick={onClose} className="rounded-md border border-edge px-3 py-1.5 text-sm text-dim">
            Cancel
          </button>
          <button
            type="button"
            onClick={() => void submit()}
            disabled={busy}
            className="rounded-md border border-accent/50 bg-accent/15 px-3 py-1.5 text-sm text-accent font-semibold disabled:opacity-50"
          >
            {busy ? 'Creating…' : 'Create'}
          </button>
        </div>
      </div>
    </Modal>
  )
}

export function Admin() {
  const { user: me } = useAuth()
  const toast = useToast()
  const [users, setUsers] = useState<AdminUserView[]>([])
  const [rolesMap, setRolesMap] = useState<Record<string, RoleView>>({})
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [reloadKey, setReloadKey] = useState(0)
  const [createOpen, setCreateOpen] = useState(false)
  const [editTarget, setEditTarget] = useState<AdminUserView | null>(null)
  const [pwTarget, setPwTarget] = useState<AdminUserView | null>(null)
  const [editName, setEditName] = useState('')
  const [editRoles, setEditRoles] = useState<string[]>([])
  const [editEnabled, setEditEnabled] = useState(true)
  const [newPassword, setNewPassword] = useState('')
  const [busy, setBusy] = useState(false)

  const allRoles = Object.keys(rolesMap)

  useEffect(() => {
    let cancelled = false
    Promise.all([api.adminUsers(), api.adminRoles()])
      .then(([u, r]) => {
        if (cancelled) return
        setUsers(u)
        setRolesMap(r)
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

  const refresh = useCallback(() => setReloadKey((k) => k + 1), [])

  const openEdit = (u: AdminUserView) => {
    setEditTarget(u)
    setEditName(u.displayName)
    setEditRoles([...u.roles])
    setEditEnabled(u.enabled)
  }

  const saveEdit = async () => {
    if (!editTarget?.id) return
    setBusy(true)
    try {
      const updated = await api.adminUpdateUser(editTarget.id, {
        displayName: editName,
        roles: editRoles,
        enabled: editEnabled,
      })
      setUsers((prev) => prev.map((x) => (x.id === updated.id ? updated : x)))
      toast.success(`User ${updated.username} updated`)
      setEditTarget(null)
    } catch (err) {
      toast.error(apiErrorMessage(err))
    } finally {
      setBusy(false)
    }
  }

  const savePassword = async () => {
    if (!pwTarget?.id) return
    const problem = passwordProblems(newPassword)
    if (problem) {
      toast.error(problem)
      return
    }
    setBusy(true)
    try {
      const updated = await api.adminPassword(pwTarget.id, newPassword)
      setUsers((prev) => prev.map((x) => (x.id === updated.id ? updated : x)))
      toast.success(`Password reset for ${updated.username} — all sessions revoked`)
      setPwTarget(null)
      setNewPassword('')
    } catch (err) {
      toast.error(apiErrorMessage(err))
    } finally {
      setBusy(false)
    }
  }

  const unlock = async (u: AdminUserView) => {
    if (!u.id) return
    try {
      const updated = await api.adminUnlock(u.id)
      setUsers((prev) => prev.map((x) => (x.id === updated.id ? updated : x)))
      toast.success(`User ${updated.username} unlocked`)
    } catch (err) {
      toast.error(apiErrorMessage(err))
    }
  }

  if (loading) return <LoadingPanel label="Loading users…" />
  if (error) return <ErrorPanel message={error} onRetry={refresh} />

  return (
    <div className="space-y-6">
      <PageHeader
        title="Administration"
        description="User accounts, roles and least-privilege permissions"
        actions={
          <button
            type="button"
            onClick={() => setCreateOpen(true)}
            className="rounded-md border border-accent/50 bg-accent/15 px-3 py-1.5 text-sm text-accent font-semibold"
          >
            Create user
          </button>
        }
      />

      <div className="rounded-xl border border-edge bg-panel overflow-x-auto">
        <table className="w-full text-sm">
          <thead>
            <tr className="text-left text-[10px] uppercase tracking-wide text-faint border-b border-edge">
              <th className="px-4 py-2 font-normal">Username</th>
              <th className="px-2 py-2 font-normal">Display name</th>
              <th className="px-2 py-2 font-normal">Roles</th>
              <th className="px-2 py-2 font-normal">Enabled</th>
              <th className="px-2 py-2 font-normal">Failed</th>
              <th className="px-2 py-2 font-normal">Locked</th>
              <th className="px-2 py-2 font-normal">Last login</th>
              <th className="px-4 py-2 font-normal text-right">Actions</th>
            </tr>
          </thead>
          <tbody>
            {users.map((u) => (
              <tr key={u.id} className="border-b border-edge last:border-b-0">
                <td className="px-4 py-2 font-mono text-bright">
                  {u.username}
                  {u.username === me?.username && <span className="ml-2 text-[10px] text-faint">(you)</span>}
                </td>
                <td className="px-2 py-2 text-dim">{u.displayName}</td>
                <td className="px-2 py-2"><RoleChips roles={u.roles} /></td>
                <td className="px-2 py-2">
                  <span className={u.enabled ? 'text-ok' : 'text-danger'}>{u.enabled ? 'yes' : 'no'}</span>
                </td>
                <td className="px-2 py-2 font-mono text-dim">{u.failedAttempts}</td>
                <td className="px-2 py-2">
                  <span className={u.locked ? 'text-danger' : 'text-faint'}>{u.locked ? 'locked' : '—'}</span>
                </td>
                <td className="px-2 py-2 font-mono text-xs text-faint">
                  {u.lastLoginAt ? new Date(u.lastLoginAt).toLocaleString() : '—'}
                </td>
                <td className="px-4 py-2 text-right whitespace-nowrap">
                  <button
                    type="button"
                    onClick={() => openEdit(u)}
                    className="rounded-md border border-edge px-2 py-1 text-xs text-dim hover:text-accent mr-1"
                  >
                    Edit
                  </button>
                  <button
                    type="button"
                    onClick={() => {
                      setPwTarget(u)
                      setNewPassword('')
                    }}
                    className="rounded-md border border-edge px-2 py-1 text-xs text-dim hover:text-warn mr-1"
                  >
                    Reset password
                  </button>
                  <button
                    type="button"
                    onClick={() => void unlock(u)}
                    disabled={!u.locked && u.failedAttempts === 0}
                    className="rounded-md border border-edge px-2 py-1 text-xs text-dim hover:text-ok disabled:opacity-40"
                  >
                    Unlock
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      <div className="rounded-xl border border-edge bg-panel p-4">
        <div className="text-xs uppercase tracking-widest text-faint mb-3">Roles &amp; permissions</div>
        <div className="grid grid-cols-1 md:grid-cols-3 gap-3">
          {Object.values(rolesMap).map((role) => (
            <div key={role.name} className="rounded-lg border border-edge bg-panel-2/40 p-3">
              <div className="flex items-center gap-2 mb-1">
                <span className="rounded border border-accent/40 bg-accent/10 px-1.5 py-0.5 text-[10px] font-semibold text-accent">
                  {role.name}
                </span>
              </div>
              <p className="text-xs text-dim mb-2">{role.description}</p>
              <div className="flex flex-wrap gap-1">
                {role.permissions.map((p) => (
                  <span key={p} className="rounded border border-edge bg-base px-1.5 py-0.5 text-[10px] font-mono text-faint">
                    {p}
                  </span>
                ))}
              </div>
            </div>
          ))}
        </div>
      </div>

      <CreateUserModal
        open={createOpen}
        onClose={() => setCreateOpen(false)}
        allRoles={allRoles.length > 0 ? allRoles : ['ADMIN', 'ANALYST', 'VIEWER']}
        onCreated={refresh}
      />

      <Modal open={editTarget !== null} onClose={() => setEditTarget(null)} title={`Edit ${editTarget?.username ?? ''}`}>
        <div className="space-y-3">
          <div>
            <label className="block text-xs uppercase tracking-wide text-faint mb-1">Display name</label>
            <input
              value={editName}
              onChange={(e) => setEditName(e.target.value)}
              className="w-full rounded-md border border-edge bg-base px-3 py-2 text-sm text-bright"
            />
          </div>
          <div>
            <label className="block text-xs uppercase tracking-wide text-faint mb-1">Roles</label>
            <div className="flex flex-wrap gap-3">
              {allRoles.map((r) => (
                <label key={r} className="flex items-center gap-1.5 text-sm text-dim cursor-pointer">
                  <input
                    type="checkbox"
                    checked={editRoles.includes(r)}
                    onChange={() =>
                      setEditRoles((prev) => (prev.includes(r) ? prev.filter((x) => x !== r) : [...prev, r]))
                    }
                    className="accent-[#22d3ee]"
                  />
                  {r}
                </label>
              ))}
            </div>
          </div>
          <label className="flex items-center gap-2 text-sm text-dim cursor-pointer">
            <input
              type="checkbox"
              checked={editEnabled}
              onChange={(e) => setEditEnabled(e.target.checked)}
              className="accent-[#22d3ee]"
            />
            Enabled
          </label>
          <div className="flex justify-end gap-2 pt-2 border-t border-edge">
            <button type="button" onClick={() => setEditTarget(null)} className="rounded-md border border-edge px-3 py-1.5 text-sm text-dim">
              Cancel
            </button>
            <button
              type="button"
              onClick={() => void saveEdit()}
              disabled={busy}
              className="rounded-md border border-accent/50 bg-accent/15 px-3 py-1.5 text-sm text-accent font-semibold disabled:opacity-50"
            >
              {busy ? 'Saving…' : 'Save'}
            </button>
          </div>
        </div>
      </Modal>

      <Modal open={pwTarget !== null} onClose={() => setPwTarget(null)} title={`Reset password — ${pwTarget?.username ?? ''}`}>
        <div className="space-y-3">
          <div>
            <label className="block text-xs uppercase tracking-wide text-faint mb-1">New password</label>
            <input
              type="password"
              value={newPassword}
              onChange={(e) => setNewPassword(e.target.value)}
              className="w-full rounded-md border border-edge bg-base px-3 py-2 text-sm text-bright"
            />
            <p className="text-[11px] text-faint mt-1">
              Min 12 characters with a letter and a digit. Resetting revokes all sessions.
            </p>
          </div>
          <div className="flex justify-end gap-2 pt-2 border-t border-edge">
            <button type="button" onClick={() => setPwTarget(null)} className="rounded-md border border-edge px-3 py-1.5 text-sm text-dim">
              Cancel
            </button>
            <button
              type="button"
              onClick={() => void savePassword()}
              disabled={busy}
              className="rounded-md border border-warn/50 bg-warn/10 px-3 py-1.5 text-sm text-warn font-semibold disabled:opacity-50"
            >
              {busy ? 'Resetting…' : 'Reset password'}
            </button>
          </div>
        </div>
      </Modal>
    </div>
  )
}
