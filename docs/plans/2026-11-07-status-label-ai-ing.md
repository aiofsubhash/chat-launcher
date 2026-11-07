# Cloud caption label: ai-ing / ai (2026-11-07)

Owner request (WhatsApp, Nov 7): "The text says cloud ai, when loading let it say ai-ing
and when not loading let it just say ai."

## Change

- ChatMessage gains `streaming` (true while a cloud reply streams in).
- New `cloudCaption(streaming)`: "ai-ing" while streaming, "ai" once settled.
- Chat caption under cloud replies uses it; history captions read "Instinct · ai".
- Scope is labels only: no behavior, prompt, privacy or settings-copy change.
- v0.5.5 (versionCode 10).

## Verification

- `gradle testDebugUnitTest`: includes new CloudCaptionTest (streaming/settled labels).
- History screenshot record shows "Instinct · ai".
- Physical-phone check remains the owner's install-over test.
