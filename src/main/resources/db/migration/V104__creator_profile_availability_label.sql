ALTER TABLE creator_profiles
    ADD COLUMN IF NOT EXISTS availability_label VARCHAR(80);

COMMENT ON COLUMN creator_profiles.availability_label IS
    'Custom status label shown when the creator is available (empty = default Available).';
