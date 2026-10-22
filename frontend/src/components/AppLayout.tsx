import { useCallback, useEffect, useRef, useState } from 'react'
import { Outlet } from 'react-router-dom'
import * as api from '../api/client'
import { useAuth } from '../auth/AuthContext'
import { useSSE } from '../hooks/useSSE'
import { Sidebar } from './Sidebar'

export function AppLayout() {
  const { hasPermission } = useAuth()
  const [badges, setBadges] = useState({ approvals: 0, alerts: 0 })
  const canSeeEvents = hasPermission('events.read')
  const canSeeDashboard = hasPermission('dashboard.read')

  const refreshBadges = useCallback(async () => {
    if (!canSeeDashboard) return
    try {
      const summary = await api.dashboard()
      setBadges({ approvals: summary.alerts.pendingApprovals, alerts: summary.alerts.open })
    } catch {
      // badge counts are best-effort
    }
  }, [canSeeDashboard])

  useEffect(() => {
    if (!canSeeDashboard) return
    let cancelled = false
    api
      .dashboard()
      .then((summary) => {
        if (!cancelled) setBadges({ approvals: summary.alerts.pendingApprovals, alerts: summary.alerts.open })
      })
      .catch(() => {})
    return () => {
      cancelled = true
    }
  }, [canSeeDashboard])

  const refreshRef = useRef(refreshBadges)
  useEffect(() => {
    refreshRef.current = refreshBadges
  }, [refreshBadges])

  const connected = useSSE(
    useCallback((name: string, data: unknown) => {
      if (name === 'reset') {
        void refreshRef.current()
        return
      }
      if (name === 'alert' && data && typeof data === 'object') {
        const alert = (data as { alert?: { status?: string } }).alert
        if (alert?.status === 'OPEN') {
          setBadges((b) => ({ ...b, alerts: b.alerts + 1 }))
        } else if (alert?.status === 'REVIEWED') {
          setBadges((b) => ({ ...b, alerts: Math.max(0, b.alerts - 1) }))
        }
        return
      }
      if (name === 'approval' && data && typeof data === 'object') {
        const approval = (data as { approval?: { status?: string } }).approval
        if (approval?.status === 'PENDING') {
          setBadges((b) => ({ ...b, approvals: b.approvals + 1 }))
        } else if (approval?.status === 'APPROVED' || approval?.status === 'DENIED') {
          setBadges((b) => ({ ...b, approvals: Math.max(0, b.approvals - 1) }))
        }
      }
    }, []),
    canSeeEvents,
  )

  return (
    <div className="min-h-screen flex bg-base">
      <Sidebar badges={badges} />
      <div className="flex-1 min-w-0 flex flex-col">
        <header className="h-10 shrink-0 border-b border-edge bg-panel flex items-center justify-end px-4 gap-2">
          <span
            className={`h-2 w-2 rounded-full ${connected ? 'bg-ok' : 'bg-faint'}`}
            title={connected ? 'Live stream connected' : 'Live stream offline'}
          />
          <span className="text-[10px] uppercase tracking-widest text-faint">
            {connected ? 'Live' : 'Offline'}
          </span>
        </header>
        <main className="flex-1 p-4 lg:p-6 overflow-x-hidden">
          <Outlet />
        </main>
      </div>
    </div>
  )
}
