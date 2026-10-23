# ARGUS — Autonomous Risk Guard for Untrusted Systems

Real-time AI-agent security gateway. Every action an autonomous agent attempts flows
through ARGUS, which scores risk (0–100), explains the decision, and enforces it:
**ALLOWED** (execute), **RESTRICTED** (execute with redacted output), **PENDING_APPROVAL**
(hold for an administrator), or **BLOCKED** (no side effects). Contained agents can be
paused, isolated, and restored; every decision lands in an append-only audit ledger.

> This repository also contains the original static prototype, now moved into
> `prototype/` (`prototype/css/`, `prototype/js/`, `prototype/pages/`,
> `prototype/index.html` — see `prototype/README.md` / `prototype/PROJECT_SUMMARY.md`).
> The production implementation is the `backend/` + `frontend/` pair documented here.

## Stack

| Layer     | Tech |
|-----------|------|
| Frontend  | React 19, Vite 8, TypeScript, Tailwind CSS v4, react-router-dom 7 (zero other runtime deps) |
| Backend   | Java 21, Spring Boot 3.5.16, Spring Security (stateless HS256 JWT + RBAC), springdoc OpenAPI |
| Data      | PostgreSQL 16 (Flyway migrations, `ddl-auto: validate`), H2 for tests |
| Realtime  | Server-Sent Events (`/api/events/stream`, header-authenticated) |
| Documents | Apache PDFBox 3 for PDF text extraction + prompt/injection analysis |
| Deploy    | Docker Compose (Postgres + backend + nginx-served frontend) |

## Features (ported MVP + engines)

1. **Live dashboard** — threat level, decision mix, kill-chain distribution, risk board, SSE-fed timeline.
2. **Real-time feed** — every evaluated action with risk score, decision, explainable factors; filter + search.
3. **Agent policies** — least-privilege tools/categories/action restrictions, clearance, drift baselines; pause / isolate / restore.
4. **Enforcement pipeline** — deterministic risk engine (sensitivity, policy, drift, containment, rate, keyword factors) with proven no-side-effects for held/blocked actions.
5. **Approval queue** — PENDING_APPROVAL actions execute only after an explicit, audited admin decision.
6. **Alerts & containment** — playbook actions (block resource, pause/resume, revoke tool, mark reviewed) + containment history.
7. **Append-only audit trail** — search + JSON/CSV export; DB-level append-only enforcement.
8. **Prompt & PDF analysis** — 12 indicator families (injection, bypass, exfiltration, hidden content, …), gateway preview, history.
9. **Trust graph** — agent nodes, communication edges, suspicious chains, blast-radius BFS.
10. **Demo lab** — 9 scripted scenarios (normal procurement, malicious PDF, rogue tool, identity drift, …) + manual gateway submission form.
11. **RBAC admin** — ADMIN / ANALYST / VIEWER roles, 18 permissions, user lifecycle (create, edit, roles, password reset, unlock), progressive login lockout.
12. **Evidence vault** — validated uploads (extension allow-list, content sanity checks), sha-256 hashing, download/delete.

### Risk bands

| Score | Decision | Enforcement |
|------:|----------|-------------|
| 0–39  | ALLOWED | executed against the (mock) tool runtime |
| 40–59 | RESTRICTED | executed, sensitive output redacted |
| 60–69 | PENDING_APPROVAL | held; no side effects until approved |
| 70–100 | BLOCKED | never executed |

## Quick start

### Hackathon demo (one command, one URL, no Docker)

```bat
cd ARGUS
start-demo.bat
```

Builds the React UI, embeds it in the Spring Boot backend, starts the server
(in-memory H2, ambient autopilot traffic ON) and opens **http://localhost:8080** —
the API and the UI share one origin, so every screen shows live data from the database.

- UI: http://localhost:8080
- API + Swagger: http://localhost:8080/swagger-ui.html
- Health: http://localhost:8080/actuator/health

The original static prototype (hardcoded data, no server) lives in `prototype/` —
open `prototype/pages/index.html` only if you want to see it.

### Docker Compose

```bash
cd ARGUS
set ARGUS_JWT_SECRET=any-random-string-at-least-32-characters-long   # PowerShell
export ARGUS_JWT_SECRET=any-random-string-at-least-32-characters-long # bash
docker compose up --build
```

- Frontend: http://localhost:3000
- Backend API + Swagger UI: http://localhost:8080/swagger-ui.html
- Health: http://localhost:8080/actuator/health

### Local development

Backend (Java 21+, Maven 3.9+):

