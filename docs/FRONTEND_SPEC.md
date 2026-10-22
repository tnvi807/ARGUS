# ARGUS Frontend Specification

React 19 + Vite + TypeScript + Tailwind CSS v4 + react-router-dom v7. Zero extra runtime
dependencies beyond what is already in `frontend/package.json` (hand-rolled SVG charts).

## Stack constraints

- Dev proxy: Vite server proxies `/api` (and `/actuator`) to `http://localhost:8080` (see `vite.config.ts`).
- Auth: JWT stored in `localStorage` under key `argus.token`; sent as `Authorization: Bearer <token>` on every request. 401 anywhere → clear token, redirect to `/login`.
- SSE: browsers' `EventSource` cannot send headers, so the stream MUST be consumed with `fetch` + `ReadableStream` and an `Authorization` header, parsing `event:`/`data:`/`: ping` frames manually.
- Theme: dark security-operations console. Background near-black (`#0b0f14` family), panel surfaces slightly lighter with 1px borders (`#1c2630` family), accent cyan (`#22d3ee`) for interactive/active, amber/red/green reserved for risk semantics. Monospace for scores/IDs. Tailwind v4 `@import "tailwindcss"` + `@theme` custom colors in `index.css`.
- No emojis as icons in chrome (agent icons supplied by API are fine).

## TypeScript API types (`src/api/types.ts`)

Mirror the backend exactly. Core types (all fields camelCase as served):

