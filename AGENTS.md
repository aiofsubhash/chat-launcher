# AGENTS.md - guidance for agents working on chat-launcher

An Android home-screen launcher where the home screen is a chat, built for and with
Subhash (first user). Private repo under aiofsubhash; subhash-kashyap is collaborator.

Current release: v0.5.4 (versionCode 9), developed on branch `wip-device-feedback`.

## Read first

- `README.md` - vision, use cases, platform decision (Android only, `ROLE_HOME`).
- `docs/STRATEGY.md` - target problem, approach, metrics.
- `DESIGN.md` - the design baseline set by the owner. Match it exactly; change it only
  when the owner asks. Calm paper background, wobbly grid, Nunito, sticky-note dock,
  pastel brown Send. Dark mode needs contrast and a11y checks.
- `docs/TESTING.md` - device smoke-test checklist.
- Latest plan in `docs/plans/` before touching a feature.

## Build and test

Single-module Kotlin/Compose app. JDK 17 + Android SDK (platform 34).

    gradle assembleDebug   # must pass before every commit
    gradle testDebugUnitTest

- Tests: JUnit4 + Robolectric; Compose UI tests; Roborazzi screenshot records for the
  visual baseline (light/dark, narrow widths).
- The build sandbox has no KVM, so the emulator cannot boot. Renders and Robolectric are
  not device testing: never claim phone verification from them. Physical-phone items
  (permission flows, real totals, install-over) stay explicitly unverified until the
  owner checks them.

## Process (owner's standing instructions)

- Compound Engineering loop, run as synchronous prompt files in `docs/prompts/`:
  research -> implement (ce-plan then ce-work for a feature) -> review -> compound
  (write `docs/solutions/<date>-<topic>.md` after each loop).
- Push continuously: commit and push each finished chunk; don't hold work locally until
  a whole build is done.
- Don't interrupt a running build. When a build keeps failing, debug it (ce-debug /
  investigate) rather than killing it.
- Small, compiling increments. Update the plan doc when scope moves.
- Design work uses the Compound Engineering design skills.
- Keep the done / tested-but-unverified-on-phone / left list current; test the whole
  path before shipping a build and state plainly what is not verified.

## Distribution

- APKs go to a private Google Drive link shared with [private delivery email removed],
  never to GitHub.
- Installs are over the previous version (don't uninstall); the signing key is backed
  up - losing it again forces an uninstall.
- Send UI previews before a build when the look changed; self-review previews against
  the design baseline first.

## Safety boundaries

- Handoffs, never autonomous actions: messaging apps open a draft, Uber gets the
  destination for review. The app never sends or books by itself. Confirmations say a
  draft or screen was opened, not that something was sent.
- No secrets in the repo, logs, or command lines. The AI enrollment ticket is injected
  at build time from a file path (`-PenrollmentTicketFile=...`); empty means manual
  pairing link only.
- Untrusted content is never executed: HTML replies render with JavaScript disabled
  and remote images blocked.
- Usage stats (v0.5.4) stay on-device: no persistence, no upload, current Android
  profile only (work profile is separate). Usage access is a one-time system setting
  the user grants.
- AI chat: free models only, no paid fallback, via the `chat-launcher-proxy`
  Cloudflare Worker. The OpenRouter key lives server-side, never in the APK.

## Deferred (owner decides; do not build)

- Phone-execution harness (filesystem + API key + computer use).
- Mindless-loop detection and its UX (usage stats shipped without it).
- Snake on the home screen; todos answered from saved todos without an LLM call;
  history in a plain on-device text file that survives reinstall.
