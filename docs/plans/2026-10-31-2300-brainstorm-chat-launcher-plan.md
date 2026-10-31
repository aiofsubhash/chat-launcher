---
artifact_contract: ce-unified-plan/v1
product_contract_source: ce-brainstorm
---

# Chat Launcher - requirements and v1 plan

## Goal capsule

An Android home-screen replacement whose only surface is a chat. You type (later: talk) what you want; the launcher opens apps, prepares messages, answers questions, and can hide distracting apps behind focus profiles.

## Product contract (from Subhash, Oct 31, 2026)

"The chat is for everything to do on phone - now I'm talking to my phone."

Use cases, verbatim anchors:
1. "Send this message to Avinash: 'intended message'" - chat drafts a message for a person; the user confirms the send in the target app.
2. "Just typing whatsapp should give an option to open whatsapp" - app-name suggestions while typing.
3. Same for Swiggy or any installed app.
4. "It should also be able to do stuff, uses phone filesystem + api key + computer use maybe - a harness is needed for this feature maybe later" - DEFERRED to a later version.
5. "This is also normal chat, like Google/ChatGPT - chat responds in html the way chatgpt does lately, useful for learning user intent before giving a text-only markdown response" - open-ended AI chat; rich HTML rendering is v2 (rendered untrusted content must never be executable).
6. "Useful for focus modes - once enabled (with or without chat) it will not display options like insta, whatsapp based on profiles (custom made, with or without chat)" - v1: one focus toggle with a hidden-apps set; profiles come later.

Platform decision (Oct 31): Android only. Android exposes a documented home-screen role; iOS has no launcher API.

## v1 scope (this commit)

- Home-screen launcher activity (CATEGORY_HOME) with a single chat screen.
- Fuzzy app suggestions as chips above the keyboard; tap to launch.
- `send ... to X: message` and `send text via <app>` -> share-sheet draft. The app never sends on the user's behalf.
- `focus` / `focus off` toggle; long-press-hide deferred, hidden set stored locally.
- Open-ended chat through the Anthropic Messages API when the user adds their own API key in Settings (stored on-device only).
- No permissions beyond INTERNET; app listing via manifest `<queries>`.

## Later

- Execution harness: filesystem access, computer-use agent (use case 4 - explicitly deferred by owner).
- Contact-aware message routing (needs READ_CONTACTS consent flow).
- Inspiration from Sumit's repo when access is granted (currently private).

## Status (Oct 31)

v0.1.0 built and compiles clean (`gradle assembleDebug`). Focus profiles (6) and HTML responses with JavaScript disabled (5) are in. Review + compound passes done: docs/reviews/, docs/solutions/. Device smoke test pending (build sandbox has no KVM).

## Architecture

Single-module Kotlin app, Jetpack Compose UI.
- `MainActivity.kt` - chat screen, suggestions, settings dialog, app resolution.
- `CommandRouter.kt` - text -> action; drafts only, never sends.
- `LlmClient.kt` - minimal Anthropic Messages API client; key from SharedPreferences.

## Build

Requires Android SDK (platform 34) and JDK 17:

    gradle :assembleDebug   # or open in Android Studio

Then set Chat Launcher as the home app when Android prompts (or Settings > Apps > Default apps > Home app).

## Verification

- Gradle compiles the debug APK (toolchain permitting in CI/local).
- Manual smoke: install APK, set as home, type "whatsapp" -> suggestion chip -> launches; "focus" -> suggestions hide configured apps; "send hello to Avinash:" -> share sheet opens with draft.
