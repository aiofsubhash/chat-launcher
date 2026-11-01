# CE-PLAN: Launcher UX overhaul, Work mode, dark mode (2026-11-01)

Decision document per Compound Engineering ce-plan: scope, decisions, units, files, tests, risks. No pre-written code; implementation details belong to ce-work.

## Sources (user authority)
- WhatsApp 2026-11-01 14:13 voice note (UX overhaul), relayed by main.
- WhatsApp 2026-11-01 14:23 voice note (Work mode), verbatim: work mode profile; work results prioritised in search/open; duplicate Gmail must offer BOTH work and personal, never silently pick; briefcase icon for work entries in the text-first UI; work timings "useful" but no schedule supplied.
- WhatsApp 2026-11-01 14:32 text: dark-mode option, good contrast, check a11y.
- WhatsApp 2026-11-01 13:56 + main relays: OpenRouter free models only, no paid fallback/spend.
- Standing: private repo aiofsubhash/chat-launcher; completed chunks pushed continuously; Drive publication only after visual gate; drafts-only messaging, never auto-send; no secrets in repo/APK.

## Scope IN
1. Conversation UX overhaul (U1-U3 below).
2. Work mode / managed-profile support (U4).
3. Dark mode + accessibility pass (U5).
4. Visual gate: re-recorded renders of every changed state before Drive publication (U6).

## Scope OUT
- Focus mode only-show vs hide redesign: user's wording is tentative; keep current hide semantics, flag as open question for user, do not silently settle.
- Work timings/scheduling: no schedule supplied; data model keeps it optional/unconfigured. No UI invention.
- MDM bypass of any kind; work-profile discovery only via public Android APIs.
- Live OpenRouter verification (blocked on user's vault approval; independent of this build).
- Drive/APK publication (main's gate).

## Decisions
- D1: Suggestions and the draft channel picker list only apps actually installed (queried), never hardcoded WhatsApp/Business entries. (User: "only whatever is there".)
- D2: Selected states use bold text, no tick marks, and never color alone (a11y).
- D3: Rename the default/example focus profile "Study" to "Focus".
- D4: Suggestions sit inline left of the Send button in the composer row, not above the composer.
- D5: Draft channel selection becomes an inline assistant question in the conversation ("WhatsApp or Gmail?") shown only when the command names no channel; an explicit channel bypasses the question. The modal dialog is removed.
- D6: Assistant replies render unbubbled (plain text on paper); user messages get a subtle bubble shape (not button-styled) with clear separation from the composer.
- D7: Assistant answers stream (SSE for OpenRouter and Anthropic), rendered progressively; on stream failure fall back to the non-streaming request and say nothing about the fallback mechanics.
- D8: Fresh chat resets after 30 minutes of inactivity. An unsent composer draft and any unanswered assistant question are never silently discarded; the reset keeps the thread in that case and surfaces the conflict in the status line.
- D9: Completion/status copy states only what actually happened: "draft opened in WhatsApp - confirm there to send", never "sent"; "Uber opening with destination prefilled - confirm there", never "booked". No fabricated location claims.
- D10: Work mode is a launcher profile (distinct from OS work profile): when active, work-app results sort first. Duplicate personal+work app entries are always shown as separate choices, labeled with a briefcase marker for the work one. Work apps discovered via LauncherApps.getActivityList per user profile; devices without a work profile degrade gracefully (no briefcase, single entries). MDM-paused work profile shows apps as unavailable, matching OS behavior.
- D11: Dark mode is a user option (System/Light/Dark) preserving the paper design language with readable contrast; verify rendered contrast in tests.
- D12: OpenRouter requests stay fail-closed (catalog-verified free set + provider max_price 0) - already implemented, included here for completeness.

## Units (atomic, independently testable)
- U1 App discovery + ranking: installed-apps-only query; work-profile app discovery; duplicate labeling (briefcase); Work-mode priority ordering. Files: CommandRouter.kt, MainActivity.kt (app query), new AppDiscovery seam. Tests: unit - duplicate personal/work Gmail yields two labeled entries; no work profile yields single unlabeled entry; work mode sorts work entries first; non-installed app never suggested.
- U2 Conversation rendering + inline channel question: unbubbled assistant, user bubble, composer row layout with inline suggestions, modal removed. Files: MainActivity.kt, CommandRouter.kt (pending-question state). Tests: Robolectric renders - explicit channel sends draft without question; absent channel asks inline; conversation states render as specified.
- U3 Session reset + truthful status copy: 30-min inactivity reset preserving drafts/pending questions; handoff-truthful strings. Files: MainActivity.kt, CommandRouter.kt. Tests: unit for reset predicate and draft preservation; string assertions for handoff copy.
- U4 Settings: rename default profile to Focus; bold-no-tick selected states; theme option (System/Light/Dark). Files: SettingsScreen (MainActivity.kt), theme/colors. Tests: renders for settings states in light and dark; contrast check on key text/background pairs.
- U5 Dark theme + a11y: dark palette with verified contrast ratios (WCAG AA for text), touch targets >= 48dp, content descriptions for icon-only controls (briefcase, settings), focus/keyboard semantics. Files: theme, MainActivity.kt, SettingsScreen. Tests: contrast unit tests from the palette; render review for light/dark.
- U6 Visual gate: re-record all changed renders, human-verify pixels, report to main for Drive decision.

## Risks
- R1: Work-profile APIs (LauncherApps profiles) can't be exercised in Robolectric; device-dependent behavior verified by abstraction + unit tests only, flagged as device-unverified in the handoff.
- R2: SSE streaming differences between Anthropic and OpenRouter; parser unit-tested against captured fixtures, live path unverified until vault approval.
- R3: "Only-show vs hide" focus semantics unresolved; blocked on user, not guessed.
- R4: Dark theme over custom wobbly paper rendering may clash; keep paper texture subtle in dark mode, verify renders.
