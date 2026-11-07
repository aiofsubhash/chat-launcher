# App usage stats (activity-monitor level) - plan

Owner Nov 7: "Can the launcher know how much time I've sent on apps" -> "Activity monitor
level is enough" -> "Make it happen". Scope is exactly that: which apps were open and for
how long today. NOT in scope: mindless-loop detection and its UX (parked as future feature
item 50, owner decides later), screen/message/content reading, storing or uploading usage.

## Approach

- Manifest: `PACKAGE_USAGE_STATS` (special access; the user enables Usage access once in
  system settings - deep-linked from the tab).
- `UsageStats.kt`: `UsageSource` interface (injectable for tests), `AndroidUsageSource`
  over `UsageStatsManager.queryUsageStats(INTERVAL_DAILY, midnight, now)`, `UsageStatsLogic`
  with pure merge/sort/format functions. Access check: AppOps `OPSTR_GET_USAGE_STATS`
  (unsafeCheckOpNoThrow on 29+), falling back to a proof-by-read one-day query for devices
  that answer MODE_DEFAULT while granted.
- Settings gets a "Usage" tab (Home, Usage, History, Context, Account). Tabs move to
  13sp / 2dp padding so "Account" still fits single-line at 320dp; the narrow-tabs test
  now includes "Usage".
- Tab states: no source, no access (explanation + "Open usage access settings" button),
  empty day, and the list: total line plus per-app rows (label, "last HH:mm", duration).
  Refreshes via LifecycleResumeEffect so returning from the system toggle shows fresh data.
- Labels come from the installed launchable apps (plain label, personal name wins over the
  briefcase variant); unknown/system packages are dropped, sub-minute entries are clutter.
- v0.5.4 / versionCode 9. Data never leaves the phone; the app stores nothing itself.

## Verification

- `UsageStatsLogicTest`: merge, label filter, sub-minute drop, ordering, duration format.
- `UsageTabTest` (Robolectric): no-access shows the enable button; access shows the total
  line and per-app rows with durations.
- Screenshot records: usage tab with data (light), no-access state, updated narrow-tabs
  shots with five tabs.
- Physical phone (owner's): the Usage access grant flow and real numbers.

## Review correction
Use UsageEvents sessions, not daily aggregate buckets which Android can expand beyond the requested range. Clip sessions to midnight/now; include short visits; current Android user only. Tabs horizontally scroll on narrow phones. No usage persistence or network transmission.
