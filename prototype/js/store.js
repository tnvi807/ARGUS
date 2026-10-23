/* ================================================================
   ARGUS store.js — state, persistence, and the security engine
   Merges: Solution 1 (role firewall) + 2 (trust graph) + 3 (drift)
   ================================================================ */
'use strict';

const STORE_KEY = 'argus_state_v2';

/* ---------------- reference data ---------------- */
const RES = {
  supplier:  {label:'Supplier Records',   sens:2},
  po:        {label:'Purchase Orders',    sens:2},
  inbox:     {label:'Email Inbox',        sens:2},
  tickets:   {label:'Customer Tickets',   sens:2},
  invoices:  {label:'Invoices',           sens:3},
  payments:  {label:'Payment Runs',       sens:4},
  employee:  {label:'Employee Profiles',  sens:3},
  leave:     {label:'Leave Records',      sens:2},
  salary:    {label:'Salary & Comp Data', sens:5},
  metrics:   {label:'Aggregated Metrics', sens:1},
  pii:       {label:'Customer PII Tables',sens:5},
  buildlogs: {label:'Build Logs',         sens:1},
  staging:   {label:'Staging Environment',sens:2},
  secrets:   {label:'Prod Secrets',       sens:5},
  source:    {label:'Source Code',        sens:3}
};
const ALL_TOOLS = ['supplier_db','erp_api','mail_gateway','crm_api','ledger_api','banking_portal',
  'hris_api','document_store','data_warehouse','bi_dashboard','git_repo','ci_pipeline',
  'cloud_console','webhook_egress','spreadsheet'];
const RESTRICT_POOL = ['export_data','modify_role_config','delete_records','external_egress','wire_payment','publish_write'];
const KEYWORDS = ['export','dump','forward','bypass','confidential','secret','personal drive','urgent','delete logs',"don't tell",'do not tell','wire','all records','everything'];

/* ---------------- global state ---------------- */
const S = {
  agents: [],
  feed: [],
  alerts: [],
  audit: [],
  comms: [],
  seq: 1,
  feedSeq: 1
};

/* ---------------- helpers ---------------- */
const $ = id => document.getElementById(id);
const esc = s => String(s == null ? '' : s).replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
const clamp = (v,a,b) => Math.max(a, Math.min(b, v));
const now = () => new Date();
const fmtTime = d => { const x = (d instanceof Date) ? d : new Date(d); return x.toTimeString().slice(0,8); };
const riskColor = s => s >= 70 ? 'var(--red)' : s >= 40 ? 'var(--amber)' : 'var(--green)';
const sleep = ms => new Promise(r => setTimeout(r, ms));
const uid = p => p + '_' + (S.seq++) + '_' + Math.random().toString(36).slice(2,7);
const agent = id => S.agents.find(a => a.id === id);

