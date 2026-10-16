/* ARGUS — drift detection page controller */
'use strict';

let driftSel = null;

function renderAll(){
  const qp = new URLSearchParams(location.search).get('agent');
  if(qp && agent(qp)) driftSel = qp;
  if(!driftSel || !agent(driftSel)) driftSel = S.agents[0] ? S.agents[0].id : null;
  if(!driftSel) return;

  $('driftAgents').innerHTML = S.agents.map(a =>
    '<button class="btn ' + (a.id === driftSel ? 'active-sel' : '') + '" onclick="selectDrift(\'' + a.id + '\')">' + a.icon + ' ' + esc(a.name) + '</button>'
  ).join('');

  const a = agent(driftSel);
  const integ = integrity(a);
  const slope = linSlope(a.driftHist.map(p => p.sens));
  const baseSens = a.base.avgSens;
  const nowSens = a.driftHist[a.driftHist.length-1].sens;
  const offRatio = a.fingerprint.actions ? Math.round(a.fingerprint.offHours / a.fingerprint.actions * 100) : 0;
  const novelCats = Object.keys(a.fingerprint.cats).filter(c => !(a.base.cats[c] > .08));

  $('driftRing').innerHTML = ring(integ, 92);
  const verdict = integ >= 85
    ? ['✅ Identity stable','This agent still behaves like itself. All deviations are within normal noise.']
    : integ >= 60
    ? ['⚠ Identity drifting','Behaviour is sliding away from the original role. Review recent actions — this is exactly what a slow attack looks like.']
    : ['⛔ Identity compromised','This is no longer the agent you registered. Its behaviour fingerprint has diverged dangerously — auto-containment recommended.'];
  $('driftVerdict').innerHTML = '<span style="color:' + (integ >= 85 ? 'var(--green)' : integ >= 60 ? 'var(--amber)' : 'var(--red)') + '">' + verdict[0] + '</span>';
  $('driftVerdictSub').textContent = verdict[1];

  $('driftStats').innerHTML =
    dstat('Baseline avg data sensitivity', baseSens.toFixed(1) + ' / 5', 'ok') +
    dstat('Current avg data sensitivity', nowSens.toFixed(1) + ' / 5 ' + (nowSens > baseSens ? '↑' : ''),
      nowSens > baseSens + .8 ? 'up' : nowSens > baseSens + .3 ? 'warn' : 'ok') +
    dstat('Trend slope (per day)', (slope >= 0 ? '+' : '') + slope.toFixed(2) + '/day',
      slope > .35 ? 'up' : slope > .15 ? 'warn' : 'ok') +
    dstat('Off-hours activity', offRatio + '%', offRatio > 15 ? 'warn' : 'ok') +
    dstat('New data types touched (never in baseline)', novelCats.length ? novelCats.map(c => RES[c].label).join(', ') : 'none',
      novelCats.length ? 'up' : 'ok') +
    dstat('Actions observed in session', a.fingerprint.actions, 'ok') +
    dstat('Exports attempted', a.fingerprint.exports, a.fingerprint.exports ? 'warn' : 'ok');

  $('driftSlope').innerHTML = slope > 0.35
    ? '<div class="factor" style="border-color:rgba(248,113,113,.4)"><span style="font-size:14px">🚨</span><span class="fn"><b style="color:var(--red)">Slow-attack pattern confirmed.</b> Sensitivity of data accessed is climbing steadily (+' + slope.toFixed(2) + '/day). This matches the "read a little more each day, export at the end" playbook. Action: FLAG + containment recommended.</span></div>'
    : '<div class="factor"><span style="font-size:14px">✅</span><span class="fn">Trend line flat — no gradual escalation detected in this window.</span></div>';

  drawDriftChart(a);
  updateBadge();
}

function dstat(k, v, cls){
  return '<div class="dstat"><span class="k">' + k + '</span><span class="v ' + (cls || '') + '">' + v + '</span></div>';
}

function selectDrift(id){
  driftSel = id;
  history.replaceState(null, '', 'drift.html?agent=' + id);
  renderAll();
}

function drawDriftChart(a){
  const cv = $('driftChart'); if(!cv) return;
  const dpr = window.devicePixelRatio || 1;
  const W = cv.clientWidth || 600, H = 240;
  cv.width = W * dpr; cv.height = H * dpr;
  const ctx = cv.getContext('2d'); ctx.scale(dpr, dpr);
  ctx.clearRect(0,0,W,H);
  const pad = {l:36, r:14, t:14, b:26};
  const data = a.driftHist;
  const maxY = 5.5, minY = 0;
  const X = i => pad.l + (i/(data.length-1))*(W-pad.l-pad.r);
  const Y = v => pad.t + (1-(v-minY)/(maxY-minY))*(H-pad.t-pad.b);
  ctx.strokeStyle = '#16233a'; ctx.fillStyle = '#5c7189'; ctx.font = '10px monospace'; ctx.lineWidth = 1;
  for(let v = 0; v <= 5; v++){
    ctx.beginPath(); ctx.moveTo(pad.l, Y(v)); ctx.lineTo(W-pad.r, Y(v)); ctx.stroke();
    ctx.fillText(v.toFixed(0), 12, Y(v)+3);
  }
  const bs = a.base.avgSens;
  ctx.fillStyle = 'rgba(52,211,153,.06)';
  ctx.fillRect(pad.l, Y(bs+.6), W-pad.l-pad.r, Y(bs-.6)-Y(bs+.6));
  ctx.strokeStyle = 'rgba(52,211,153,.4)'; ctx.setLineDash([4,4]);
  ctx.beginPath(); ctx.moveTo(pad.l, Y(bs)); ctx.lineTo(W-pad.r, Y(bs)); ctx.stroke(); ctx.setLineDash([]);
  ctx.beginPath();
  data.forEach((p,i) => i ? ctx.lineTo(X(i), Y(p.sens)) : ctx.moveTo(X(i), Y(p.sens)));
  const grad = ctx.createLinearGradient(0, pad.t, 0, H-pad.b);
  grad.addColorStop(0, 'rgba(248,113,113,.28)'); grad.addColorStop(1, 'rgba(248,113,113,0)');
  ctx.lineTo(X(data.length-1), H-pad.b); ctx.lineTo(X(0), H-pad.b); ctx.closePath();
  ctx.fillStyle = grad; ctx.fill();
  ctx.beginPath();
  data.forEach((p,i) => i ? ctx.lineTo(X(i), Y(p.sens)) : ctx.moveTo(X(i), Y(p.sens)));
  ctx.strokeStyle = '#f87171'; ctx.lineWidth = 2; ctx.stroke();
  data.forEach((p,i) => {
    ctx.beginPath(); ctx.arc(X(i), Y(p.sens), 3, 0, Math.PI*2);
    ctx.fillStyle = p.sens > bs + 1 ? '#f87171' : '#34d399'; ctx.fill();
  });
  ctx.fillStyle = '#5c7189';
  data.forEach((p,i) => { if(i % 3 === 0 || i === data.length-1) ctx.fillText(p.d, X(i)-10, H-8); });
  ctx.fillStyle = '#34d399'; ctx.fillRect(W-150, 10, 10, 10);
  ctx.fillStyle = '#8ba0b8'; ctx.fillText('normal baseline band', W-136, 19);
}

initChrome('drift');
renderAll();
startSim();
runDemoFromURL();
window.addEventListener('storage', e => { if(e.key === STORE_KEY){ loadState(); renderAll(); } });
setInterval(() => { if(!demoBusy) renderAll(); }, 2500);
