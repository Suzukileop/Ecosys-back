ALTER TABLE creator_profiles
    ADD COLUMN IF NOT EXISTS studio_content_headline VARCHAR(160);

COMMENT ON COLUMN creator_profiles.studio_content_headline IS
    'Custom headline shown above the content tab publish button in Creator Studio';
