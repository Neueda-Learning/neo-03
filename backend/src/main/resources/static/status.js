// The zero-build status page for one service: who am I, and what have I been asked to
// decide. Deliberately dependency-free so `docker compose up` gives you something to look
// at without the frontend container running.
const POLL_MS = 2000;

const el = (id) => document.getElementById(id);

async function json(path) {
  const res = await fetch(path);
  if (!res.ok) throw new Error('HTTP ' + res.status);
  return res.json();
}

function time(iso) {
  return iso ? new Date(iso).toLocaleTimeString() : '—';
}

async function refreshIdentity() {
  try {
    const [health, info] = await Promise.all([json('/health'), json('/info')]);
    const pill = el('pill');
    pill.textContent = health.status;
    pill.className = 'pill ' + (health.status === 'UP' ? 'up' : 'down');
    el('who').textContent = info.service;
    const mocked = info.mockedDependencies?.length
      ? info.mockedDependencies.join(', ')
      : 'nothing';
    el('sub').textContent =
      `${info.serviceId} · ${info.domain} · v${info.version} · mocking ${mocked}`;
  } catch {
    const pill = el('pill');
    pill.textContent = 'DOWN';
    pill.className = 'pill down';
  }
}

async function refreshRows() {
  let rows;
  try {
    rows = await json('/api/v1/applications');
  } catch {
    return;
  }

  el('count').textContent = rows.length ? `${rows.length} seen` : '';

  if (rows.length === 0) {
    el('rows').innerHTML =
      '<tr><td colspan="3" class="empty">Nothing received yet — send one from the ' +
      'sidecar at localhost:9000.</td></tr>';
    return;
  }

  // Three columns, because demo_showcase has three. Replace this when you replace the table.
  el('rows').innerHTML = rows
    .map(
      (r) => `<tr>
        <td class="mono">${r.applicationId}</td>
        <td><span class="st st-${r.status}">${r.status}</span></td>
        <td>${time(r.createdAt)}</td>
      </tr>`
    )
    .join('');
}

function tick() {
  refreshIdentity();
  refreshRows();
}

tick();
setInterval(tick, POLL_MS);