```ts
export interface UserView { id: number|null; username: string; displayName: string; roles: string[]; permissions: string[] }
export interface LoginResponse { token: string; expiresInSeconds: number; user: UserView }
export interface ApiError { timestamp: string; status: number; code: string; message: string; path: string; fieldErrors?: {field: string; message: string}[]|null }

export interface Factor { name: string; weight: number; detail: string }

export interface FeedEvent {
  id: number|null; ts: string; agentId: string; agentName: string|null; agentIcon: string|null;
  agentRole: string|null; kind: string; tool: string|null; category: string|null; target: string|null;
  message: string|null; toAgent: string|null; sensitivity: number; riskScore: number;
  decision: 'ALLOWED'|'RESTRICTED'|'PENDING_APPROVAL'|'BLOCKED'; reason: string;
  factors: Factor[]; executed: boolean; executionDetail: string|null; approvalId: number|null;
}

export interface AgentSummary {
  id: string; name: string; icon: string|null; roleTitle: string; description: string|null;
  tools: string[]; allowed: string[]; restricted: string[]; restrictActions: string[];
  blockedResources: string[]; clearance: number; status: 'ACTIVE'|'PAUSED'|'ISOLATED';
  lastRisk: number; integrity: number; actions: number; fileReads: number; apiCalls: number;
  exports: number; offHours: number; baselineRate: number; baselineOffHours: number;
  baselineAvgSens: number; avgSensitivity: number; driftHistory: {label: string; sens: number}[];
  updatedAt: string|null;
}

export interface DriftView {
  agentId: string; agentName: string; icon: string|null; points: {label: string; sens: number}[];
  baselineAvgSens: number; currentAvgSens: number; slope: number; integrity: number;
  verdict: string; offHoursPct: number; novelCategories: number; exfiltrationAttempts: number;
  actionCount: number;
}

export interface AlertView {
  id: number|null; ts: string; agentId: string; agentName: string|null; agentIcon: string|null;
  agentRole: string|null; kind: string; tool: string|null; category: string|null; target: string|null;
  message: string|null; riskScore: number; decision: string; reason: string; factors: Factor[];
  status: 'OPEN'|'REVIEWED'; containment: string|null; demo: string|null; eventId: number|null;
}

export interface ApprovalRequest {
  id: number|null; ts: string; agentId: string; agentName: string; eventId: number|null;
  kind: string; tool: string|null; category: string|null; target: string|null; message: string|null;
  riskScore: number; reason: string|null; factorsJson: string|null;
  status: 'PENDING'|'APPROVED'|'DENIED'; decidedBy: string|null; decidedAt: string|null;
  decisionNote: string|null; executedAt: string|null; executionDetail: string|null;
}

export interface ContainmentAction {
  id: number|null; ts: string; agentId: string; agentName: string|null;
  kind: 'BLOCK_RESOURCE'|'PAUSE_AGENT'|'RESUME_AGENT'|'ISOLATE_AGENT'|'RESTORE_AGENT'|'REVOKE_TOOL';
  detail: string; trigger: string|null; actor: string|null; alertId: number|null; active: boolean;
}

export interface AuditView {
  id: number|null; ts: string; actor: string|null; agent: string|null; role: string|null;
  kind: string; description: string; target: string|null; decision: string;
  score: number|null; reason: string|null; factors: Factor[];
}
export interface AuditPage { items: AuditView[]; page: number; size: number; totalElements: number; totalPages: number }

export interface DashboardSummary {
  generatedAt: string;
  agents: { registered: number; active: number; paused: number; isolated: number };
  traffic: { requestsPerMinute: number; requestsPerHour: number; requests24h: number; avgRisk24h: number };
  decisions24h: Record<string, number>; // ALLOWED | RESTRICTED | PENDING_APPROVAL | BLOCKED
  alerts: { open: number; total: number; pendingApprovals: number };
  threatLevel: 'HIGH'|'ELEVATED'|'LOW';
  killChain24h: Record<string, number>;
  riskBoard: { id: string; name: string; icon: string|null; status: string; lastRisk: number; integrity: number; blockedResources: number }[];
  timeline: FeedEvent[];
  openIncidents: number;
}

export interface GraphNode { id: string; name: string; icon: string|null; role: string; status: string; lastRisk: number; integrity: number; tools: string[]; blockedResources: string[] }
export interface GraphEdge { from: string; to: string; count: number; flagged: boolean; lastMessage: string; lastTs: string }
export interface SuspiciousChain { from: string; to: string; message: string; ts: string }
export interface GraphData { nodes: GraphNode[]; edges: GraphEdge[]; suspiciousChains: SuspiciousChain[] }
export interface BlastRadius { start: string; infected: string[]; paths: {from: string; to: string; depth: number; tool?: string}[]; predictions: string[]; atRisk: number }

export interface Indicator { type: string; indicator: string; severity: 'HIGH'|'MEDIUM'|'LOW'; weight: number; evidence: string; offset: number }
export interface AffectedAgent { agentId: string; name: string; icon: string; roleTitle: string; relation: string }
export interface GatewayPreview { agentId: string; agentName: string; kind: string; tool: string; category: string; target: string; score: number; decision: string; factors: Factor[]; note: string }
export interface AnalysisResult {
  id: number|null; ts: string; source: 'PROMPT'|'PDF'; filename: string|null; actor: string|null;
  indicators: Indicator[]; score: number; recommendation: 'ALLOW'|'RESTRICT'|'REQUIRE_APPROVAL'|'BLOCK';
  reason: string; sensitiveDataRisk: boolean; sensitiveValues: number; affectedAgents: AffectedAgent[];
  gateway: GatewayPreview|null; textExcerpt: string; textLength: number;
  policyImpact: { categoriesMentioned: string[]; agentsMentioned: string[]; restrictedCategoriesTriggered: string[]; note: string };
}

export interface DemoCatalogEntry { title: string; description: string; type: 'gateway'|'analysis' }
export interface UploadedFile { id: number|null; ts: string; originalName: string; storedName: string; contentType: string|null; sizeBytes: number; sha256: string|null; uploadedBy: string|null; kind: string|null }
export interface PolicyCatalog { tools: string[]; categories: {code: string; label: string; sensitivity: number}[]; restrictActions: string[]; actionKinds: string[] }
export interface PolicyTemplate { key: string; name: string; roleTitle: string; description: string; icon: string; clearance: number; tools: string[]; allowed: string[]; restricted: string[]; restrictActions: string[] }
export interface SubmitView { eventId: number|null; agentId: string; riskScore: number; decision: string; reason: string; factors: Factor[]; alertId: number|null; approvalId: number|null; executed: boolean; executionDetail: string|null }
export interface AdminUserView { id: number|null; username: string; displayName: string; roles: string[]; enabled: boolean; failedAttempts: number; locked: boolean; tokenVersion: number; createdAt: string|null; lastLoginAt: string|null }
```

