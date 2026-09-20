CREATE INDEX idx_card_transactions_player_name_created
    ON card_transactions(player_name COLLATE NOCASE, created_at DESC);
