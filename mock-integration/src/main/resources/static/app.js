/*
 * Control panel for the two mock identity agencies.
 *
 * Plain fetch, no framework, no build. It polls /api/v1/admin/config every 2 seconds so a
 * failover happening in the module next door shows up here as the tax agency's call counter
 * moving — which is the single most useful thing this page does.
 */

const AGENCIES = [
  { slug: 'national', title: 'National Identity Agency', role: 'Primary · holds the document registers, so it is the only source that can answer documentGenuine' },
  { slug: 'tax',      title: 'Tax Agency',               role: 'Fallback · confirms name, date of birth and address. Has never seen the document' },
];

const cards = document.getElementById('cards');
const msg = document.getElementById('msg');

// Render the two cards once; polling only updates their values, so typing in a field is not
// interrupted every two seconds by a re-render.
cards.innerHTML = AGENCIES.map(a => `
  <div class="card" data-agency="${a.slug}">
    <h2>${a.title} <span class="state" data-state>…</span></h2>
    <div class="role">${a.role}</div>
    <label for="lat-${a.slug}">Latency (ms) — above 2000 forces the caller's timeout</label>
    <input type="number" id="lat-${a.slug}" data-field="latencyMs" min="0" step="250">
    <label for="fail-${a.slug}">Failure rate (%) — answers 503 this often</label>
    <input type="number" id="fail-${a.slug}" data-field="failureRatePct" min="0" max="100" step="5">
    <div class="row">
      <input type="checkbox" id="kill-${a.slug}" data-field="killSwitch">
      <label for="kill-${a.slug}">Kill switch — refuse every check</label>
    </div>
    <button data-apply>Apply</button>
    <div class="calls">calls answered: <b data-calls>0</b></div>
  </div>
`).join('');

function stateOf(cfg) {
  if (cfg.killSwitch) return { cls: 'down', text: 'REFUSING' };
  if (cfg.failureRatePct > 0) return { cls: 'slow', text: `FAILING ${cfg.failureRatePct}%` };
  if (cfg.latencyMs > 2000) return { cls: 'slow', text: `SLOW ${cfg.latencyMs}ms` };
  if (cfg.latencyMs > 0) return { cls: 'up', text: `UP · ${cfg.latencyMs}ms` };
  return { cls: 'up', text: 'UP' };
}

async function refresh() {
  let data;
  try {
    data = await (await fetch('/api/v1/admin/config')).json();
  } catch (e) {
    msg.textContent = 'cannot reach the mock — is it running?';
    return;
  }
  msg.textContent = '';
  for (const a of AGENCIES) {
    const card = cards.querySelector(`[data-agency="${a.slug}"]`);
    const cfg = data.agencies[a.slug];
    if (!cfg) continue;

    // The badge and the counter are read-only, so they always follow the server.
    const state = stateOf(cfg);
    const badge = card.querySelector('[data-state]');
    badge.textContent = state.text;
    badge.className = `state ${state.cls}`;
    card.querySelector('[data-calls]').textContent = data.calls[a.slug] ?? 0;

    // The INPUTS are different: they are what the operator is editing, and this poll runs every
    // two seconds. Reconciling them from the server would silently undo a tick or a typed value
    // the moment the user pauses to read — which looks like the page ignoring you, and is worse
    // than stale, because you then press Apply on values you did not choose.
    //
    // An `activeElement` check is not enough: focus leaves a checkbox as soon as you click
    // anything else, including the Apply button. So a card goes DIRTY on the first edit and stops
    // taking server values until Apply or Reset settles it.
    if (card.dataset.dirty === 'true') continue;
    for (const input of card.querySelectorAll('[data-field]')) {
      const key = input.dataset.field;
      if (input.type === 'checkbox') input.checked = !!cfg[key];
      else input.value = cfg[key];
    }
  }
}

// Any edit marks its card dirty, so the poll stops reconciling that card's inputs.
// 'input' covers typing and checkbox toggles alike, and it bubbles.
cards.addEventListener('input', (e) => {
  const card = e.target.closest('[data-agency]');
  if (card) card.dataset.dirty = 'true';
});

cards.addEventListener('click', async (e) => {
  const button = e.target.closest('[data-apply]');
  if (!button) return;
  const card = button.closest('[data-agency]');
  const slug = card.dataset.agency;
  const body = {
    latencyMs: Number(card.querySelector('[data-field="latencyMs"]').value || 0),
    failureRatePct: Number(card.querySelector('[data-field="failureRatePct"]').value || 0),
    killSwitch: card.querySelector('[data-field="killSwitch"]').checked,
  };
  const res = await fetch(`/api/v1/admin/config/${slug}`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  });
  // A 400 here is the validation working — show what it said rather than failing silently.
  msg.textContent = res.ok ? `${slug} updated` : `${slug}: ${(await res.json()).message}`;
  // Settled: the server now holds what this card shows, so let the poll drive it again. On a
  // 400 the card STAYS dirty — the rejected value is still on screen to be corrected, and
  // replacing it with the server's old one would hide what you got wrong.
  if (res.ok) card.dataset.dirty = 'false';
  refresh();
});

document.getElementById('reset').addEventListener('click', async () => {
  await fetch('/api/v1/admin/reset', { method: 'POST' });
  msg.textContent = 'both agencies back to healthy';
  // Reset is authoritative over every card, dirty or not — that is what makes it a way out of
  // any state, including a half-typed one.
  for (const card of cards.querySelectorAll('[data-agency]')) card.dataset.dirty = 'false';
  refresh();
});

fetch('/info').then(r => r.json()).then(i => {
  document.getElementById('version').textContent = `${i.service} · v${i.version}`;
}).catch(() => {});

refresh();
setInterval(refresh, 2000);
