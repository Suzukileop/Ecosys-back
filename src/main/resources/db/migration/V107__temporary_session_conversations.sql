-- Isolated temporary guest conversations (separate from individual DMs).
ALTER TABLE conversations
    ADD COLUMN IF NOT EXISTS temporary_session BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE conversation_invites
    ADD COLUMN IF NOT EXISTS source_conversation_id UUID NULL REFERENCES conversations(id) ON DELETE SET NULL;

CREATE INDEX IF NOT EXISTS idx_conversation_invites_source_conversation
    ON conversation_invites (source_conversation_id);

CREATE INDEX IF NOT EXISTS idx_conversations_temporary_session
    ON conversations (temporary_session)
    WHERE temporary_session = TRUE;

-- End guests that were wrongly attached to permanent (non-temporary) conversations.
UPDATE conversation_participants cp
SET left_at = NOW()
FROM conversations c
WHERE cp.conversation_id = c.id
  AND cp.role = 'GUEST'
  AND cp.left_at IS NULL
  AND c.temporary_session = FALSE;

UPDATE conversation_invites ci
SET status = 'CANCELLED'
FROM conversations c
WHERE ci.conversation_id = c.id
  AND c.temporary_session = FALSE
  AND ci.status IN ('PENDING', 'ACCEPTED')
  AND ci.invitee_id IS NOT NULL;