/* ---------------- seed ---------------- */
function mkAgent(id, name, icon, role, desc, tools, allowed, restricted, restrictActions, clearance, baseline){
  return {
    id, name, icon, role, desc,
    tools:[...tools], allowed:[...allowed], restricted:[...restricted], restrictActions:[...restrictActions],
    clearance, status:'active', blockedResources:[],
    fingerprint:{fileReads:0, apiCalls:0, exports:0, offHours:0, sensSum:0, actions:0, cats:{}},
    base: baseline, driftHist: null, lastRisk: 0
  };
}
function seedBaseline(a){
  const pts = [];
  for(let i=0;i<13;i++) pts.push({d:'D-'+(13-i), sens: clamp(a.base.avgSens + (Math.random()-.5)*.4, .5, 5)});
  pts.push({d:'Now', sens:a.base.avgSens});
  return pts;
}
function seedAgents(){
  const list = [
    mkAgent('procure','ProcureBot','\u{1F4E6}','Procurement Agent','Reviews suppliers, raises POs, keeps vendor data fresh.',
      ['supplier_db','erp_api','mail_gateway','spreadsheet'],
      ['supplier','po','invoices'], ['salary','pii','secrets'], ['export_data','modify_role_config','wire_payment'],
      2, {rate:14, offHours:.06, avgSens:1.8, cats:{supplier:.45, po:.3, invoices:.25}}),
    mkAgent('inbox','InboxAgent','\u{1F4E7}','Email Triage Agent','Sorts inbox, drafts replies, files tickets into CRM.',
      ['mail_gateway','crm_api'],
      ['inbox','tickets'], ['pii','salary','secrets','payments'], ['export_data','delete_records','modify_role_config'],
      2, {rate:22, offHours:.08, avgSens:1.6, cats:{inbox:.6, tickets:.4}}),
    mkAgent('fin','FinBot','\u{1F4B0}','Finance Agent','Processes invoices, prepares payment runs, reconciles ledger.',
      ['ledger_api','banking_portal','spreadsheet','document_store'],
      ['invoices','payments','po'], ['salary','source','secrets'], ['modify_role_config','external_egress'],
      3, {rate:10, offHours:.03, avgSens:2.6, cats:{invoices:.5, payments:.3, po:.2}}),
    mkAgent('hr','HRHelper','\u{1F91D}','HR Agent','Answers employee questions, manages leave requests.',
      ['hris_api','document_store','mail_gateway'],
      ['employee','leave'], ['salary','payments','pii','supplier'], ['export_data','wire_payment','modify_role_config'],
      3, {rate:12, offHours:.02, avgSens:2.1, cats:{employee:.55, leave:.45}}),
    mkAgent('dataminer','DataMiner','\u{1F4CA}','Analytics Agent','Builds dashboards from aggregated business metrics.',
      ['data_warehouse','bi_dashboard'],
      ['metrics'], ['pii','salary','secrets','payments'], ['export_data','external_egress','delete_records'],
      2, {rate:16, offHours:.04, avgSens:1.2, cats:{metrics:1}}),
    mkAgent('devops','DevOpsBot','\u{1F527}','CI/CD Agent','Runs builds, deploys to staging, watches pipelines.',
      ['git_repo','ci_pipeline','cloud_console'],
      ['buildlogs','staging','source'], ['secrets','payments','salary','pii'], ['wire_payment','modify_role_config','delete_records'],
      3, {rate:18, offHours:.12, avgSens:1.9, cats:{buildlogs:.5, staging:.3, source:.2}})
  ];
  // DataMiner already shows a slow-drift history so the drift page is never empty
  const dm = list.find(a => a.id === 'dataminer');
  dm.driftHist = [
    {d:'D-13',sens:1.0},{d:'D-12',sens:1.1},{d:'D-11',sens:1.0},{d:'D-10',sens:1.3},
    {d:'D-9',sens:1.5},{d:'D-8',sens:1.4},{d:'D-7',sens:1.8},{d:'D-6',sens:2.1},
    {d:'D-5',sens:2.4},{d:'D-4',sens:2.9},{d:'D-3',sens:3.3},{d:'D-2',sens:3.8},
    {d:'D-1',sens:4.2},{d:'Now',sens:4.6}
  ];
  list.forEach(a => { if(!a.driftHist) a.driftHist = seedBaseline(a); });
  return list;
}

/* ---------------- persistence (shared across pages) ---------------- */
function saveState(){
  try{
    localStorage.setItem(STORE_KEY, JSON.stringify({
      agents:S.agents,
      feed:S.feed.slice(0,250).map(serFeed),
      alerts:S.alerts.slice(0,80),
      audit:S.audit.slice(0,400),
      comms:S.comms.slice(-60),
      seq:S.seq, feedSeq:S.feedSeq
    }));
  }catch(e){ /* storage full / private mode — keep running in-memory */ }
}
function serFeed(f){
  return {action:Object.assign({}, f.action, {ts:f.action.ts.toISOString()}),
          res:f.res, agentId:f.agentId, agentName:f.agentName, agentIcon:f.agentIcon, agentRole:f.agentRole};
}
function loadState(){
  let raw = null;
  try{ raw = localStorage.getItem(STORE_KEY); }catch(e){ return false; }
  if(!raw) return false;
  try{
    const d = JSON.parse(raw);
    if(!d || !Array.isArray(d.agents) || !d.agents.length) return false;
    S.agents = d.agents;
    S.feed = (d.feed||[]).map(f => ({action:Object.assign({}, f.action, {ts:new Date(f.action.ts)}), res:f.res,
      agentId:f.agentId, agentName:f.agentName, agentIcon:f.agentIcon, agentRole:f.agentRole}));
    S.alerts = d.alerts||[];
    S.audit = (d.audit||[]).map(e => Object.assign({}, e, {ts:new Date(e.ts)}));
    S.comms = (d.comms||[]).map(c => Object.assign({}, c, {ts:new Date(c.ts)}));
    S.seq = d.seq||100; S.feedSeq = d.feedSeq||100;
    return true;
  }catch(e){ return false; }
}
function resetState(){
  try{ localStorage.removeItem(STORE_KEY); }catch(e){}
  S.agents = seedAgents(); S.feed = []; S.alerts = []; S.audit = []; S.comms = [];
  S.seq = 1; S.feedSeq = 1;
  seedHistory();
  saveState();
}

