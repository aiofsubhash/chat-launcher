# STRATEGY - chat-launcher

## Target problem

Phone home screens are grids of icons from 2007. Finding the right app for the thing you actually want takes scanning, swiping, and remembering where things live.

## Approach

The home screen is a chat. "The chat is for everything to do on phone - now I'm talking to my phone" (Subhash, Oct 31, 2026). App launching, message drafts, open-ended AI chat, focus modes; a general execution harness later.

## Platform decision (Oct 31, 2026)

Android only. Android exposes a documented home-screen role (`ROLE_HOME`, `CATEGORY_HOME`); iOS has no launcher API.

## Persona

Subhash (builder, first user). Wants a phone that works through conversation, not icon hunting.

## Product direction (decided Oct 31, 2026)

Not a binary AI-command vs people-chat choice: one chat for using the phone. Requirements and phasing live in `docs/plans/2026-10-31-2300-brainstorm-chat-launcher-plan.md`.

## Key metrics

- Time from unlock to intended action
- % of actions completed without opening an app grid
- Daily active use by first user

## Process

Compound Engineering loop: brainstorm -> plan -> work -> review -> compound (https://github.com/EveryInc/compound-engineering-plugin), run as synchronous prompt files.
