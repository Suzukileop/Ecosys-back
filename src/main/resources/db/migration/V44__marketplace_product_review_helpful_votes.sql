ALTER TABLE marketplace_product_reviews
    ADD COLUMN IF NOT EXISTS helpful_yes_count INT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS helpful_no_count INT NOT NULL DEFAULT 0;

CREATE TABLE IF NOT EXISTS marketplace_product_review_helpful_votes (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    review_id  UUID NOT NULL REFERENCES marketplace_product_reviews(id) ON DELETE CASCADE,
    user_id    UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    helpful    BOOLEAN NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (review_id, user_id)
);

CREATE INDEX IF NOT EXISTS idx_review_helpful_votes_review
    ON marketplace_product_review_helpful_votes(review_id);
