import type {
  ActionSubmitBody,
  AdminUserView,
  ApiError,
  AgentPolicyBody,
  AgentSummary,
  AlertView,
  AnalysisHistoryItem,
  AnalysisResult,
  ApprovalRequest,
  AuditPage,
  BlastRadius,
  ContainmentAction,
  CreateUserBody,
  DashboardMeta,
  DashboardSummary,
  DemoCatalogResponse,
  DemoResetResponse,
  DemoRunResponse,
  DriftView,
  FeedEvent,
  GraphData,
  LoginResponse,
  PermissionEntityView,
  PolicyCatalog,
  PolicyTemplate,
  RoleView,
  SubmitView,
  UpdateUserBody,
  UploadedFile,
  UserView,
} from './types'

export const TOKEN_KEY = 'argus.token'

export class ApiRequestError extends Error {
  status: number
  body: ApiError | null

  constructor(status: number, body: ApiError | null, message: string) {
    super(message)
    this.name = 'ApiRequestError'
    this.status = status
    this.body = body
  }
}

export function getToken(): string | null {
  return localStorage.getItem(TOKEN_KEY)
}

export function setToken(token: string | null): void {
  if (token) {
    localStorage.setItem(TOKEN_KEY, token)
  } else {
    localStorage.removeItem(TOKEN_KEY)
  }
}

export function apiErrorMessage(err: unknown): string {
  if (err instanceof ApiRequestError) {
    if (err.body?.fieldErrors?.length) {
      return err.body.fieldErrors.map((f) => `${f.field}: ${f.message}`).join('; ')
    }
    return err.body?.message ?? err.message
  }
  if (err instanceof Error) {
    return err.message
  }
  return 'Unexpected error'
}

export function fieldErrorsOf(err: unknown): Record<string, string> {
  const out: Record<string, string> = {}
  if (err instanceof ApiRequestError && err.body?.fieldErrors) {
    for (const fe of err.body.fieldErrors) {
      out[fe.field] = fe.message
    }
  }
  return out
}

async function request<T>(path: string, init: RequestInit = {}): Promise<T> {
  const headers = new Headers(init.headers)
  const token = getToken()
  if (token) {
    headers.set('Authorization', `Bearer ${token}`)
  }
  if (init.body && !(init.body instanceof FormData) && !headers.has('Content-Type')) {
    headers.set('Content-Type', 'application/json')
  }
  const res = await fetch(path, { ...init, headers })
  if (res.status === 401) {
    setToken(null)
    window.dispatchEvent(new Event('argus:unauthorized'))
  }
  if (!res.ok) {
    let body: ApiError | null = null
    try {
      body = (await res.json()) as ApiError
    } catch {
      body = null
    }
    throw new ApiRequestError(res.status, body, body?.message ?? `Request failed (${res.status})`)
  }
  if (res.status === 204) {
    return undefined as T
  }
  const text = await res.text()
  return (text ? JSON.parse(text) : undefined) as T
}

function qs(params: Record<string, string | number | undefined | null>): string {
  const search = new URLSearchParams()
  for (const [key, value] of Object.entries(params)) {
    if (value !== undefined && value !== null && value !== '') {
      search.set(key, String(value))
    }
  }
  const s = search.toString()
  return s ? `?${s}` : ''
}

async function uploadMultipart<T>(path: string, file: File): Promise<T> {
  const form = new FormData()
  form.append('file', file)
  return request<T>(path, { method: 'POST', body: form })
}

async function fetchBlob(path: string): Promise<Blob> {
  const headers = new Headers()
  const token = getToken()
  if (token) {
    headers.set('Authorization', `Bearer ${token}`)
  }
  const res = await fetch(path, { headers })
  if (res.status === 401) {
    setToken(null)
    window.dispatchEvent(new Event('argus:unauthorized'))
  }
  if (!res.ok) {
    throw new ApiRequestError(res.status, null, `Download failed (${res.status})`)
  }
  return res.blob()
}

