import { useState } from 'react'
import type { FormEvent } from 'react'
import { useNavigate } from 'react-router-dom'
import { apiErrorMessage } from '../api/client'
import { useAuth } from '../auth/AuthContext'

export function Login() {
  const { login } = useAuth()
  const navigate = useNavigate()
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  const onSubmit = async (e: FormEvent) => {
    e.preventDefault()
    setBusy(true)
    setError(null)
    try {
      await login(username.trim(), password)
      navigate('/', { replace: true })
    } catch (err) {
      setError(apiErrorMessage(err))
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="min-h-screen flex items-center justify-center p-4 bg-base">
      <div className="w-full max-w-sm">
        <div className="text-center mb-6">
          <div className="text-3xl font-bold tracking-[0.3em] text-accent">ARGUS</div>
          <div className="text-sm text-dim mt-2">Autonomous Risk Guard for Untrusted Systems</div>
        </div>
        <form
          onSubmit={onSubmit}
          className="rounded-xl border border-edge bg-panel p-6 space-y-4 shadow-2xl"
        >
          <div>
            <label htmlFor="username" className="block text-xs uppercase tracking-wide text-faint mb-1">
              Username
            </label>
            <input
              id="username"
              value={username}
              onChange={(e) => setUsername(e.target.value)}
              autoComplete="username"
              required
              className="w-full rounded-md border border-edge bg-base px-3 py-2 text-sm text-bright focus:outline-none focus:border-accent/60"
            />
          </div>
          <div>
            <label htmlFor="password" className="block text-xs uppercase tracking-wide text-faint mb-1">
              Password
            </label>
            <input
              id="password"
              type="password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              autoComplete="current-password"
              required
              className="w-full rounded-md border border-edge bg-base px-3 py-2 text-sm text-bright focus:outline-none focus:border-accent/60"
            />
          </div>
          {error && (
            <div className="rounded-md border border-danger/40 bg-danger/10 px-3 py-2 text-sm text-danger break-words">
              {error}
            </div>
          )}
          <button
            type="submit"
            disabled={busy}
            className="w-full rounded-md bg-accent/15 border border-accent/50 text-accent font-semibold py-2 text-sm hover:bg-accent/25 disabled:opacity-50"
          >
            {busy ? 'Signing in…' : 'Sign in'}
          </button>
          <p className="text-[11px] text-faint text-center">
            Seeded accounts: <span className="font-mono">admin</span> · <span className="font-mono">analyst</span> ·{' '}
            <span className="font-mono">viewer</span> (passwords per environment)
          </p>
        </form>
      </div>
    </div>
  )
}
