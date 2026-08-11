ALTER TABLE creator_profiles
    ADD COLUMN IF NOT EXISTS cover_url VARCHAR(500);
