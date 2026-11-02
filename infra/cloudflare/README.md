# Launcher private AI proxy v4

Stable SSE-only POST /v1/chat/completions. One current nonsensitive user text only, no history/system prompt.4000 chars,16KiB body,600output tokens,total30s. Auth Bearer or X-Tester-Token. Free-only server route catalog and D1 model_routes order/enable. Unknown pairs fail closed. Current qualified catalog: xingchen-agi/xing4.0-29b-a4b:free via phala, zero retention/no training routing required. No APK model list or upstream secret. Adding newly reviewed routes needs server change, never APK. D1 operators can disable/reorder existing reviewed routes without source change.

Every inference attempt uses atomic50/day6/minute global quota. Up to2 attempts pre-stream only, per-attempt quota, live endpoint price/provider validation, zero price caps, pinned provider/no external fallback, data_collection deny + zdr.401/402 stop; availability failures may try next approved route. No retry after SSE starts. With only1 qualified route enabled, there is no second working fallback yet. Safe errors expose status category only, never upstream body/key/prompt. Logs disabled.

Pairing: POST /v1/pair/redeem {code:64hex} returns {credential,endpoint}; code expires15min, operator-issued only, SHA256 hash stored, atomic D1 batch insert credential/delete code. Per-install credential256bits, hash stored, revocable. No public enrollment. Rate gate global100/min and privacy hashed IP buckets10/min (collisions deliberately share quota); fixed256IP buckets bounds storage. Keys remain encrypted Worker secrets.

Apply schema.sql (idempotent; v4 additions are additive), preserves quota singleton. Issuance must generate secret code privately, write only SHA256hash+expiry to pairing_codes via supported operator flow, deliver code as one-tap app pairing URL privately; never commit raw code or credential. Code revocation DELETE by hash; install revocation set revoked1 by hash. No code issuance public endpoint.

19 Node tests including real SQLite race + pairing single-use. Run node --test worker.test.js (Node22).
