ALTER TABLE creator_profiles
  ADD COLUMN IF NOT EXISTS portfolio_works jsonb NOT NULL DEFAULT '[]'::jsonb;
