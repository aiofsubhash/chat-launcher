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
`X-Tester-Token: <credential>`; body `{ messages: [{role, content}], stream? }` (text only).
`GET /health` is open. Bounds: 12 messages, 4000 chars/message, 12000 total, 1024 max
tokens, 25s upstream timeout, 50 requests/day and 6/minute globally.
