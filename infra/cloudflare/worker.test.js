// Tests for the Chat Launcher free-tier proxy.
// The quota tests run against REAL SQLite (node:sqlite) behind a D1-shaped async stub whose
// statements yield between calls - reproducing the interleaving infra used to demonstrate
// the old read-then-increment race (20/20 concurrent requests admitted vs cap 6).
import test from 'node:test';
import assert from 'node:assert/strict';
import { DatabaseSync } from 'node:sqlite';
import { handleRequest } from './worker.js';

const SCHEMA = `
CREATE TABLE IF NOT EXISTS quota_singleton (
  id INTEGER PRIMARY KEY CHECK (id = 1),
  day_key TEXT NOT NULL DEFAULT '',
  day_count INTEGER NOT NULL DEFAULT 0,
  minute_key TEXT NOT NULL DEFAULT '',
  minute_count INTEGER NOT NULL DEFAULT 0
);
INSERT OR IGNORE INTO quota_singleton (id) VALUES (1);`;

// D1-shaped async wrapper over real SQLite. Each statement awaits before executing, so
// concurrent in-flight requests genuinely interleave between statements - but a single
// statement (the conditional UPDATE) stays atomic, exactly as D1/SQLite guarantees.
function makeD1() {
  const db = new DatabaseSync(':memory:');
  db.exec(SCHEMA);
  const tick = () => new Promise((r) => setImmediate(r));
  return {
    _db: db,
    prepare(sql) {
      return {
        bind(...args) {
          return {
            async first() {
              await tick();
              return db.prepare(sql).get(...args) ?? null;
            },
            async run() {
              await tick();
              return db.prepare(sql).run(...args);
            },
          };
        },
      };
    },
  };
}

const FIXED_NOW = new Date('2026-11-02T12:00:00.000Z');
const TOKEN = 'tok-1';

function makeEnv(overrides = {}) {
  return { TESTER_TOKEN: TOKEN, OPENROUTER_API_KEY: 'sk-or-test', QUOTA_DB: makeD1(), ...overrides };
}

function makeReq({ body, headers = {} } = {}) {
  return new Request('https://proxy.test/v1/chat/completions', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', 'X-Tester-Token': TOKEN, ...headers },
    body: body ?? JSON.stringify({ messages: [{ role: 'user', content: 'hello' }] }),
  });
}

function sseResponse(text = 'data: [DONE]\n\n') {
  return new Response(new Blob([text]).stream(), { status: 200 });
}

async function okRequest(env, now = FIXED_NOW) {
  globalThis.fetch = async () => sseResponse();
  return handleRequest(makeReq(), env, {}, now);
}

test('missing token -> 401', async () => {
  const res = await handleRequest(
    new Request('https://proxy.test/v1/chat/completions', { method: 'POST', body: '{}' }),
    makeEnv(), {}, FIXED_NOW);
  assert.equal(res.status, 401);
});

test('no upstream key -> 503 fail closed', async () => {
  const res = await handleRequest(makeReq(), makeEnv({ OPENROUTER_API_KEY: undefined }), {}, FIXED_NOW);
  assert.equal(res.status, 503);
});

test('valid single user message -> 200, upstream sees fixed model and full free-only gate', async () => {
  let captured;
  globalThis.fetch = async (url, init) => { captured = JSON.parse(init.body); return sseResponse(); };
  const res = await handleRequest(makeReq({
    body: JSON.stringify({
      model: 'openai/gpt-5', // must be ignored
      stream: false,         // must be ignored
      messages: [{ role: 'user', content: 'hello' }],
    }),
  }), makeEnv(), {}, FIXED_NOW);
  assert.equal(res.status, 200);
  assert.equal(captured.model, 'nvidia/nemotron-3-ultra-550b-a55b:free');
  assert.equal(captured.stream, true);
  assert.deepEqual(captured.messages, [{ role: 'user', content: 'hello' }]);
  assert.equal(captured.provider.allow_fallbacks, false);
  assert.equal(captured.provider.max_price.prompt, 0);
  assert.equal(captured.provider.max_price.completion, 0);
});

test('system prompt or history rejected - exactly one user message only', async () => {
  for (const messages of [
    [{ role: 'system', content: 'x' }, { role: 'user', content: 'hi' }],
    [{ role: 'user', content: 'a' }, { role: 'user', content: 'b' }],
    [{ role: 'system', content: 'x' }],
    [],
  ]) {
    const res = await handleRequest(makeReq({ body: JSON.stringify({ messages }) }), makeEnv(), {}, FIXED_NOW);
    assert.equal(res.status, 400, JSON.stringify(messages));
  }
});

test('non-string or oversized content -> 400', async () => {
  for (const content of [42, 'x'.repeat(4001)]) {
    const res = await handleRequest(
      makeReq({ body: JSON.stringify({ messages: [{ role: 'user', content }] }) }),
      makeEnv(), {}, FIXED_NOW);
    assert.equal(res.status, 400);
  }
});

