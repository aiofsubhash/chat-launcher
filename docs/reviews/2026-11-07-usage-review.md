# Usage review

- Fixed a correctness blocker: Android queryUsageStats may expand boundaries to whole buckets. Changed to event-based foreground sessions clipped to local midnight and now, with a one-day lookback for midnight-spanning sessions. Tests cover overlap, screen-off, short visits and current sessions.
- Removed arbitrary sub-minute suppression. All positive app time is shown.
- Statistics belong to the current Android user profile only. Work-profile entries are excluded, and the UI says that work usage is separate.
- No network calls or persistent usage storage added. Existing AI and profile behavior unchanged.
- Denied access has its own state; read failure is not shown as a zero-use day.
- Narrow tabs scroll horizontally rather than clip or shrink text. Light/dark renders reviewed separately.
- Full regression suite: 231 tests, zero failures/errors, one intentionally skipped integration test. Physical OEM permission screens and real-device counts remain unverified.
