/* ARGUS — agent registry page controller */
'use strict';

function renderAll(){
  renderAgents();
  renderTemplates();
  updateBadge();
}

function renderAgents(){
  $('agentGrid').innerHTML = S.agents.map(a => {
    const integ = integrity(a);
    return '<div class="agent-card ' + (a.status === 'paused' ? 'paused' : '') + '">' +
      '<div class="agent-head"><div class="avatar">' + a.icon + '</div>' +
      '<div style="flex:1;min-width:0"><div class="nm">' + esc(a.name) + '</div><div class="rl">' + esc(a.role) + '</div></div>' +
      '<span class="status-chip ' + (a.status === 'active' ? 'st-active' : 'st-paused') + '">' + a.status.toUpperCase() + '</span></div>' +
      '<p style="font-size:11.5px;color:var(--muted);line-height:1.55;margin-bottom:8px">' + esc(a.desc) + '</p>' +
      '<div class="ring-wrap">' + ring(integ, 52) +
      '<div class="meta">Identity fingerprint<br><b>' + integ + '% intact</b> · clearance <b>' + a.clearance + '/5</b></div></div>' +
      '<div><span style="font-size:10px;color:var(--dim);text-transform:uppercase;letter-spacing:.8px">Permitted tools</span><br>' +
      a.tools.map(t => '<span class="chip tool">⚙ ' + esc(t) + '</span>').join('') + '</div>' +
      '<div style="margin-top:6px"><span style="font-size:10px;color:var(--dim);text-transform:uppercase;letter-spacing:.8px">Allowed data</span><br>' +
      a.allowed.map(c => '<span class="chip res">' + esc(RES[c] ? RES[c].label : c) + '</span>').join('') + '</div>' +
      '<div style="margin-top:6px"><span style="font-size:10px;color:var(--dim);text-transform:uppercase;letter-spacing:.8px">Restricted</span><br>' +
      a.restricted.map(c => '<span class="chip warn">🔒 ' + esc(RES[c] ? RES[c].label : c) + '</span>').join('') +
      a.restrictActions.map(r => '<span class="chip warn">⚠ ' + esc(r.replace(/_/g,' ')) + '</span>').join('') + '</div>' +
      (a.blockedResources.length ? '<div style="margin-top:8px;font-size:11px;color:var(--red)">⛔ Contained: ' + a.blockedResources.map(c => esc(RES[c].label)).join(', ') + '</div>' : '') +
      '<div class="agent-actions-row">' +
      '<a class="btn sm" href="drift.html?agent=' + a.id + '">Inspect</a>' +
      (a.status === 'active'
        ? '<button class="btn amber sm" onclick="pauseAgent(\'' + a.id + '\')">Pause</button>'
        : '<button class="btn green sm" onclick="resumeAgent(\'' + a.id + '\')">Resume</button>') +
      '<button class="btn danger sm" onclick="removeAgent(\'' + a.id + '\')">Remove</button></div></div>';
  }).join('');
}

function renderTemplates(){
  const templates = [
    {n:'📦 Procurement', tools:['supplier_db','erp_api','mail_gateway'], allowed:['supplier','po','invoices'], restricted:['salary','pii','secrets'], cl:2},
    {n:'💰 Finance', tools:['ledger_api','banking_portal','spreadsheet'], allowed:['invoices','payments','po'], restricted:['salary','source','secrets'], cl:3},
    {n:'🤝 HR', tools:['hris_api','document_store','mail_gateway'], allowed:['employee','leave'], restricted:['salary','pii','payments'], cl:3},
    {n:'📊 Analytics', tools:['data_warehouse','bi_dashboard'], allowed:['metrics'], restricted:['pii','salary','secrets'], cl:2}
  ];
  $('roleTemplates').innerHTML = templates.map((t,i) =>
    '<div class="card" style="cursor:pointer" onclick="newFromTemplate(' + i + ')">' +
    '<h3>' + t.n + '</h3>' +
    '<div>' + t.tools.map(x => '<span class="chip tool">' + x + '</span>').join('') + '</div>' +
    '<div style="margin-top:6px">' + t.allowed.map(x => '<span class="chip res">' + RES[x].label + '</span>').join('') + '</div>' +
    '<div style="margin-top:6px">' + t.restricted.map(x => '<span class="chip warn">🔒 ' + RES[x].label + '</span>').join('') + '</div>' +
    '<p class="hint">Click to register with least privilege (clearance ' + t.cl + '/5).</p></div>'
  ).join('');
  window._templates = templates;
}

function newFromTemplate(i){
  const t = window._templates[i];
  openNewAgent({name:t.n.replace(/^\S+\s/,''), roleTitle:t.n.replace(/^\S+\s/,'') + ' Agent',
    desc:'Auto-registered from least-privilege template.', tools:t.tools, allowed:t.allowed, restricted:t.restricted, cl:t.cl});
}

