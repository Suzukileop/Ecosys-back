CREATE TABLE scheduled_posts (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  client_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  platform VARCHAR(30) NOT NULL,
  content_url VARCHAR(500),
  content_type VARCHAR(20) DEFAULT 'EXTERNAL_URL',
  caption TEXT,
  niche_ref VARCHAR(20),
  scheduled_at TIMESTAMP NOT NULL,
  status VARCHAR(30) DEFAULT 'SCHEDULED',
  published_at TIMESTAMP,
  error_message TEXT,
  created_at TIMESTAMP DEFAULT now(),
  updated_at TIMESTAMP
);

CREATE INDEX idx_scheduled_posts_client ON scheduled_posts(client_id);
CREATE INDEX idx_scheduled_posts_scheduled_at ON scheduled_posts(scheduled_at);
CREATE INDEX idx_scheduled_posts_status ON scheduled_posts(status);
CREATE INDEX idx_scheduled_posts_catchup ON scheduled_posts(status, scheduled_at)
  WHERE status = 'SCHEDULED';