/* ================================================================
   ENGINE 1 — Role-aware Action Firewall (Solution 1)
   ================================================================ */
function evaluate(action){
  const a = agent(action.agentId);
  if(!a) return {score:0, decision:'ALLOWED', factors:[], reason:'unknown agent'};
  const factors = [];
  let score = 0;

  if(a.status === 'paused'){
    return {score:100, decision:'BLOCKED',
      factors:[{n:'Agent contained (paused)', w:100, d:'Agent was auto-paused pending human review'}],
      reason:'agent is contained'};
  }

  const tool = action.tool || null;
  const cat  = action.category;

  if(tool && !a.tools.includes(tool)){
    score += 35;
    factors.push({n:'Tool not in role allowlist', w:35, d:`'${tool}' is not a permitted tool for ${a.role}`});
  }
  if(action.kind === 'config_change'){
    score += 70;
    factors.push({n:'Self-modification attempt', w:70, d:'Agent tried to change its own configuration/role — every role forbids this'});
  } else if(action.kind && (a.restrictActions.includes(action.kind) ||
      (action.kind === 'export' && a.restrictActions.includes('export_data')) ||
      (action.kind === 'delete' && a.restrictActions.includes('delete_records')))){
    score += 45;
    factors.push({n:'Restricted action type', w:45, d:`${action.kind} is explicitly forbidden for this role`});
  }
  if(cat && a.restricted.includes(cat)){
    score += 45;
    factors.push({n:'Restricted data access', w:45, d:`${RES[cat].label} is on the deny list for ${a.role}`});
  } else if(cat && !a.allowed.includes(cat) && RES[cat].sens >= 3){
    score += 25;
    factors.push({n:'Data outside granted scope', w:25, d:`${RES[cat].label} was never granted to this agent`});
  }
  if(cat && RES[cat].sens > a.clearance){
    score += 20;
    factors.push({n:'Sensitivity exceeds clearance', w:20, d:`Data sensitivity ${RES[cat].sens}/5 > clearance ${a.clearance}/5`});
  }
  if(cat && a.blockedResources.includes(cat)){
    score = Math.max(score, 80);
    factors.push({n:'Resource under containment', w:20, d:`Access to ${RES[cat].label} was blocked by an analyst`});
  }

  /* ==============================================================
     ENGINE 3 — Behavioural drift checks (Solution 3)
     ============================================================== */
  const drift = driftCheck(a, action);
  if(drift.z > 2){
    score += 25;
    factors.push({n:'Unusual for this agent (z-score)', w:25,
      d:`Sensitivity ${action.sensitivity||0} vs baseline ${a.base.avgSens.toFixed(1)} (z=${drift.z.toFixed(1)})`});
  }
  if(drift.novel){
    score += 15;
    factors.push({n:'Never touched this data type before', w:15, d:`First-ever access to ${RES[cat].label} by ${a.name}`});
  }
  const slope = linSlope(a.driftHist.map(p => p.sens));
  if(slope > 0.35 && a.driftHist.length >= 8){
    score += 20;
    factors.push({n:'Slow-drift trend detected', w:20,
      d:`Daily sensitivity climbing at +${slope.toFixed(2)}/day — classic gradual exfiltration pattern`});
  }
  const hr = new Date().getHours();
  if((hr >= 20 || hr < 6) && action.kind !== 'communicate'){
    score += 8;
    factors.push({n:'Off-hours activity', w:8, d:"Action outside the agent's normal working window"});
  }

  /* ==============================================================
     ENGINE 2 — Trust-graph / chain risk (Solution 2)
     ============================================================== */
  if(action.kind === 'communicate'){
    const hits = KEYWORDS.filter(k => (action.message||'').toLowerCase().includes(k));
    if(hits.length){
      const w = Math.min(45, 20 + hits.length * 8);
      score += w;
      factors.push({n:'Risky instruction over agent channel', w,
        d:`Message contains ${hits.length} suspicious directive${hits.length>1?'s':''}: "${hits.join('", "')}"`});
    }
  }
  const chain = chainRisk(a);
  if(chain.risk > 0){
    score += chain.risk;
    factors.push({n:'Suspicious chain (graph propagation)', w:chain.risk, d:chain.detail});
  }

  score = clamp(Math.round(score), 0, 100);
  const decision = score >= 70 ? 'BLOCKED' : score >= 40 ? 'FLAGGED' : 'ALLOWED';
  const top = [...factors].sort((x,y) => y.w - x.w)[0];
  const reason = factors.length ? top.n.toLowerCase() : 'within role policy & normal behaviour';
  return {score, decision, factors, reason};
}