function openNewAgent(prefill){
  prefill = prefill || {};
  const bg = document.createElement('div'); bg.className = 'modal-bg';
  bg.innerHTML = '<div class="modal"><h3>🤖 Register a new agent</h3>' +
    '<div class="field"><label>Agent name</label><input type="text" id="naName" placeholder="e.g. LegalEagle" value="' + esc(prefill.name || '') + '"></div>' +
    '<div class="field"><label>Role title</label><input type="text" id="naRole" placeholder="e.g. Contract Review Agent" value="' + esc(prefill.roleTitle || '') + '"></div>' +
    '<div class="field"><label>What is this agent\'s job?</label><input type="text" id="naDesc" placeholder="Summarises contracts, flags risky clauses" value="' + esc(prefill.desc || '') + '"></div>' +
    '<div class="field"><label>Permitted tools</label><div class="checks" id="naTools">' +
    ALL_TOOLS.map(t => '<label><input type="checkbox" value="' + t + '"' + (prefill.tools && prefill.tools.includes(t) ? ' checked' : '') + '> ' + t + '</label>').join('') + '</div></div>' +
    '<div class="field"><label>Allowed data</label><div class="checks" id="naAllowed">' +
    Object.keys(RES).map(c => '<label><input type="checkbox" value="' + c + '"' + (prefill.allowed && prefill.allowed.includes(c) ? ' checked' : '') + '> ' + RES[c].label + '</label>').join('') + '</div></div>' +
    '<div class="field"><label>Restricted (never allowed)</label><div class="checks" id="naRestricted">' +
    Object.keys(RES).map(c => '<label><input type="checkbox" value="' + c + '"' + (prefill.restricted && prefill.restricted.includes(c) ? ' checked' : '') + '> ' + RES[c].label + '</label>').join('') + '</div></div>' +
    '<div class="field"><label>Forbidden action types</label><div class="checks" id="naRestr">' +
    RESTRICT_POOL.map(r => '<label><input type="checkbox" value="' + r + '"' + (['export_data','modify_role_config'].includes(r) ? ' checked' : '') + '> ' + r.replace(/_/g,' ') + '</label>').join('') + '</div></div>' +
    '<div class="field"><label>Clearance level: <span id="naClV">' + (prefill.cl || 2) + '</span> / 5</label>' +
    '<input type="range" min="1" max="5" value="' + (prefill.cl || 2) + '" id="naCl" oninput="document.getElementById(\'naClV\').textContent=this.value"></div>' +
    '<div class="modal-foot"><button class="btn" onclick="this.closest(\'.modal-bg\').remove()">Cancel</button>' +
    '<button class="btn primary" onclick="saveNewAgent(this)">Register agent</button></div></div>';
  document.body.appendChild(bg);
  bg.addEventListener('click', e => { if(e.target === bg) bg.remove(); });
}

function saveNewAgent(btn){
  const modal = btn.closest('.modal-bg');
  const name = $('naName').value.trim() || 'NewAgent';
  const role = $('naRole').value.trim() || 'Custom Agent';
  const desc = $('naDesc').value.trim() || 'Custom-registered agent.';
  const tools = [...$('naTools').querySelectorAll('input:checked')].map(i => i.value);
  const allowed = [...$('naAllowed').querySelectorAll('input:checked')].map(i => i.value);
  const restricted = [...$('naRestricted').querySelectorAll('input:checked')].map(i => i.value);
  const restrictActions = [...$('naRestr').querySelectorAll('input:checked')].map(i => i.value);
  const cl = +$('naCl').value;
  const id = name.toLowerCase().replace(/[^a-z0-9]/g,'') + '_' + Math.floor(Math.random()*900+100);
  const baseCats = {}; allowed.forEach(c => baseCats[c] = 1/Math.max(1, allowed.length));
  const a = mkAgent(id, esc(name), '🤖', esc(role), esc(desc), tools, allowed, restricted, restrictActions, cl,
    {rate:10, offHours:.05, avgSens: allowed.length ? (allowed.reduce((s,c) => s + RES[c].sens, 0) / allowed.length) : 1.5, cats:baseCats});
  a.driftHist = seedBaseline(a);
  S.agents.push(a);
  saveState();
  modal.remove();
  toast('ok','Agent registered', name + ' onboarded with least privilege (' + tools.length + ' tools, ' + allowed.length + ' data scopes).');
  renderAll();
}

function pauseAgent(id){
  const a = agent(id); a.status = 'paused';
  logContain(a, 'pause-agent', 'manual pause from registry');
  toast('flagged','Agent paused', a.name + ' frozen.');
  saveState(); renderAll();
}
function resumeAgent(id){
  const a = agent(id); a.status = 'active'; a.blockedResources = [];
  logContain(a, 'resume-agent', 'analyst resumed agent');
  toast('ok','Agent resumed', a.name + ' back online, containment cleared.');
  saveState(); renderAll();
}
function removeAgent(id){
  const a = agent(id);
  S.agents = S.agents.filter(x => x.id !== id);
  saveState();
  toast('flagged','Agent removed', a.name + ' de-registered from ARGUS.');
  renderAll();
}

initChrome('agents');
renderAll();
runDemoFromURL();
window.addEventListener('storage', e => { if(e.key === STORE_KEY){ loadState(); renderAll(); } });
