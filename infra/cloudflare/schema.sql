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

-- v4 additive migration
CREATE TABLE IF NOT EXISTS model_routes (model TEXT PRIMARY KEY,provider TEXT NOT NULL,enabled INTEGER NOT NULL DEFAULT 0,priority INTEGER NOT NULL DEFAULT 100);
INSERT OR IGNORE INTO model_routes(model,provider,enabled,priority) VALUES('xingchen-agi/xing4.0-29b-a4b:free','phala',1,10);
CREATE TABLE IF NOT EXISTS pairing_codes(code_hash TEXT PRIMARY KEY,expires_at INTEGER NOT NULL);
CREATE TABLE IF NOT EXISTS install_credentials(token_hash TEXT PRIMARY KEY,created_at INTEGER NOT NULL,revoked INTEGER NOT NULL DEFAULT 0);
CREATE TABLE IF NOT EXISTS pairing_limits(key TEXT PRIMARY KEY,window INTEGER NOT NULL,count INTEGER NOT NULL);
