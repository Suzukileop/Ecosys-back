-- History of profile photos and cover banners (kept when replaced).
CREATE TABLE IF NOT EXISTS creator_profile_images (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    kind VARCHAR(20) NOT NULL,
    url VARCHAR(1000) NOT NULL,
    cover_object_position_y INTEGER,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_creator_profile_images_kind CHECK (kind IN ('AVATAR', 'COVER')),
    CONSTRAINT uq_creator_profile_images_user_kind_url UNIQUE (user_id, kind, url)
);

CREATE INDEX IF NOT EXISTS idx_creator_profile_images_user_kind_created
    ON creator_profile_images (user_id, kind, created_at DESC);
