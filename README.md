# chat-launcher

An Android launcher where the home screen is a chat.

## Vision

Chat is the interface. Instead of a grid of icons, you talk: the launcher interprets what you want and brings the right app, action, or answer to you.

## Open product question

Two directions are being decided between:

1. **AI-command chat** - type or say what you want ("open Spotify", "message Amma I'm running late", "what's on my calendar") and the launcher does it. Precedents: Saint John, ARIA (open-source agentic launchers on Android).
2. **People-chat hub** - your conversations are the home screen; a typed line routes to the right person on the right app. Precedent: MinkLauncher's message hub.

## Platform

- **Android only** (decision Oct 31, 2026: iOS out of scope - Apple offers no home-screen replacement API).
- Android supports a real home-screen replacement via `ROLE_HOME` / `CATEGORY_HOME` intent filter (user grants the home role to the app).

## Process

Built with the Compound Engineering loop (https://github.com/EveryInc/compound-engineering-plugin): brainstorm requirements, plan, work, review, compound learnings. See `docs/STRATEGY.md`.
