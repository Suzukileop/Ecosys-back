ALTER TABLE creator_profiles
    ADD COLUMN IF NOT EXISTS cover_object_position_y SMALLINT NOT NULL DEFAULT 50;
