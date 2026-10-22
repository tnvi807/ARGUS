import { NavLink } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'

interface NavItem {
  to: string
  label: string
  permission: string
  badgeKey?: 'approvals' | 'alerts'
}

const NAV: NavItem[] = [
  { to: '/', label: 'Dashboard', permission: 'dashboard.read' },
  { to: '/feed', label: 'Live Feed', permission: 'events.read' },
  { to: '/agents', label: 'Agents', permission: 'agents.read' },
  { to: '/graph', label: 'Trust Graph', permission: 'agents.read' },
  { to: '/approvals', label: 'Approvals', permission: 'approvals.read', badgeKey: 'approvals' },
  { to: '/alerts', label: 'Alerts', permission: 'alerts.read', badgeKey: 'alerts' },
  { to: '/audit', label: 'Audit Trail', permission: 'audit.read' },
  { to: '/analysis', label: 'Analysis', permission: 'analysis.create' },
  { to: '/demo', label: 'Demo Lab', permission: 'dashboard.read' },
  { to: '/admin', label: 'Administration', permission: 'admin.manage' },
]

export function Sidebar({
  badges,
}: {
  badges: { approvals: number; alerts: number }
}) {
  const { user, logout, hasPermission } = useAuth()

  return (
    <aside className="w-60 shrink-0 border-r border-edge bg-panel flex flex-col h-screen sticky top-0">
      <div className="px-4 py-4 border-b border-edge">
        <div className="text-lg font-bold tracking-[0.25em] text-accent">ARGUS</div>
        <div className="text-[10px] uppercase tracking-widest text-faint mt-0.5">Autonomous Risk Guard</div>
      </div>
      <nav className="flex-1 overflow-y-auto py-3 px-2 space-y-0.5">
        {NAV.filter((item) => hasPermission(item.permission)).map((item) => {
          const count = item.badgeKey ? badges[item.badgeKey] : 0
          return (
            <NavLink
              key={item.to}
              to={item.to}
              end={item.to === '/'}
              className={({ isActive }) =>
                `flex items-center justify-between rounded-md px-3 py-2 text-sm transition-colors ${
                  isActive
                    ? 'bg-accent/10 text-accent border border-accent/30'
                    : 'text-dim hover:bg-panel-2 hover:text-bright border border-transparent'
                }`
              }
            >
              <span>{item.label}</span>
              {count > 0 && (
                <span className="font-mono text-[10px] rounded px-1.5 py-0.5 bg-warn/20 text-warn border border-warn/40">
                  {count}
                </span>
              )}
            </NavLink>
          )
        })}
      </nav>
      <div className="border-t border-edge px-4 py-3">
        <div className="flex items-center justify-between gap-2">
          <div className="min-w-0">
            <div className="text-sm text-bright truncate">{user?.displayName || user?.username}</div>
            <div className="text-[10px] text-faint truncate">
              {(user?.roles || []).join(' · ') || 'no roles'}
            </div>
          </div>
          <button
            type="button"
            onClick={() => void logout()}
            className="shrink-0 rounded-md border border-edge px-2 py-1 text-xs text-dim hover:text-danger hover:border-danger/40"
          >
            Logout
          </button>
        </div>
      </div>
    </aside>
  )
}
