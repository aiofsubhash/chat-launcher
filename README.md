# chat-launcher

An Android launcher where the home screen is a chat.

## Vision

Chat is the interface. Instead of a grid of icons, you talk: the launcher interprets what you want and brings the right app, action, or answer to you.

## Use cases (Subhash, Oct 31, 2026)

1. Send this message to a person: "intended message" - opens a share-sheet draft (never auto-sends) [v0.1]
2. Typing "whatsapp" offers to open WhatsApp (same for any app) [v0.1]
3. Open-ended AI chat with HTML responses (rendered with JavaScript disabled) [v0.1]
4. Phone-execution harness (filesystem + API key + computer use) - later, explicitly deferred
5. Focus modes: named profiles that hide chosen apps [v0.1]

## Platform

- **Android only** (decision Oct 31, 2026: iOS has no home-screen replacement API).
- Real home-screen replacement via `ROLE_HOME` / `CATEGORY_HOME`.

## Build

Single-module Kotlin/Compose app. Needs JDK 17 + Android SDK (platform 34):

    gradle assembleDebug

Then set Chat Launcher as the default home app.

## Process

Built with the Compound Engineering loop (https://github.com/EveryInc/compound-engineering-plugin), executed as synchronous prompt files: brainstorm requirements, plan, work, review, compound learnings. See `docs/STRATEGY.md` and `docs/plans/`.
