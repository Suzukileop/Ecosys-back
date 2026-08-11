-- Allow multiple reviews per user per product (max 3/day enforced in application code).
-- Product average_rating and review_count use each user's latest review only.

ALTER TABLE marketplace_product_reviews
    DROP CONSTRAINT IF EXISTS marketplace_product_reviews_product_id_user_id_key;

CREATE INDEX IF NOT EXISTS idx_product_reviews_user_product_created
    ON marketplace_product_reviews (user_id, product_id, created_at DESC);

-- Backfill denormalized ratings: one count per distinct reviewer, avg of latest rating each.
UPDATE marketplace_products p
SET
    review_count = COALESCE(sub.cnt, 0),
    average_rating = sub.avg_rating
FROM (
    SELECT
        latest.product_id,
        COUNT(*)::int AS cnt,
        ROUND(AVG(latest.rating)::numeric, 2) AS avg_rating
    FROM (
        SELECT DISTINCT ON (product_id, user_id) product_id, rating
        FROM marketplace_product_reviews
        ORDER BY product_id, user_id, created_at DESC
    ) latest
    GROUP BY latest.product_id
) sub
WHERE p.id = sub.product_id;

UPDATE marketplace_products
SET review_count = 0,
    average_rating = NULL
WHERE id NOT IN (SELECT DISTINCT product_id FROM marketplace_product_reviews);
