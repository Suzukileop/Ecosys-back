-- Trust stars replace the creator review / reputation system: one star per user per account.
CREATE TABLE IF NOT EXISTS creator_stars (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    creator_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_creator_stars_creator_user UNIQUE (creator_id, user_id),
    CONSTRAINT ck_creator_stars_not_self CHECK (creator_id <> user_id)
);

CREATE INDEX IF NOT EXISTS idx_creator_stars_creator_id ON creator_stars(creator_id);
CREATE INDEX IF NOT EXISTS idx_creator_stars_user_id ON creator_stars(user_id);

DROP TABLE IF EXISTS creator_reviews;
