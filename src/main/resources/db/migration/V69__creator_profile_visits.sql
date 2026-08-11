ALTER TABLE creator_profiles
    ADD COLUMN IF NOT EXISTS profile_visits INTEGER NOT NULL DEFAULT 0;

CREATE TABLE IF NOT EXISTS creator_profile_visits (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    creator_user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    viewer_user_id UUID REFERENCES users(id) ON DELETE SET NULL,
    visitor_key VARCHAR(120) NOT NULL,
    viewed_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_creator_profile_visits_visitor UNIQUE (creator_user_id, visitor_key)
);

CREATE INDEX IF NOT EXISTS idx_creator_profile_visits_creator ON creator_profile_visits(creator_user_id);