```bash
cd backend
mvn spring-boot:run            # H2-free: needs Postgres, or use compose just for `db`
mvn test                       # 38 integration tests on ephemeral H2 (MODE=PostgreSQL)
```

Frontend (Node 20+):

```bash
cd frontend
npm install
npm run dev                    # http://localhost:5173, proxies /api → :8080
npm run build                  # typecheck + production bundle
```

### Seeded accounts

| Username | Role | Default password (override via env) |
|----------|------|-------------------------------------|
| `admin` | ADMIN | `ChangeMe!Argus2026` (`ARGUS_ADMIN_PASSWORD`) |
| `analyst` | ANALYST | `Analyst!Argus2026` (`ARGUS_ANALYST_PASSWORD`) |
| `viewer` | VIEWER | `Viewer!Argus2026` (`ARGUS_VIEWER_PASSWORD`) |

Five failed logins lock an account for 15 minutes. Logout / role change instantly revokes
all outstanding JWTs (token-version claim).

## Permissions matrix

| Permission | VIEWER | ANALYST | ADMIN |
|------------|:------:|:-------:|:-----:|
| dashboard.read, agents.read, alerts.read, audit.read, events.read, analysis.create | ✅ | ✅ | ✅ |
| agents.write, policies.write, alerts.act, audit.export, evidence.upload/read, actions.submit, demos.run, sim.control, approvals.read | — | ✅ | ✅ |
| admin.manage, approvals.act | — | — | ✅ |

## Project layout

```
ARGUS/
├── docker-compose.yml        # Postgres + backend + frontend
├── start-demo.bat            # one-command local demo (no Docker)
├── backend/                  # Spring Boot 3.5 gateway (Flyway schema, JWT, engines, REST + SSE)
│   └── src/main/java/com/argus/
│       ├── auth/ security/ admin/     # JWT, RBAC, users
│       ├── engine/ catalog/ runtime/  # risk scoring + enforcement + mock tools
│       ├── analysis/                  # prompt/PDF engines
│       ├── agent/ containment/ drift  # policies, identity drift, blast radius
│       ├── event/ alerts/ approval/ audit/ evidence/
│       ├── demo/ stream/ feed/ graph/ dashboard/
│       └── seed/                      # permissions, roles, users, demo agents
│   └── src/main/resources/db/migration/V1__init.sql
├── frontend/                 # React 19 SPA (12 routes, SSE live views, SVG charts)
├── docs/FRONTEND_SPEC.md     # API contract + UI specification
├── prototype/                # legacy static prototype (css/ js/ pages/ index.html)
│   └── README.md / PROJECT_SUMMARY.md  # legacy prototype docs
```

## API surface

All routes under `/api` (Bearer JWT). Highlights:

- `POST /api/auth/login|logout`, `GET /api/auth/me`
- `GET /api/dashboard`, `/api/feed`, `/api/agents`, `/api/graph`, `/api/alerts`, `/api/approvals`
- `POST /api/actions` (manual gateway submission), `/api/approvals/{id}/decision`
- `POST /api/analyse/prompt`, `/api/analyse/pdf`, `GET /api/analyse/demo-pdf`
- `POST /api/demos/{key}`, `/api/demos/reset`
- `GET /api/audit`, `/api/audit/export`, `/api/evidence`, `/api/containments`
- `GET /api/events/stream` (SSE: `feed`, `alert`, `agent`, `approval`, `containment`, `analysis`, `reset`)
- `GET|POST /api/admin/users...` (ADMIN only)

Full request/response shapes: `docs/FRONTEND_SPEC.md` and live OpenAPI at
`/v3/api-docs` / Swagger UI.

## Testing

`backend/` ships 38 integration tests (`mvn test`) covering auth/RBAC/lockout,
enforcement guarantees (blocked/held actions provably have no side effects, approvals
execute, isolated agents are refused), analysis engines, audit append-only + evidence
validation, and the demo lab. Tests run on ephemeral H2 (`MODE=PostgreSQL`) with Flyway
migrations and `ddl-auto: validate` — the schema is owned by `V1__init.sql`.

## Security notes

- **Rotate `ARGUS_JWT_SECRET`** (≥ 32 bytes) before any shared deployment; the built-in dev secret only logs a warning.
- JWTs are short-lived, stateless, and revocable via token version — no server sessions.
- Failed logins are generic ("Invalid username or password"); lockout after 5 attempts.
- Evidence/PDF uploads are extension-allow-listed, size-capped, content-sniffed, and hashed.
- The audit ledger rejects UPDATE/DELETE at the JPA event-listener level.
- CORS origins are configurable (`ARGUS_CORS_ORIGINS`).
