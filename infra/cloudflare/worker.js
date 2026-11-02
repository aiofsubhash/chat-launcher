// Chat Launcher - free-tier AI proxy (Cloudflare Worker)
//
// Security properties (enforced here, never trusted to the client):
//   - Requires OPENROUTER_API_KEY (Worker secret). If missing, every AI call fails closed.
//   - Model is fixed server-side; client-supplied model/provider fields are ignored.
//   - Contract is SSE-only: the proxy always streams (stream:true upstream); clients read SSE.
//   - Request body is read with a hard byte cap BEFORE JSON parsing.
//   - Exactly one user message per call: no client system prompts, no conversation history.
//   - The free-only gate pins provider routing and price caps server-side:
//     max_price {prompt: 0, completion: 0}, allow_fallbacks: false.
//     If the model's free tier is unavailable, the request fails - it can never spend money.
//   - Tester mode additionally requires the shared TESTER_TOKEN secret
//     (Authorization: Bearer <token> or X-Tester-Token header).
//   - A global daily and per-minute quota is enforced with ONE atomic conditional UPDATE
//     (singleton row, RETURNING) - no read-then-write race.
//   - The upstream request has a lifetime timeout covering the whole response stream,
//     not just response headers.
//   - Clients only ever receive generic error bodies; upstream error text is never relayed.

const MODEL = 'nvidia/nemotron-3-ultra-550b-a55b:free';
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

async function handleRequest(request, env, ctx, now = new Date()) {
  if (request.method !== 'POST') return errorResponse(405, 'method not allowed');

  // Tester gate: shared secret, singular TESTER_TOKEN (matches the deployed secret name).
  if (!env.TESTER_TOKEN) return errorResponse(503, 'ai connection is not configured');
  const bearer = (request.headers.get('Authorization') || '').replace(/^Bearer\s+/i, '');
  const headerToken = request.headers.get('X-Tester-Token') || '';
  if (bearer !== env.TESTER_TOKEN && headerToken !== env.TESTER_TOKEN) {
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

    await checkQuota(env.QUOTA_DB, now);

    const timeoutMs = Number(env.UPSTREAM_TIMEOUT_MS) > 0
      ? Number(env.UPSTREAM_TIMEOUT_MS)
      : UPSTREAM_TIMEOUT_MS_DEFAULT;
    const controller = new AbortController();
    // Lifetime timeout: stays armed through the entire response stream.
    const timer = setTimeout(() => controller.abort(), timeoutMs);

    let upstream;
    try {
      upstream = await fetch(UPSTREAM_URL, {
        method: 'POST',
        headers: {
          Authorization: `Bearer ${env.OPENROUTER_API_KEY}`,
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({
          model: MODEL,
          stream: true,
          max_tokens: MAX_OUTPUT_TOKENS,
          provider: FREE_ONLY_PROVIDER,
          messages: [{ role: 'user', content: userText }],
        }),
        signal: controller.signal,
      });
    } catch (err) {
      clearTimeout(timer);
      if (err && err.name === 'AbortError') return errorResponse(504, 'request timed out');
      return errorResponse(502, 'upstream unavailable');
    }

    if (!upstream.ok || !upstream.body) {
      clearTimeout(timer);
      if (upstream.body) upstream.body.cancel().catch(() => {});
      return errorResponse(502, 'upstream unavailable'); // never relay upstream error text
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
