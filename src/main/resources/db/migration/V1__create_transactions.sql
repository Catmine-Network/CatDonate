CREATE TABLE card_transactions (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    request_id TEXT NOT NULL UNIQUE,
    player_uuid TEXT NOT NULL,
    player_name TEXT NOT NULL,
    provider TEXT NOT NULL,
    telco TEXT NOT NULL,
    declared_amount INTEGER NOT NULL,
    actual_value INTEGER,
    provider_received INTEGER,
    status TEXT NOT NULL,
    provider_transaction_id TEXT,
    poll_count INTEGER NOT NULL DEFAULT 0,
    next_poll_at INTEGER,
    reward_commands TEXT,
    reward_multiplier INTEGER,
    reward_state TEXT NOT NULL DEFAULT 'NONE',
    reward_executed_count INTEGER NOT NULL DEFAULT 0,
    encrypted_code TEXT,
    encrypted_serial TEXT,
    serial_masked TEXT NOT NULL,
    fingerprint TEXT NOT NULL UNIQUE,
    last_error TEXT,
    notification_key TEXT,
    notification_delivered INTEGER NOT NULL DEFAULT 0,
    created_at INTEGER NOT NULL,
    updated_at INTEGER NOT NULL,
    completed_at INTEGER,
    sensitive_expires_at INTEGER
);

CREATE INDEX idx_card_transactions_player_created
    ON card_transactions(player_uuid, created_at DESC);
CREATE INDEX idx_card_transactions_poll
    ON card_transactions(status, next_poll_at);

CREATE TABLE transaction_events (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    request_id TEXT NOT NULL,
    event_type TEXT NOT NULL,
    old_status TEXT,
    new_status TEXT,
    detail TEXT,
    actor TEXT,
    created_at INTEGER NOT NULL,
    FOREIGN KEY (request_id) REFERENCES card_transactions(request_id)
);

CREATE INDEX idx_transaction_events_request
    ON transaction_events(request_id, created_at);
