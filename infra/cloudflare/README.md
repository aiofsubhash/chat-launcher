# Connect AI proxy (Cloudflare Worker)

Holds the upstream OpenRouter key server-side so the launcher app (and its tester) never
needs it. The app sends a per-tester pairing credential; the Worker fixes the free model
`nvidia/nemotron-3-ultra-550b-a55b:free`, enforces a global quota, and fails closed.

- `worker.js` - the Worker (no dependencies).
- `schema.sql` - D1 quota table (`QUOTA_DB` binding, database `chat-launcher-quota`).
- `worker.test.js` - `node --test worker.test.js` (10 tests, no dependencies).
- `wrangler.toml` - non-secret config; deployer sets `OPENROUTER_API_KEY` and
  `TESTER_TOKEN` (single shared tester secret).

Contract: `POST /v1/chat/completions` with `Authorization: Bearer <credential>` or
`X-Tester-Token: <credential>` (or `Authorization: Bearer <credential>`); body
`{ messages: [{role: "user", content}] }` with EXACTLY one user text message - no client
system prompts, history, model or provider overrides.
`GET /health` is open. The response contract is SSE-only: the proxy always streams
(`stream: true` upstream); clients consume `text/event-stream`. Bounds: one message,
4000 chars, 600 max tokens, 30s upstream lifetime timeout (covers the whole stream, not
just headers), 50 requests/day and 6/minute globally, enforced by one atomic conditional
UPDATE on a singleton D1 row.
Free-only is enforced server-side: fixed `:free` model, `provider.max_price`
{prompt: 0, completion: 0, request: 0, image: 0}, `allow_fallbacks: false`. There is no
pinned provider list - routing must satisfy a zero price or the request fails.
