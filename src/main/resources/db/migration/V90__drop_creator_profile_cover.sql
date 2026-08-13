-- Remove creator profile cover banner (avatar-only header).
DELETE FROM creator_profile_images WHERE kind = 'COVER';

ALTER TABLE creator_profiles DROP COLUMN IF EXISTS cover_url;
ALTER TABLE creator_profiles DROP COLUMN IF EXISTS cover_object_position_y;

ALTER TABLE creator_profile_images DROP COLUMN IF EXISTS cover_object_position_y;

ALTER TABLE creator_profile_images DROP CONSTRAINT IF EXISTS ck_creator_profile_images_kind;
ALTER TABLE creator_profile_images
    ADD CONSTRAINT ck_creator_profile_images_kind CHECK (kind IN ('AVATAR'));
