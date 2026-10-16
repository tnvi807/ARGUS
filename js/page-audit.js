/* ARGUS — audit log page controller */
'use strict';

function renderAll(){
  renderAudit();
  const open = S.alerts.filter(a => a.status === 'open').length;
  const blocked = S.feed.filter(f => f.res.decision === 'BLOCKED').length;
  const lvl = (open >= 4 || blocked >= 5) ? 3 : open >= 1 ? 2 : 1;
  $('threatBox').className = 'threat lvl-' + lvl;
  $('threatText').textContent = 'THREAT LEVEL: ' + ['LOW','ELEVATED','HIGH'][lvl-1];
  updateBadge();
}

function renderAudit(){
  const q = ($('auditSearch').value || '').toLowerCase();
  const rows = S.audit.filter(e => !q || (e.agent + e.desc + e.decision + e.reason + e.target).toLowerCase().includes(q));
  $('auditCount').textContent = rows.length + ' entries';
  $('auditBody').innerHTML = rows.slice(0,200).map(e => {
    const dec = String(e.decision || '').toLowerCase();
    const cls = dec.startsWith('block') || dec.startsWith('enforc') ? 'd-blocked' : dec.startsWith('flag') ? 'd-flagged' : 'd-allowed';
    return '<tr><td class="mono">' + fmtTime(e.ts) + '</td>' +
      '<td>' + esc(e.agent) + '<div style="font-size:10px;color:var(--dim)">' + esc(e.role || '') + '</div></td>' +
      '<td>' + esc(e.desc) + '</td>' +
      '<td class="mono" style="max-width:220px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap" title="' + esc(e.target) + '">' + esc(e.target) + '</td>' +
      '<td><span class="badge-dec ' + cls + '">' + esc(e.decision) + '</span></td>' +
      '<td class="mono" style="color:' + (typeof e.score === 'number' ? riskColor(e.score) : 'var(--dim)') + '">' + e.score + '</td>' +
      '<td style="max-width:250px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap" title="' + esc(e.reason) + '">' + esc(e.reason) + '</td></tr>';
  }).join('') || '<tr><td colspan="7" style="text-align:center;padding:30px;color:var(--dim)">No entries match your search.</td></tr>';
}

function exportAudit(fmt){
  let text, mime, name;
  if(fmt === 'json'){
    text = JSON.stringify(S.audit.map(e => ({
      ts: new Date(e.ts).toISOString(), agent:e.agent, role:e.role, action:e.desc,
      target:e.target, decision:e.decision, score:e.score, reason:e.reason, factors:e.factors
    })), null, 2);
    mime = 'application/json'; name = 'argus-audit.json';
  } else {
    const q = v => '"' + String(v).replace(/"/g,'""') + '"';
    text = 'time,agent,role,action,target,decision,score,reason\n' +
      S.audit.map(e => [new Date(e.ts).toISOString(), e.agent, e.role, e.desc, e.target, e.decision, e.score, e.reason].map(q).join(',')).join('\n');
    mime = 'text/csv'; name = 'argus-audit.csv';
  }
  const blob = new Blob([text], {type:mime});
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a'); a.href = url; a.download = name; a.click();
  URL.revokeObjectURL(url);
  toast('ok','Audit exported', S.audit.length + ' entries saved as ' + name);
}

initChrome('audit');
renderAll();
startSim();
runDemoFromURL();
window.addEventListener('storage', e => { if(e.key === STORE_KEY){ loadState(); renderAll(); } });
setInterval(() => { if(!demoBusy) renderAll(); }, 3000);