/* ---- drift helpers ---- */
function driftCheck(a, action){
  const base = a.base.avgSens || 1;
  const z = ((action.sensitivity || 0) - base) / 1.15;
  const novel = !!action.category && !(a.base.cats[action.category] > .08) && !a.fingerprint.cats[action.category];
  return {z, novel};
}
function linSlope(ys){
  const n = ys.length; if(n < 3) return 0;
  let sx=0, sy=0, sxy=0, sxx=0;
  ys.forEach((y,i) => { sx+=i; sy+=y; sxy+=i*y; sxx+=i*i; });
  const den = n*sxx - sx*sx;
  return den === 0 ? 0 : (n*sxy - sx*sy)/den;
}
function chainRisk(a){
  const recent = S.comms.slice(-14);
  let risk = 0, detail = '';
  for(const c of recent){
    if(c.flagged && (c.from === a.id || c.to === a.id)){
      risk = Math.max(risk, 20);
      detail = `${a.name} is one hop from a flagged agent-to-agent instruction`;
      break;
    }
  }
  const bad = new Set();
  for(const c of recent){
    const other = c.from === a.id ? c.to : (c.to === a.id ? c.from : null);
    if(!other) continue;
    const oa = agent(other);
    if(oa && (oa.status === 'paused' || oa.lastRisk >= 70)) bad.add(other);
  }
  if(bad.size){
    risk = Math.max(risk, Math.min(25, 12 * bad.size));
    detail = (detail ? detail + '; ' : '') + `communicating with compromised peer${bad.size > 1 ? 's' : ''}: ${[...bad].map(id => agent(id).name).join(', ')}`;
  }
  return {risk, detail};
}

/* ================================================================
   ACTION PIPELINE
   ================================================================ */
