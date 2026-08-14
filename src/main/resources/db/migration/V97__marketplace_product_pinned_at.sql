-- Pin products to the top of My Product / shop listings.
ALTER TABLE marketplace_products
    ADD COLUMN IF NOT EXISTS pinned_at TIMESTAMP;

CREATE INDEX IF NOT EXISTS idx_marketplace_products_creator_pinned
    ON marketplace_products (creator_id, pinned_at DESC NULLS LAST, created_at DESC);
