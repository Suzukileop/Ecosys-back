ALTER TABLE creator_profiles
  ADD COLUMN IF NOT EXISTS team_members JSONB NOT NULL DEFAULT '[]'::jsonb,
  ADD COLUMN IF NOT EXISTS gallery_items JSONB NOT NULL DEFAULT '[]'::jsonb;
