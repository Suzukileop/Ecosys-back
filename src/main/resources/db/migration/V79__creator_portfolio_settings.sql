ALTER TABLE creator_profiles
    ADD COLUMN IF NOT EXISTS portfolio_settings JSONB NOT NULL DEFAULT '{}'::jsonb;
