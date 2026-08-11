CREATE TABLE creator_follows (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    follower_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    creator_id  UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_at  TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_creator_follows_pair UNIQUE (follower_id, creator_id),
    CONSTRAINT chk_creator_follows_not_self CHECK (follower_id <> creator_id)
);

CREATE INDEX idx_creator_follows_follower ON creator_follows(follower_id);
CREATE INDEX idx_creator_follows_creator ON creator_follows(creator_id);
