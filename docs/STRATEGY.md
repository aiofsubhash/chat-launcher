# STRATEGY - chat-launcher

## Target problem

Phone home screens are grids of icons from 2007. Finding the right app for the thing you actually want takes scanning, swiping, and remembering where things live.

## Approach

The home screen is a chat. You say what you want; the launcher brings the app, action, or answer to you.

## Platform decision (Oct 31, 2026)

Android only. Android exposes a documented home-screen role (`ROLE_HOME`, `CATEGORY_HOME`); iOS has no launcher API, so the full vision is only buildable on Android.

## Persona

Subhash (builder, first user). Wants a phone that works through conversation, not icon hunting.

## Open product question (awaiting decision)

- **AI-command chat**: type/say intent, launcher acts (launch app, draft message, answer). Precedents: Saint John, ARIA.
- **People-chat hub**: conversations as home screen, typed line routes to person + app. Precedent: MinkLauncher.

The brainstorm (`docs/brainstorms/`) starts once this is decided.

## Key metrics

- Time from unlock to intended action
- % of actions completed without opening the app grid
- Daily active use by first user

## Process

Compound Engineering loop: brainstorm -> plan -> work -> review -> compound (https://github.com/EveryInc/compound-engineering-plugin).
