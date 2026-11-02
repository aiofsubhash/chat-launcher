// Chat Launcher - free-tier AI proxy (Cloudflare Worker)
//
// Server-only reviewed model routes, D1 enable/order, zero-price+ZDR enforced.
// One current nonsensitive user message; authenticated installs; no content logs.
// Atomic global inference-attempt quotas and one-time pairing redemption.

const UPSTREAM_URL = 'https://openrouter.ai/api/v1/chat/completions';

const MAX_BODY_BYTES = 16 * 1024;      // hard cap on raw request body, enforced pre-parse
const MAX_INPUT_CHARS = 4000;          // bound on the single user text
const MAX_OUTPUT_TOKENS = 600;
const UPSTREAM_TIMEOUT_MS_DEFAULT = 30000;

const DAILY_CAP = 50;
const MINUTE_CAP = 6;

// No provider 'only' allow-list: any pinned list is an ungrounded provider selection.
// The fixed :free model plus all-zero price caps and no fallbacks enforce free-only:
// OpenRouter must route to an endpoint whose price is exactly 0 or the request fails.
const FREE_ONLY_PROVIDER = {
  max_price: { prompt: 0, completion: 0, request: 0, image: 0 },
  allow_fallbacks: false,
};

const SSE_HEADERS = {
  'Content-Type': 'text/event-stream',
  'Cache-Control': 'no-cache',
};

// Single atomic statement: increments day/minute counters on the singleton row only when
// both caps still have room. Zero rows returned means the request is denied. Because the
// check and the increment are one UPDATE, concurrent requests cannot both observe slack.
const QUOTA_SQL = `
UPDATE quota_singleton
SET day_count    = CASE WHEN day_key = ?1 THEN day_count ELSE 0 END + 1,
    minute_count = CASE WHEN minute_key = ?2 THEN minute_count ELSE 0 END + 1,
    day_key      = ?1,
    minute_key   = ?2
WHERE id = 1
  AND (CASE WHEN day_key = ?1 THEN day_count ELSE 0 END) + 1 <= ?3
  AND (CASE WHEN minute_key = ?2 THEN minute_count ELSE 0 END) + 1 <= ?4
RETURNING day_count, minute_count`;

class HttpError extends Error {
  constructor(status, message) {
    super(message);
    this.status = status;
  }
}

