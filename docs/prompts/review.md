# Review pass (run synchronously, one pass at a time)

Adapted from ce-code-review (https://github.com/EveryInc/compound-engineering-plugin).

1. Review the diff against the active plan: correctness, safety boundaries (no auto-send, no secrets, no executing untrusted content), and obvious performance traps.
2. Classify each finding: blocker / should-fix / note. Fix blockers and should-fixes before marking the pass done; record notes in `docs/reviews/`.
3. Rebuild after fixes; a review pass is not done until the build is green.
4. Record the outcome in `docs/reviews/<date>-<scope>-review.md`.
