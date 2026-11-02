-- D1 schema for the quota store. A single singleton row keeps the check-and-increment
-- atomic: every admission is ONE conditional UPDATE against this row.
CREATE TABLE IF NOT EXISTS quota_singleton (
  id INTEGER PRIMARY KEY CHECK (id = 1),
  day_key TEXT NOT NULL DEFAULT '',
  day_count INTEGER NOT NULL DEFAULT 0,
  minute_key TEXT NOT NULL DEFAULT '',
  minute_count INTEGER NOT NULL DEFAULT 0
);
INSERT OR IGNORE INTO quota_singleton (id) VALUES (1);