function errorResponse(status, message) {
  return new Response(JSON.stringify({ error: { message, type: 'proxy_error' } }), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}

async function readBoundedBody(request, limit) {
  if (!request.body) return '';
  const reader = request.body.getReader();
  const chunks = [];
  let size = 0;
  try {
    for (;;) {
      const { done, value } = await reader.read();
      if (done) break;
      size += value.byteLength;
      if (size > limit) throw new HttpError(413, 'request too large');
      chunks.push(value);
    }
  } finally {
    reader.cancel().catch(() => {});
  }
  const buf = new Uint8Array(size);
  let offset = 0;
  for (const chunk of chunks) {
    buf.set(chunk, offset);
    offset += chunk.byteLength;
  }
  return new TextDecoder().decode(buf);
}

function extractUserText(body) {
  const messages = body && body.messages;
  if (!Array.isArray(messages) || messages.length !== 1) {
    throw new HttpError(400, 'expected exactly one message');
  }
  const message = messages[0];
  if (!message || message.role !== 'user' || typeof message.content !== 'string') {
    throw new HttpError(400, 'expected a single user text message');
  }
  const text = message.content.trim();
  if (!text) throw new HttpError(400, 'empty message');
  if (text.length > MAX_INPUT_CHARS) throw new HttpError(400, 'message too long');
  return text;
}

async function checkQuota(db, now) {
  const dayKey = now.toISOString().slice(0, 10);
  const minuteKey = now.toISOString().slice(0, 13) + ':' + now.toISOString().slice(14, 16);
  // Idempotent singleton bootstrap; INSERT OR IGNORE is itself atomic, no race.
  await db.prepare('INSERT OR IGNORE INTO quota_singleton (id) VALUES (1)').bind().run();
  const row = await db.prepare(QUOTA_SQL).bind(dayKey, minuteKey, DAILY_CAP, MINUTE_CAP).first();
  if (!row) {
    const counts = await db
      .prepare('SELECT day_count, minute_count, day_key, minute_key FROM quota_singleton WHERE id = 1')
      .bind()
      .first();
    const dayCount = counts && counts.day_key === dayKey ? counts.day_count : 0;
    const limit = dayCount >= DAILY_CAP ? 'daily' : 'per-minute';
    throw new HttpError(429, `quota exceeded (${limit} limit)`);
  }
}

async function handleRequest(request, env, ctx, now = new Date(), catalog = CATALOG) {
  if (request.method !== 'POST') return errorResponse(405, 'method not allowed');

  // Tester gate: shared secret, singular TESTER_TOKEN (matches the deployed secret name).
  if (!env.TESTER_TOKEN) return errorResponse(503, 'ai connection is not configured');
  const bearer = (request.headers.get('Authorization') || '').replace(/^Bearer\s+/i, '');
  const headerToken = request.headers.get('X-Tester-Token') || '';
  let auth=false;
  try { auth=await authenticated(bearer || headerToken,env); } catch { return errorResponse(503,'ai connection is not configured'); }
  if (!auth) {
    return errorResponse(401, 'unauthorized');
  }

  // Fail closed without the upstream key.
  if (!env.OPENROUTER_API_KEY) return errorResponse(503, 'ai connection is not configured');

  try {
    const raw = await readBoundedBody(request, MAX_BODY_BYTES);
    let body;
    try {
      body = JSON.parse(raw);
    } catch {
      throw new HttpError(400, 'invalid request body');
    }
    const userText = extractUserText(body);

    const timeoutMs = Number(env.UPSTREAM_TIMEOUT_MS) > 0 ? Number(env.UPSTREAM_TIMEOUT_MS) : UPSTREAM_TIMEOUT_MS_DEFAULT;
    const controller = new AbortController();
    const timer = setTimeout(() => controller.abort(), timeoutMs);
    let upstream;
    let lastCategory = 'no_eligible_route';
    try {
      const config = await env.QUOTA_DB.prepare('SELECT model, provider FROM model_routes WHERE enabled = 1 ORDER BY priority, model LIMIT 16').all();
      const routes = [...new Map((config.results || []).map(r => [r.model, r])).values()];
      let attempts = 0;
      for (const route of routes) {
        const policy = catalog[route.model];
        if (!policy || route.provider !== policy.provider) continue;
        let eligible = false;
        try {
          const meta = await fetch('https://openrouter.ai/api/v1/models/' + route.model + '/endpoints', {signal: controller.signal});
          if (meta.ok) eligible = eligibleEndpoint(await meta.json(), route);
          else if (meta.body) await meta.body.cancel();
        } catch (e) { if (controller.signal.aborted) throw e; }
        if (!eligible) continue;
        if (attempts >= 2) break;
        await checkQuota(env.QUOTA_DB, now);
        attempts++;
        try {
          upstream = await fetch(UPSTREAM_URL, {
            method: 'POST', headers: { Authorization: `Bearer ${env.OPENROUTER_API_KEY}`, 'Content-Type':'application/json' },
            body: JSON.stringify({model: route.model, stream:true, max_tokens:MAX_OUTPUT_TOKENS,
              reasoning:{enabled:false},
              provider:{...FREE_ONLY_PROVIDER, only:[policy.provider], data_collection:'deny', zdr:true},
              messages:[{role:'user',content:userText}]}), signal:controller.signal,
          });
        } catch (e) { if (controller.signal.aborted) throw e; lastCategory='upstream_unavailable'; continue; }
        if (upstream.ok && upstream.body && (upstream.headers.get('content-type') || '').includes('text/event-stream')) break;
        const status = upstream.status;
        lastCategory = status === 401 ? 'upstream_auth' : status === 402 ? 'upstream_billing' : status === 429 ? 'upstream_rate_limit' : 'upstream_unavailable';
        if (upstream.body) await upstream.body.cancel().catch(()=>{});
        upstream = null;
        if (status === 401 || status === 402 || ![404,408,429].includes(status) && status < 500) break;
      }
      if (!upstream) { clearTimeout(timer); return errorResponse(502,lastCategory); }
    } catch (err) {
      clearTimeout(timer);
      if (controller.signal.aborted) return errorResponse(504,'request timed out');
      if (err instanceof HttpError) return errorResponse(err.status,err.message);
      return errorResponse(502,'upstream_unavailable');
    }

    const { readable, writable } = new TransformStream();
    // On upstream error/abort, pipeTo propagates the error to the writable side itself.
    // Never call writable.abort() here - the stream is locked during pipeTo and the
    // resulting rejection would be unhandled.
    const pump = upstream.body
      .pipeTo(writable)
      .catch(() => {})
      .finally(() => clearTimeout(timer));
    if (ctx && typeof ctx.waitUntil === 'function') ctx.waitUntil(pump);

    return new Response(readable, { status: 200, headers: SSE_HEADERS });
  } catch (err) {
    if (err instanceof HttpError) return errorResponse(err.status, err.message);
    return errorResponse(500, "internal error"); // generic; never leak internals
  }
}

export default {
  async fetch(request, env, ctx) {
    const url = new URL(request.url);
    if (url.pathname === '/v1/pair/redeem') return redeem(request,env);
    if (url.pathname === '/health' && request.method === 'GET') {
      return new Response(JSON.stringify({ status: 'ok' }), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      });
    }
    if (url.pathname !== '/v1/chat/completions') return errorResponse(404, 'not found');
    return handleRequest(request, env, ctx);
  },
};

