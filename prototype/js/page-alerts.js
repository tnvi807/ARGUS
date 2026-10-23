/* ARGUS — alerts & containment page controller */
'use strict';

function renderAll(){
  renderAlerts();
  const open = S.alerts.filter(a => a.status === 'open').length;
  const blocked = S.feed.filter(f => f.res.decision === 'BLOCKED').length;
  const lvl = (open >= 4 || blocked >= 5) ? 3 : open >= 1 ? 2 : 1;
  $('threatBox').className = 'threat lvl-' + lvl;
  $('threatText').textContent = 'THREAT LEVEL: ' + ['LOW','ELEVATED','HIGH'][lvl-1];
  updateBadge();
}

function renderAlerts(){
  if(!S.alerts.length){
    $('alertList').innerHTML = emptyBox('No alerts yet. Run an attack demo to see explainable alerts in action.');
    return;
  }
  $('alertList').innerHTML = S.alerts.map(al => {
    const sev = al.res.score >= 70 ? 'sev-high' : 'sev-med';
    const r = al.res.score;
    const circ = 2*Math.PI*24, off = circ*(1 - r/100);
    return '<div class="alert-card ' + sev + '">' +
      '<div class="alert-top">' +
      '<div class="score-gauge"><svg width="56" height="56" viewBox="0 0 56 56">' +
      '<circle cx="28" cy="28" r="24" fill="none" stroke="#1a2740" stroke-width="5"/>' +
      '<circle cx="28" cy="28" r="24" fill="none" stroke="' + riskColor(r) + '" stroke-width="5" stroke-linecap="round"' +
      ' stroke-dasharray="' + circ + '" stroke-dashoffset="' + off + '" transform="rotate(-90 28 28)"/></svg>' +
      '<div class="num" style="color:' + riskColor(r) + '">' + r + '<small>/100</small></div></div>' +
      '<div class="alert-title"><h4>' + al.agentIcon + ' ' + esc(al.agentName) + ' &mdash; ' + esc(shortDesc({action:al.action})) + '</h4>' +
      '<div class="meta">' + esc(al.agentRole) + ' · ' + fmtTime(al.ts) + ' · <span class="badge-dec d-' + al.res.decision.toLowerCase() + '">' + al.res.decision + '</span>' +
      (al.status === 'contained' ? '<span class="badge-dec d-blocked" style="margin-left:5px">CONTAINED</span>' : '') +
      (al.status === 'dismissed' ? '<span class="badge-dec d-allowed" style="margin-left:5px">DISMISSED</span>' : '') + '</div>' +
      '<div class="expect"><div><span>Expected role behaviour</span>' + esc(roleShould(al.agentId)) + '</div>' +
      '<div><span>Actual attempt</span>' + esc(shortDesc({action:al.action})) + '</div></div></div></div>' +
      '<div class="factors">' +
      (al.res.factors.length ? al.res.factors.map(f =>
        '<div class="factor"><span class="fn"><b>' + esc(f.n) + '</b> — ' + esc(f.d) + '</span>' +
        '<span class="fw ' + (f.w >= 35 ? 'hi' : '') + '">+' + f.w + '</span></div>').join('')
        : '<div class="factor"><span class="fn">No rule fired (informational)</span></div>') +
      '</div>' +
      '<div class="evidence">🔍 Evidence: agent=' + esc(al.agentName) + ' | action=' + esc(al.action.kind) +
      ' | tool=' + esc(al.action.tool || '-') + ' | target=' + esc(al.action.target || al.action.message || '-') +
      ' | sensitivity=' + (al.action.sensitivity || 0) + '/5 | decision=' + al.res.decision + '</div>' +
      (al.containment ? '<div style="font-size:11.5px;color:var(--amber);margin-top:4px">🛡 Auto-containment executed: ' + esc(al.containment) + '</div>' : '') +
      '<div class="alert-actions">' +
      '<button class="btn danger sm" onclick="containResource(\'' + al.id + '\')">🔒 Block this resource</button>' +
      '<button class="btn amber sm" onclick="pauseFromAlert(\'' + al.id + '\')">⏸ Pause agent</button>' +
      '<button class="btn sm" onclick="revokeTool(\'' + al.id + '\')">✂ Revoke tool</button>' +
      '<button class="btn green sm" onclick="dismissAlert(\'' + al.id + '\')">✓ Mark reviewed</button>' +
      '</div></div>';
  }).join('');
}

function containResource(alertId){
  const al = S.alerts.find(x => x.id === alertId); if(!al) return;
  const a = agent(al.agentId);
  const cat = al.action.category;
  if(cat && !a.blockedResources.includes(cat)) a.blockedResources.push(cat);
  al.status = 'contained';
  al.containment = (al.containment ? al.containment + ' · ' : '') + 'Resource ' + (RES[cat] ? RES[cat].label : cat) + ' blocked';
  logContain(a, 'block-resource', 'analyst blocked ' + cat);
  toast('ok','Resource contained', a.name + ' can no longer touch ' + (RES[cat] ? RES[cat].label : cat) + '. Legitimate work continues.');
  saveState(); renderAll();
}
function pauseFromAlert(alertId){
  const al = S.alerts.find(x => x.id === alertId); if(!al) return;
  const a = agent(al.agentId);
  a.status = 'paused'; al.status = 'contained';
  al.containment = (al.containment ? al.containment + ' · ' : '') + 'Agent paused';
  logContain(a, 'pause-agent', 'analyst paused agent');
  toast('blocked','Agent paused', a.name + ' is frozen until a human reviews it.');
  saveState(); renderAll();
}
function revokeTool(alertId){
  const al = S.alerts.find(x => x.id === alertId); if(!al) return;
  const a = agent(al.agentId);
  const t = al.action.tool;
  if(t) a.tools = a.tools.filter(x => x !== t);
  al.status = 'contained';
  al.containment = (al.containment ? al.containment + ' · ' : '') + 'Tool ' + (t || '?') + ' revoked';
  logContain(a, 'revoke-tool', 'revoked ' + t);
  toast('ok','Tool revoked', a.name + ' lost access to ' + t + '.');
  saveState(); renderAll();
}
function dismissAlert(alertId){
  const al = S.alerts.find(x => x.id === alertId);
  if(al){ al.status = 'dismissed'; saveState(); renderAll(); }
}
function clearDismissed(){
  S.alerts = S.alerts.filter(a => a.status !== 'dismissed');
  saveState(); renderAll();
}

initChrome('alerts');
renderAll();
startSim();
runDemoFromURL();
window.addEventListener('storage', e => { if(e.key === STORE_KEY){ loadState(); renderAll(); } });
setInterval(() => { if(!demoBusy) renderAll(); }, 2500);
