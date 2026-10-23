/* ================================================================
   ARGUS ui.js — shared chrome: nav, clock, toasts, common widgets
   ================================================================ */
'use strict';

const TITLES = {
  dashboard:['Overview','security operations center'],
  agents:['Agent Registry','roles · tools · data · restrictions'],
  live:['Live Activity','every action, every millisecond'],
  graph:['Trust Graph','who talks to whom · where breaches spread'],
  drift:['Drift Detection','behavioural baselines · slow-attack defense'],
  alerts:['Alerts & Containment','explainable scores · one-click playbooks'],
  audit:['Audit Log','append-only · exportable · compliance-ready'],
  features:['How It Works','plain-English guide for humans']
};

function initChrome(page){
  // build sidebar links + highlight active page
  buildSidebar(page);
  const t = TITLES[page] || TITLES.dashboard;
  const titleEl = $('viewTitle'); if(titleEl) titleEl.textContent = t[0];
  const crumbEl = $('viewCrumb'); if(crumbEl) crumbEl.textContent = t[1];

  // clock
  const tick = () => { const c = $('clock'); if(c) c.textContent = new Date().toTimeString().slice(0,8); };
  tick(); setInterval(tick, 1000);

  // autopilot toggle persists across pages
  const ap = $('autopilot');
  if(ap){
    ap.checked = localStorage.getItem('argus_autopilot') !== '0';
    ap.addEventListener('change', () => {
      localStorage.setItem('argus_autopilot', ap.checked ? '1' : '0');
      S.autopilot = ap.checked;
    });
    S.autopilot = ap.checked;
  }

  // alert badge
  updateBadge();
}

function updateBadge(){
  const open = S.alerts.filter(a => a.status === 'open').length;
  document.querySelectorAll('.nav-alert-badge').forEach(el => {
    el.textContent = open;
    el.classList.toggle('hide', open === 0);
  });
}

/* ---------------- toasts ---------------- */
function toast(kind, title, msg){
  const box = $('toasts'); if(!box) return;
  const t = document.createElement('div');
  t.className = 'toast ' + (kind === 'blocked' ? 'blocked' : kind === 'flagged' ? 'flagged' : 'ok');
  const ico = kind === 'blocked' ? '⛔' : kind === 'flagged' ? '⚠' : '✅';
  t.innerHTML = '<span class="ti">' + ico + '</span><div><b>' + esc(title) + '</b><p>' + esc(msg) + '</p></div>';
  box.appendChild(t);
  setTimeout(() => { t.style.transition = '.3s'; t.style.opacity = '0'; t.style.transform = 'translateX(20px)'; setTimeout(() => t.remove(), 320); }, 4200);
}

/* ---------------- shared widgets ---------------- */
function ring(pct, size){
  size = size || 52;
  const r = size/2 - 5, c = 2*Math.PI*r, off = c*(1 - pct/100);
  const col = pct >= 85 ? 'var(--green)' : pct >= 60 ? 'var(--amber)' : 'var(--red)';
  return '<svg width="' + size + '" height="' + size + '" viewBox="0 0 ' + size + ' ' + size + '">' +
    '<circle cx="' + size/2 + '" cy="' + size/2 + '" r="' + r + '" fill="none" stroke="#1a2740" stroke-width="5"/>' +
    '<circle cx="' + size/2 + '" cy="' + size/2 + '" r="' + r + '" fill="none" stroke="' + col + '" stroke-width="5"' +
    ' stroke-linecap="round" stroke-dasharray="' + c + '" stroke-dashoffset="' + off + '"' +
    ' transform="rotate(-90 ' + size/2 + ' ' + size/2 + ')"/>' +
    '<text x="50%" y="54%" text-anchor="middle" fill="' + col + '" font-size="' + (size > 60 ? 15 : 11) + '" font-family="monospace" font-weight="700">' + pct + '%</text></svg>';
}

function feedRow(f){
  const d = f.res.decision.toLowerCase();
  return '<div class="feed-row ' + d + '">' +
    '<span class="ts">' + fmtTime(f.action.ts) + '</span>' +
    '<div class="av">' + f.agentIcon + '</div>' +
    '<div class="body"><div class="t1">' + esc(f.agentName) + ' · ' + esc(shortDesc(f)) + '</div>' +
    '<div class="t2">' + esc(f.res.reason) + '</div></div>' +
    '<div class="riskbar"><i style="width:' + f.res.score + '%;background:' + riskColor(f.res.score) + '"></i></div>' +
    '<span class="risk-num" style="color:' + riskColor(f.res.score) + '">' + f.res.score + '</span>' +
    '<span class="badge-dec d-' + d + '">' + f.res.decision + '</span></div>';
}

function shortDesc(f){
  const map = {read:'Read', api_call:'API call', export:'Export', write:'Write', config_change:'Config change', communicate:'Message', delete:'Delete'};
  if(f.action.kind === 'communicate' && f.action.to){
    const t = agent(f.action.to);
    return '→ ' + (t ? t.name : '?') + ': "' + String(f.action.message || '').slice(0,52) + '"';
  }
  let s = map[f.action.kind] || f.action.kind;
  if(f.action.category) s += ' ' + (RES[f.action.category] ? RES[f.action.category].label : f.action.category);
  if(f.action.target) s += ' → ' + String(f.action.target).slice(0,48);
  return s;
}

function emptyBox(t){
  return '<div class="empty"><div class="big">👁</div>' + t + '</div>';
}

function roleShould(id){
  const a = agent(id); if(!a) return '—';
  return 'Use ' + a.tools.slice(0,2).join(', ') + ' on ' + a.allowed.slice(0,2).map(c => RES[c].label).join(', ');
}

function killStage(e){
  if(e.kind === 'config_change') return 'Privilege Abuse';
  if(e.kind === 'communicate' && e.decision !== 'ALLOWED') return 'Lateral Movement';
  if(e.kind === 'export') return 'Exfiltration';
  if(e.kind === 'delete') return 'Collection';
  if(/tool not in role/i.test(e.reason || '')) return 'Tool Abuse';
  if(e.score >= 40) return 'Collection';
  return 'Recon';
}

/* ---------------- sidebar builder (used by every page) ---------------- */
function buildSidebar(active){
  const pages = [
    ['dashboard','index.html','▣','Overview'],
    ['agents','agents.html','🤖','Agent Registry'],
    ['live','live.html','⚡','Live Activity'],
    ['graph','graph.html','🔗','Trust Graph'],
    ['drift','drift.html','📈','Drift Detection'],
    ['alerts','alerts.html','🚨','Alerts'],
    ['audit','audit.html','📋','Audit Log'],
    ['features','features.html','💡','How It Works']
  ];
  const nav = $('nav');
  if(!nav) return;
  nav.innerHTML = pages.map(p =>
    '<a class="navbtn' + (p[0] === active ? ' active' : '') + '" href="' + p[1] + '">' +
    '<span class="ico">' + p[2] + '</span> ' + p[3] +
    (p[0] === 'alerts' ? ' <span class="badge hide nav-alert-badge">0</span>' : '') + '</a>'
  ).join('');
}
