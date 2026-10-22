import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom'
import { AppLayout } from './components/AppLayout'
import { ToastProvider } from './components/Toast'
import { AuthProvider, RequireAuth, RequirePermission } from './auth/AuthContext'
import { Admin } from './pages/Admin'
import { AgentDetail } from './pages/AgentDetail'
import { Agents } from './pages/Agents'
import { Alerts } from './pages/Alerts'
import { Analysis } from './pages/Analysis'
import { Approvals } from './pages/Approvals'
import { Audit } from './pages/Audit'
import { Dashboard } from './pages/Dashboard'
import { DemoLab } from './pages/DemoLab'
import { Feed } from './pages/Feed'
import { Graph } from './pages/Graph'
import { Login } from './pages/Login'

export default function App() {
  return (
    <AuthProvider>
      <ToastProvider>
        <BrowserRouter>
          <Routes>
            <Route path="/login" element={<Login />} />
            <Route element={<RequireAuth />}>
              <Route element={<AppLayout />}>
                <Route
                  index
                  element={
                    <RequirePermission code="dashboard.read">
                      <Dashboard />
                    </RequirePermission>
                  }
                />
                <Route
                  path="feed"
                  element={
                    <RequirePermission code="events.read">
                      <Feed />
                    </RequirePermission>
                  }
                />
                <Route
                  path="agents"
                  element={
                    <RequirePermission code="agents.read">
                      <Agents />
                    </RequirePermission>
                  }
                />
                <Route
                  path="agents/:id"
                  element={
                    <RequirePermission code="agents.read">
                      <AgentDetail />
                    </RequirePermission>
                  }
                />
                <Route
                  path="graph"
                  element={
                    <RequirePermission code="agents.read">
                      <Graph />
                    </RequirePermission>
                  }
                />
                <Route
                  path="approvals"
                  element={
                    <RequirePermission code="approvals.read">
                      <Approvals />
                    </RequirePermission>
                  }
                />
                <Route
                  path="alerts"
                  element={
                    <RequirePermission code="alerts.read">
                      <Alerts />
                    </RequirePermission>
                  }
                />
                <Route
                  path="audit"
                  element={
                    <RequirePermission code="audit.read">
                      <Audit />
                    </RequirePermission>
                  }
                />
                <Route
                  path="analysis"
                  element={
                    <RequirePermission code="analysis.create">
                      <Analysis />
                    </RequirePermission>
                  }
                />
                <Route
                  path="demo"
                  element={
                    <RequirePermission code="dashboard.read">
                      <DemoLab />
                    </RequirePermission>
                  }
                />
                <Route
                  path="admin"
                  element={
                    <RequirePermission code="admin.manage">
                      <Admin />
                    </RequirePermission>
                  }
                />
                <Route path="*" element={<Navigate to="/" replace />} />
              </Route>
            </Route>
          </Routes>
        </BrowserRouter>
      </ToastProvider>
    </AuthProvider>
  )
}
