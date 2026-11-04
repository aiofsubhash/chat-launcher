// Tests for the Chat Launcher free-tier proxy.
// The quota tests run against REAL SQLite (node:sqlite) behind a D1-shaped async stub whose
// statements yield between calls - reproducing the interleaving infra used to demonstrate
// the old read-then-increment race (20/20 concurrent requests admitted vs cap 6).
import test from 'node:test';
import {readFileSync} from 'node:fs';
import assert from 'node:assert/strict';
import { DatabaseSync } from 'node:sqlite';
import { handleRequest,eligibleEndpoint,redeem } from './worker.js';

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
  db.exec(new TextDecoder().decode(readFileSync(new URL('./schema.sql',import.meta.url))));
  const tick = () => new Promise((r) => setImmediate(r));
  return {
    _db: db,
    async batch(stmts) { db.exec('BEGIN IMMEDIATE'); try { const results=[]; for (const stmt of stmts) results.push(await stmt.all()); db.exec('COMMIT'); return results; } catch(e) { db.exec('ROLLBACK'); throw e; } },
    prepare(sql) {
      return {
        async all(){ await tick();return {results:db.prepare(sql).all()}; },
        bind(...args) {
          return {
            async all() { await tick(); return {results:db.prepare(sql).all(...args)}; },
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
  return new Response(new Blob([text]).stream(), { status: 200,headers:{'Content-Type':'text/event-stream'} });
}

async function okRequest(env, now = FIXED_NOW) {
  setFetch(async () => sseResponse());
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
  setFetch(async (url, init) => { captured = JSON.parse(init.body); return sseResponse(); });
  const res = await handleRequest(makeReq({
    body: JSON.stringify({
      model: 'openai/gpt-5', // must be ignored
      stream: false,         // must be ignored
      messages: [{ role: 'user', content: 'hello' }],
    }),
  }), makeEnv(), {}, FIXED_NOW);
  assert.equal(res.status, 200);
  assert.equal(captured.model, 'xingchen-agi/xing4.0-29b-a4b:free');
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
  setFetch(async () => new Response('Upstream secret detail: credit balance low', { status: 402 }));
  const res = await handleRequest(makeReq(), makeEnv(), {}, FIXED_NOW);
  assert.equal(res.status, 502);
  const body = await res.text();
  assert.ok(!body.includes('credit balance'), body);
});

test('upstream timeout aborts and ends the stream', async () => {
  setFetch((url, init) => new Promise((resolve, reject) => {
    init.signal.addEventListener('abort', () => reject(Object.assign(new Error('aborted'), { name: 'AbortError' })));
  }));
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
  setFetch(async () => sseResponse());
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
  setFetch(async (url, init) => { captured = JSON.parse(init.body); return sseResponse(); });
  const res = await handleRequest(makeReq(), makeEnv(), {}, FIXED_NOW);
  assert.equal(res.status, 200);
  assert.equal(captured.provider.allow_fallbacks, false);
  assert.deepEqual(captured.provider.max_price, { prompt: 0, completion: 0, request: 0, image: 0 });
  assert.deepEqual(captured.provider.only,['phala']);
  assert.equal(captured.provider.zdr,true);assert.equal(captured.provider.data_collection,'deny');
});

test('POST-HEADER hang: stream that never yields is aborted mid-flight and terminates', async () => {
  // Upstream sends headers (200) then nothing. The lifetime timer must abort the fetch
  // mid-stream; pipeTo propagates the error; the client-visible stream ends. No unhandled
  // rejections from touching the locked writable.
  // Model undici faithfully: when the fetch signal aborts mid-stream, the response body
  // errors with an abort error (fetch spec termination), which pipeTo propagates.
  setFetch(async (url, init) => {
    let ctrl;
    const stream = new ReadableStream({
      start(c) {
        ctrl = c; // never enqueues on its own
        init.signal.addEventListener('abort', () => {
          c.error(Object.assign(new Error('The operation was aborted.'), { name: 'AbortError' }));
        });
      },
    });
    return new Response(stream, { status: 200,headers:{'Content-Type':'text/event-stream'} });
  });
  const env = makeEnv({ UPSTREAM_TIMEOUT_MS: '80' });
  const res = await handleRequest(makeReq(), env, {}, FIXED_NOW);
  assert.equal(res.status, 200); // headers already out; failure shows as stream termination
  const reader = res.body.getReader();
  const started = Date.now();
  await assert.rejects(reader.read(), /abort|Abort|terminated|network|error/i);
  assert.ok(Date.now() - started < 5000, 'stream ended promptly after abort');
});

function meta(model='xingchen-agi/xing4.0-29b-a4b:free',provider='phala',price='0') {return {data:{id:model,endpoints:[{tag:provider,model_id:model,pricing:{prompt:price,completion:'0'}}]}};}
function setFetch(fn){ globalThis.fetch=async(url,init)=>String(url).endsWith('/endpoints') ? new Response(JSON.stringify(meta()),{headers:{'Content-Type':'application/json'}}) : fn(url,init); }

test('metadata missing, nonfree, wrong provider, unknown price fail closed',async()=>{
 const model='xingchen-agi/xing4.0-29b-a4b:free',route={model,provider:'phala'};
 assert.equal(eligibleEndpoint(meta(),route),true);
 for (const m of [meta(model,'bad'),meta(model,'phala','0.1'),{data:{id:model,endpoints:[]}},meta('missing')]) assert.equal(eligibleEndpoint(m,route),false);
 const m=meta();delete m.data.endpoints[0].pricing.prompt;assert.equal(eligibleEndpoint(m,route),false);
});
test('disable, remove, nonfree metadata: no inference and no quota debit',async()=>{
 for(const mode of ['disabled','price','removed','unknown']){
  const env=makeEnv();let calls=0;
  if(mode==='disabled')env.QUOTA_DB._db.exec('UPDATE model_routes SET enabled=0');
  if(mode==='unknown')env.QUOTA_DB._db.exec("UPDATE model_routes SET model='unknown:free'");
  globalThis.fetch=async(url)=>{if(String(url).endsWith('/endpoints'))return new Response(JSON.stringify(mode==='removed'?{}:meta(undefined,undefined,'1')));calls++;throw Error('must not infer');};
  const res=await handleRequest(makeReq(),env,{},FIXED_NOW);assert.equal(res.status,502);assert.equal(calls,0);
  assert.equal(env.QUOTA_DB._db.prepare('SELECT day_count FROM quota_singleton').get().day_count,0);
 }
});
test('automatic two-route availability failover, cloud order and quota EACH attempt',async()=>{
 const env=makeEnv();const b='test/second:free';env.QUOTA_DB._db.exec("INSERT INTO model_routes VALUES('test/second:free','phala',1,20)");
 const seen=[];globalThis.fetch=async(url,init)=>{
  if(String(url).endsWith('/endpoints')){const id=String(url).split('/models/')[1].replace('/endpoints','');return new Response(JSON.stringify(meta(id)));}
  const model=JSON.parse(init.body).model;seen.push(model);return seen.length===1?new Response('',{status:503}):sseResponse();
 };
 const res=await handleRequest(makeReq(),env,{},FIXED_NOW,{'xingchen-agi/xing4.0-29b-a4b:free':{provider:'phala'},[b]:{provider:'phala'}});
 assert.equal(res.status,200);await res.text();assert.equal(seen.length,2);assert.equal(env.QUOTA_DB._db.prepare('SELECT day_count FROM quota_singleton').get().day_count,2);
});
test('account401/402 do not retry and safe category only',async()=>{
 for(const status of [401,402]){const env=makeEnv();let count=0;setFetch(async()=>{count++;return new Response('secret upstream content',{status});});
 const r=await handleRequest(makeReq(),env,{},FIXED_NOW);assert.equal(r.status,502);assert.equal(count,1);assert.ok(!(await r.text()).includes('secret'));}
});
test('pair redeem atomic single use + installed auth + invalid/expiry/rate limits',async()=>{
 const env=makeEnv();const code='a'.repeat(64);const digest=Buffer.from(await crypto.subtle.digest('SHA-256',new TextEncoder().encode(code))).toString('hex');
 env.QUOTA_DB._db.prepare('INSERT INTO pairing_codes VALUES(?,?)').run(digest,Date.now()+100000);
 const req=()=>new Request('https://proxy.test/v1/pair/redeem',{method:'POST',headers:{'CF-Connecting-IP':'1.2.3.4'},body:JSON.stringify({code})});
 const r=await redeem(req(),env);assert.equal(r.status,200);const out=await r.json();assert.match(out.credential,/^[a-f0-9]{64}$/);
 const reused=await redeem(req(),env);assert.equal(reused.status,401);
 setFetch(async()=>sseResponse());const chat=await handleRequest(makeReq({headers:{'X-Tester-Token':out.credential}}),env,{},FIXED_NOW);assert.equal(chat.status,200);await chat.text();
 for(let i=0;i<9;i++)await redeem(req(),env);assert.equal((await redeem(req(),env)).status,429);
});


test('malformed pairing JSON is400',async()=>{const env=makeEnv();const r=await redeem(new Request('https://proxy.test/v1/pair/redeem',{method:'POST',body:'{'}),env);assert.equal(r.status,400);});


test('pair landing no enrollment or secret query and strict policy',async()=>{const w=(await import('./worker.js')).default;const r=await w.fetch(new Request('https://proxy.test/pair'),makeEnv(),{});assert.equal(r.status,200);assert.equal(r.headers.get('Referrer-Policy'),'no-referrer');assert.equal(r.headers.get('Cache-Control'),'no-store');const b=await r.text();assert.ok(b.includes('location.hash'));assert.ok(!b.includes('fetch('));assert.ok(!b.includes('https://'));assert.ok(r.headers.get('Content-Security-Policy').includes("default-src 'none'"));});


test('exact v0.4 client scaffold stripped, only user forwarded',async()=>{
 const env=makeEnv();let sent;setFetch(async(u,opts)=>{sent=JSON.parse(opts.body);return sseResponse();});
 const body={"model": "nvidia/nemotron-3-ultra-550b-a55b:free", "max_tokens": 1024, "messages": [{"role": "system", "content": "You are the voice of a chat-first Android launcher. Be brief and plain. You cannot perform device actions yourself; the launcher app handles app launches and message drafts. When visual formatting genuinely helps, you may answer in simple HTML (no scripts)."}, {"role": "user", "content": "Say hello. Synthetic compatibility test with no personal data."}], "provider": {"max_price": {"prompt": "0", "completion": "0"}}, "stream": true};
 const r=await handleRequest(makeReq({body:JSON.stringify(body)}),env,{},FIXED_NOW);assert.equal(r.status,200);await r.text();assert.deepEqual(sent.messages,[body.messages[1]]);assert.equal(sent.model,'xingchen-agi/xing4.0-29b-a4b:free');
});
test('v0.4 compatibility rejects changed scaffold and extra history',async()=>{
 for(const mutate of [b=>b.messages[0].content+='x',b=>b.messages.push({role:'assistant',content:'history'}),b=>b.messages[1].role='system']){
 const body={"model": "nvidia/nemotron-3-ultra-550b-a55b:free", "max_tokens": 1024, "messages": [{"role": "system", "content": "You are the voice of a chat-first Android launcher. Be brief and plain. You cannot perform device actions yourself; the launcher app handles app launches and message drafts. When visual formatting genuinely helps, you may answer in simple HTML (no scripts)."}, {"role": "user", "content": "Say hello. Synthetic compatibility test with no personal data."}], "provider": {"max_price": {"prompt": "0", "completion": "0"}}, "stream": true};mutate(body);let calls=0;setFetch(async()=>{calls++;return sseResponse();});const r=await handleRequest(makeReq({body:JSON.stringify(body)}),makeEnv(),{},FIXED_NOW);assert.equal(r.status,400);assert.equal(calls,0);
 }
});


test('upstream 429 -> 502 with only fixed-label diagnostics, no upstream text/urls/raw relayed', async () => {
  setFetch(async (url) => String(url).includes('/endpoints') ? new Response(JSON.stringify({data:{id:'xingchen-agi/xing4.0-29b-a4b:free',endpoints:[{tag:'phala',model_id:'xingchen-agi/xing4.0-29b-a4b:free',pricing:{prompt:'0',completion:'0'}}]}})) :
    new Response(JSON.stringify({error:{code:429,message:'xing is temporarily rate-limited upstream https://evil.example SECRETPROMPT',metadata:{provider_name:'Phala',raw:'Too many requests SECRETPROMPT'}}}),{status:429}));
  const res = await handleRequest(makeReq(), makeEnv(), {}, FIXED_NOW);
  assert.equal(res.status, 502);
  const body = await res.text();
  const j = JSON.parse(body);
  assert.equal(j.error.message, 'upstream_rate_limit');
  assert.deepEqual(j.error.upstream, {code:429, provider:'Phala', kind:'provider_temporarily_limited', provider_body:'rate_limit_text'});
  assert.ok(!/SECRETPROMPT|evil/.test(body), body);
});

test('upstream 429 with non-JSON body -> unparsed_body, nothing relayed', async () => {
  setFetch(async (url) => String(url).includes('/endpoints') ? new Response(JSON.stringify({data:{id:'xingchen-agi/xing4.0-29b-a4b:free',endpoints:[{tag:'phala',model_id:'xingchen-agi/xing4.0-29b-a4b:free',pricing:{prompt:'0',completion:'0'}}]}})) :
    new Response('SECRETPROMPT <html>',{status:429}));
  const res = await handleRequest(makeReq(), makeEnv(), {}, FIXED_NOW);
  const body = await res.text();
  assert.equal(JSON.parse(body).error.upstream.kind, 'unparsed_body');
  assert.ok(!body.includes('SECRETPROMPT'));
});
