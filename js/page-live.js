/* ARGUS — live activity page controller */
'use strict';

let feedFilter = 'all';

function renderAll(){
  const rows = S.feed.filter(f => feedFilter === 'all' || f.res.decision === feedFilter);
  $('liveCount').textContent = rows.length + ' events';
  $('liveFeed').innerHTML = rows.slice(0,120).map(feedRow).join('') || emptyBox('Nothing matches this filter yet.');

  // topbar threat level
  const open = S.alerts.filter(a => a.status === 'open').length;
  const blocked = S.feed.filter(f => f.res.decision === 'BLOCKED').length;
  const lvl = (open >= 4 || blocked >= 5) ? 3 : open >= 1 ? 2 : 1;
  $('threatBox').className = 'threat lvl-' + lvl;
  $('threatText').textContent = 'THREAT LEVEL: ' + ['LOW','ELEVATED','HIGH'][lvl-1];
  updateBadge();
}

function setFeedFilter(f, btn){
  feedFilter = f;
  document.querySelectorAll('.pill-tabs .btn').forEach(b => b.classList.remove('active-sel'));
  btn.classList.add('active-sel');
  renderAll();
}

initChrome('live');
renderAll();
startSim();
runDemoFromURL();
window.addEventListener('storage', e => { if(e.key === STORE_KEY){ loadState(); renderAll(); } });
setInterval(() => { if(!demoBusy) renderAll(); }, 1800);