function saveBlob(blob: Blob, filename: string): void {
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = filename
  document.body.appendChild(a)
  a.click()
  a.remove()
  URL.revokeObjectURL(url)
}

export async function login(username: string, password: string): Promise<LoginResponse> {
  return request<LoginResponse>('/api/auth/login', {
    method: 'POST',
    body: JSON.stringify({ username, password }),
  })
}

export async function logout(): Promise<void> {
  await request<void>('/api/auth/logout', { method: 'POST' })
}

export async function me(): Promise<UserView> {
  return request<UserView>('/api/auth/me')
}

export async function dashboard(): Promise<DashboardSummary> {
  return request<DashboardSummary>('/api/dashboard')
}

export async function dashboardMeta(): Promise<DashboardMeta> {
  return request<DashboardMeta>('/api/dashboard/meta')
}

export async function feed(decision?: string, q?: string, size?: number): Promise<FeedEvent[]> {
  return request<FeedEvent[]>(`/api/feed${qs({ decision, q, size })}`)
}

export async function agents(): Promise<AgentSummary[]> {
  return request<AgentSummary[]>('/api/agents')
}

export async function agent(id: string): Promise<AgentSummary> {
  return request<AgentSummary>(`/api/agents/${encodeURIComponent(id)}`)
}

export async function agentDrift(id: string): Promise<DriftView> {
  return request<DriftView>(`/api/agents/${encodeURIComponent(id)}/drift`)
}

export async function agentStatus(id: string, status: 'ACTIVE'|'PAUSED'): Promise<AgentSummary> {
  return request<AgentSummary>(`/api/agents/${encodeURIComponent(id)}/status`, {
    method: 'POST',
    body: JSON.stringify({ status }),
  })
}

export async function isolate(id: string): Promise<AgentSummary> {
  return request<AgentSummary>(`/api/agents/${encodeURIComponent(id)}/isolate`, { method: 'POST' })
}

export async function restore(id: string): Promise<AgentSummary> {
  return request<AgentSummary>(`/api/agents/${encodeURIComponent(id)}/restore`, { method: 'POST' })
}

export async function createAgent(body: AgentPolicyBody): Promise<AgentSummary> {
  return request<AgentSummary>('/api/agents', { method: 'POST', body: JSON.stringify(body) })
}

export async function updateAgent(id: string, body: AgentPolicyBody): Promise<AgentSummary> {
  return request<AgentSummary>(`/api/agents/${encodeURIComponent(id)}`, {
    method: 'PUT',
    body: JSON.stringify(body),
  })
}

export async function deleteAgent(id: string): Promise<void> {
  await request<void>(`/api/agents/${encodeURIComponent(id)}`, { method: 'DELETE' })
}

export async function policyTemplates(): Promise<PolicyTemplate[]> {
  return request<PolicyTemplate[]>('/api/policies/templates')
}

export async function policyCatalog(): Promise<PolicyCatalog> {
  return request<PolicyCatalog>('/api/policies/catalog')
}

export async function alerts(status?: string): Promise<AlertView[]> {
  return request<AlertView[]>(`/api/alerts${qs({ status })}`)
}

export async function playbook(id: number, action: string, value?: string): Promise<AlertView> {
  return request<AlertView>(`/api/alerts/${id}/playbook`, {
    method: 'POST',
    body: JSON.stringify({ action, value }),
  })
}

export async function containments(): Promise<ContainmentAction[]> {
  return request<ContainmentAction[]>('/api/containments')
}

export async function approvals(status?: string): Promise<ApprovalRequest[]> {
  return request<ApprovalRequest[]>(`/api/approvals${qs({ status })}`)
}

export async function decide(id: number, decision: 'APPROVE'|'DENY', note?: string): Promise<ApprovalRequest> {
  return request<ApprovalRequest>(`/api/approvals/${id}/decision`, {
    method: 'POST',
    body: JSON.stringify({ decision, note }),
  })
}

export async function audit(q?: string, page?: number, size?: number): Promise<AuditPage> {
  return request<AuditPage>(`/api/audit${qs({ q, page, size })}`)
}