## API client (`src/api/client.ts`)

`request<T>(path, init?)` wrapper: attaches Bearer header, JSON-parses errors into `ApiError`,
throws a typed `ApiRequestError` (carrying `status` + `ApiError`). Expose named functions:
`login`, `logout`, `me`, `dashboard`, `dashboardMeta`, `feed(decision?, q?, size?)`,
`agents`, `agent(id)`, `agentDrift(id)`, `agentStatus(id, status)`, `isolate(id)`, `restore(id)`,
`alerts(status?)`, `playbook(id, action, value?)`, `containments`, `approvals(status?)`,
`decide(id, decision, note?)`, `audit(q?, page?, size?)`, `analysePrompt(content)`,
`analysePdf(file)`, `analyseHistory(size?)`, `demos()`, `runDemo(key)`, `resetDemo()`,
`submitAction(cmd)`, `graph()`, `blast(from)`, `evidenceList()`, `evidenceUpload(file)`,
`evidenceDelete(id)`, `adminUsers()`, `adminCreateUser(body)`, `adminUpdateUser(id, body)`,
`adminPassword(id, password)`, `adminUnlock(id)`, `adminRoles()`, `adminPermissions()`.
Download helpers for `/api/audit/export` and evidence files (fetch blob → object URL).
`uploadMultipart(path, file)` for PDF/evidence.

## SSE hook (`src/hooks/useSSE.ts`)

`useSSE(onEvent: (name: string, data: any) => void, enabled = true)` — fetch `/api/events/stream`
with Bearer header, parse the stream incrementally (split on `\n\n`, respect `event:` and `data:`
fields, ignore `: ping` comments and empty frames), JSON-parse data, call `onEvent`. Reconnect with
5s backoff on error when still enabled. Cleanup aborts the controller on unmount/disable.

## Routing + layout

Routes (all except `/login` inside `<AppLayout/>` requiring auth):

| Path | Page | Min permission |
|---|---|---|
| `/login` | Login | — |
| `/` | Dashboard | `dashboard.read` |
| `/feed` | Live Feed | `events.read` |
| `/agents` | Agents grid | `agents.read` |
| `/agents/:id` | Agent detail (tabs: Overview, Policy, Drift) | `agents.read` |
| `/graph` | Trust graph + blast radius | `agents.read` |
| `/approvals` | Approval queue | `approvals.read` |
| `/alerts` | Alerts + containment history | `alerts.read` |
| `/audit` | Audit trail | `audit.read` |
| `/analysis` | Prompt/PDF analysis + history | `analysis.create` |
| `/demo` | Demo lab + manual action submit | `dashboard.read` (run needs `demos.run`) |
| `/admin` | User administration | `admin.manage` |

`AppLayout`: fixed left sidebar nav (ARGUS wordmark, nav links filtered by `hasPermission(code)`,
user chip + logout at bottom), main content area, global live-status dot (green when SSE connected).
Nav items show badges where meaningful (pending approvals count, open alerts count) fed by SSE.

Auth context: `AuthProvider` loads `/api/auth/me` on mount if token exists; `login()` stores token
and user; `logout()` calls API then clears. `useAuth()` exposes `{ user, login, logout, hasPermission }`.
`RequireAuth` wrapper redirects to `/login`; `RequirePermission` renders an "Access denied" panel
when the user lacks the permission (not a redirect — better UX for shared links).

