CREATE TABLE publication_analytics (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  post_id UUID NOT NULL REFERENCES scheduled_posts(id) ON DELETE CASCADE,
  platform VARCHAR(30) NOT NULL,
  views INT DEFAULT 0,
  likes INT DEFAULT 0,
  shares INT DEFAULT 0,
  comments INT DEFAULT 0,
  recorded_at TIMESTAMP DEFAULT now(),
  updated_at TIMESTAMP
);

CREATE INDEX idx_analytics_post_id ON publication_analytics(post_id);