test('raw body over 16KiB -> 413 before parsing', async () => {
  const huge = '{"messages":[{"role":"user","content":"' + 'x'.repeat(20 * 1024) + '"}]}';
  const res = await handleRequest(makeReq({ body: huge }), makeEnv(), {}, FIXED_NOW);
  assert.equal(res.status, 413);
});

test('upstream failure -> generic 502, upstream error text never relayed', async () => {
  globalThis.fetch = async () => new Response('Upstream secret detail: credit balance low', { status: 402 });
  const res = await handleRequest(makeReq(), makeEnv(), {}, FIXED_NOW);
  assert.equal(res.status, 502);
  const body = await res.text();
  assert.ok(!body.includes('credit balance'), body);
});

test('upstream timeout aborts and ends the stream', async () => {
  globalThis.fetch = (url, init) => new Promise((resolve, reject) => {
    init.signal.addEventListener('abort', () => reject(Object.assign(new Error('aborted'), { name: 'AbortError' })));
  });
  const env = makeEnv({ UPSTREAM_TIMEOUT_MS: '50' });
  const res = await handleRequest(makeReq(), env, {}, FIXED_NOW);
  assert.equal(res.status, 504);
});

test('sequential cap: 7th request within one minute -> 429 (real SQLite)', async () => {
  const env = makeEnv();
  for (let i = 0; i < 6; i++) assert.equal((await okRequest(env)).status, 200, `request ${i + 1}`);
  const denied = await okRequest(env);
  assert.equal(denied.status, 429);
});

test('CONCURRENT REGRESSION: 20 simultaneous requests admit at most the minute cap of 6', async () => {
  // Infra reproduced 20/20 admissions against the previous SELECT-then-INSERT quota under
  // these same async interleavings. The single conditional UPDATE must admit exactly 6.
  const env = makeEnv();
  globalThis.fetch = async () => sseResponse();
  const results = await Promise.all(
    Array.from({ length: 20 }, () => handleRequest(makeReq(), env, {}, FIXED_NOW)));
  const admitted = results.filter((r) => r.status === 200).length;
  const denied = results.filter((r) => r.status === 429).length;
  assert.equal(admitted, 6, `admitted ${admitted}`);
  assert.equal(denied, 14, `denied ${denied}`);
  const row = env.QUOTA_DB._db.prepare('SELECT minute_count FROM quota_singleton WHERE id = 1').get();
  assert.equal(row.minute_count, 6);
});

test('new minute window resets the minute counter', async () => {
  const env = makeEnv();
  for (let i = 0; i < 6; i++) assert.equal((await okRequest(env)).status, 200);
  const later = new Date('2026-11-02T12:01:00.000Z');
  assert.equal((await okRequest(env, later)).status, 200);
});

test('GET /health -> 200 ok (routed through the fetch export)', async () => {
  const worker = (await import('./worker.js')).default;
  const res = await worker.fetch(new Request('https://proxy.test/health', { method: 'GET' }), makeEnv(), {});
  assert.equal(res.status, 200);
  assert.deepEqual(await res.json(), { status: 'ok' });
});

test('upstream free-only gate: no provider allow-list, all four price fields zero', async () => {
  let captured;
  globalThis.fetch = async (url, init) => { captured = JSON.parse(init.body); return sseResponse(); };
  const res = await handleRequest(makeReq(), makeEnv(), {}, FIXED_NOW);
  assert.equal(res.status, 200);
  assert.equal(captured.provider.allow_fallbacks, false);
  assert.deepEqual(captured.provider.max_price, { prompt: 0, completion: 0, request: 0, image: 0 });
  assert.ok(!('only' in captured.provider), 'no ungrounded provider allow-list');
});

test('POST-HEADER hang: stream that never yields is aborted mid-flight and terminates', async () => {
  // Upstream sends headers (200) then nothing. The lifetime timer must abort the fetch
  // mid-stream; pipeTo propagates the error; the client-visible stream ends. No unhandled
  // rejections from touching the locked writable.
  // Model undici faithfully: when the fetch signal aborts mid-stream, the response body
  // errors with an abort error (fetch spec termination), which pipeTo propagates.
  globalThis.fetch = async (url, init) => {
    let ctrl;
    const stream = new ReadableStream({
      start(c) {
        ctrl = c; // never enqueues on its own
        init.signal.addEventListener('abort', () => {
          c.error(Object.assign(new Error('The operation was aborted.'), { name: 'AbortError' }));
        });
      },
    });
    return new Response(stream, { status: 200 });
  };
  const env = makeEnv({ UPSTREAM_TIMEOUT_MS: '80' });
  const res = await handleRequest(makeReq(), env, {}, FIXED_NOW);
  assert.equal(res.status, 200); // headers already out; failure shows as stream termination
  const reader = res.body.getReader();
  const started = Date.now();
  await assert.rejects(reader.read(), /abort|Abort|terminated|network|error/i);
  assert.ok(Date.now() - started < 5000, 'stream ended promptly after abort');
});
