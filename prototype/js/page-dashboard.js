/* ARGUS — dashboard page controller */
'use strict';

function renderAll(){
  renderStats();
  renderDashFeed();
  renderKillchain();
  renderRiskBoard();
  updateBadge();
}

function renderStats(){
  const blocked = S.feed.filter(f => f.res.decision === 'BLOCKED').length;
  const open = S.alerts.filter(a => a.status === 'open').length;
  const avg = S.feed.length ? Math.round(S.feed.reduce((s,f) => s + f.res.score, 0) / S.feed.length) : 0;
  const paused = S.agents.filter(a => a.status === 'paused').length;
  $('stAgents').textContent = S.agents.length;
  $('stAgentsSub').textContent = paused + ' paused · ' + (S.agents.length - paused) + ' active';
  $('stActions').textContent = S.feed.length;
  $('stBlocked').textContent = blocked;
  $('stAlerts').textContent = open;
  $('stRisk').textContent = avg;

  const lvl = (open >= 4 || blocked >= 5) ? 3 : open >= 1 ? 2 : 1;
  const tb = $('threatBox');
  tb.className = 'threat lvl-' + lvl;
  $('threatText').textContent = 'THREAT LEVEL: ' + ['LOW','ELEVATED','HIGH'][lvl-1];
}

function renderDashFeed(){
  const el = $('dashFeed');
  el.innerHTML = S.feed.slice(0,8).map(feedRow).join('') || emptyBox('No activity yet — enable Autopilot or run an attack demo.');
}

function renderKillchain(){
  const stages = ['Recon','Tool Abuse','Lateral Movement','Collection','Privilege Abuse','Exfiltration'];
  const counts = {}; stages.forEach(s => counts[s] = 0);
  S.audit.forEach(e => {
    if(e.decision === 'ALLOWED') return;
    const st = killStage(e); if(st) counts[st]++;
  });
  const maxc = Math.max(1, ...Object.values(counts));
  $('killchain').innerHTML = stages.map(s => {
    const col = counts[s] === 0 ? '#2a3f5c' : counts[s] < 3 ? 'var(--amber)' : 'var(--red)';
    return '<div style="display:flex;align-items:center;gap:9px;margin-bottom:7px;font-size:11.5px">' +
      '<span style="width:118px;color:var(--muted)">' + s + '</span>' +
      '<div style="flex:1;height:7px;border-radius:4px;background:#1a2740;overflow:hidden">' +
      '<i style="display:block;height:100%;width:' + (counts[s]/maxc*100) + '%;background:' + col + ';border-radius:4px"></i></div>' +
      '<span style="font-family:var(--mono);font-weight:700;color:' + col + ';width:20px;text-align:right">' + counts[s] + '</span></div>';
  }).join('');
}

function renderRiskBoard(){
  $('riskBoard').innerHTML = S.agents.map(a => {
    const integ = integrity(a);
    const col = integ >= 85 ? 'var(--green)' : integ >= 60 ? 'var(--amber)' : 'var(--red)';
    return '<a class="agent-card ' + (a.status === 'paused' ? 'paused' : '') + '" href="drift.html?agent=' + a.id + '" style="display:block">' +
      '<div class="agent-head"><div class="avatar">' + a.icon + '</div>' +
      '<div style="flex:1"><div class="nm">' + esc(a.name) + '</div><div class="rl">' + esc(a.role) + '</div></div>' +
      '<span class="status-chip ' + (a.status === 'active' ? 'st-active' : 'st-paused') + '">' + a.status.toUpperCase() + '</span></div>' +
      '<div style="display:flex;align-items:center;gap:10px">' + ring(integ, 46) +
      '<div style="font-size:11px;color:var(--muted);line-height:1.6">Identity integrity<br><b style="font-family:var(--mono);color:' + col + '">' + integ + '%</b>' +
      ' · last risk <b style="font-family:var(--mono);color:' + riskColor(a.lastRisk) + '">' + a.lastRisk + '</b></div></div></a>';
  }).join('');
}

initChrome('dashboard');
renderAll();
startSim();
runDemoFromURL();
// re-render when another page mutates shared state (storage event)
window.addEventListener('storage', e => { if(e.key === STORE_KEY){ loadState(); renderAll(); } });
setInterval(() => { if(!demoBusy) renderAll(); }, 2500);