export async function exportAudit(format: 'json'|'csv', q?: string): Promise<void> {
  const blob = await fetchBlob(`/api/audit/export${qs({ format, q })}`)
  const stamp = new Date().toISOString().slice(0, 10).replace(/-/g, '')
  saveBlob(blob, `argus-audit-${stamp}.${format}`)
}

export async function analysePrompt(content: string): Promise<AnalysisResult> {
  return request<AnalysisResult>('/api/analyse/prompt', {
    method: 'POST',
    body: JSON.stringify({ content }),
  })
}

export async function analysePdf(file: File): Promise<AnalysisResult> {
  return uploadMultipart<AnalysisResult>('/api/analyse/pdf', file)
}

export async function analyseHistory(size?: number): Promise<AnalysisHistoryItem[]> {
  return request<AnalysisHistoryItem[]>(`/api/analyse/history${qs({ size })}`)
}

export async function analyseGet(id: number): Promise<AnalysisHistoryItem> {
  return request<AnalysisHistoryItem>(`/api/analyse/${id}`)
}

export async function fetchDemoPdf(): Promise<File> {
  const blob = await fetchBlob('/api/analyse/demo-pdf')
  return new File([blob], 'argus-malicious-demo.pdf', { type: 'application/pdf' })
}

export async function demos(): Promise<DemoCatalogResponse> {
  return request<DemoCatalogResponse>('/api/demos')
}

export async function runDemo(key: string): Promise<DemoRunResponse> {
  return request<DemoRunResponse>(`/api/demos/${encodeURIComponent(key)}`, { method: 'POST' })
}

export async function resetDemo(): Promise<DemoResetResponse> {
  return request<DemoResetResponse>('/api/demos/reset', { method: 'POST' })
}

export async function submitAction(cmd: ActionSubmitBody): Promise<SubmitView> {
  return request<SubmitView>('/api/actions', { method: 'POST', body: JSON.stringify(cmd) })
}

export async function graph(): Promise<GraphData> {
  return request<GraphData>('/api/graph')
}

export async function blast(from: string): Promise<BlastRadius> {
  return request<BlastRadius>(`/api/graph/blast${qs({ from })}`)
}

export async function evidenceList(): Promise<UploadedFile[]> {
  return request<UploadedFile[]>('/api/evidence')
}

export async function evidenceUpload(file: File): Promise<UploadedFile> {
  return uploadMultipart<UploadedFile>('/api/evidence', file)
}

export async function evidenceDelete(id: number): Promise<void> {
  await request<void>(`/api/evidence/${id}`, { method: 'DELETE' })
}

export async function downloadEvidence(file: UploadedFile): Promise<void> {
  const blob = await fetchBlob(`/api/evidence/${file.id}/download`)
  saveBlob(blob, file.originalName || `evidence-${file.id}`)
}

export async function adminUsers(): Promise<AdminUserView[]> {
  return request<AdminUserView[]>('/api/admin/users')
}

export async function adminCreateUser(body: CreateUserBody): Promise<AdminUserView> {
  return request<AdminUserView>('/api/admin/users', { method: 'POST', body: JSON.stringify(body) })
}

export async function adminUpdateUser(id: number, body: UpdateUserBody): Promise<AdminUserView> {
  return request<AdminUserView>(`/api/admin/users/${id}`, { method: 'PUT', body: JSON.stringify(body) })
}

export async function adminPassword(id: number, password: string): Promise<AdminUserView> {
  return request<AdminUserView>(`/api/admin/users/${id}/password`, {
    method: 'POST',
    body: JSON.stringify({ password }),
  })
}

export async function adminUnlock(id: number): Promise<AdminUserView> {
  return request<AdminUserView>(`/api/admin/users/${id}/unlock`, { method: 'POST' })
}

export async function adminRoles(): Promise<Record<string, RoleView>> {
  return request<Record<string, RoleView>>('/api/admin/roles')
}

export async function adminPermissions(): Promise<PermissionEntityView[]> {
  return request<PermissionEntityView[]>('/api/admin/permissions')
}