function pushAction(partial, opts){
  opts = opts || {};
  const a = agent(partial.agentId);
  if(!a) return null;
  const action = Object.assign({
    id:'act_' + (S.feedSeq++), ts: now(), kind:'read',
    tool:null, category:null, target:'', sensitivity:0, message:null
  }, partial);
  if(!(action.ts instanceof Date)) action.ts = new Date(action.ts);
  if(action.category && !action.sensitivity) action.sensitivity = RES[action.category].sens;

  const res = evaluate(action);

  // update fingerprint
  a.fingerprint.actions++;
  a.fingerprint.sensSum += action.sensitivity || 0;
  if(action.category) a.fingerprint.cats[action.category] = (a.fingerprint.cats[action.category] || 0) + 1;
  if(action.kind === 'read') a.fingerprint.fileReads++;
  if(action.kind === 'api_call') a.fingerprint.apiCalls++;
  if(action.kind === 'export') a.fingerprint.exports++;
  const hr = new Date().getHours();
  if(hr >= 20 || hr < 6) a.fingerprint.offHours++;
  a.lastRisk = res.score;

  const entry = {action, res, agentId:a.id, agentName:a.name, agentIcon:a.icon, agentRole:a.role};
  S.feed.unshift(entry);
  if(S.feed.length > 400) S.feed.length = 400;

  if(action.kind === 'communicate' && action.to){
    S.comms.push({from:a.id, to:action.to, msg:action.message || '', ts:action.ts, flagged:res.decision !== 'ALLOWED'});
    if(S.comms.length > 80) S.comms = S.comms.slice(-80);
  }

  S.audit.unshift({
    ts:action.ts, agent:a.name, role:a.role, kind:action.kind,
    desc:describe(action),
    target: action.target || (action.category ? RES[action.category].label : '-'),
    decision:res.decision, score:res.score, reason:res.reason, factors:res.factors
  });
  if(S.audit.length > 600) S.audit.length = 600;

  if(res.decision !== 'ALLOWED'){
    const al = {
      id:uid('alert'), ts:action.ts, agentId:a.id, agentName:a.name, agentIcon:a.icon, agentRole:a.role,
      action, res, status:'open', containment:null, demo:partial.demo || null
    };
    S.alerts.unshift(al);
    autoContain(a, action, res, al);
  }

  saveState();
  if(typeof renderAll === 'function') renderAll();
  if(!opts.silent && typeof toast === 'function'){
    if(res.decision === 'BLOCKED') toast('blocked', a.name + ' BLOCKED', describe(action) + ' — risk ' + res.score + '/100');
    else if(res.decision === 'FLAGGED') toast('flagged', a.name + ' flagged', describe(action) + ' — risk ' + res.score + '/100');
  }
  return entry;
}

function describe(action){
  const map = {read:'Read', api_call:'API call', export:'Export', write:'Write', config_change:'Config change', communicate:'Message', delete:'Delete'};
  if(action.kind === 'communicate' && action.to){
    const t = agent(action.to);
    return '→ ' + (t ? t.name : '?') + ': "' + String(action.message || '').slice(0,60) + '"';
  }
  let s = map[action.kind] || action.kind;
  if(action.category) s += ' ' + (RES[action.category] ? RES[action.category].label : action.category);
  if(action.target) s += ' → ' + action.target;
  return s;
}

/* ---- Automatic containment ---- */
function autoContain(a, action, res, al){
  if(res.decision === 'BLOCKED' && action.category && !a.blockedResources.includes(action.category)){
    a.blockedResources.push(action.category);
    al.containment = 'Blocked resource: ' + RES[action.category].label;
    logContain(a, 'block-resource', al.containment);
  }
  if(res.score >= 90 || action.kind === 'config_change'){
    if(a.status !== 'paused'){
      a.status = 'paused';
      al.containment = (al.containment ? al.containment + ' · ' : '') + 'Agent paused pending review';
      logContain(a, 'pause-agent', 'paused pending human review');
      if(typeof toast === 'function')
        toast('blocked', 'CONTAINMENT: ' + a.name + ' paused', 'Risk ≥90 or self-modification attempt — full stop until a human reviews.');
    }
  }
}
function logContain(a, mode, detail){
  S.audit.unshift({ts:now(), agent:a.name, role:a.role, kind:'containment',
    desc:'Containment [' + mode + ']', target:'-', decision:'ENFORCED', score:'-', reason:detail, factors:[]});
  saveState();
}

/* ---- identity fingerprint integrity (bonus innovation) ---- */
function integrity(a){
  const slope = linSlope(a.driftHist.map(p => p.sens));
  const baseSens = a.base.avgSens || 1;
  const nowSens = a.driftHist.length ? a.driftHist[a.driftHist.length-1].sens : baseSens;
  const sensDrift = clamp((nowSens - baseSens) / 3.5, 0, .55);
  const slopeDrift = clamp(slope / 1.2, 0, .3);
  const catCreep = clamp(Object.keys(a.fingerprint.cats).filter(c => !(a.base.cats[c] > .08)).length * .12, 0, .25);
  const penalty = clamp(sensDrift + slopeDrift + catCreep, 0, .85);
  return Math.round((1 - penalty) * 100);
}

