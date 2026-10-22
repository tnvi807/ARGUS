import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react'
import type { ReactNode } from 'react'
import { Navigate, Outlet } from 'react-router-dom'
import * as api from '../api/client'
import type { UserView } from '../api/types'
import { LoadingPanel } from '../components/LoadingPanel'

interface AuthContextValue {
  user: UserView | null
  loading: boolean
  login: (username: string, password: string) => Promise<void>
  logout: () => Promise<void>
  hasPermission: (code: string) => boolean
}

const AuthContext = createContext<AuthContextValue | null>(null)

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<UserView | null>(null)
  const [loading, setLoading] = useState(() => api.getToken() !== null)

  useEffect(() => {
    const token = api.getToken()
    if (!token) {
      return
    }
    let cancelled = false
    api
      .me()
      .then((u) => {
        if (!cancelled) {
          setUser(u)
        }
      })
      .catch(() => {
        if (!cancelled) {
          api.setToken(null)
          setUser(null)
        }
      })
      .finally(() => {
        if (!cancelled) {
          setLoading(false)
        }
      })
    return () => {
      cancelled = true
    }
  }, [])

  useEffect(() => {
    const onUnauthorized = () => {
      api.setToken(null)
      setUser(null)
      setLoading(false)
    }
    window.addEventListener('argus:unauthorized', onUnauthorized)
    return () => window.removeEventListener('argus:unauthorized', onUnauthorized)
  }, [])

  const login = useCallback(async (username: string, password: string) => {
    const res = await api.login(username, password)
    api.setToken(res.token)
    setUser(res.user)
    setLoading(false)
  }, [])

  const logout = useCallback(async () => {
    try {
      await api.logout()
    } catch {
      // local logout proceeds even if the server call fails
    }
    api.setToken(null)
    setUser(null)
  }, [])

  const hasPermission = useCallback(
    (code: string) => user?.permissions.includes(code) ?? false,
    [user],
  )

  const value = useMemo(
    () => ({ user, loading, login, logout, hasPermission }),
    [user, loading, login, logout, hasPermission],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext)
  if (!ctx) {
    throw new Error('useAuth must be used within AuthProvider')
  }
  return ctx
}

export function RequireAuth() {
  const { user, loading } = useAuth()
  if (loading) {
    return <LoadingPanel label="Restoring session…" />
  }
  if (!user) {
    return <Navigate to="/login" replace />
  }
  return <Outlet />
}

export function RequirePermission({ code, children }: { code: string; children?: ReactNode }) {
  const { hasPermission } = useAuth()
  if (!hasPermission(code)) {
    return (
      <div className="rounded-xl border border-edge bg-panel p-8 max-w-lg mx-auto mt-16 text-center">
        <div className="text-2xl mb-2 text-warn">Access denied</div>
        <p className="text-dim mb-1">
          Your account does not hold the <span className="font-mono text-bright">{code}</span> permission
          required for this page.
        </p>
        <p className="text-faint text-sm">Contact an administrator if you believe this is wrong.</p>
      </div>
    )
  }
  return <>{children ?? <Outlet />}</>
}
