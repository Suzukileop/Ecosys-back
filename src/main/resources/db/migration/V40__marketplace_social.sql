CREATE TABLE content_reactions (
  id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id     UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  target_type VARCHAR(20) NOT NULL,
  target_id   UUID NOT NULL,
  type        VARCHAR(20) NOT NULL,
  created_at  TIMESTAMP NOT NULL DEFAULT now(),
  CONSTRAINT uq_content_reactions_user_target UNIQUE (user_id, target_type, target_id)
);

CREATE INDEX idx_content_reactions_target ON content_reactions(target_type, target_id);

CREATE TABLE content_favorites (
  id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id     UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  target_type VARCHAR(20) NOT NULL,
  target_id   UUID NOT NULL,
  created_at  TIMESTAMP NOT NULL DEFAULT now(),
  CONSTRAINT uq_content_favorites_user_target UNIQUE (user_id, target_type, target_id)
);

CREATE INDEX idx_content_favorites_user ON content_favorites(user_id);
CREATE INDEX idx_content_favorites_target ON content_favorites(target_type, target_id);

CREATE TABLE content_comments (
  id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  target_type VARCHAR(20) NOT NULL,
  target_id   UUID NOT NULL,
  user_id     UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  comment     TEXT NOT NULL,
  parent_id   UUID REFERENCES content_comments(id) ON DELETE CASCADE,
  created_at  TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_content_comments_target ON content_comments(target_type, target_id);

CREATE TABLE content_reports (
  id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  target_type VARCHAR(20) NOT NULL,
  target_id   UUID NOT NULL,
  reporter_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  reason      VARCHAR(50) NOT NULL,
  details     TEXT,
  status      VARCHAR(20) NOT NULL DEFAULT 'PENDING',
  admin_notes TEXT,
  created_at  TIMESTAMP NOT NULL DEFAULT now(),
  updated_at  TIMESTAMP
);

CREATE INDEX idx_content_reports_status ON content_reports(status);

CREATE TABLE content_shares (
  id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  target_type VARCHAR(20) NOT NULL,
  target_id   UUID NOT NULL,
  platform    VARCHAR(50) NOT NULL,
  user_id     UUID REFERENCES users(id) ON DELETE SET NULL,
  created_at  TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_content_shares_target ON content_shares(target_type, target_id);