## Pages

### Login
Centered card: ARGUS wordmark, tagline "Autonomous Risk Guard for Untrusted Systems", username +
password fields, submit. Show server error message (e.g. lockout) inline. On success → navigate to
`/`. Prefill hint text: seeded accounts `admin`/`analyst`/`viewer` (passwords per environment).

### Dashboard
- Row of stat cards: Threat Level (badge HIGH/ELEVATED/LOW), Open Incidents, Pending Approvals, Actions 24h, Avg Risk 24h, Active/Total agents.
- Decision donut or stacked bar for `decisions24h` (ALLOWED green, RESTRICTED amber, PENDING cyan, BLOCKED red).
- Kill-chain horizontal bars (`killChain24h`).
- Risk board table (top 6 agents: icon, name, status chip, lastRisk mini-gauge, integrity %, blocked count) linking to agent detail.
- Recent timeline (last 12 feed events) — shared `FeedRow` component.
- Live: SSE `feed`/`alert`/`agent` events prepend to timeline and refresh summary (debounced refetch ≤ 5s).

### Live Feed
Filter bar: decision select (ALL/ALLOWED/RESTRICTED/PENDING_APPROVAL/BLOCKED) + free-text search.
List of `FeedRow`: timestamp, agent icon+name, kind + tool + category chips, target (mono, truncated),
risk score pill (color by band), decision badge, reason (truncated, title=full). Expandable row
revealing factor breakdown (name/weight/detail) and execution detail. SSE `feed` events prepend with
a subtle highlight animation.

### Agents
Card grid: icon, name, roleTitle, status chip (ACTIVE green / PAUSED amber / ISOLATED red), lastRisk
gauge, integrity bar, tool count, blockedResources count. Quick actions per card (pause/resume,
isolate) gated on `alerts.act`. "New agent" button (modal form from PolicyTemplate select +
catalog-driven multi-selects) gated on `agents.write`.

### Agent detail
Header: icon, name, roleTitle, status, clearance, quick actions (pause/resume/isolate/restore —
restore gated `admin.manage`), delete (gated `policies.write`, confirm dialog).
Tabs:
- Overview: description, stats (actions, fileReads, apiCalls, exports, offHours, avgSensitivity),
  baseline metrics, blockedResources chips.
- Policy: editable form (tools/allowed/restricted/restrictActions from catalog, clearance slider,
  baseline numbers) → PUT; validation errors from `fieldErrors` shown per field.
- Drift: SVG line chart of `points` (sens over labels) with baselineAvgSens reference line, verdict
  banner (Identity Stable ≥85 green / Identity Drifting ≥60 amber / Identity Compromised <60 red),
  slope, offHoursPct, novelCategories, exfiltrationAttempts.

### Graph
SVG force-free layout (deterministic circle layout by node count): nodes as circles (size by
lastRisk, stroke color by status), edges as lines (width by count, red if flagged, label tooltip =
lastMessage). Suspicious chains listed beside the graph. Click node → side panel with node details +
"Compute blast radius" button (`GET /api/graph/blast?from=`) showing infected set, paths (depth),
and prediction sentences.

### Approvals
Queue of PENDING approvals (filter ALL/PENDING): each card shows agent, action (kind/tool/category/
target/message), riskScore, reason, parsed factorsJson (JSON.parse with try/catch). Approve/Deny
buttons (gated `approvals.act`) with optional note input; APPROVE confirms "this executes the held
action". Decided items collapse to a decided list (decidedBy, decisionNote, executionDetail).
SSE `approval` events refresh the queue.

### Alerts
Two sections: Open incidents (top 50 OPEN) + Containment history.
Incident card: severity (riskScore), agent, action summary, reason, factors, containment note,
playbook action buttons gated `alerts.act`: Mark reviewed, Pause agent, Resume agent, Block resource
(value = category, default from alert), Revoke tool (value = tool name). After acting, alert's
`containment` field updates. Containment history: table of `ContainmentAction` (ts, agent, kind
chip, detail, trigger, actor, active).