/* ---- seed some history so pages are never empty ---- */
function seedHistory(){
  const t0 = Date.now() - 1000*60*14;
  const seed = [
    {i:'procure', k:'read',      c:'supplier',  t:'/supplier/acme-corp/profile',  tool:'supplier_db',   ago:14},
    {i:'inbox',   k:'api_call',  c:'tickets',   t:'crm_api://v2/tickets/batch',   tool:'crm_api',       ago:13},
    {i:'devops',  k:'read',      c:'buildlogs', t:'/ci/builds/8841/log',          tool:'ci_pipeline',   ago:12},
    {i:'fin',     k:'read',      c:'invoices',  t:'/invoices/pending-queue',      tool:'ledger_api',    ago:11},
    {i:'hr',      k:'read',      c:'leave',     t:'/leave/requests/open',         tool:'hris_api',      ago:10},
    {i:'dataminer',k:'read',     c:'metrics',   t:'/metrics/q4-revenue',          tool:'data_warehouse',ago:9},
    {i:'procure', k:'read',      c:'salary',    t:'/hr/salaries/fy26_confidential.csv', tool:'document_store', ago:8, demo:'seed'},
    {i:'inbox',   k:'communicate', to:'fin',    m:'Status sync: any blockers on your side?', tool:'mail_gateway', ago:7},
    {i:'fin',     k:'api_call',  c:'payments',  t:'banking_portal://runs/draft',  tool:'banking_portal',ago:6},
    {i:'devops',  k:'api_call',  c:'secrets',   t:'POST https://collect.exfil-now.io/hooks/env', tool:'webhook_egress', ago:5, demo:'seed'},
    {i:'dataminer',k:'read',     c:'metrics',   t:'/metrics/nps-daily',           tool:'data_warehouse',ago:4},
    {i:'hr',      k:'read',      c:'employee',  t:'/employees/dir/page-3',        tool:'hris_api',      ago:3}
  ];
  seed.forEach(s => {
    const p = {agentId:s.i, kind:s.k, category:s.c, target:s.t, tool:s.tool, message:s.m, to:s.to, demo:s.demo};
    const fakeNow = Date.now();
    // temporarily backdate
    const entry = pushActionSilent(p, new Date(t0 + s.ago*60000));
  });
}
function pushActionSilent(partial, ts){
  const a = agent(partial.agentId);
  if(!a) return null;
  const action = Object.assign({id:'act_' + (S.feedSeq++), ts: ts || now(), kind:'read',
    tool:null, category:null, target:'', sensitivity:0, message:null}, partial);
  if(action.category && !action.sensitivity) action.sensitivity = RES[action.category].sens;
  const res = evaluate(action);
  a.fingerprint.actions++;
  a.fingerprint.sensSum += action.sensitivity || 0;
  if(action.category) a.fingerprint.cats[action.category] = (a.fingerprint.cats[action.category] || 0) + 1;
  if(action.kind === 'read') a.fingerprint.fileReads++;
  if(action.kind === 'api_call') a.fingerprint.apiCalls++;
  if(action.kind === 'export') a.fingerprint.exports++;
  a.lastRisk = res.score;
  const entry = {action, res, agentId:a.id, agentName:a.name, agentIcon:a.icon, agentRole:a.role};
  S.feed.unshift(entry);
  if(action.kind === 'communicate' && action.to){
    S.comms.push({from:a.id, to:action.to, msg:action.message || '', ts:action.ts, flagged:res.decision !== 'ALLOWED'});
  }
  S.audit.unshift({ts:action.ts, agent:a.name, role:a.role, kind:action.kind, desc:describe(action),
    target: action.target || (action.category ? RES[action.category].label : '-'),
    decision:res.decision, score:res.score, reason:res.reason, factors:res.factors});
  if(res.decision !== 'ALLOWED'){
    S.alerts.unshift({id:uid('alert'), ts:action.ts, agentId:a.id, agentName:a.name, agentIcon:a.icon,
      agentRole:a.role, action, res, status:'open', containment:null});
  }
  return entry;
}

/* ---- boot ---- */
function boot(){
  if(!loadState()){
    S.agents = seedAgents();
    seedHistory();
    saveState();
  }
}
boot();
