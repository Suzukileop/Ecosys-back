ALTER TABLE scheduled_posts
  ADD COLUMN IF NOT EXISTS config_id UUID REFERENCES scheduled_configs(id) ON DELETE SET NULL,
  ADD COLUMN IF NOT EXISTS niche_request_id UUID REFERENCES niche_requests(id) ON DELETE SET NULL;

CREATE INDEX IF NOT EXISTS idx_scheduled_posts_config_id ON scheduled_posts(config_id);
CREATE INDEX IF NOT EXISTS idx_scheduled_posts_niche_request_id ON scheduled_posts(niche_request_id);

-- Harmoniser la colonne status (V9 : VARCHAR(30))
ALTER TABLE scheduled_posts ALTER COLUMN status TYPE VARCHAR(20);
