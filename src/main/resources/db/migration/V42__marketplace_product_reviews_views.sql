-- Product catalog enhancements: compare-at price, video metadata, reviews, unique views

ALTER TABLE marketplace_products
    ADD COLUMN IF NOT EXISTS compare_at_price_cents INT CHECK (compare_at_price_cents IS NULL OR compare_at_price_cents >= 0),
    ADD COLUMN IF NOT EXISTS video_duration_seconds INT CHECK (video_duration_seconds IS NULL OR video_duration_seconds > 0),
    ADD COLUMN IF NOT EXISTS video_resolution VARCHAR(10),
    ADD COLUMN IF NOT EXISTS is_bestseller BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS review_count INT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS average_rating NUMERIC(3, 2);

CREATE TABLE IF NOT EXISTS marketplace_product_views (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    product_id  UUID NOT NULL REFERENCES marketplace_products(id) ON DELETE CASCADE,
    user_id     UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    viewed_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (product_id, user_id)
);

CREATE INDEX IF NOT EXISTS idx_product_views_product ON marketplace_product_views(product_id);
CREATE INDEX IF NOT EXISTS idx_product_views_user ON marketplace_product_views(user_id);

CREATE TABLE IF NOT EXISTS marketplace_product_reviews (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    product_id  UUID NOT NULL REFERENCES marketplace_products(id) ON DELETE CASCADE,
    user_id     UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    rating      INT NOT NULL CHECK (rating BETWEEN 1 AND 5),
    comment     TEXT,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ,
    UNIQUE (product_id, user_id)
);

CREATE INDEX IF NOT EXISTS idx_product_reviews_product ON marketplace_product_reviews(product_id);
