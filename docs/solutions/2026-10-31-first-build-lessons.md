# Compound - first build loop (Oct 31, 2026)

## What was built
v0.1.0 chat-first Android launcher: single-activity Compose chat home (CATEGORY_HOME), fuzzy app suggestions, share-sheet message drafts, named focus profiles, Anthropic-backed open chat, HTML bubbles (JS disabled).

## What broke and how it was found
- `indexOf(query, true)` compile error - caught by first gradle build; Kotlin needs named `ignoreCase = true`.
- Keystroke-time PackageManager queries and a 6-item cap leaking into the settings editor - caught in the review pass.

## Decisions
- Android only (iOS has no home-screen API) - user decision, Oct 31.
- Drafts only, never auto-send - safety boundary from product owner.
- Execution harness (filesystem/computer-use) explicitly deferred by user.
- Single-module Gradle layout at repo root to keep the project minimal.

## For the next loop
- Build is reproducible with JDK 17 + Android SDK 34 + `gradle assembleDebug` (no wrapper committed; Gradle 8.9 known good).
- Emulator needs KVM; verify UI on a physical device instead.
- Inspiration repo (github.com/sumit189/tui) is private - pending access.
