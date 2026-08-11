ALTER TABLE creator_profiles
  ADD COLUMN IF NOT EXISTS social_links JSONB,
  ADD COLUMN IF NOT EXISTS portfolio_count INT DEFAULT 0;
