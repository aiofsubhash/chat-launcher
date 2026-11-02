---
artifact_contract: ce-unified-plan/v1
product_contract_source: ce-brainstorm
---

# Device-feedback revision - requirements and decisions (Nov 2, 2026, ~18:49 IST voice)

Source: owner's first real-device trial of v0.2.0 (WhatsApp voice note, Nov 2 18:49 IST).
Earlier plans: 2026-10-31 brainstorm plan. Corrections inside the voice note win
(Timer/default alarm, NOT PhonePe; allow-list profiles; dropdown arrow not plus).

## Requirements

R1. Sticky notes sit a little higher on home; a tap must FEEL like a tap (visible pressed state).
R2. Chat must not take over the screen. Notes stay visible; conversation lives in a
    bottom-only section that scrolls inside itself.
R3. Keyboard must not cover the composer: whole bottom section (text area + Send) stays
    visible while typing. Evidence limit: Robolectric cannot show a real IME; layout uses
    adjustResize + inset-aware tests, real-keyboard behavior stays UNVERIFIED until device trial.
R4. Message intents work with NO API key, deterministically: "send a message to X on WhatsApp",
    "send X text on whatsapp", "send X: text", "message X on telegram saying ...", missing text
    asks inline and the next line completes it. A half-parseable private command gets a local
    clarifying question, never a silent cloud call. All drafts stay user-confirmed in the
    target app (he picks the chat, taps Send).
R5. History: every exchange (user line, local reply, handoff, cloud reply, errors) persists
    on-device and shows in Settings > History. Local only; cloud replies tagged.
R6. Settings: more spacious, clearer hierarchy, even padded tap targets (ripple includes
    left padding), consistent spacing scale. No new decoration.
R7. Profiles become allow-lists ("select what to add", not "what to remove"):
    - built-in Focus: only a "Timer" sticky note opening the phone's default clock/alarm app.
    - built-in Work: managed work-profile apps always available + user additions; editable.
    - built-ins cannot be deleted; custom profiles unchanged in meaning.
    - Migration from hidden-lists: invert against the CURRENT installed list, only when that
      list is non-empty; keep a backup of the old set; name collisions with built-ins migrate
      as "<name> (migrated)". Deferred (not destructive) when the app list is unavailable.
R8. Profile switcher: dropdown arrow (not plus), more space, calm container.
R9. Connect AI: configurable chat endpoint + on-device credential (pairing code) so a
    Cloudflare Worker can hold the upstream key. No credential in APK/repo. Disclosure shown
    before saving: only typed chat text leaves the phone; provider may log it. Cloud replies
    captioned "cloud ai". Direct-OpenRouter free-only gate (catalog check + max_price 0/0)
    unchanged; proxy free-only enforcement is infra-verified separately, not claimed in-app.
R10. Cloudflare Worker source owned here under infra/cloudflare (secret-free): POST
    /v1/chat/completions, bearer tester credential, server-fixed model
    nvidia/nemotron-3-ultra-550b-a55b:free, stream passthrough, bounded input/output/timeout,
    global quota via D1 (50/day, 6/min), fail closed. Infra owns account, secrets, deployment.

## Out of scope
Real-keyboard/device/MDM/TalkBack verification (render + unit tests only), live proxy calls
until infra deploys and verifies, paid anything.
