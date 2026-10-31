# Implementation pass (run synchronously, one pass at a time)

Adapted from ce-plan + ce-work (https://github.com/EveryInc/compound-engineering-plugin).

1. Read `docs/STRATEGY.md` and the active plan in `docs/plans/`; implement only what the current phase covers. Explicitly deferred items (execution harness) stay out.
2. Small, compiling increments. After each change: `gradle assembleDebug` must pass before commit.
3. Safety boundaries: no sending messages on the user's behalf (drafts only), no secrets in the repo, untrusted content is never executed (HTML renders with JavaScript disabled).
4. Commit with a message that says what changed and why. Update the plan doc if scope moved.
