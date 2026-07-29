// Thin fetch wrapper. Base is empty so paths are same-origin (nginx proxies in the
// container, Vite proxies in dev). Override with VITE_API_BASE if you must.
//
// Everything the UI calls goes through here on purpose: in the deployed stack the whole
// app is served under a path prefix (/neo-03) and VITE_API_BASE is how every URL
// picks it up. A raw fetch('/api/...') inside a component works on your laptop and 404s
// on the load balancer.
const BASE = import.meta.env.VITE_API_BASE || '';

async function request(path, options = {}) {
  const res = await fetch(BASE + path, {
    headers: { 'Content-Type': 'application/json' },
    ...options,
  });
  if (!res.ok) {
    let message = `HTTP ${res.status}`;
    try {
      const body = await res.json();
      if (body.message) message = body.message;
    } catch {
      /* non-JSON error body */
    }
    const error = new Error(message);
    error.status = res.status;
    throw error;
  }
  if (res.status === 204) return null;
  return res.json();
}

// This UI only ever READS. Applications arrive from the orchestrator — the real one, or the
// sidecar playing it at http://localhost:9000 — never from a button in here. That is the
// contract: your module is called, it does not call itself.
export const api = {
  health: () => request('/health'),
  info: () => request('/info'),
  listApplications: () => request('/api/v1/applications'),
  listReviewQueue: () => request('/api/v1/review-queue'),
  recordReviewDecision: (kycId, decision) => request(`/api/v1/review-queue/${kycId}/decision`, {
    method: 'POST',
    body: JSON.stringify(decision),
  }),

  // Every provider call behind one case, oldest first — what the board's detail panel draws.
  //
  // Keyed on kycId, not applicationId: application_id has no unique constraint, so one
  // application can own several cases and an application-keyed lookup would have to guess.
  // It is also the key the board rows already carry.
  //
  // A known case with no attempts answers 200 [] — the provider was never called. Only an
  // unknown case is a 404.
  listAttempts: (kycId) => request(`/api/v1/applications/${kycId}/attempts`),
};

// REMOVED: getApplication(id) -> GET /api/v1/applications/{id}
//
// It had never worked. No backend route matched it, so every call 404'd, and nothing in this app
// called it — a function that looks like a usable hook and is not is worse than a missing one,
// because the next person writes a screen around it before finding out.
