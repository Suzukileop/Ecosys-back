ALTER TABLE creator_profiles
  ADD COLUMN IF NOT EXISTS about_us JSONB;
