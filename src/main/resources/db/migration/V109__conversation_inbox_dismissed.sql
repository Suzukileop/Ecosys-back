ALTER TABLE conversation_participants
    ADD COLUMN IF NOT EXISTS inbox_dismissed_at TIMESTAMP NULL;

CREATE INDEX IF NOT EXISTS idx_conversation_participants_inbox_dismissed
    ON conversation_participants (user_id, inbox_dismissed_at)
    WHERE inbox_dismissed_at IS NULL;
