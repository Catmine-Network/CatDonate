CREATE TABLE player_top_up_security (
    player_uuid TEXT PRIMARY KEY,
    consecutive_failed_cards INTEGER NOT NULL DEFAULT 0,
    blocked_until INTEGER,
    updated_at INTEGER NOT NULL
);

CREATE INDEX idx_player_top_up_security_blocked_until
    ON player_top_up_security(blocked_until);
