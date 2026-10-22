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
  decisions24h: Record<string, number>;
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

export interface DashboardMeta {
  templates: PolicyTemplate[];
  catalog: PolicyCatalog;
  demos: Record<string, DemoCatalogEntry>;
  decisionThresholds: Record<string, string>;
}

export interface DemoCatalogResponse { demos: Record<string, DemoCatalogEntry>; running: boolean }
export interface DemoRunResponse { key: string; started: boolean; message?: string }
export interface DemoResetResponse { reset: boolean; eventsCleared: number; agentsSeeded: number }

export interface AnalysisHistoryItem {
  id: number|null; ts: string; actor: string|null; sourceType: string; filename: string|null;
  contentHash: string|null; textExcerpt: string|null; textLength: number; indicatorCount: number;
  score: number; recommendation: string; reason: string|null; indicatorsJson: string|null;
  affectedAgentsJson: string|null; gatewayJson: string|null; sensitiveValues: number;
}

export interface RoleView { name: string; description: string; permissions: string[] }
export interface PermissionEntityView { code: string; description: string }

export interface AgentPolicyBody {
  id?: string|null; name: string; icon?: string|null; roleTitle: string; description?: string|null;
  tools: string[]; allowed: string[]; restricted: string[]; restrictActions: string[];
  clearance: number; baselineRate?: number|null; baselineOffHours?: number|null;
  baselineAvgSens?: number|null; baselineCats?: Record<string, number>|null;
}

export interface ActionSubmitBody {
  agentId: string; kind: string; tool?: string|null; category?: string|null;
  target?: string|null; message?: string|null; toAgent?: string|null;
}

export interface CreateUserBody { username: string; password: string; displayName?: string; roles: string[] }
export interface UpdateUserBody { displayName?: string; roles?: string[]; enabled?: boolean }
