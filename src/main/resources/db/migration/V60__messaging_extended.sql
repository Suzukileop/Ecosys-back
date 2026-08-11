-- Extended messaging: groups, attachments, invites, calls, read receipts

ALTER TABLE conversations
    ADD COLUMN IF NOT EXISTS type VARCHAR(20) NOT NULL DEFAULT 'direct',
    ADD COLUMN IF NOT EXISTS title VARCHAR(200),
    ADD COLUMN IF NOT EXISTS created_by UUID REFERENCES users(id);

ALTER TABLE conversation_participants
    ADD COLUMN IF NOT EXISTS role VARCHAR(20) NOT NULL DEFAULT 'member',
    ADD COLUMN IF NOT EXISTS left_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS expires_at TIMESTAMP;

ALTER TABLE direct_messages
    ADD COLUMN IF NOT EXISTS message_type VARCHAR(20) NOT NULL DEFAULT 'text';

ALTER TABLE direct_messages
    ALTER COLUMN content DROP NOT NULL;

CREATE TABLE IF NOT EXISTS message_attachments (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    message_id      UUID NOT NULL REFERENCES direct_messages(id) ON DELETE CASCADE,
    object_key      VARCHAR(500) NOT NULL,
    original_filename VARCHAR(255) NOT NULL,
    content_type    VARCHAR(120) NOT NULL,
    size_bytes      BIGINT NOT NULL,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS conversation_invites (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    conversation_id UUID NOT NULL REFERENCES conversations(id) ON DELETE CASCADE,
    token           VARCHAR(64) NOT NULL UNIQUE,
    created_by      UUID NOT NULL REFERENCES users(id),
    role            VARCHAR(20) NOT NULL DEFAULT 'guest',
    expires_at      TIMESTAMP NOT NULL,
    max_uses        INT NOT NULL DEFAULT 1,
    use_count       INT NOT NULL DEFAULT 0,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS call_sessions (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    conversation_id UUID NOT NULL REFERENCES conversations(id) ON DELETE CASCADE,
    initiator_id    UUID NOT NULL REFERENCES users(id),
    call_type       VARCHAR(10) NOT NULL DEFAULT 'voice',
    status          VARCHAR(20) NOT NULL DEFAULT 'ringing',
    started_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    ended_at        TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_message_attachments_message ON message_attachments(message_id);
CREATE INDEX IF NOT EXISTS idx_conversation_invites_token ON conversation_invites(token);
CREATE INDEX IF NOT EXISTS idx_call_sessions_conversation ON call_sessions(conversation_id, status);