export { handleRequest };

// Reviewed privacy-qualified catalog. Cloud D1 controls enable/order, never APK.
// New pairs require a server review; arbitrary D1 rows do not widen disclosure.
const CATALOG = Object.freeze({
  'xingchen-agi/xing4.0-29b-a4b:free': {provider:'phala'},
});
function eligibleEndpoint(meta, route) {
  if (!route.model.endsWith(':free') || meta?.data?.id !== route.model) return false;
  return (meta.data.endpoints || []).some(e => e.tag === route.provider && e.model_id === route.model &&
    e.pricing && ['prompt','completion'].every(k => typeof e.pricing[k] !== 'undefined' && Number(e.pricing[k]) === 0) &&
    Object.entries(e.pricing).every(([k,v]) => k === 'discount' || Number(v) === 0));
}
async function hash(value) {
  const bytes=await crypto.subtle.digest('SHA-256',new TextEncoder().encode(value));
  return [...new Uint8Array(bytes)].map(x=>x.toString(16).padStart(2,'0')).join('');
}
function randomCredential() { return [...crypto.getRandomValues(new Uint8Array(32))].map(x=>x.toString(16).padStart(2,'0')).join(''); }
async function authenticated(token,env) {
  if (!token) return false;
  if (token === env.TESTER_TOKEN) return true;
  if (!/^[a-f0-9]{64}$/.test(token)) return false;
  const row=await env.QUOTA_DB.prepare('SELECT token_hash FROM install_credentials WHERE token_hash = ? AND revoked = 0').bind(await hash(token)).first();
  return !!row;
}
async function redeem(request,env) {
  if (request.method !== 'POST') return errorResponse(405,'method not allowed');
  try {
    const ip=request.headers.get('CF-Connecting-IP') || 'unknown';
    const minute=Math.floor(Date.now()/60000);
    // Limit per-minute global100 and per-IP10. Atomic counters, no raw IP retained.
    for (const [key,cap] of [['global',100],['ip:'+(await hash(ip)).slice(0,2),10]]) {
      const r=await env.QUOTA_DB.prepare(`INSERT INTO pairing_limits (key, window, count) VALUES (?, ?, 1)
        ON CONFLICT(key) DO UPDATE SET window=excluded.window, count=CASE WHEN pairing_limits.window=excluded.window THEN pairing_limits.count+1 ELSE 1 END
        WHERE pairing_limits.window != excluded.window OR pairing_limits.count < ? RETURNING count`).bind(key,minute,cap).first();
      if (!r) return errorResponse(429,'pairing rate limit');
    }
    const body=JSON.parse(await readBoundedBody(request,1024));
    if (!body || typeof body.code !== 'string' || !/^[a-f0-9]{64}$/.test(body.code)) return errorResponse(400,'invalid pairing request');
    const credential=randomCredential();
    // Atomic transaction: conditional credential insertion and code consumption.
    const codeHash=await hash(body.code), tokenHash=await hash(credential), now=Date.now();
    const results=await env.QUOTA_DB.batch([
      env.QUOTA_DB.prepare('INSERT INTO install_credentials(token_hash,created_at,revoked) SELECT ?,?,0 WHERE EXISTS (SELECT 1 FROM pairing_codes WHERE code_hash=? AND expires_at>?) RETURNING token_hash').bind(tokenHash,now,codeHash,now),
      env.QUOTA_DB.prepare('DELETE FROM pairing_codes WHERE code_hash=? AND expires_at>? RETURNING code_hash').bind(codeHash,now),
    ]);
    if (!results[0]?.results?.length || !results[1]?.results?.length) return errorResponse(401,'pairing code invalid or expired');
    return new Response(JSON.stringify({credential,endpoint:new URL('/v1/chat/completions',request.url).href}),{headers:{'Content-Type':'application/json','Cache-Control':'no-store'}});
  } catch(e) { return errorResponse(e instanceof HttpError ? e.status : 503,'pairing unavailable'); }
}
export {eligibleEndpoint,authenticated,redeem};
