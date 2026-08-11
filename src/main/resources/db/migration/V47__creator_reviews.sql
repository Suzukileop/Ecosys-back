CREATE TABLE IF NOT EXISTS creator_reviews (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    creator_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    reviewer_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    rating INT NOT NULL CHECK (rating >= 1 AND rating <= 5),
    comment TEXT,
    would_recommend BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP,
    CONSTRAINT uq_creator_reviews_creator_reviewer UNIQUE (creator_id, reviewer_id)
);

CREATE INDEX IF NOT EXISTS idx_creator_reviews_creator_id ON creator_reviews(creator_id);
CREATE INDEX IF NOT EXISTS idx_creator_reviews_created_at ON creator_reviews(created_at DESC);
