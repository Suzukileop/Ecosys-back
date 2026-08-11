-- Direct (targeted) temporary guest invites with accept/decline flow

ALTER TABLE conversation_invites
    ADD COLUMN IF NOT EXISTS invitee_id UUID REFERENCES users(id),
    ADD COLUMN IF NOT EXISTS status VARCHAR(20);

CREATE INDEX IF NOT EXISTS idx_conversation_invites_invitee_status
    ON conversation_invites(invitee_id, status);

CREATE INDEX IF NOT EXISTS idx_conversation_invites_conversation_invitee
    ON conversation_invites(conversation_id, invitee_id);
