ALTER TABLE chat_messages ALTER COLUMN sender_id DROP NOT NULL;

ALTER TABLE chat_messages
  ADD COLUMN IF NOT EXISTS sender_type VARCHAR(10) DEFAULT 'HUMAN',
  ADD COLUMN IF NOT EXISTS niche_request_id UUID REFERENCES niche_requests(id) ON DELETE SET NULL;

CREATE INDEX IF NOT EXISTS idx_chat_messages_niche_request_id ON chat_messages(niche_request_id);
