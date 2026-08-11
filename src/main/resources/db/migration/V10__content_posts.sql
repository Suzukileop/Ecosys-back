CREATE TABLE content_posts (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  creator_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  title VARCHAR(300) NOT NULL,
  genre VARCHAR(100),
  media_url VARCHAR(500) NOT NULL,
  thumbnail_url VARCHAR(500),
  description TEXT,
  price_info VARCHAR(200),
  tools_used JSONB DEFAULT '[]',
  external_ref VARCHAR(50),
  is_public BOOLEAN DEFAULT true,
  views INT DEFAULT 0,
  likes INT DEFAULT 0,
  deleted_at TIMESTAMP,
  created_at TIMESTAMP DEFAULT now(),
  updated_at TIMESTAMP
);

CREATE INDEX idx_content_posts_creator ON content_posts(creator_id);
CREATE INDEX idx_content_posts_public ON content_posts(is_public) WHERE deleted_at IS NULL;
CREATE INDEX idx_content_posts_genre ON content_posts(genre) WHERE deleted_at IS NULL;
