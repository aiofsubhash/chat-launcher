# Chat Launcher — Design Baseline

Baseline set by Subhash (voice notes, Nov 1 2026). This document is the reference for all future design work on this app. Change it only when the user asks.

## Home screen (baseline)

- **Background:** calm white "paper" with a wobbly hand-drawn grid, line alpha ~0.17.
- **Typeface:** Nunito throughout.
- **Top bar:** "Fresh chat" label on the left (same size as dock app labels, ~17sp), tweak/sliders icon on the right (two vertical lines with knobs, line-only, no background, no gear icon). The tweak icon opens Settings.
- **Date + time:** centered just below the top bar, ~26sp, slightly lowered (~24dp top padding).
- **Dock:** four sticky notes (pale yellow #F6EFC1 cards, slight hand-tilted rotation, app label only, no icon tiles). Defaults: Maps, Chrome, Spotify, PhonePe. Max 4, customizable in Settings.
- **Composer:** tall, rounded input with placeholder "write what you want to do". Send button: pastel brown #B08968, corner radius 12, text "Send", no icon.
- **Suggestions:** text-only suggestion chips, no icons.

## Settings page

- Anthropic API key field.
- Grid style toggle (wobbly hand-drawn vs straight).
- Home apps checkboxes (pick up to 4 for the dock).
- Focus profiles section (create/activate profiles).
- Work-mode sticky dock is fixed to Slack, Claude, Gmail (owner 12:44): the work-profile
  instance wins a personal/work duplicate, a personal-only install still shows, and no
  other work app appears. Display only - the Work allow-list still governs what opens.

## Later (user-listed ideas, NOT implemented yet)

- Focus-mode background image.
- Pomodoro timer popup, started by a text button above the composer.
- Focus-mode capture-and-hold replies with history shown in Settings.
- App action button at the bottom, parallel to Send.
- "Call <name>" phone flow.
- Research only (do not implement): WhatsApp send-message mechanics, Uber booking. Uncertain nouns from voice transcription (e.g. "email", "call") must not trigger actual actions.

## Engineering notes

- Visual verification is via Roborazzi/Robolectric preview renders; never claim device testing from these.
- Robolectric test environment has no real app icons, so previews show label-only sticky notes; real icons appear on device.
- Material3 AlertDialog with text fields never reaches Espresso idle in Robolectric (infinite animation), which blocks automated Settings screenshots.
