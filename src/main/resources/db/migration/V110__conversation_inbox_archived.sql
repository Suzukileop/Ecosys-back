ALTER TABLE conversation_participants
    ADD COLUMN IF NOT EXISTS inbox_archived_at TIMESTAMP NULL;

CREATE INDEX IF NOT EXISTS idx_conversation_participants_inbox_archived
    ON conversation_participants (user_id, inbox_archived_at)
    WHERE inbox_archived_at IS NOT NULL;
