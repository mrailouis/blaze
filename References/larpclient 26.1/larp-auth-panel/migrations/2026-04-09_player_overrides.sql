CREATE TABLE IF NOT EXISTS player_overrides (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    target_uuid TEXT NOT NULL UNIQUE,
    target_name TEXT,
    custom_name TEXT,
    scale_x REAL NOT NULL DEFAULT 1,
    scale_y REAL NOT NULL DEFAULT 1,
    scale_z REAL NOT NULL DEFAULT 1,
    enabled INTEGER NOT NULL DEFAULT 1,
    last_resolved_at TEXT,
    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_player_overrides_enabled_updated_at
    ON player_overrides (enabled, updated_at);
