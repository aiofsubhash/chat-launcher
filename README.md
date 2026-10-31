# chat-launcher

An Android launcher where the home screen is a chat.

## Vision

Chat is the interface. Instead of a grid of icons, you talk: the launcher interprets what you want and brings the right app, action, or answer to you.

## Use cases (Subhash, Oct 31, 2026)

1. Send this message to a person: "intended message"
2. Typing "whatsapp" offers to open WhatsApp (same for any app)
3. Open-ended AI chat, later with rich HTML responses
4. Phone-execution harness (filesystem + API key + computer use) - later
5. Focus modes that hide chosen apps, with or without chat

## Platform

- **Android only** (decision Oct 31, 2026: iOS has no home-screen replacement API).
- Real home-screen replacement via `ROLE_HOME` / `CATEGORY_HOME`.

## Build

Single-module Kotlin/Compose app. Needs JDK 17 + Android SDK (platform 34):

    gradle assembleDebug

Then set Chat Launcher as the default home app.

## Process

Built with the Compound Engineering loop (https://github.com/EveryInc/compound-engineering-plugin), executed as synchronous prompt files: brainstorm requirements, plan, work, review, compound learnings. See `docs/STRATEGY.md` and `docs/plans/`.