### Audit
Search input (q) + Export buttons (JSON, CSV — gated `audit.export`, trigger download). Paged table
(prev/next + page indicator using `totalPages`): ts, actor, agent, kind, description, target,
decision badge, score, reason. Row expand shows factors.

### Analysis
Two input panels side by side (stack on mobile):
- Prompt: textarea (max 20000) + Analyse button.
- PDF: file input (accept application/pdf) + "Use malicious demo PDF" link (downloads
  `/api/analyse/demo-pdf` then uploads it) + Analyse button.
Result panel: overall score gauge + recommendation badge (ALLOW/RESTRICT/REQUIRE_APPROVAL/BLOCK),
reason, indicators table (severity chip, type, indicator mono, evidence, weight), sensitive data
callout, affected agents chips, gateway preview card (decision + factors), text excerpt (collapsible),
policy impact. Below: history list from `/api/analyse/history` (score, recommendation, source,
filename, ts) — clicking loads persisted detail (parse `indicatorsJson` etc. with try/catch).

### Demo lab
- Catalog grid of 9 demos (title, description, type chip gateway/analysis). Run button (gated
  `demos.run`) → POST; show 202 started / 409 busy toast. `malicious_pdf` type navigates to
  `/analysis` instead. Reset button (confirm) → POST reset, toast with eventsCleared/agentsSeeded.
- "Manual gateway submission" form (gated `actions.submit`): agent select (from agents list),
  kind select (catalog.actionKinds), tool select, category select (catalog.categories with
  sensitivity), target, message, toAgent. Submit → result card showing SubmitView (score, decision,
  reason, factors, executed, executionDetail, alertId/approvalId links).
- Live mirror: last 20 feed events below (SSE).

### Admin
Users table: username, displayName, roles chips, enabled toggle, failedAttempts, locked, lastLoginAt.
Actions per row: edit (modal: displayName, roles checkboxes, enabled), reset password (modal, min 12
chars letter+digit), unlock. "Create user" modal (username regex `[a-z0-9][a-z0-9._-]{2,31}`,
password rules, roles multi-select). Roles reference panel from `/api/admin/roles` (name,
description, permission codes). Guard: self-disable / self-role-strip / last-admin errors surface as
toast from server message.

## Shared components (`src/components/`)

- `AppLayout`, `Sidebar`, `RequireAuth`, `RequirePermission`
- `DecisionBadge` (4 variants), `StatusChip` (agent status), `RiskGauge` (0-100 horizontal bar
  colored: <40 green, <60 amber, <70 orange, else red), `RiskPill` (compact numeric)
- `FeedRow` (used by feed page, dashboard timeline, demo live mirror)
- `FactorList` (renders Factor[])
- `Sparkline` / `LineChart` (pure SVG, props: points, baseline?, height), `BarRow` (kill-chain)
- `Modal` (portal + backdrop + escape), `Toast` (`ToastProvider` with `useToast()`; bottom-right)
- `EmptyState`, `LoadingPanel`, `ErrorPanel`
- `SearchInput`, `PageHeader` (title + description + actions slot)

## State conventions

- Pages fetch on mount with `useEffect` + local `{loading, error, data}` state (no global data cache).
- SSE handlers mutate only the owning page's state via setState callbacks passed to `useSSE`.
- Permission checks use `useAuth().hasPermission(code)`; never rely on hiding alone — every API
  error of 403 shows a toast.

## Vite config

Server proxy:
```ts
server: { proxy: { '/api': 'http://localhost:8080', '/actuator': 'http://localhost:8080' } }
```

## Definition of done

`cmd /c "npm run build"` in `frontend/` passes (tsc -b + vite build) with zero errors; `npm run lint`
passes; every route above renders with seeded demo data; SSE live-updates feed/dashboard/approvals;
no console errors in normal flow.
