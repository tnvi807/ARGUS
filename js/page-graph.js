/* ARGUS — trust graph page controller */
'use strict';

function renderAll(){
  renderGraph();
  const sel = $('blastSelect');
  const cur = sel.value;
  sel.innerHTML = S.agents.map(a => '<option value="' + a.id + '">' + esc(a.name) + '</option>').join('');
  if(cur && agent(cur)) sel.value = cur;
  updateBadge();
}

function renderGraph(){
  const svg = $('graphSvg');
  const W = 1150, H = 560, cx = W/2 - 80, cy = H/2;
  const n = S.agents.length;
  const agentPos = {};
  S.agents.forEach((a,i) => {
    const ang = -Math.PI/2 + (i/n)*Math.PI*2;
    agentPos[a.id] = {x: cx + Math.cos(ang)*175, y: cy + Math.sin(ang)*155};
  });
  const toolPos = {};
  ALL_TOOLS.forEach((t,i) => {
    const ang = -Math.PI/2 + (i/ALL_TOOLS.length)*Math.PI*2;
    toolPos[t] = {x: cx + Math.cos(ang)*330, y: cy + Math.sin(ang)*245};
  });
  const inf = S.blast ? S.blast.infected : null;

  let g = '';
  // tool edges
  S.agents.forEach(a => {
    a.tools.forEach(t => {
      const p1 = agentPos[a.id], p2 = toolPos[t]; if(!p2) return;
      const hot = inf && inf.has(a.id);
      g += '<line x1="' + p1.x + '" y1="' + p1.y + '" x2="' + p2.x + '" y2="' + p2.y + '" stroke="' + (hot ? 'rgba(248,113,113,.55)' : '#2a3a52') + '" stroke-width="1"/>';
    });
  });
  // comms edges
  const seen = new Set();
  S.comms.slice(-24).forEach(c => {
    const key = [c.from,c.to].sort().join('|'); if(seen.has(key)) return; seen.add(key);
    const p1 = agentPos[c.from], p2 = agentPos[c.to]; if(!p1 || !p2) return;
    const hot = inf && inf.has(c.from) && inf.has(c.to);
    const flaggedEdge = c.flagged;
    g += '<line x1="' + p1.x + '" y1="' + p1.y + '" x2="' + p2.x + '" y2="' + p2.y + '"' +
      ' stroke="' + (hot || flaggedEdge ? 'var(--red)' : 'var(--cyan)') + '" stroke-width="' + (flaggedEdge ? 2.5 : 1.8) + '"' +
      ' stroke-dasharray="6 5" opacity="' + (hot || flaggedEdge ? 0.95 : 0.45) + '">' +
      '<animate attributeName="stroke-dashoffset" from="22" to="0" dur="1.4s" repeatCount="indefinite"/></line>';
  });
  // tool nodes
  Object.entries(toolPos).forEach(([t,p]) => {
    const used = S.agents.some(a => a.tools.includes(t));
    g += '<g opacity="' + (used ? 1 : 0.35) + '">' +
      '<rect x="' + (p.x-42) + '" y="' + (p.y-11) + '" width="84" height="22" rx="6" fill="#0e1a2c" stroke="#24354f"/>' +
      '<text x="' + p.x + '" y="' + (p.y+4) + '" text-anchor="middle" fill="#6b829e" font-size="9.5" font-family="monospace">' + esc(t) + '</text></g>';
  });
  // agent nodes
  S.agents.forEach(a => {
    const p = agentPos[a.id];
    const hot = inf && inf.has(a.id);
    const isStart = S.blast && S.blast.start === a.id;
    const col = a.status === 'paused' ? 'var(--red)' : hot ? 'var(--amber)' : 'var(--cyan)';
    const integ = integrity(a);
    g += '<g style="cursor:pointer" onclick="location.href=\'drift.html?agent=' + a.id + '\'">' +
      (hot ? '<circle cx="' + p.x + '" cy="' + p.y + '" r="40" fill="rgba(248,113,113,.08)" stroke="rgba(248,113,113,.3)"/>' : '') +
      (isStart ? '<circle cx="' + p.x + '" cy="' + p.y + '" r="34" fill="none" stroke="var(--red)" stroke-width="2" stroke-dasharray="4 4">' +
        '<animateTransform attributeName="transform" type="rotate" from="0 ' + p.x + ' ' + p.y + '" to="360 ' + p.x + ' ' + p.y + '" dur="6s" repeatCount="indefinite"/></circle>' : '') +
      '<circle cx="' + p.x + '" cy="' + p.y + '" r="26" fill="#0f1d31" stroke="' + col + '" stroke-width="2"/>' +
      '<text x="' + p.x + '" y="' + (p.y+7) + '" text-anchor="middle" font-size="17">' + a.icon + '</text>' +
      '<text x="' + p.x + '" y="' + (p.y+44) + '" text-anchor="middle" fill="#c7d5e6" font-size="11" font-weight="700">' + esc(a.name) + '</text>' +
      '<text x="' + p.x + '" y="' + (p.y+57) + '" text-anchor="middle" fill="' + (integ >= 85 ? '#34d399' : integ >= 60 ? '#fbbf24' : '#f87171') + '" font-size="9.5" font-family="monospace">' + integ + '% · ' + a.status + '</text></g>';
  });
  svg.innerHTML = g;
}

initChrome('graph');
resetGraphSide();
renderAll();
startSim();
runDemoFromURL();
window.addEventListener('storage', e => { if(e.key === STORE_KEY){ loadState(); renderAll(); } });
setInterval(() => { if(!demoBusy) renderAll(); }, 3000);
